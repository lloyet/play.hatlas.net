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
import org.bukkit.block.BlockState;
import org.bukkit.util.BlockVector;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.structure.StructureManager;
import org.minecraft.atlas.Atlas;
import org.minecraft.atlas.faction.Faction;
import org.minecraft.atlas.faction.FactionManager;
import org.minecraft.atlas.donjon.ElectricalCreeperManager;
import org.minecraft.atlas.donjon.RaiderPickaxe;
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

    /**
     * Tracks which donjon IDs each player has visited at least one chunk of.
     */
    private static final Map<UUID, Set<String>> playerVisitedDonjons = new HashMap<>();

    /**
     * Cached per-structure offsets derived from the NBT palette on first placement.
     * {@code spawnOffsets} — relative positions of every RESPAWN_ANCHOR (wave spawn points).
     * {@code nametagOffset} — relative position of the VAULT block (nametag anchor).
     */
    private record StructureOffsets(List<BlockVector> spawnOffsets, BlockVector nametagOffset, int protectionRadiusChunks) {}
    private static final Map<String, StructureOffsets> structureOffsetCache = new HashMap<>();

    /** Holds the absolute world locations computed after a structure is placed. */
    private record PlacedStructureResult(Location nametagLocation, List<Location> spawnPoints) {}

    /** Type-specific configuration, loaded from config.yml. */
    private static final Map<DonjonType, DonjonTypeConfig> typeConfigMap = new EnumMap<>(DonjonType.class);

    // -------------------------------------------------------------------------
    // Global settings (loaded from config)
    // -------------------------------------------------------------------------

    private static long activationIntervalMs  = 6L  * 3_600_000L;
    private static long idleTimeoutMs         = 24L * 3_600_000L;
    private static long lastActivationTime    = 0L;
    private static int  maxActiveDonjonons    = 2;

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
        maxActiveDonjonons   = sec.getInt("max_active_donjons", 2);

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

        // Persist which players have visited which donjons (needed for Smuggler NPC teleport)
        config.set("visited_donjons", null);
        if (!playerVisitedDonjons.isEmpty()) {
            ConfigurationSection visitedSection = config.createSection("visited_donjons");
            for (Map.Entry<UUID, Set<String>> entry : playerVisitedDonjons.entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    visitedSection.set(entry.getKey().toString(), new ArrayList<>(entry.getValue()));
                }
            }
        }

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

            List<Location> spawnPts = d.getSpawnPoints();
            if (!spawnPts.isEmpty()) {
                List<String> encoded = new ArrayList<>();
                for (Location pt : spawnPts) {
                    encoded.add(pt.getBlockX() + "," + pt.getBlockY() + "," + pt.getBlockZ());
                }
                s.set("spawn_points", encoded);
            }

            if (d.getTextDisplayUUID() != null) {
                s.set("text_display_uuid", d.getTextDisplayUUID().toString());
            }

            Location ts = d.getTeleportSpawn();
            if (ts != null) {
                s.set("teleport_spawn_x",     ts.getX());
                s.set("teleport_spawn_y",     ts.getY());
                s.set("teleport_spawn_z",     ts.getZ());
                s.set("teleport_spawn_yaw",   (double) ts.getYaw());
                s.set("teleport_spawn_pitch", (double) ts.getPitch());
            }
        }
    }

    public static void loadDonjons(FileConfiguration config) {
        donjons.clear();
        playerVisitedDonjons.clear();

        ConfigurationSection visitedSection = config.getConfigurationSection("visited_donjons");
        if (visitedSection != null) {
            for (String uuidStr : visitedSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    List<String> ids = visitedSection.getStringList(uuidStr);
                    if (!ids.isEmpty()) {
                        playerVisitedDonjons.put(uuid, new HashSet<>(ids));
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }

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

            List<String> spawnPtsRaw = s.getStringList("spawn_points");
            if (!spawnPtsRaw.isEmpty()) {
                List<Location> spawnPts = new ArrayList<>();
                for (String enc : spawnPtsRaw) {
                    String[] parts = enc.split(",");
                    if (parts.length != 3) continue;
                    try {
                        int sx = Integer.parseInt(parts[0].trim());
                        int sy = Integer.parseInt(parts[1].trim());
                        int sz = Integer.parseInt(parts[2].trim());
                        spawnPts.add(new Location(world, sx + 0.5, sy, sz + 0.5));
                    } catch (NumberFormatException ignored) {}
                }
                donjon.setSpawnPoints(spawnPts);
            }

            String displayUUIDStr = s.getString("text_display_uuid");
            if (displayUUIDStr != null) {
                try { donjon.setTextDisplayUUID(UUID.fromString(displayUUIDStr)); }
                catch (IllegalArgumentException ignored) {}
            }

            if (s.contains("teleport_spawn_x")) {
                Location ts = new Location(world,
                        s.getDouble("teleport_spawn_x"),
                        s.getDouble("teleport_spawn_y"),
                        s.getDouble("teleport_spawn_z"),
                        (float) s.getDouble("teleport_spawn_yaw"),
                        (float) s.getDouble("teleport_spawn_pitch"));
                donjon.setTeleportSpawn(ts);
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
            Atlas.donjonsConfig.set("donjon.last_activation_time", lastActivationTime);
            Atlas.saveDonjonsConfig();
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

        for (Player p : Atlas.instance.getServer().getOnlinePlayers()) {
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
        int radius = getOrCacheStructureOffsets(cfg.structureFilename()).protectionRadiusChunks();
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

    /**
     * Activates a donjon via the scheduler — assigns a fresh random level and rarity first.
     */
    public static void activateDonjon(Donjon donjon) {
        donjon.setLevel(randomLevel());
        donjon.setRarity(randomRarity());
        doActivateDonjon(donjon);
    }

    /**
     * Force-activates a donjon via an enchanted Ominous Trial Key.
     * Assigns a high level (50–99) and LEGENDARY/MYSTIC/GODDESS rarity before activating.
     */
    public static void forceActivateDonjon(Donjon donjon) {
        donjon.setLevel(ThreadLocalRandom.current().nextInt(50, 100));
        DonjonRarity[] forcedRarities = {DonjonRarity.LEGENDARY, DonjonRarity.MYSTIC, DonjonRarity.GODDESS};
        donjon.setRarity(forcedRarities[ThreadLocalRandom.current().nextInt(forcedRarities.length)]);
        doActivateDonjon(donjon);
    }

    /** Internal activation: sets ACTIVE state, updates nametag, and broadcasts. */
    private static void doActivateDonjon(Donjon donjon) {
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
        spawnOrUpdateNametag(donjon);
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

        int numWaves = cfg.minWaves() + (int) Math.round(levelFactor * (cfg.maxWaves() - cfg.minWaves()));
        int baseMobCount = cfg.minMobsPerWave()
                + (int) Math.round(levelFactor * (cfg.maxMobsPerWave() - cfg.minMobsPerWave()));

        List<DonjonWave> waves = new ArrayList<>();
        List<String> mobTypes = cfg.mobTypes();
        List<String> bossTypes = cfg.bossTypes();

        for (int i = 1; i <= numWaves; i++) {
            boolean isBoss = (i == numWaves);
            List<String> mobsForWave = new ArrayList<>();

            if (isBoss) {
                int bossCount = cfg.bossCountMin()
                        + (cfg.bossCountMax() > cfg.bossCountMin()
                        ? rand.nextInt(cfg.bossCountMax() - cfg.bossCountMin() + 1) : 0);
                if (!bossTypes.isEmpty()) {
                    for (int j = 0; j < bossCount; j++) {
                        mobsForWave.add(bossTypes.get(rand.nextInt(bossTypes.size())));
                    }
                }
            } else {
                double variation = 0.8 + rand.nextDouble() * 0.4;
                int count = Math.max(cfg.minMobsPerWave(), (int)(baseMobCount * variation));
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
        double hpMult = cfg.baseHpMultiplier() + lf * (cfg.maxHpMultiplier() - cfg.baseHpMultiplier());
        double atkMult = cfg.baseAttackMultiplier() + lf * (cfg.maxAttackMultiplier() - cfg.baseAttackMultiplier());

        if (wave.isBossWave()) {
            hpMult  *= cfg.bossHpMultiplier();
            atkMult *= cfg.bossAttackMultiplier();
        }

        double finalHpMult = hpMult;
        double finalAtkMult = atkMult;
        double bossSpeedMult = cfg.bossSpeedMultiplier();
        String donjonId = donjon.getId();

        List<Location> spawnPoints = donjon.getSpawnPoints();
        List<String> mobsToSpawn = wave.getMobTypesToSpawn();
        int spawnCount = spawnPoints.size();

        for (int i = 0; i < mobsToSpawn.size(); i++) {
            Location spawnLoc = spawnCount > 0
                    ? spawnPoints.get(i % spawnCount).clone()
                    : randomSpawnLocation(donjon);
            List<UUID> uuids = spawnMobEntity(mobsToSpawn.get(i), spawnLoc, finalHpMult, finalAtkMult,
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
        int total = wave.getMobTypesToSpawn().size();
        int remaining = wave.getSpawnedEntities().size();
        int killed = total - remaining;
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
                Atlas.instance.getServer().getScheduler().runTaskLater(Atlas.instance, () -> {
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
        long baseExp = (cfg != null) ? cfg.minExpReward() : 500L;
        long maxExp  = (cfg != null) ? cfg.maxExpReward() : 10000L;
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

        Location dropLoc = donjon.getNametagLocation() != null
                ? donjon.getNametagLocation() : donjon.getCenter();
        World dropWorld = dropLoc.getWorld();

        // Drop an enchanted Ominous Trial Key when EPIC+ donjon is completed
        if (donjon.getRarity().ordinal() >= DonjonRarity.EPIC.ordinal()) {
            double dropChance = ominousKeyDropChance(donjon.getRarity());
            if (dropWorld != null && ThreadLocalRandom.current().nextDouble() < dropChance) {
                ItemStack ominousKey = new ItemStack(Material.OMINOUS_TRIAL_KEY);
                ominousKey.addUnsafeEnchantment(Enchantment.UNBREAKING, 1);
                dropWorld.dropItemNaturally(dropLoc, ominousKey);
            }
        }

        if (dropWorld != null) {
            int level = donjon.getLevel();
            DonjonRarity rarity = donjon.getRarity();
            int scaledAmount = 1 + (int) (level / 99.0 * (rarity.ordinal() + 1));

            // Always drop creeper eggs — amount scales with level and rarity
            dropWorld.dropItemNaturally(dropLoc,
                    ElectricalCreeperManager.createCreeperEgg(scaledAmount));

            // Very low chance to drop an electrical creeper egg
            if (ThreadLocalRandom.current().nextDouble() < ElectricalCreeperManager.electricalDropChance(rarity)) {
                dropWorld.dropItemNaturally(dropLoc, ElectricalCreeperManager.createElectricalCreeperEgg());
            }

            // Always drop TNT — same scaling formula as creeper eggs
            dropWorld.dropItemNaturally(dropLoc, new ItemStack(Material.TNT, scaledAmount));

            // Drop wither skeleton skulls for EPIC+ donjons — same scaling formula
            if (rarity.ordinal() >= DonjonRarity.EPIC.ordinal()) {
                dropWorld.dropItemNaturally(dropLoc, new ItemStack(Material.WITHER_SKELETON_SKULL, scaledAmount));
            }

            // Drop raider pickaxe if level >= 50 and EPIC or above
            if (level >= 50 && rarity.ordinal() >= DonjonRarity.EPIC.ordinal()) {
                dropWorld.dropItemNaturally(dropLoc, RaiderPickaxe.create());
            }
        }

        setIdle(donjon, null);
    }

    /** Drop-chance for the enchanted Ominous Trial Key: (1 – normalizedWeight) + 10%, clamped to [0, 1]. */
    private static double ominousKeyDropChance(DonjonRarity rarity) {
        int totalWeight = 0;
        for (int w : rarityWeights) totalWeight += w;
        double normalizedWeight = (double) rarityWeights[rarity.ordinal()] / totalWeight;

        return Math.min(1.0, (1.0 - normalizedWeight) + 0.10);
    }

    static void resetDonjon(Donjon donjon) {
        donjon.setInProgress(false);
        donjon.setCurrentWaveIndex(0);
        donjon.setStartingFaction(null);
        donjon.getBossDamageMap().clear();
        donjon.getPlayersInside().clear();
        clearEntities(donjon);
        donjon.getWaves().clear();
        spawnOrUpdateNametag(donjon);
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
        String typeName = cfg != null ? cfg.displayName() : donjon.getType().getDisplayName();

        Component statusLine;
        if (donjon.isInProgress()) {
            statusLine = Component.text("⚔ IN PROGRESS ⚔", NamedTextColor.YELLOW);
        } else if (donjon.getStatus() == DonjonStatus.ACTIVE) {
            statusLine = Component.text("◆ ACTIVE ◆", NamedTextColor.GREEN);
        } else {
            statusLine = Component.text("◇ IDLE ◇", NamedTextColor.DARK_GRAY);
        }

        boolean idle = donjon.getStatus() == DonjonStatus.IDLE && !donjon.isInProgress();

        Component nameLine = Component.text("[" + typeName + "] ", NamedTextColor.GRAY)
                .append(Component.text(donjon.getName(),
                        idle ? NamedTextColor.DARK_GRAY : donjon.getRarity().getColor()));

        if (idle) {
            return nameLine
                    .append(Component.newline())
                    .append(statusLine);
        }

        return nameLine
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
    private static List<UUID> spawnMobEntity(String mobTypeName, Location loc, double hpMult, double atkMult, boolean isBoss, double speedMult, String donjonId, int waveIndex) {
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
            case "ZOMBIE" -> w.spawn(loc, Zombie.class);
            case "BABY_ZOMBIE" -> w.spawn(loc, Zombie.class, Ageable::setBaby);
            case "HUSK" -> w.spawn(loc, Husk.class);
            case "DROWNED" -> w.spawn(loc, Drowned.class);
            case "GUARDIAN" -> w.spawn(loc, Guardian.class);
            case "ELDER_GUARDIAN" -> w.spawn(loc, ElderGuardian.class);
            case "SKELETON" -> w.spawn(loc, Skeleton.class);
            case "STRAY" -> w.spawn(loc, Stray.class);
            case "BOGGED" -> w.spawn(loc, Bogged.class);
            case "WITHER_SKELETON" -> w.spawn(loc, WitherSkeleton.class);
            case "SPIDER" -> w.spawn(loc, Spider.class);
            case "CAVE_SPIDER" -> w.spawn(loc, CaveSpider.class);
            case "SLIME" -> w.spawn(loc, Slime.class, s -> s.setSize(2));
            case "MAGMA_CUBE" -> w.spawn(loc, MagmaCube.class, s -> s.setSize(2));
            case "SILVERFISH" -> w.spawn(loc, Silverfish.class);
            case "ENDERMITE" -> w.spawn(loc, Endermite.class);
            case "ENDERMAN" -> w.spawn(loc, Enderman.class);
            case "PHANTOM" -> w.spawn(loc, Phantom.class);
            case "SHULKER" -> w.spawn(loc, Shulker.class);
            case "BLAZE" -> w.spawn(loc, Blaze.class);
            case "GHAST" -> w.spawn(loc, Ghast.class);
            case "PIGLIN" -> w.spawn(loc, Piglin.class);
            case "PIGLIN_BRUTE" -> w.spawn(loc, PiglinBrute.class);
            case "WITCH" -> w.spawn(loc, Witch.class);
            case "PILLAGER" -> w.spawn(loc, Pillager.class);
            case "VINDICATOR" -> w.spawn(loc, Vindicator.class);
            case "CREEPER" -> w.spawn(loc, Creeper.class);
            case "BREEZE" -> w.spawn(loc, Breeze.class);
            default -> null;
        };
    }

    private static List<UUID> spawnChickenJockey(Location loc, double hpMult, double atkMult,
                                                   boolean isBoss, double speedMult,
                                                   String donjonId, int waveIndex) {
        World w = loc.getWorld();
        if (w == null) return List.of();

        Chicken chicken = w.spawn(loc, Chicken.class);
        Zombie baby = w.spawn(loc, Zombie.class, Ageable::setBaby);
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

        BossDropConfig dropCfg = cfg.bossDrops().get(donjon.getRarity());
        if (dropCfg == null) return;

        World world = loc.getWorld();
        if (world == null) return;

        for (ItemStack item : dropCfg.rollDrops()) {
            world.dropItemNaturally(loc, item);
        }
    }

    /**
     * Creates a donjon of the given type, placing the NBT structure with its NW-bottom corner
     * at {@code origin} (the player's feet). The donjon center is set to the same location.
     * Used exclusively by the {@code /donjon create} admin command.
     *
     * @return the created {@link Donjon}, or {@code null} on failure.
     */
    public static Donjon generateDonjon(DonjonType type, Location origin) {
        if (donjonExistsNear(origin, 64)) return null;

        DonjonTypeConfig cfg = typeConfigMap.get(type);
        if (cfg == null) return null;

        Location blockOrigin = origin.toBlockLocation();
        PlacedStructureResult placed = placeStructure(cfg, blockOrigin);

        Donjon donjon = createDonjon(type, blockOrigin);
        if (donjon == null) return null;
        if (placed != null) {
            donjon.setNametagLocation(placed.nametagLocation());
            donjon.setSpawnPoints(placed.spawnPoints());
            spawnOrUpdateNametag(donjon);
        }

        Bukkit.getScheduler().runTaskLater(Atlas.instance, () -> {
            saveDonjonConfig(Atlas.donjonsConfig);
            Atlas.saveDonjonsConfig();
        }, 20L);

        return donjon;
    }

    /**
     * Computes and caches {@link StructureOffsets} for an already-loaded structure object.
     * Collects all RESPAWN_ANCHOR positions (spawn points), the first VAULT (nametag),
     * and derives the protection radius from the structure's XZ footprint.
     */
    private static StructureOffsets computeStructureOffsets(String filename, org.bukkit.structure.Structure structure) {
        return structureOffsetCache.computeIfAbsent(filename, k -> {
            List<BlockVector> spawnOffsets = new ArrayList<>();
            BlockVector nametagOffset = null;
            if (!structure.getPalettes().isEmpty()) {
                for (BlockState block : structure.getPalettes().getFirst().getBlocks()) {
                    Material type = block.getType();
                    Location blockLocation = block.getLocation();
                    if (type == Material.RESPAWN_ANCHOR) {
                        spawnOffsets.add(new BlockVector(blockLocation.getBlockX(), blockLocation.getBlockY(), blockLocation.getBlockZ()));
                    } else if (nametagOffset == null && type == Material.VAULT) {
                        nametagOffset = new BlockVector(blockLocation.getBlockX(), blockLocation.getBlockY(), blockLocation.getBlockZ());
                    }
                }
            }

            BlockVector size = structure.getSize();
            if (nametagOffset == null) {
                nametagOffset = new BlockVector(size.getBlockX() / 2, 0, size.getBlockZ() / 2);
            }

            int maxDim = Math.max(size.getBlockX(), size.getBlockZ());
            int radius = (int) Math.ceil(maxDim / 16.0) + 1;

            return new StructureOffsets(List.copyOf(spawnOffsets), nametagOffset, radius);
        });
    }

    /**
     * Ensures the {@link StructureOffsets} for the given filename are in the cache,
     * loading the structure file if necessary. Returns a fallback with radius 3 on any error.
     */
    private static StructureOffsets getOrCacheStructureOffsets(String filename) {
        if (structureOffsetCache.containsKey(filename)) return structureOffsetCache.get(filename);
        if (filename == null || filename.isEmpty())
            return new StructureOffsets(List.of(), new BlockVector(0, 0, 0), 3);
        File file = new File(Atlas.instance.getDataFolder(), "structures/" + filename);
        if (!file.exists())
            return new StructureOffsets(List.of(), new BlockVector(0, 0, 0), 3);
        try {
            org.bukkit.structure.Structure structure = Bukkit.getServer().getStructureManager().loadStructure(file);
            return computeStructureOffsets(filename, structure);
        } catch (IOException e) {
            Atlas.instance.getLogger().warning("Failed to read structure '" + filename + "': " + e.getMessage());
            return new StructureOffsets(List.of(), new BlockVector(0, 0, 0), 3);
        }
    }

    /**
     * Loads the NBT structure file and places it in the world, with {@code loc} as the NW-bottom corner.
     *
     * <ul>
     *   <li>Every {@code RESPAWN_ANCHOR} block becomes a wave spawn point.</li>
     *   <li>The first {@code VAULT} block found in the placed region is the nametag anchor.</li>
     * </ul>
     *
     * @param origin NW-bottom corner of the structure.
     * @return a {@link PlacedStructureResult} with absolute world locations, or {@code null} on error.
     */
    private static PlacedStructureResult placeStructure(DonjonTypeConfig cfg, Location origin) {
        String filename = cfg.structureFilename();
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

            // Resolve spawn offsets from palette[0] (cached per filename).
            StructureOffsets offsets = computeStructureOffsets(filename, structure);

            int ox = origin.getBlockX(), oy = origin.getBlockY(), oz = origin.getBlockZ();
            World world = origin.getWorld();

            List<Location> spawnPoints = new ArrayList<>();
            for (BlockVector sv : offsets.spawnOffsets()) {
                spawnPoints.add(new Location(world, ox + sv.getBlockX() + 0.5, oy + sv.getBlockY(), oz + sv.getBlockZ() + 0.5));
            }

            BlockVector nv = offsets.nametagOffset();
            Location nametagLoc = new Location(world, ox + nv.getBlockX() + 0.5, oy + nv.getBlockY(), oz + nv.getBlockZ() + 0.5);

            return new PlacedStructureResult(nametagLoc, spawnPoints);

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
    public static void recordPlayerVisit(UUID playerUUID, String donjonId) {
        playerVisitedDonjons.computeIfAbsent(playerUUID, k -> new HashSet<>()).add(donjonId);
    }

    public static boolean hasPlayerVisitedDonjon(UUID playerUUID, String donjonId) {
        Set<String> visited = playerVisitedDonjons.get(playerUUID);
        return visited != null && visited.contains(donjonId);
    }

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
        long activeCount = donjons.values().stream()
                .filter(d -> d.getStatus() == DonjonStatus.ACTIVE)
                .count();
        if (activeCount >= maxActiveDonjonons) return;

        List<Donjon> idle = donjons.values().stream()
                .filter(d -> d.getStatus() == DonjonStatus.IDLE)
                .toList();
        if (idle.isEmpty()) return;

        Donjon selected = idle.get(ThreadLocalRandom.current().nextInt(idle.size()));
        activateDonjon(selected);
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
        List<String> adjectives = cfg.nameAdjectives();
        List<String> nouns      = cfg.nameNouns();
        if (adjectives.isEmpty() || nouns.isEmpty()) return type.getDisplayName();
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        String adj  = adjectives.get(rand.nextInt(adjectives.size()));
        String noun = nouns.get(rand.nextInt(nouns.size()));
        return adj + " " + noun;
    }

    private static Location randomSpawnLocation(Donjon donjon) {
        List<Location> spawnPoints = donjon.getSpawnPoints();
        if (!spawnPoints.isEmpty()) {
            return spawnPoints.get(ThreadLocalRandom.current().nextInt(spawnPoints.size())).clone();
        }
        // Fallback: random offset from center when no spawn points are stored
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        Location c = donjon.getCenter();
        int range = 8;
        return c.clone().add(rand.nextInt(-range, range + 1), 0, rand.nextInt(-range, range + 1));
    }

    private static void computeProtectedChunks(Donjon donjon) {
        DonjonTypeConfig cfg = typeConfigMap.get(donjon.getType());
        if (cfg != null) computeProtectedChunks(donjon, cfg);
    }

    private static void computeProtectedChunks(Donjon donjon, DonjonTypeConfig cfg) {
        donjon.getProtectedChunkKeys().clear();
        Location c = donjon.getCenter();
        int radius = getOrCacheStructureOffsets(cfg.structureFilename()).protectionRadiusChunks();
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
