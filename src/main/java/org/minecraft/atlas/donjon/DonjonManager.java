package org.minecraft.atlas.donjon;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.minecraft.atlas.Atlas;
import org.minecraft.atlas.faction.Faction;
import org.minecraft.atlas.faction.FactionManager;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class DonjonManager {

    // -------------------------------------------------------------------------
    // In-memory state
    // -------------------------------------------------------------------------

    /** All registered donjons, keyed by their unique ID. */
    private static final Map<String, Donjon> donjons = new LinkedHashMap<>();

    /** Maps a totem block location key ("world:x:y:z") to a donjon ID. */
    private static final Map<String, String> totemLocationToId = new HashMap<>();

    /** Maps a spawned mob UUID to the donjon ID it belongs to. */
    private static final Map<UUID, String> entityToDonjonId = new HashMap<>();

    /** Type-specific configuration, loaded from config.yml. */
    private static final Map<DonjonType, DonjonTypeConfig> typeConfigMap = new EnumMap<>(DonjonType.class);

    // -------------------------------------------------------------------------
    // Global settings (loaded from config)
    // -------------------------------------------------------------------------

    private static long activationIntervalMs  = 6L  * 3_600_000L;
    private static long idleTimeoutMs         = 24L * 3_600_000L;
    private static long lastActivationTime    = 0L;

    /** {minLevel, maxLevel, weight} per difficulty range */
    private static int[][] difficultyRanges = {
            {0,  9,  66}, {10, 49, 14}, {50, 74, 10},
            {75, 89,  5}, {90, 94,  3}, {95, 99,  2}
    };

    /** Rarity weights indexed by DonjonRarity.ordinal() */
    private static int[] rarityWeights = {66, 14, 10, 5, 3, 2};

    // -------------------------------------------------------------------------
    // PDC keys (initialised in loadConfig, after Atlas.instance is set)
    // -------------------------------------------------------------------------

    public static NamespacedKey keyDonjonId;
    public static NamespacedKey keyDonjonWave;
    public static NamespacedKey keyIsBoss;
    public static NamespacedKey keyTotemDisplay;

    // -------------------------------------------------------------------------
    // Config loading
    // -------------------------------------------------------------------------

    public static void loadConfig(FileConfiguration config) {
        keyDonjonId    = new NamespacedKey(Atlas.instance, "donjon_id");
        keyDonjonWave  = new NamespacedKey(Atlas.instance, "donjon_wave");
        keyIsBoss      = new NamespacedKey(Atlas.instance, "donjon_is_boss");
        keyTotemDisplay = new NamespacedKey(Atlas.instance, "donjon_totem_display");

        ConfigurationSection sec = config.getConfigurationSection("donjon");
        if (sec == null) return;

        activationIntervalMs = sec.getLong("activation_interval_hours", 6) * 3_600_000L;
        idleTimeoutMs        = sec.getLong("idle_timeout_hours", 24)        * 3_600_000L;
        lastActivationTime   = sec.getLong("last_activation_time", 0);

        // Difficulty ranges
        List<?> diffList = sec.getList("difficulty");
        if (diffList != null && !diffList.isEmpty()) {
            List<int[]> ranges = new ArrayList<>();
            for (Object obj : diffList) {
                if (obj instanceof Map<?, ?> m) {
                    ranges.add(new int[]{
                            toInt(m.get("min")),
                            toInt(m.get("max")),
                            toInt(m.get("weight"))
                    });
                }
            }
            if (!ranges.isEmpty()) difficultyRanges = ranges.toArray(new int[0][]);
        }

        // Rarity weights
        ConfigurationSection raritySec = sec.getConfigurationSection("rarity");
        if (raritySec != null) {
            rarityWeights = new int[]{
                    raritySec.getInt("common",    66),
                    raritySec.getInt("rare",      14),
                    raritySec.getInt("epic",      10),
                    raritySec.getInt("legendary",  5),
                    raritySec.getInt("mystic",     3),
                    raritySec.getInt("goddess",    2)
            };
        }

        // Type configs
        ConfigurationSection typesSec = sec.getConfigurationSection("types");
        if (typesSec != null) {
            for (DonjonType type : DonjonType.values()) {
                ConfigurationSection typeSec = typesSec.getConfigurationSection(type.getConfigKey());
                if (typeSec != null) {
                    typeConfigMap.put(type, DonjonTypeConfig.load(typeSec));
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    public static void saveDonjonConfig(FileConfiguration config) {
        config.set("donjon.last_activation_time", lastActivationTime);
        config.set("donjon.instances", null);

        if (donjons.isEmpty()) return;

        ConfigurationSection instances = config.createSection("donjon.instances");
        for (Donjon d : donjons.values()) {
            ConfigurationSection s = instances.createSection(d.getId());
            s.set("type",             d.getType().getConfigKey());
            s.set("name",             d.getName());
            s.set("level",            d.getLevel());
            s.set("rarity",           d.getRarity().name());
            s.set("status",           d.getStatus().name());
            s.set("activation_time",  d.getActivationTime());
            s.set("timeout_warned",   d.isTimeoutWarned());

            Location c = d.getCenter();
            s.set("world",    c.getWorld().getName());
            s.set("center_x", c.getBlockX());
            s.set("center_y", c.getBlockY());
            s.set("center_z", c.getBlockZ());

            Location t = d.getTotemLocation();
            if (t != null) {
                s.set("totem_x", t.getBlockX());
                s.set("totem_y", t.getBlockY());
                s.set("totem_z", t.getBlockZ());
            }

            if (d.getTextDisplayUUID() != null) {
                s.set("text_display_uuid", d.getTextDisplayUUID().toString());
            }
        }
    }

    public static void loadDonjons(FileConfiguration config) {
        donjons.clear();
        totemLocationToId.clear();

        ConfigurationSection instances = config.getConfigurationSection("donjon.instances");
        if (instances == null) return;

        for (String id : instances.getKeys(false)) {
            ConfigurationSection s = instances.getConfigurationSection(id);
            if (s == null) continue;

            DonjonType type = DonjonType.fromConfigKey(s.getString("type", ""));
            if (type == null) continue;

            String worldName = s.getString("world");
            if (worldName == null) continue;
            World world = Bukkit.getWorld(worldName);
            if (world == null) continue;

            Location center = new Location(world,
                    s.getInt("center_x"), s.getInt("center_y"), s.getInt("center_z"));

            String name = s.getString("name", "Unknown");
            int level = s.getInt("level", 0);
            DonjonRarity rarity = parseEnum(DonjonRarity.class, s.getString("rarity"), DonjonRarity.COMMON);

            Donjon donjon = new Donjon(id, name, type, center, level, rarity);
            donjon.setStatus(parseEnum(DonjonStatus.class, s.getString("status"), DonjonStatus.IDLE));
            donjon.setActivationTime(s.getLong("activation_time", 0));
            donjon.setTimeoutWarned(s.getBoolean("timeout_warned", false));

            if (s.contains("totem_x")) {
                Location totemLoc = new Location(world,
                        s.getInt("totem_x"), s.getInt("totem_y"), s.getInt("totem_z"));
                donjon.setTotemLocation(totemLoc);
                totemLocationToId.put(locationKey(totemLoc), id);
            }

            String displayUUIDStr = s.getString("text_display_uuid");
            if (displayUUIDStr != null) {
                try { donjon.setTextDisplayUUID(UUID.fromString(displayUUIDStr)); }
                catch (IllegalArgumentException ignored) {}
            }

            computeProtectedChunks(donjon);
            donjons.put(id, donjon);
        }
    }

    // -------------------------------------------------------------------------
    // Scheduler
    // -------------------------------------------------------------------------

    public static void schedule(Plugin plugin) {
        // Every 2 s: player presence check
        Bukkit.getScheduler().runTaskTimer(plugin, DonjonManager::tick, 40L, 40L);
        // Every 60 s: activation timer, timeout checks
        Bukkit.getScheduler().runTaskTimer(plugin, DonjonManager::minuteTick, 1200L, 1200L);
    }

    private static void tick() {
        for (Donjon d : donjons.values()) {
            if (d.getStatus() == DonjonStatus.ACTIVE && d.isInProgress()) {
                checkPlayerPresence(d);
            }
        }
    }

    private static void minuteTick() {
        long now = System.currentTimeMillis();

        // Activation timer
        if (!donjons.isEmpty() && (now - lastActivationTime) >= activationIntervalMs) {
            activateRandomDonjon();
            lastActivationTime = now;
            Atlas.instance.getConfig().set("donjon.last_activation_time", lastActivationTime);
            Atlas.instance.saveConfig();
        }

        // Timeout checks
        for (Donjon d : new ArrayList<>(donjons.values())) {
            if (d.getStatus() != DonjonStatus.ACTIVE || d.getActivationTime() == 0) continue;

            long elapsed = now - d.getActivationTime();
            long warnThreshold = idleTimeoutMs - 3_600_000L; // warn 1 h before timeout

            if (!d.isTimeoutWarned() && elapsed >= warnThreshold) {
                d.setTimeoutWarned(true);
                broadcastAll(Component.text("⚠ Donjon ", NamedTextColor.YELLOW)
                        .append(Component.text(d.getName(), d.getRarity().getColor()))
                        .append(Component.text(" will become idle in 1 hour!", NamedTextColor.YELLOW)));
            }

            if (elapsed >= idleTimeoutMs) {
                setIdle(d, "timed out");
            }
        }
    }

    private static void checkPlayerPresence(Donjon donjon) {
        World world = donjon.getCenter().getWorld();
        Set<UUID> inside = new HashSet<>();

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().equals(world) && isInDonjon(p.getLocation(), donjon)) {
                inside.add(p.getUniqueId());
            }
        }

        donjon.getPlayersInside().clear();
        donjon.getPlayersInside().addAll(inside);

        if (inside.isEmpty()) {
            resetDonjon(donjon);
        }
    }

    // -------------------------------------------------------------------------
    // Donjon creation / deletion
    // -------------------------------------------------------------------------

    public static Donjon createDonjon(DonjonType type, Location center, Location totemLocation) {
        DonjonTypeConfig cfg = typeConfigMap.get(type);
        if (cfg == null) {
            Atlas.instance.getLogger().warning("No config for donjon type: " + type);
            return null;
        }

        String id = UUID.randomUUID().toString().substring(0, 8);
        int level = randomLevel();
        DonjonRarity rarity = randomRarity();
        String name = generateName(type, cfg);

        Donjon donjon = new Donjon(id, name, type, center, level, rarity);
        donjon.setStatus(DonjonStatus.IDLE);
        donjon.setTotemLocation(totemLocation);

        computeProtectedChunks(donjon, cfg);
        placeTotem(donjon, totemLocation);
        spawnOrUpdateNametag(donjon);

        donjons.put(id, donjon);
        totemLocationToId.put(locationKey(totemLocation), id);

        return donjon;
    }

    public static void deleteDonjon(String id) {
        Donjon donjon = donjons.remove(id);
        if (donjon == null) return;

        // Remove totem entry
        if (donjon.getTotemLocation() != null) {
            totemLocationToId.remove(locationKey(donjon.getTotemLocation()));
        }

        // Remove text display
        if (donjon.getTextDisplayUUID() != null) {
            Entity e = Bukkit.getEntity(donjon.getTextDisplayUUID());
            if (e != null) e.remove();
        }

        // Kill all tracked mobs
        clearEntities(donjon);
    }

    // -------------------------------------------------------------------------
    // Activation
    // -------------------------------------------------------------------------

    public static void activateDonjon(Donjon donjon) {
        donjon.setStatus(DonjonStatus.ACTIVE);
        donjon.setActivationTime(System.currentTimeMillis());
        donjon.setTimeoutWarned(false);
        spawnOrUpdateNametag(donjon);

        broadcastAll(Component.text("⚡ The donjon ", NamedTextColor.GOLD)
                .append(Component.text(donjon.getName(), donjon.getRarity().getColor()))
                .append(Component.text(" [Lv." + donjon.getLevel() + " — " + donjon.getRarity().getDisplayName() + "]", NamedTextColor.YELLOW))
                .append(Component.text(" is now ACTIVE!", NamedTextColor.GOLD)));
    }

    // -------------------------------------------------------------------------
    // Donjon start (player triggers)
    // -------------------------------------------------------------------------

    public static boolean startDonjon(Donjon donjon, Player starter, String factionName) {
        DonjonTypeConfig cfg = typeConfigMap.get(donjon.getType());
        if (cfg == null) return false;

        donjon.setWaves(generateWaves(donjon, cfg));
        donjon.setCurrentWaveIndex(0);
        donjon.setInProgress(true);
        donjon.setStartingFaction(factionName);
        donjon.getBossDamageMap().clear();

        Faction faction = FactionManager.getFaction(factionName);
        NamedTextColor factionColor = faction != null ? faction.getColor() : NamedTextColor.WHITE;

        broadcastAll(Component.text("⚔ Faction ", NamedTextColor.GOLD)
                .append(Component.text("[" + factionName + "]", factionColor))
                .append(Component.text(" has started the donjon ", NamedTextColor.GOLD))
                .append(Component.text(donjon.getName(), donjon.getRarity().getColor()))
                .append(Component.text("!", NamedTextColor.GOLD)));

        startWave(donjon, 0);
        return true;
    }

    // -------------------------------------------------------------------------
    // Wave management
    // -------------------------------------------------------------------------

    private static List<DonjonWave> generateWaves(Donjon donjon, DonjonTypeConfig cfg) {
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        double levelFactor = donjon.getLevel() / 99.0;

        int numWaves = cfg.getMinWaves() + (int) Math.round(levelFactor * (cfg.getMaxWaves() - cfg.getMinWaves()));
        int baseMobCount = cfg.getMinMobsPerWave()
                + (int) Math.round(levelFactor * (cfg.getMaxMobsPerWave() - cfg.getMinMobsPerWave()));

        List<DonjonWave> waves = new ArrayList<>();
        List<String> mobTypes = cfg.getMobTypes();
        List<String> bossTypes = cfg.getBossTypes();

        for (int i = 1; i <= numWaves; i++) {
            boolean isBoss = (i == numWaves);
            List<String> mobsForWave = new ArrayList<>();

            if (isBoss) {
                int bossCount = cfg.getBossCountMin()
                        + (cfg.getBossCountMax() > cfg.getBossCountMin()
                        ? rand.nextInt(cfg.getBossCountMax() - cfg.getBossCountMin() + 1) : 0);
                if (!bossTypes.isEmpty()) {
                    for (int j = 0; j < bossCount; j++) {
                        mobsForWave.add(bossTypes.get(rand.nextInt(bossTypes.size())));
                    }
                }
            } else {
                double variation = 0.8 + rand.nextDouble() * 0.4;
                int count = Math.max(cfg.getMinMobsPerWave(), (int)(baseMobCount * variation));
                if (!mobTypes.isEmpty()) {
                    for (int j = 0; j < count; j++) {
                        mobsForWave.add(mobTypes.get(rand.nextInt(mobTypes.size())));
                    }
                }
            }

            waves.add(new DonjonWave(i, isBoss, mobsForWave));
        }

        return waves;
    }

    static void startWave(Donjon donjon, int waveIndex) {
        if (waveIndex >= donjon.getWaves().size()) {
            completeDonjon(donjon);
            return;
        }

        DonjonWave wave = donjon.getWaves().get(waveIndex);
        donjon.setCurrentWaveIndex(waveIndex);

        DonjonTypeConfig cfg = typeConfigMap.get(donjon.getType());
        if (cfg == null) return;

        double lf = donjon.getLevel() / 99.0;
        double hpMult = cfg.getBaseHpMultiplier() + lf * (cfg.getMaxHpMultiplier() - cfg.getBaseHpMultiplier());
        double atkMult = cfg.getBaseAttackMultiplier() + lf * (cfg.getMaxAttackMultiplier() - cfg.getBaseAttackMultiplier());

        if (wave.isBossWave()) {
            hpMult  *= cfg.getBossHpMultiplier();
            atkMult *= cfg.getBossAttackMultiplier();
        }

        double finalHpMult = hpMult;
        double finalAtkMult = atkMult;
        double bossSpeedMult = cfg.getBossSpeedMultiplier();
        String donjonId = donjon.getId();

        for (String mobType : wave.getMobTypesToSpawn()) {
            Location spawnLoc = randomSpawnLocation(donjon);
            List<UUID> uuids = spawnMobEntity(mobType, spawnLoc, finalHpMult, finalAtkMult,
                    wave.isBossWave(), wave.isBossWave() ? bossSpeedMult : 1.0,
                    donjonId, waveIndex);
            for (UUID uuid : uuids) {
                wave.addSpawnedEntity(uuid);
                entityToDonjonId.put(uuid, donjonId);
            }
        }
        wave.setStarted(true);

        broadcastToDonjonPlayers(donjon,
                wave.isBossWave()
                        ? Component.text("☠ BOSS WAVE — Wave " + wave.getWaveNumber() + "!", NamedTextColor.DARK_RED)
                        : Component.text("⚡ Wave " + wave.getWaveNumber() + " begins!", NamedTextColor.YELLOW));
    }

    // -------------------------------------------------------------------------
    // Entity death / damage tracking
    // -------------------------------------------------------------------------

    public static void onEntityDeath(LivingEntity entity) {
        UUID uuid = entity.getUniqueId();
        String donjonId = entityToDonjonId.remove(uuid);
        if (donjonId == null) return;

        Donjon donjon = donjons.get(donjonId);
        if (donjon == null || !donjon.isInProgress()) return;

        DonjonWave wave = donjon.getCurrentWave();
        if (wave == null) return;

        wave.removeSpawnedEntity(uuid);

        // If baby zombie dies, also kill its vehicle (chicken in a jockey)
        Entity vehicle = entity.getVehicle();
        if (vehicle != null) {
            UUID vehicleUUID = vehicle.getUniqueId();
            donjon.getAuxiliaryEntities().remove(vehicleUUID);
            entityToDonjonId.remove(vehicleUUID);
            wave.removeSpawnedEntity(vehicleUUID);
            vehicle.remove();
        }

        if (wave.isCompleted()) {
            int next = donjon.getCurrentWaveIndex() + 1;
            if (next >= donjon.getWaves().size()) {
                completeDonjon(donjon);
            } else {
                broadcastToDonjonPlayers(donjon, Component.text(
                        "✔ Wave " + wave.getWaveNumber() + " cleared! Next wave in 5 s...", NamedTextColor.GREEN));
                Bukkit.getScheduler().runTaskLater(Atlas.instance, () -> {
                    if (donjon.isInProgress()) startWave(donjon, next);
                }, 100L);
            }
        }
    }

    public static void onBossDamage(LivingEntity boss, double damage, String factionName) {
        UUID uuid = boss.getUniqueId();
        String donjonId = entityToDonjonId.get(uuid);
        if (donjonId == null) return;

        Donjon donjon = donjons.get(donjonId);
        if (donjon == null || !donjon.isInProgress()) return;

        DonjonWave wave = donjon.getCurrentWave();
        if (wave == null || !wave.isBossWave()) return;

        donjon.getBossDamageMap().merge(factionName, damage, Double::sum);
    }

    // -------------------------------------------------------------------------
    // Donjon completion / reset / idle
    // -------------------------------------------------------------------------

    private static void completeDonjon(Donjon donjon) {
        Map<String, Double> dmgMap = donjon.getBossDamageMap();
        double totalDmg = dmgMap.values().stream().mapToDouble(Double::doubleValue).sum();

        DonjonTypeConfig cfg = typeConfigMap.get(donjon.getType());
        long baseExp = (cfg != null) ? cfg.getMinExpReward() : 500L;
        long maxExp  = (cfg != null) ? cfg.getMaxExpReward() : 10000L;
        long totalExp = (long)((baseExp + (donjon.getLevel() / 99.0) * (maxExp - baseExp))
                * donjon.getRarity().getExpMultiplier());

        // Find leading faction
        String mainFaction = donjon.getStartingFaction();
        if (totalDmg > 0) {
            mainFaction = dmgMap.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(mainFaction);
        }

        // Award exp proportional to boss damage
        if (totalDmg > 0) {
            for (Map.Entry<String, Double> e : dmgMap.entrySet()) {
                int share = (int)(totalExp * (e.getValue() / totalDmg));
                if (share > 0) FactionManager.addExpToFaction(e.getKey(), share);
            }
        }

        String mainDisplay = mainFaction != null ? mainFaction : "Unknown";
        Faction mainF = mainFaction != null ? FactionManager.getFaction(mainFaction) : null;
        NamedTextColor mainColor = mainF != null ? mainF.getColor() : NamedTextColor.WHITE;

        broadcastAll(Component.text("★ The donjon ", NamedTextColor.GOLD)
                .append(Component.text(donjon.getName(), donjon.getRarity().getColor()))
                .append(Component.text(" was cleared by faction ", NamedTextColor.GOLD))
                .append(Component.text("[" + mainDisplay + "]", mainColor))
                .append(Component.text("! " + totalExp + " exp distributed.", NamedTextColor.GOLD)));

        setIdle(donjon, null);
    }

    static void resetDonjon(Donjon donjon) {
        donjon.setInProgress(false);
        donjon.setCurrentWaveIndex(0);
        donjon.setStartingFaction(null);
        donjon.getBossDamageMap().clear();
        donjon.getPlayersInside().clear();
        clearEntities(donjon);
        donjon.getWaves().clear();
    }

    public static void setIdle(Donjon donjon, String reason) {
        donjon.setStatus(DonjonStatus.IDLE);
        donjon.setInProgress(false);
        donjon.setActivationTime(0);
        donjon.setTimeoutWarned(false);
        donjon.setStartingFaction(null);
        donjon.getBossDamageMap().clear();
        clearEntities(donjon);
        donjon.getWaves().clear();
        donjon.setCurrentWaveIndex(0);
        spawnOrUpdateNametag(donjon);

        if ("timed out".equals(reason)) {
            broadcastAll(Component.text("⌛ Donjon ", NamedTextColor.GRAY)
                    .append(Component.text(donjon.getName(), donjon.getRarity().getColor()))
                    .append(Component.text(" has become idle (timed out).", NamedTextColor.GRAY)));
        }
    }

    private static void clearEntities(Donjon donjon) {
        for (DonjonWave wave : donjon.getWaves()) {
            for (UUID uuid : new HashSet<>(wave.getSpawnedEntities())) {
                Entity e = Bukkit.getEntity(uuid);
                if (e != null) e.remove();
                entityToDonjonId.remove(uuid);
            }
            wave.getSpawnedEntities().clear();
        }
        for (UUID uuid : new HashSet<>(donjon.getAuxiliaryEntities())) {
            Entity e = Bukkit.getEntity(uuid);
            if (e != null) e.remove();
            entityToDonjonId.remove(uuid);
        }
        donjon.getAuxiliaryEntities().clear();
    }

    // -------------------------------------------------------------------------
    // Nametag
    // -------------------------------------------------------------------------

    public static void spawnOrUpdateNametag(Donjon donjon) {
        if (donjon.getTotemLocation() == null) return;
        Location totemLoc = donjon.getTotemLocation();
        Location displayLoc = totemLoc.clone().add(0.5, 3.5, 0.5);

        TextDisplay display = null;
        UUID existingUUID = donjon.getTextDisplayUUID();
        if (existingUUID != null) {
            Entity e = Bukkit.getEntity(existingUUID);
            if (e instanceof TextDisplay td) display = td;
        }

        if (display == null) {
            if (!displayLoc.isChunkLoaded()) return;
            final String id = donjon.getId();
            display = displayLoc.getWorld().spawn(displayLoc, TextDisplay.class, td -> {
                td.setBillboard(Display.Billboard.CENTER);
                td.setPersistent(true);
                td.setInvulnerable(true);
                td.setGravity(false);
                td.getPersistentDataContainer().set(keyTotemDisplay, PersistentDataType.STRING, id);
            });
            donjon.setTextDisplayUUID(display.getUniqueId());
        }

        display.text(buildNametag(donjon));
    }

    public static void restoreNametag(TextDisplay display) {
        String id = display.getPersistentDataContainer().get(keyTotemDisplay, PersistentDataType.STRING);
        if (id == null) return;
        Donjon donjon = donjons.get(id);
        if (donjon == null) {
            // Orphaned display from a deleted donjon
            display.remove();
            return;
        }
        donjon.setTextDisplayUUID(display.getUniqueId());
        display.text(buildNametag(donjon));
    }

    private static Component buildNametag(Donjon donjon) {
        DonjonTypeConfig cfg = typeConfigMap.get(donjon.getType());
        String typeName = cfg != null ? cfg.getDisplayName() : donjon.getType().getDisplayName();

        Component statusLine = donjon.getStatus() == DonjonStatus.ACTIVE
                ? Component.text("◆ ACTIVE ◆", NamedTextColor.GREEN)
                : Component.text("◇ IDLE ◇", NamedTextColor.DARK_GRAY);

        return Component.text("[" + typeName + "] ", NamedTextColor.GRAY)
                .append(Component.text(donjon.getName(), donjon.getRarity().getColor()))
                .append(Component.newline())
                .append(Component.text("Lv." + donjon.getLevel() + " ✦ ", NamedTextColor.YELLOW))
                .append(Component.text(donjon.getRarity().getDisplayName(), donjon.getRarity().getColor()))
                .append(Component.newline())
                .append(statusLine);
    }

    // -------------------------------------------------------------------------
    // Totem placement
    // -------------------------------------------------------------------------

    private static void placeTotem(Donjon donjon, Location loc) {
        World w = loc.getWorld();
        if (w == null) return;
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();

        // Core: Respawn Anchor at y (the interactive trigger)
        w.getBlockAt(x, y,     z).setType(Material.RESPAWN_ANCHOR);
        // Below: Crying Obsidian foundation
        w.getBlockAt(x, y - 1, z).setType(Material.CRYING_OBSIDIAN);
        // Crying Obsidian ring at y (N/S/E/W)
        w.getBlockAt(x + 1, y, z).setType(Material.CRYING_OBSIDIAN);
        w.getBlockAt(x - 1, y, z).setType(Material.CRYING_OBSIDIAN);
        w.getBlockAt(x, y, z + 1).setType(Material.CRYING_OBSIDIAN);
        w.getBlockAt(x, y, z - 1).setType(Material.CRYING_OBSIDIAN);
        // Top spine
        w.getBlockAt(x, y + 1, z).setType(Material.CRYING_OBSIDIAN);
        w.getBlockAt(x, y + 2, z).setType(Material.OBSIDIAN);
    }

    // -------------------------------------------------------------------------
    // Mob spawning
    // -------------------------------------------------------------------------

    /**
     * Spawns one logical "mob entry" (which may produce multiple entities for special
     * types like CHICKEN_JOCKEY).  Returns the UUIDs that should be tracked in the wave.
     */
    @SuppressWarnings("unchecked")
    private static List<UUID> spawnMobEntity(String mobTypeName, Location loc,
                                              double hpMult, double atkMult,
                                              boolean isBoss, double speedMult,
                                              String donjonId, int waveIndex) {
        World world = loc.getWorld();
        if (world == null) return List.of();

        if ("CHICKEN_JOCKEY".equalsIgnoreCase(mobTypeName)) {
            return spawnChickenJockey(loc, hpMult, atkMult, isBoss, speedMult, donjonId, waveIndex);
        }

        LivingEntity entity = spawnSingleMob(mobTypeName, loc);
        if (entity == null) return List.of();

        applyMultipliers(entity, hpMult, atkMult, speedMult);
        tagEntity(entity, donjonId, waveIndex, isBoss);
        return List.of(entity.getUniqueId());
    }

    private static LivingEntity spawnSingleMob(String name, Location loc) {
        World w = loc.getWorld();
        if (w == null) return null;
        return switch (name.toUpperCase()) {
            case "ZOMBIE"      -> w.spawn(loc, Zombie.class);
            case "BABY_ZOMBIE" -> w.spawn(loc, Zombie.class, z -> z.setBaby(true));
            case "HUSK"        -> w.spawn(loc, Husk.class);
            case "SKELETON"    -> w.spawn(loc, Skeleton.class);
            case "STRAY"       -> w.spawn(loc, Stray.class);
            case "BOGGED"      -> w.spawn(loc, Bogged.class);
            case "SPIDER"      -> w.spawn(loc, Spider.class);
            case "CAVE_SPIDER" -> w.spawn(loc, CaveSpider.class);
            case "SLIME"       -> w.spawn(loc, Slime.class, s -> s.setSize(2));
            case "SILVERFISH"  -> w.spawn(loc, Silverfish.class);
            case "BREEZE"      -> w.spawn(loc, Breeze.class);
            default -> null;
        };
    }

    private static List<UUID> spawnChickenJockey(Location loc, double hpMult, double atkMult,
                                                   boolean isBoss, double speedMult,
                                                   String donjonId, int waveIndex) {
        World w = loc.getWorld();
        if (w == null) return List.of();

        Chicken chicken = w.spawn(loc, Chicken.class);
        Zombie baby = w.spawn(loc, Zombie.class, z -> z.setBaby(true));
        chicken.addPassenger(baby);

        applyMultipliers(baby, hpMult, atkMult, speedMult);
        tagEntity(baby, donjonId, waveIndex, isBoss);
        // Tag chicken for reference but don't include in wave count; track as auxiliary
        entityToDonjonId.put(chicken.getUniqueId(), donjonId);

        Donjon d = donjons.get(donjonId);
        if (d != null) d.getAuxiliaryEntities().add(chicken.getUniqueId());

        return List.of(baby.getUniqueId()); // only zombie counts for wave completion
    }

    private static void applyMultipliers(LivingEntity e, double hpMult, double atkMult, double speedMult) {
        AttributeInstance maxHp = e.getAttribute(Attribute.MAX_HEALTH);
        if (maxHp != null) {
            maxHp.setBaseValue(maxHp.getBaseValue() * hpMult);
            e.setHealth(e.getMaxHealth());
        }
        AttributeInstance atk = e.getAttribute(Attribute.ATTACK_DAMAGE);
        if (atk != null) {
            atk.setBaseValue(atk.getBaseValue() * atkMult);
        }
        if (speedMult != 1.0) {
            AttributeInstance speed = e.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speed != null) {
                speed.setBaseValue(speed.getBaseValue() * speedMult);
            }
        }
    }

    private static void tagEntity(LivingEntity e, String donjonId, int waveIndex, boolean isBoss) {
        e.getPersistentDataContainer().set(keyDonjonId,   PersistentDataType.STRING,  donjonId);
        e.getPersistentDataContainer().set(keyDonjonWave, PersistentDataType.INTEGER, waveIndex);
        if (isBoss) {
            e.getPersistentDataContainer().set(keyIsBoss, PersistentDataType.BYTE, (byte) 1);
        }
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    public static boolean isBossEntity(LivingEntity e) {
        return e.getPersistentDataContainer().has(keyIsBoss, PersistentDataType.BYTE);
    }

    public static boolean isDonjonEntity(LivingEntity e) {
        return e.getPersistentDataContainer().has(keyDonjonId, PersistentDataType.STRING);
    }

    public static String getDonjonIdForEntity(UUID uuid) {
        return entityToDonjonId.get(uuid);
    }

    public static String getDonjonIdAtTotem(Location loc) {
        return totemLocationToId.get(locationKey(loc));
    }

    public static Donjon getDonjon(String id) {
        return donjons.get(id);
    }

    public static Map<String, Donjon> getDonjons() {
        return Collections.unmodifiableMap(donjons);
    }

    public static boolean isInDonjon(Location loc, Donjon donjon) {
        if (!loc.getWorld().equals(donjon.getCenter().getWorld())) return false;
        long key = Chunk.getChunkKey(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        return donjon.getProtectedChunkKeys().contains(key);
    }

    public static Donjon getDonjonAtChunk(World world, long chunkKey) {
        for (Donjon d : donjons.values()) {
            if (d.getCenter().getWorld().equals(world) && d.getProtectedChunkKeys().contains(chunkKey)) {
                return d;
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void activateRandomDonjon() {
        List<Donjon> idle = donjons.values().stream()
                .filter(d -> d.getStatus() == DonjonStatus.IDLE)
                .collect(Collectors.toList());
        if (idle.isEmpty()) return;
        activateDonjon(idle.get(ThreadLocalRandom.current().nextInt(idle.size())));
    }

    private static int randomLevel() {
        int totalWeight = 0;
        for (int[] range : difficultyRanges) totalWeight += range[2];
        int roll = ThreadLocalRandom.current().nextInt(Math.max(totalWeight, 1));
        int cumulative = 0;
        for (int[] range : difficultyRanges) {
            cumulative += range[2];
            if (roll < cumulative) {
                return ThreadLocalRandom.current().nextInt(range[0], range[1] + 1);
            }
        }
        return 0;
    }

    private static DonjonRarity randomRarity() {
        int total = 0;
        for (int w : rarityWeights) total += w;
        int roll = ThreadLocalRandom.current().nextInt(Math.max(total, 1));
        int cumulative = 0;
        DonjonRarity[] rarities = DonjonRarity.values();
        for (int i = 0; i < rarityWeights.length; i++) {
            cumulative += rarityWeights[i];
            if (roll < cumulative) return rarities[i];
        }
        return DonjonRarity.COMMON;
    }

    private static String generateName(DonjonType type, DonjonTypeConfig cfg) {
        List<String> adjectives = cfg.getNameAdjectives();
        List<String> nouns      = cfg.getNameNouns();
        if (adjectives.isEmpty() || nouns.isEmpty()) return type.getDisplayName();
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        String adj  = adjectives.get(rand.nextInt(adjectives.size()));
        String noun = nouns.get(rand.nextInt(nouns.size()));
        return adj + " " + noun;
    }

    private static Location randomSpawnLocation(Donjon donjon) {
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        Location c = donjon.getCenter();
        int range = 8;
        return c.clone().add(
                rand.nextInt(-range, range + 1),
                0,
                rand.nextInt(-range, range + 1));
    }

    private static void computeProtectedChunks(Donjon donjon) {
        DonjonTypeConfig cfg = typeConfigMap.get(donjon.getType());
        if (cfg != null) computeProtectedChunks(donjon, cfg);
    }

    private static void computeProtectedChunks(Donjon donjon, DonjonTypeConfig cfg) {
        donjon.getProtectedChunkKeys().clear();
        Location c = donjon.getCenter();
        int radius = cfg.getProtectionRadiusChunks();
        int cx = c.getBlockX() >> 4, cz = c.getBlockZ() >> 4;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                donjon.getProtectedChunkKeys().add(Chunk.getChunkKey(cx + dx, cz + dz));
            }
        }
    }

    private static void broadcastAll(Component msg) {
        for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(msg);
    }

    private static void broadcastToDonjonPlayers(Donjon donjon, Component msg) {
        World world = donjon.getCenter().getWorld();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().equals(world) && isInDonjon(p.getLocation(), donjon)) {
                p.sendMessage(msg);
            }
        }
    }

    private static String locationKey(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    private static int toInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        return 0;
    }

    private static <T extends Enum<T>> T parseEnum(Class<T> clazz, String value, T fallback) {
        if (value == null) return fallback;
        try { return Enum.valueOf(clazz, value); }
        catch (IllegalArgumentException e) { return fallback; }
    }
}
