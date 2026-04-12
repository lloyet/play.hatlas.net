package org.minecraft.atlas.donjon;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.util.BlockVector;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Biome;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.structure.StructureManager;
import org.bukkit.util.BoundingBox;
import org.minecraft.atlas.Atlas;
import org.minecraft.atlas.faction.Faction;
import org.minecraft.atlas.faction.FactionManager;
import org.minecraft.atlas.util.TitleUtil;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class DonjonManager {

    // -------------------------------------------------------------------------
    // In-memory state
    // -------------------------------------------------------------------------

    /** All registered donjons, keyed by their unique ID. */
    private static final Map<String, Donjon> donjons = new LinkedHashMap<>();

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
    private static double spawnChance         = 0.05;

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
        spawnChance          = sec.getDouble("spawn_chance", 0.05);

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

            Location nt = d.getNametagLocation();
            if (nt != null) {
                s.set("nametag_x", nt.getBlockX());
                s.set("nametag_y", nt.getBlockY());
                s.set("nametag_z", nt.getBlockZ());
            }

            if (d.getTextDisplayUUID() != null) {
                s.set("text_display_uuid", d.getTextDisplayUUID().toString());
            }
        }
    }

    public static void loadDonjons(FileConfiguration config) {
        donjons.clear();

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

            if (s.contains("nametag_x")) {
                donjon.setNametagLocation(new Location(world,
                        s.getInt("nametag_x"), s.getInt("nametag_y"), s.getInt("nametag_z")));
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
                broadcastGlobal(
                        Component.text("⚠ Donjon ", NamedTextColor.YELLOW)
                                .append(Component.text(d.getName(), d.getRarity().getColor()))
                                .append(Component.text(" will become idle in 1 hour!", NamedTextColor.YELLOW)),
                        "⚠ " + d.getName() + " idle soon!",
                        NamedTextColor.YELLOW);
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

    public static Donjon createDonjon(DonjonType type, Location center) {
        DonjonTypeConfig cfg = typeConfigMap.get(type);
        if (cfg == null) {
            Atlas.instance.getLogger().warning("No config for donjon type: " + type);
            return null;
        }

        // Reject if any proposed protected chunk already belongs to another donjon
        int cx = center.getBlockX() >> 4, cz = center.getBlockZ() >> 4;
        int radius = cfg.getProtectionRadiusChunks();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                long key = Chunk.getChunkKey(cx + dx, cz + dz);
                if (getDonjonAtChunk(center.getWorld(), key) != null) {
                    Atlas.instance.getLogger().warning(
                            "Cannot create donjon: proposed chunks overlap with an existing donjon.");
                    return null;
                }
            }
        }

        String id = UUID.randomUUID().toString().substring(0, 8);
        int level = randomLevel();
        DonjonRarity rarity = randomRarity();
        String name = generateName(type, cfg);

        Donjon donjon = new Donjon(id, name, type, center, level, rarity);
        donjon.setStatus(DonjonStatus.IDLE);

        computeProtectedChunks(donjon, cfg);
        spawnOrUpdateNametag(donjon);

        donjons.put(id, donjon);

        return donjon;
    }

    public static void deleteDonjon(String id) {
        Donjon donjon = donjons.remove(id);
        if (donjon == null) return;

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

        broadcastGlobal(
                Component.text("⚡ The donjon ", NamedTextColor.GOLD)
                        .append(Component.text(donjon.getName(), donjon.getRarity().getColor()))
                        .append(Component.text(" [Lv." + donjon.getLevel() + " — " + donjon.getRarity().getDisplayName() + "]", NamedTextColor.YELLOW))
                        .append(Component.text(" is now ACTIVE!", NamedTextColor.GOLD)),
                "⚡ " + donjon.getName() + " ACTIVE!",
                NamedTextColor.GOLD);
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

        broadcastGlobal(
                Component.text("⚔ Faction ", NamedTextColor.GOLD)
                        .append(Component.text("[" + factionName + "]", factionColor))
                        .append(Component.text(" has started the donjon ", NamedTextColor.GOLD))
                        .append(Component.text(donjon.getName(), donjon.getRarity().getColor()))
                        .append(Component.text("!", NamedTextColor.GOLD)),
                "⚔ " + donjon.getName() + " started!",
                NamedTextColor.GOLD);

        soundToDonjonPlayers(donjon, Sound.BLOCK_TRIAL_SPAWNER_OPEN_SHUTTER, 1.0f, 1.0f);
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

        if (wave.isBossWave()) {
            alertDonjonPlayers(donjon, "☠ BOSS WAVE — Wave " + wave.getWaveNumber() + "!", NamedTextColor.DARK_RED);
            soundToDonjonPlayers(donjon, Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);
        } else {
            alertDonjonPlayers(donjon, "⚡ Wave " + wave.getWaveNumber() + " begins!", NamedTextColor.YELLOW);
            randomSoundToDonjonPlayers(donjon, WAVE_SPAWN_SOUNDS, 0.8f, 1.0f);
        }
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

        // Show kill counter subtitle to players inside the donjon
        int total     = wave.getMobTypesToSpawn().size();
        int remaining = wave.getSpawnedEntities().size();
        int killed    = total - remaining;
        if (wave.isBossWave()) {
            alertDonjonPlayersSubtitle(donjon, "☠ " + killed + " / " + total, NamedTextColor.DARK_RED);
        } else {
            alertDonjonPlayersSubtitle(donjon, "⚔ " + killed + " / " + total, NamedTextColor.YELLOW);
        }

        // Drop boss loot when a boss entity dies
        if (isBossEntity(entity)) {
            spawnBossDrops(entity.getLocation(), donjon);
        }

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
                alertDonjonPlayers(donjon,
                        "✔ Wave " + wave.getWaveNumber() + " cleared! Next wave in 5 s...", NamedTextColor.GREEN);
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
        updateBossNametag(boss, damage);
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

        broadcastGlobal(
                Component.text("★ The donjon ", NamedTextColor.GOLD)
                        .append(Component.text(donjon.getName(), donjon.getRarity().getColor()))
                        .append(Component.text(" was cleared by faction ", NamedTextColor.GOLD))
                        .append(Component.text("[" + mainDisplay + "]", mainColor))
                        .append(Component.text("! " + totalExp + " exp distributed.", NamedTextColor.GOLD)),
                "★ " + donjon.getName() + " cleared!",
                NamedTextColor.GOLD);

        // Play wither death sound for all online players as a server-wide event
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, SoundCategory.MASTER, 1.0f, 1.0f);
        }

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
            broadcastGlobal(
                    Component.text("⌛ Donjon ", NamedTextColor.GRAY)
                            .append(Component.text(donjon.getName(), donjon.getRarity().getColor()))
                            .append(Component.text(" has become idle (timed out).", NamedTextColor.GRAY)),
                    "⌛ " + donjon.getName() + " idle!",
                    NamedTextColor.GRAY);
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
        Location base = donjon.getNametagLocation() != null ? donjon.getNametagLocation() : donjon.getCenter();
        Location displayLoc = base.add(0, 4.0, 0);

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
    // Mob spawning
    // -------------------------------------------------------------------------

    /**
     * Spawns one logical "mob entry" (which may produce multiple entities for special
     * types like CHICKEN_JOCKEY).  Returns the UUIDs that should be tracked in the wave.
     */
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
        if (isBoss) {
            entity.customName(buildBossNametag(entity, mobTypeName));
        } else {
            entity.customName(Component.text(prettyMobName(mobTypeName), NamedTextColor.YELLOW));
        }
        entity.setCustomNameVisible(true);
        return List.of(entity.getUniqueId());
    }

    private static LivingEntity spawnSingleMob(String name, Location loc) {
        World w = loc.getWorld();
        if (w == null) return null;
        return switch (name.toUpperCase()) {
            case "ZOMBIE"      -> w.spawn(loc, Zombie.class);
            case "BABY_ZOMBIE" -> w.spawn(loc, Zombie.class, z -> z.setBaby());
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
        Zombie baby = w.spawn(loc, Zombie.class, z -> z.setBaby());
        chicken.addPassenger(baby);

        applyMultipliers(baby, hpMult, atkMult, speedMult);
        tagEntity(baby, donjonId, waveIndex, isBoss);
        baby.customName(Component.text(prettyMobName("CHICKEN_JOCKEY"),
                isBoss ? NamedTextColor.DARK_RED : NamedTextColor.YELLOW));
        baby.setCustomNameVisible(true);
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
            e.setHealth(maxHp.getValue());
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
    // Boss drops
    // -------------------------------------------------------------------------

    private static void spawnBossDrops(Location loc, Donjon donjon) {
        DonjonTypeConfig cfg = typeConfigMap.get(donjon.getType());
        if (cfg == null) return;

        BossDropConfig dropCfg = cfg.getBossDrops().get(donjon.getRarity());
        if (dropCfg == null) return;

        World world = loc.getWorld();
        if (world == null) return;

        for (ItemStack item : dropCfg.rollDrops()) {
            world.dropItemNaturally(loc, item);
        }
    }

    // -------------------------------------------------------------------------
    // Chunk-based procedural generation
    // -------------------------------------------------------------------------

    /**
     * Called on every newly-generated chunk. Rolls the spawn chance, samples the dominant biome,
     * finds a matching donjon type, places the NBT structure, and registers the donjon.
     */
    public static void trySpawnDonjonInChunk(Chunk chunk) {
        if (ThreadLocalRandom.current().nextDouble() >= spawnChance) return;

        World world = chunk.getWorld();

        // Sample dominant biome across the chunk (4-block grid)
        Map<Biome, Integer> biomeCounts = new HashMap<>();
        for (int x = 0; x < 16; x += 4) {
            for (int z = 0; z < 16; z += 4) {
                int bx = (chunk.getX() << 4) + x;
                int bz = (chunk.getZ() << 4) + z;
                Biome b = world.getBiome(bx, world.getHighestBlockYAt(bx, bz, HeightMap.OCEAN_FLOOR), bz);
                biomeCounts.merge(b, 1, Integer::sum);
            }
        }
        
        if (biomeCounts.isEmpty()) return;

        Biome dominant = Collections.max(biomeCounts.entrySet(), Map.Entry.comparingByValue()).getKey();
        String biomeKey = dominant.getKey().getKey(); // e.g. "plains"

        // Find a DonjonType whose biome list includes the dominant biome
        DonjonType matchedType = null;
        DonjonTypeConfig matchedCfg = null;
        for (DonjonType type : DonjonType.values()) {
            DonjonTypeConfig cfg = typeConfigMap.get(type);
            if (cfg == null) continue;
            for (String b : cfg.getBiomes()) {
                if (b.equalsIgnoreCase(biomeKey)) {
                    matchedType = type;
                    matchedCfg = cfg;
                    break;
                }
            }
            if (matchedType != null) break;
        }
        if (matchedType == null) return;

        int chunkCenterX = (chunk.getX() << 4) + 8;
        int chunkCenterZ = (chunk.getZ() << 4) + 8;
        int centerY = world.getHighestBlockYAt(chunkCenterX, chunkCenterZ, HeightMap.OCEAN_FLOOR);
        Location center = new Location(world, chunkCenterX, centerY, chunkCenterZ);

        if (donjonExistsNear(center, 64)) return;

        // Place the NBT structure: NW-bottom corner at the chunk's NW corner, on the solid surface
        int originX = chunk.getX() << 4;
        int originZ = chunk.getZ() << 4;
        int originY = world.getHighestBlockYAt(originX, originZ, HeightMap.OCEAN_FLOOR);
        Location anchorLoc = placeStructure(matchedCfg, new Location(world, originX, originY, originZ));

        // Register the donjon
        Donjon donjon = createDonjon(matchedType, center);
        if (donjon == null) return;

        // Position the floating nametag above the respawn anchor (or structure center as fallback)
        if (anchorLoc != null) donjon.setNametagLocation(anchorLoc);

        Bukkit.getScheduler().runTaskLater(Atlas.instance, () -> {
            saveDonjonConfig(Atlas.instance.getConfig());
            Atlas.instance.saveConfig();
        }, 20L);
    }

    /**
     * Loads the NBT structure file from {@code <dataFolder>/structures/<filename>}, places it at
     * {@code origin}, then scans the placed footprint for a RESPAWN_ANCHOR block.
     *
     * @return the RESPAWN_ANCHOR location if one is found inside the structure,
     *         the center of the structure footprint as a fallback, or {@code null} on error.
     */
    private static Location placeStructure(DonjonTypeConfig cfg, Location origin) {
        String filename = cfg.getStructureFilename();
        if (filename == null || filename.isEmpty()) return null;

        File file = new File(Atlas.instance.getDataFolder(), "structures/" + filename);
        if (!file.exists()) {
            Atlas.instance.getLogger().warning(
                    "Structure file not found: " + file.getPath()
                    + " — place your .nbt file there and restart.");
            return null;
        }

        try {
            StructureManager sm = Bukkit.getServer().getStructureManager();
            org.bukkit.structure.Structure structure = sm.loadStructure(file);
            structure.place(origin, true, StructureRotation.NONE, Mirror.NONE, 0, 1.0f, new Random());

            // Scan the placed area for the first RESPAWN_ANCHOR
            BlockVector size = structure.getSize();
            World world = origin.getWorld();
            int ox = origin.getBlockX(), oy = origin.getBlockY(), oz = origin.getBlockZ();
            for (int dy = 0; dy < size.getBlockY(); dy++) {
                for (int dx = 0; dx < size.getBlockX(); dx++) {
                    for (int dz = 0; dz < size.getBlockZ(); dz++) {
                        if (world.getBlockAt(ox + dx, oy + dy, oz + dz).getType() != Material.RESPAWN_ANCHOR) {
                            continue;
                        }

                        return new Location(world, ox + dx + 0.5, oy + dy, oz + dz + 0.5);
                    }
                }
            }

            // Fallback: horizontal center of the structure at origin Y
            return new Location(world,
                    ox + size.getBlockX() / 2.0,
                    oy,
                    oz + size.getBlockZ() / 2.0);

        } catch (IOException e) {
            Atlas.instance.getLogger().warning("Failed to place structure '" + filename + "': " + e.getMessage());
            return null;
        }
    }

    /** Returns {@code true} if any existing donjon's center is within {@code radiusBlocks} of {@code loc}. */
    public static boolean donjonExistsNear(Location loc, double radiusBlocks) {
        double sq = radiusBlocks * radiusBlocks;
        for (Donjon d : donjons.values()) {
            if (!d.getCenter().getWorld().equals(loc.getWorld())) continue;
            if (d.getCenter().distanceSquared(loc) <= sq) return true;
        }
        return false;
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

    /** Returns true if the given chunk is within any donjon's protected area. */
    public static boolean isChunkInDonjon(String worldName, int chunkX, int chunkZ) {
        long key = Chunk.getChunkKey(chunkX, chunkZ);
        for (Donjon d : donjons.values()) {
            if (d.getCenter().getWorld().getName().equals(worldName)
                    && d.getProtectedChunkKeys().contains(key)) {
                return true;
            }
        }
        return false;
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
                .toList();
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

    private static void broadcastGlobal(Component chatMsg, String shortTitle, NamedTextColor color) {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        for (Player p : players) p.sendMessage(chatMsg);
        TitleUtil.broadcastAlertBold(players, shortTitle, color);
    }

    private static void alertDonjonPlayers(Donjon donjon, String text, NamedTextColor color) {
        World world = donjon.getCenter().getWorld();
        List<Player> players = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getWorld().equals(world) && isInDonjon(p.getLocation(), donjon))
                .collect(Collectors.toList());
        TitleUtil.broadcastAlertBold(players, text, color);
    }

    // -------------------------------------------------------------------------
    // Sound helpers
    // -------------------------------------------------------------------------

    /** 4 trial-spawner spawn sounds played at random when a wave begins. */
    private static final List<Sound> WAVE_SPAWN_SOUNDS = List.of(
            Sound.BLOCK_TRIAL_SPAWNER_SPAWN_MOB,
            Sound.BLOCK_TRIAL_SPAWNER_DETECT_PLAYER,
            Sound.BLOCK_TRIAL_SPAWNER_CLOSE_SHUTTER,
            Sound.BLOCK_TRIAL_SPAWNER_AMBIENT_OMINOUS
    );

    private static void soundToDonjonPlayers(Donjon donjon, Sound sound, float volume, float pitch) {
        World world = donjon.getCenter().getWorld();
        Location loc = donjon.getCenter();
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getWorld().equals(world) && isInDonjon(p.getLocation(), donjon))
                .forEach(p -> p.playSound(loc, sound, SoundCategory.MASTER, volume, pitch));
    }

    private static void randomSoundToDonjonPlayers(Donjon donjon, List<Sound> sounds, float volume, float pitch) {
        if (sounds.isEmpty()) return;
        soundToDonjonPlayers(donjon,
                sounds.get(ThreadLocalRandom.current().nextInt(sounds.size())), volume, pitch);
    }

    // -------------------------------------------------------------------------
    // Boss nametag helpers
    // -------------------------------------------------------------------------

    private static Component buildBossNametag(LivingEntity boss, String mobTypeName) {
        AttributeInstance maxHpAttr = boss.getAttribute(Attribute.MAX_HEALTH);
        int maxHp = maxHpAttr != null ? (int) maxHpAttr.getValue() : 0;
        int hp    = (int) Math.ceil(boss.getHealth());
        return Component.text(prettyMobName(mobTypeName) + " ", NamedTextColor.DARK_RED)
                .append(Component.text("❤ " + hp + "/" + maxHp, NamedTextColor.RED));
    }

    /** Updates the boss nametag to reflect HP remaining after {@code damage} is subtracted. */
    public static void updateBossNametag(LivingEntity boss, double damage) {
        AttributeInstance maxHpAttr = boss.getAttribute(Attribute.MAX_HEALTH);
        int maxHp = maxHpAttr != null ? (int) maxHpAttr.getValue() : 0;
        int hp    = (int) Math.max(0, Math.ceil(boss.getHealth() - damage));
        String mobName = prettyMobName(boss.getType().name());
        boss.customName(Component.text(mobName + " ", NamedTextColor.DARK_RED)
                .append(Component.text("❤ " + hp + "/" + maxHp, NamedTextColor.RED)));
    }

    // -------------------------------------------------------------------------
    // Donjon-player subtitle helper
    // -------------------------------------------------------------------------

    private static void alertDonjonPlayersSubtitle(Donjon donjon, String text, NamedTextColor color) {
        World world = donjon.getCenter().getWorld();
        List<Player> players = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getWorld().equals(world) && isInDonjon(p.getLocation(), donjon))
                .collect(Collectors.toList());
        TitleUtil.broadcastSubtitle(players, text, color);
    }

    /** Converts a config mob name like "CAVE_SPIDER" to "Cave Spider". */
    private static String prettyMobName(String name) {
        String[] parts = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)));
            sb.append(part.substring(1).toLowerCase());
        }
        return sb.toString();
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
