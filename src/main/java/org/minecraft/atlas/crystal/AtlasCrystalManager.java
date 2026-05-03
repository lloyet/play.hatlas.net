package org.minecraft.atlas.crystal;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.minecraft.atlas.Atlas;
import org.minecraft.atlas.faction.Faction;
import org.minecraft.atlas.faction.FactionClaimManager;
import org.minecraft.atlas.faction.FactionLevelManager;
import org.minecraft.atlas.faction.FactionManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AtlasCrystalManager {

    // ── PDC keys ──────────────────────────────────────────────────────────────
    private static NamespacedKey KEY_FACTION;
    private static NamespacedKey KEY_HP;
    private static NamespacedKey KEY_MAX_HP;
    private static NamespacedKey KEY_NAME;
    private static NamespacedKey KEY_HOME;           // legacy migration only
    private static NamespacedKey KEY_IMMUNE_UNTIL;
    private static NamespacedKey KEY_NAMETAG_DISPLAY;
    private static NamespacedKey KEY_HP_BONUS;
    private static NamespacedKey KEY_CLAIM_CAPACITY;
    private static NamespacedKey KEY_SPENT_SP;
    private static NamespacedKey KEY_PROTECTION_MS;
    private static NamespacedKey KEY_DEFEAT_MUL;
    private static NamespacedKey KEY_DEFEAT_WINDOW;

    public static NamespacedKey getKeyFaction() {
        if (KEY_FACTION == null) KEY_FACTION = new NamespacedKey(Atlas.instance, "atlas_crystal_faction");
        return KEY_FACTION;
    }
    private static NamespacedKey getKeyHp() {
        if (KEY_HP == null) KEY_HP = new NamespacedKey(Atlas.instance, "atlas_crystal_hp");
        return KEY_HP;
    }
    private static NamespacedKey getKeyMaxHp() {
        if (KEY_MAX_HP == null) KEY_MAX_HP = new NamespacedKey(Atlas.instance, "atlas_crystal_max_hp");
        return KEY_MAX_HP;
    }
    public static NamespacedKey getKeyName() {
        if (KEY_NAME == null) KEY_NAME = new NamespacedKey(Atlas.instance, "atlas_crystal_name");
        return KEY_NAME;
    }
    private static NamespacedKey getKeyHome() {
        if (KEY_HOME == null) KEY_HOME = new NamespacedKey(Atlas.instance, "atlas_crystal_home");
        return KEY_HOME;
    }
    private static NamespacedKey getKeyImmuneUntil() {
        if (KEY_IMMUNE_UNTIL == null) KEY_IMMUNE_UNTIL = new NamespacedKey(Atlas.instance, "atlas_crystal_immune_until");
        return KEY_IMMUNE_UNTIL;
    }
    private static NamespacedKey getKeyNametagDisplay() {
        if (KEY_NAMETAG_DISPLAY == null) KEY_NAMETAG_DISPLAY = new NamespacedKey(Atlas.instance, "atlas_crystal_nametag_display");
        return KEY_NAMETAG_DISPLAY;
    }
    private static NamespacedKey getKeyHpBonus() {
        if (KEY_HP_BONUS == null) KEY_HP_BONUS = new NamespacedKey(Atlas.instance, "atlas_crystal_hp_bonus");
        return KEY_HP_BONUS;
    }
    private static NamespacedKey getKeyClaimCapacity() {
        if (KEY_CLAIM_CAPACITY == null) KEY_CLAIM_CAPACITY = new NamespacedKey(Atlas.instance, "atlas_crystal_claim_cap");
        return KEY_CLAIM_CAPACITY;
    }
    private static NamespacedKey getKeySpentSp() {
        if (KEY_SPENT_SP == null) KEY_SPENT_SP = new NamespacedKey(Atlas.instance, "atlas_crystal_spent_sp");
        return KEY_SPENT_SP;
    }
    private static NamespacedKey getKeyProtectionMs() {
        if (KEY_PROTECTION_MS == null) KEY_PROTECTION_MS = new NamespacedKey(Atlas.instance, "atlas_crystal_protection_ms");
        return KEY_PROTECTION_MS;
    }
    private static NamespacedKey getKeyDefeatMul() {
        if (KEY_DEFEAT_MUL == null) KEY_DEFEAT_MUL = new NamespacedKey(Atlas.instance, "atlas_crystal_defeat_mul");
        return KEY_DEFEAT_MUL;
    }
    private static NamespacedKey getKeyDefeatWindow() {
        if (KEY_DEFEAT_WINDOW == null) KEY_DEFEAT_WINDOW = new NamespacedKey(Atlas.instance, "atlas_crystal_defeat_window");
        return KEY_DEFEAT_WINDOW;
    }

    // ── Config ────────────────────────────────────────────────────────────────
    public static double regenPerSecond         = 1.5;
    public static long   immunityBaseMs         = 18_000_000L;
    public static int    immunityMultiplierBase = 2;
    public static long   regenTimeoutMs         = 60_000L;

    public static void loadConfig(FileConfiguration config) {
        regenPerSecond         = config.getDouble("crystal.regen_per_second", 1.5);
        immunityBaseMs         = config.getLong("crystal.immunity_base_seconds", 18000L) * 1000L;
        immunityMultiplierBase = config.getInt("crystal.immunity_multiplier", 2);
        regenTimeoutMs         = config.getLong("crystal.regen_timeout_seconds", 60L) * 1000L;
    }

    // ── State ─────────────────────────────────────────────────────────────────
    /** entityUUID → AtlasCrystal */
    private static final Map<UUID, AtlasCrystal> crystals = new HashMap<>();

    /**
     * factionName → (crystalEntityUUID → AtlasCrystal).
     * LinkedHashMap preserves insertion order.
     */
    private static final Map<String, Map<UUID, AtlasCrystal>> factionCrystals = new HashMap<>();

    /** playerUUID → AtlasCrystal awaiting a name from the naming dialog. */
    private static final Map<UUID, AtlasCrystal> pendingNaming = new ConcurrentHashMap<>();

    /**
     * factionName → (crystalEntityUUID → home location).
     * Persisted to data file so homes are accessible even when the entity is unloaded.
     */
    private static final Map<String, LinkedHashMap<UUID, Location>> factionHomes = new LinkedHashMap<>();

    // ── Register ──────────────────────────────────────────────────────────────

    /** Registers a newly spawned atlas crystal with base HP and max HP. */
    public static AtlasCrystal register(EnderCrystal entity, String factionName) {
        double base = AtlasCrystal.BASE_MAX_HP;
        AtlasCrystal crystal = new AtlasCrystal(entity, factionName, base, base);
        entity.setCustomNameVisible(false);
        entity.getPersistentDataContainer().set(getKeyFaction(), PersistentDataType.STRING, factionName);
        entity.getPersistentDataContainer().set(getKeyHp(), PersistentDataType.DOUBLE, base);
        entity.getPersistentDataContainer().set(getKeyMaxHp(), PersistentDataType.DOUBLE, base);
        crystals.put(entity.getUniqueId(), crystal);
        crystal.updateNametag();
        return crystal;
    }

    /** Finalises the crystal's name after the player provides it. Auto-claims center chunk. */
    public static void assignName(AtlasCrystal crystal, String name) {
        crystal.setName(name);
        factionCrystals
                .computeIfAbsent(crystal.getFactionName(), k -> new LinkedHashMap<>())
                .put(crystal.getEntity().getUniqueId(), crystal);
        crystal.getEntity().getPersistentDataContainer()
                .set(getKeyName(), PersistentDataType.STRING, name);

        // Auto-claim the center chunk for this crystal
        Location loc = crystal.getEntity().getLocation();
        String worldName = loc.getWorld().getName();
        int cx = loc.getBlockX() >> 4;
        int cz = loc.getBlockZ() >> 4;
        String chunkKey = worldName + ":" + cx + ":" + cz;
        if (!crystal.getClaimedChunks().contains(chunkKey)) {
            crystal.getClaimedChunks().add(chunkKey);
            FactionClaimManager.claimChunk(crystal.getFactionName(), worldName, cx, cz);
        }

        crystal.updateNametag();
    }

    /**
     * Persists a crystal's home location to the in-memory map and to the data file.
     */
    public static void saveHome(AtlasCrystal crystal) {
        Location home = crystal.getHome();
        if (home == null || home.getWorld() == null) return;
        factionHomes.computeIfAbsent(crystal.getFactionName(), k -> new LinkedHashMap<>())
                .put(crystal.getEntity().getUniqueId(), home);
        saveCrystalData(Atlas.factionsDataConfig);
        Atlas.saveFactionsDataConfig();
    }

    // ── Persistence — scalar state in PDC ─────────────────────────────────────

    /**
     * Persists the full mutable state of a crystal to PDC.
     * Call after any significant state change (damage, upgrade applied).
     */
    public static void persistCrystalState(AtlasCrystal crystal) {
        var pdc = crystal.getEntity().getPersistentDataContainer();
        pdc.set(getKeyHp(),           PersistentDataType.DOUBLE,  crystal.getHp());
        pdc.set(getKeyMaxHp(),        PersistentDataType.DOUBLE,  crystal.getMaxHp());
        pdc.set(getKeyImmuneUntil(),  PersistentDataType.LONG,    crystal.getImmuneUntilMillis());
        pdc.set(getKeyHpBonus(),      PersistentDataType.DOUBLE,  crystal.getHpBonus());
        pdc.set(getKeyClaimCapacity(),PersistentDataType.INTEGER, crystal.getClaimCapacity());
        pdc.set(getKeySpentSp(),       PersistentDataType.INTEGER, crystal.getSpentSkillPoints());
        pdc.set(getKeyProtectionMs(),  PersistentDataType.LONG,    crystal.getPurchasedProtectionMs());
        pdc.set(getKeyDefeatMul(),     PersistentDataType.INTEGER, crystal.getDefeatMul());
        pdc.set(getKeyDefeatWindow(),  PersistentDataType.LONG,    crystal.getDefeatWindowEndMs());
    }

    // ── Persistence — complex data in factions-data.yml ───────────────────────

    public static void saveCrystalData(FileConfiguration config) {
        config.set("crystal_homes", null);
        config.set("crystal_data", null);

        if (factionHomes.isEmpty() && crystals.isEmpty()) return;

        // Homes
        if (!factionHomes.isEmpty()) {
            ConfigurationSection homeSec = config.createSection("crystal_homes");
            for (Map.Entry<String, LinkedHashMap<UUID, Location>> fEntry : factionHomes.entrySet()) {
                for (Map.Entry<UUID, Location> hEntry : fEntry.getValue().entrySet()) {
                    ConfigurationSection entry = homeSec.createSection(hEntry.getKey().toString());
                    entry.set("faction", fEntry.getKey());
                    entry.set("home", encodeHome(hEntry.getValue()));
                }
            }
        }

        // Crystal data (claims, chests, chest_sizes)
        ConfigurationSection dataSec = config.createSection("crystal_data");
        for (AtlasCrystal crystal : crystals.values()) {
            ConfigurationSection cs = dataSec.createSection(crystal.getEntity().getUniqueId().toString());
            cs.set("faction", crystal.getFactionName());
            if (crystal.isOutpost()) cs.set("outpost", true);
            if (!crystal.getClaimedChunks().isEmpty()) {
                cs.set("claimed_chunks", new ArrayList<>(crystal.getClaimedChunks()));
            }
            if (!crystal.getPurchasedChestSizes().isEmpty()) {
                cs.set("chest_sizes", crystal.getPurchasedChestSizes());
            }
            Map<Integer, ItemStack[]> chestMap = crystal.getChestContentsMap();
            if (!chestMap.isEmpty()) {
                ConfigurationSection chestsSec = cs.createSection("chests");
                for (Map.Entry<Integer, ItemStack[]> entry : chestMap.entrySet()) {
                    ConfigurationSection chestSec = chestsSec.createSection(String.valueOf(entry.getKey()));
                    ItemStack[] contents = entry.getValue();
                    for (int i = 0; i < contents.length; i++) {
                        if (contents[i] != null && contents[i].getType() != Material.AIR) {
                            chestSec.set(String.valueOf(i), contents[i]);
                        }
                    }
                }
            }
        }
    }

    /** Kept for backward compatibility with Atlas.java which calls loadCrystalHomes. */
    public static void loadCrystalHomes(FileConfiguration config) {
        loadCrystalData(config);
    }

    /** Also kept for backward compat with Atlas.onDisable which calls saveCrystalHomes. */
    public static void saveCrystalHomes(FileConfiguration config) {
        saveCrystalData(config);
    }

    private static void loadCrystalData(FileConfiguration config) {
        factionHomes.clear();

        ConfigurationSection homeSec = config.getConfigurationSection("crystal_homes");
        if (homeSec != null) {
            for (String uuidStr : homeSec.getKeys(false)) {
                ConfigurationSection entry = homeSec.getConfigurationSection(uuidStr);
                if (entry == null) continue;
                String factionName = entry.getString("faction");
                String homeStr     = entry.getString("home");
                if (factionName == null || homeStr == null) continue;
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    Location home = decodeHome(homeStr);
                    if (home != null)
                        factionHomes.computeIfAbsent(factionName, k -> new LinkedHashMap<>()).put(uuid, home);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        // crystal_data (claims, chests) is applied in restore() when entities load
    }

    // ── Restore (called when entity chunk loads) ──────────────────────────────

    /** Restores an atlas crystal from PDC when its chunk is loaded. */
    public static AtlasCrystal restore(EnderCrystal entity) {
        String factionName = entity.getPersistentDataContainer().get(getKeyFaction(), PersistentDataType.STRING);
        if (factionName == null) return null;

        Double savedHp    = entity.getPersistentDataContainer().get(getKeyHp(), PersistentDataType.DOUBLE);
        Double savedMaxHp = entity.getPersistentDataContainer().get(getKeyMaxHp(), PersistentDataType.DOUBLE);
        double hp    = savedHp    != null ? savedHp    : AtlasCrystal.BASE_MAX_HP;
        double maxHp = savedMaxHp != null ? savedMaxHp : AtlasCrystal.BASE_MAX_HP;

        String savedName = entity.getPersistentDataContainer().get(getKeyName(), PersistentDataType.STRING);
        if (savedName == null) savedName = "";

        AtlasCrystal crystal = new AtlasCrystal(entity, factionName, hp, maxHp);
        crystal.setName(savedName);
        entity.setCustomNameVisible(false);

        // Restore home — primary source is the in-memory/data-file factionHomes map
        Location home = null;
        LinkedHashMap<UUID, Location> homes = factionHomes.get(factionName);
        if (homes != null) home = homes.get(entity.getUniqueId());
        if (home == null) {
            // Migration fallback: read from legacy PDC
            String homeStr = entity.getPersistentDataContainer().get(getKeyHome(), PersistentDataType.STRING);
            if (homeStr != null) {
                home = decodeHome(homeStr);
                if (home != null) {
                    factionHomes.computeIfAbsent(factionName, k -> new LinkedHashMap<>())
                            .put(entity.getUniqueId(), home);
                    saveCrystalData(Atlas.factionsDataConfig);
                    Atlas.saveFactionsDataConfig();
                }
            }
        }
        if (home != null) crystal.setHome(home);

        // Restore scalar PDC fields (maxHp already loaded correctly, use restore methods)
        Double hpBonus        = entity.getPersistentDataContainer().get(getKeyHpBonus(),      PersistentDataType.DOUBLE);
        Integer savedClaimCap = entity.getPersistentDataContainer().get(getKeyClaimCapacity(),PersistentDataType.INTEGER);
        Integer savedSpentSp  = entity.getPersistentDataContainer().get(getKeySpentSp(),      PersistentDataType.INTEGER);
        Long savedProtMs      = entity.getPersistentDataContainer().get(getKeyProtectionMs(), PersistentDataType.LONG);
        Long savedImmune      = entity.getPersistentDataContainer().get(getKeyImmuneUntil(),  PersistentDataType.LONG);
        Integer savedDefeatMul    = entity.getPersistentDataContainer().get(getKeyDefeatMul(),    PersistentDataType.INTEGER);
        Long    savedDefeatWindow = entity.getPersistentDataContainer().get(getKeyDefeatWindow(), PersistentDataType.LONG);

        if (hpBonus           != null) crystal.restoreHpBonus(hpBonus);
        if (savedClaimCap     != null) crystal.restoreClaimCapacity(savedClaimCap);
        if (savedSpentSp      != null) crystal.restoreSpentSkillPoints(savedSpentSp);
        if (savedProtMs       != null) crystal.restoreProtectionMs(savedProtMs);
        if (savedImmune       != null) crystal.setImmuneUntilMillis(savedImmune);
        if (savedDefeatMul    != null) crystal.restoreDefeatMul(savedDefeatMul);
        if (savedDefeatWindow != null) crystal.restoreDefeatWindowEndMs(savedDefeatWindow);

        // Restore nametag display UUID
        String displayUUIDStr = entity.getPersistentDataContainer().get(getKeyNametagDisplay(), PersistentDataType.STRING);
        if (displayUUIDStr != null) {
            try { crystal.setTextDisplayUUID(UUID.fromString(displayUUIDStr)); }
            catch (IllegalArgumentException ignored) {}
        }

        // Restore claims + chests from data file
        ConfigurationSection dataSec = Atlas.factionsDataConfig.getConfigurationSection("crystal_data");
        if (dataSec != null) {
            ConfigurationSection cs = dataSec.getConfigurationSection(entity.getUniqueId().toString());
            if (cs != null) {
                if (cs.getBoolean("outpost", false)) crystal.setOutpost(true);
                for (String chunkKey : cs.getStringList("claimed_chunks")) {
                    crystal.getClaimedChunks().add(chunkKey);
                    // Ensure FactionClaimManager is in sync
                    String[] parts = chunkKey.split(":");
                    if (parts.length == 3) {
                        try {
                            FactionClaimManager.claimChunk(factionName, parts[0],
                                    Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                        } catch (NumberFormatException ignored) {}
                    }
                }
                for (int size : cs.getIntegerList("chest_sizes")) {
                    crystal.getPurchasedChestSizes().add(size);
                }
                ConfigurationSection chestsSec = cs.getConfigurationSection("chests");
                if (chestsSec != null) {
                    for (String indexStr : chestsSec.getKeys(false)) {
                        try {
                            int chestIndex = Integer.parseInt(indexStr);
                            ConfigurationSection chestSec = chestsSec.getConfigurationSection(indexStr);
                            if (chestSec == null) continue;
                            int chestSize = chestIndex < crystal.getPurchasedChestSizes().size()
                                    ? crystal.getPurchasedChestSizes().get(chestIndex) : 27;
                            ItemStack[] contents = new ItemStack[chestSize];
                            for (String slotStr : chestSec.getKeys(false)) {
                                try {
                                    int slot = Integer.parseInt(slotStr);
                                    if (slot >= 0 && slot < chestSize) contents[slot] = chestSec.getItemStack(slotStr);
                                } catch (NumberFormatException ignored) {}
                            }
                            crystal.setChestContents(chestIndex, contents);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        }

        crystals.put(entity.getUniqueId(), crystal);
        if (!savedName.isEmpty()) {
            factionCrystals.computeIfAbsent(factionName, k -> new LinkedHashMap<>())
                    .put(entity.getUniqueId(), crystal);
        }
        crystal.updateNametag();
        return crystal;
    }

    // ── Skill purchases ───────────────────────────────────────────────────────

    public static boolean purchaseHpUpgrade(String factionName, UUID crystalUUID, FactionLevelManager.HpTier tier) {
        Faction faction = FactionManager.getFaction(factionName);
        AtlasCrystal crystal = crystals.get(crystalUUID);
        if (faction == null || crystal == null) return false;
        if (!faction.spendSkillPoints(tier.cost())) return false;
        crystal.addHpBonus(tier.bonus());
        crystal.addSpentSkillPoints(tier.cost());
        crystal.setHp(Math.min(crystal.getHp() + tier.bonus(), crystal.getMaxHp()));
        persistCrystalState(crystal);
        crystal.updateNametag();
        saveCrystalData(Atlas.factionsDataConfig);
        Atlas.saveFactionsDataConfig();
        return true;
    }

    public static boolean purchaseClaimUpgrade(String factionName, UUID crystalUUID, FactionLevelManager.ClaimTier tier) {
        Faction faction = FactionManager.getFaction(factionName);
        AtlasCrystal crystal = crystals.get(crystalUUID);
        if (faction == null || crystal == null) return false;
        if (!faction.spendSkillPoints(tier.cost())) return false;
        crystal.addClaimCapacity(tier.amount());
        crystal.addSpentSkillPoints(tier.cost());
        persistCrystalState(crystal);
        saveCrystalData(Atlas.factionsDataConfig);
        Atlas.saveFactionsDataConfig();
        return true;
    }

    public static boolean purchaseChestUpgrade(String factionName, UUID crystalUUID, FactionLevelManager.ChestTier tier) {
        Faction faction = FactionManager.getFaction(factionName);
        AtlasCrystal crystal = crystals.get(crystalUUID);
        if (faction == null || crystal == null) return false;
        if (!faction.spendSkillPoints(tier.cost())) return false;
        crystal.addPurchasedChest(tier.size());
        crystal.addSpentSkillPoints(tier.cost());
        persistCrystalState(crystal);
        saveCrystalData(Atlas.factionsDataConfig);
        Atlas.saveFactionsDataConfig();
        return true;
    }

    public static boolean purchaseProtectionUpgrade(String factionName, UUID crystalUUID, FactionLevelManager.ProtectionTier tier) {
        Faction faction = FactionManager.getFaction(factionName);
        AtlasCrystal crystal = crystals.get(crystalUUID);
        if (faction == null || crystal == null) return false;
        if (!faction.spendSkillPoints(tier.cost())) return false;
        crystal.addProtectionMs(tier.durationMs());
        crystal.addSpentSkillPoints(tier.cost());
        persistCrystalState(crystal);
        saveCrystalData(Atlas.factionsDataConfig);
        Atlas.saveFactionsDataConfig();
        return true;
    }

    /** Purchases the outpost skill for a faction (one-time, faction-level unlock). */
    public static boolean purchaseOutpostUpgrade(String factionName) {
        Faction faction = FactionManager.getFaction(factionName);
        if (faction == null || faction.isOutpostUnlocked()) return false;
        if (!faction.spendSkillPoints(FactionLevelManager.getOutpostTier().cost())) return false;
        faction.setOutpostUnlocked(true);
        return true;
    }

    // ── Crystal destruction with full consequences ────────────────────────────

    /**
     * Permanently destroys a crystal and applies all consequences to the faction:
     * - Remove its claimed chunks
     * - Drop its chest contents at the crystal location
     * - Lower faction level proportionally to spentSkillPoints
     * - If all crystals gone, disband the faction
     */
    public static void destroyCrystal(UUID crystalUUID) {
        AtlasCrystal crystal = crystals.get(crystalUUID);
        if (crystal == null) return;

        String factionName = crystal.getFactionName();
        Faction faction = FactionManager.getFaction(factionName);

        // 1. Remove claims
        FactionClaimManager.removeClaimsForCrystal(new ArrayList<>(crystal.getClaimedChunks()));
        crystal.getClaimedChunks().clear();

        // 2. Drop chest contents at crystal location
        Location dropLoc = crystal.getEntity().getLocation();
        for (Map.Entry<Integer, ItemStack[]> entry : crystal.getChestContentsMap().entrySet()) {
            if (entry.getValue() == null) continue;
            for (ItemStack stack : entry.getValue()) {
                if (stack != null && stack.getType() != Material.AIR) {
                    dropLoc.getWorld().dropItemNaturally(dropLoc, stack);
                }
            }
        }
        crystal.getChestContentsMap().clear();
        crystal.getPurchasedChestSizes().clear();

        // 3. Lower faction level by spentSkillPoints / skillPointsPerLevel
        if (faction != null && crystal.getSpentSkillPoints() > 0) {
            int sp = FactionLevelManager.getSkillPointsPerLevel();
            int levelsToLose = (sp > 0) ? crystal.getSpentSkillPoints() / sp : 0;
            if (levelsToLose > 0) {
                int newLevel = Math.max(0, faction.getLevel() - levelsToLose);
                faction.setLevel(newLevel);
                faction.setExp(0);
            }
        }

        // 4. Remove crystal from tracking
        remove(crystalUUID);

        // 5. Disband faction if no crystals remain — outpost destruction never triggers disbandment
        if (!crystal.isOutpost()) {
            Map<UUID, AtlasCrystal> remaining = factionCrystals.get(factionName);
            if (remaining == null || remaining.isEmpty()) {
                FactionManager.disbandFaction(factionName);
            }
        }

        // 6. Persist
        saveCrystalData(Atlas.factionsDataConfig);
        Atlas.saveFactionsDataConfig();
    }

    // ── Claim helpers ─────────────────────────────────────────────────────────

    /**
     * Finds the best crystal in a faction to absorb a new claim.
     * Returns the nearest crystal (in chunk distance) that has free capacity.
     */
    public static AtlasCrystal findCrystalForClaim(String factionName, String worldName, int cx, int cz) {
        Map<UUID, AtlasCrystal> map = factionCrystals.get(factionName);
        if (map == null) return null;
        AtlasCrystal best = null;
        double bestDist = Double.MAX_VALUE;
        for (AtlasCrystal c : map.values()) {
            if (!c.getEntity().getWorld().getName().equals(worldName)) continue;
            if (c.getClaimedChunks().size() >= c.getClaimCapacity()) continue;
            int ccx = c.getEntity().getLocation().getBlockX() >> 4;
            int ccz = c.getEntity().getLocation().getBlockZ() >> 4;
            double dist = Math.hypot(ccx - cx, ccz - cz);
            if (dist < bestDist) { bestDist = dist; best = c; }
        }
        return best;
    }

    public static int getTotalClaimCapacity(String factionName) {
        Map<UUID, AtlasCrystal> map = factionCrystals.get(factionName);
        if (map == null) return 0;
        return map.values().stream().mapToInt(AtlasCrystal::getClaimCapacity).sum();
    }

    public static int getTotalClaimedChunks(String factionName) {
        Map<UUID, AtlasCrystal> map = factionCrystals.get(factionName);
        if (map == null) return 0;
        return map.values().stream().mapToInt(c -> c.getClaimedChunks().size()).sum();
    }

    // ── Nametag display (TextDisplay) ─────────────────────────────────────────

    /**
     * Spawns or updates the TextDisplay entity used as this crystal's overhead nametag.
     */
    public static void spawnOrUpdateNametagDisplay(AtlasCrystal crystal) {
        Location loc = crystal.getEntity().getLocation().add(0, 2.5, 0);
        TextDisplay display = null;
        UUID existingUUID = crystal.getTextDisplayUUID();
        if (existingUUID != null) {
            Entity e = Bukkit.getEntity(existingUUID);
            if (e instanceof TextDisplay td) display = td;
        }
        if (display == null) {
            if (!loc.isChunkLoaded()) return;
            display = loc.getWorld().spawn(loc, TextDisplay.class, td -> {
                td.setBillboard(Display.Billboard.CENTER);
                td.setPersistent(true);
                td.setInvulnerable(true);
                td.setGravity(false);
            });
            crystal.setTextDisplayUUID(display.getUniqueId());
            crystal.getEntity().getPersistentDataContainer()
                    .set(getKeyNametagDisplay(), PersistentDataType.STRING, display.getUniqueId().toString());
        }
        Component text = crystal.buildNametagComponent();
        display.text(text);
    }

    /** Removes the TextDisplay nametag entity for a crystal, if present. */
    private static void removeNametagDisplay(AtlasCrystal crystal) {
        UUID displayUUID = crystal.getTextDisplayUUID();
        if (displayUUID == null) return;
        Entity e = Bukkit.getEntity(displayUUID);
        if (e != null) e.remove();
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public static AtlasCrystal getCrystal(UUID entityUUID) { return crystals.get(entityUUID); }
    public static boolean isAtlasCrystal(UUID entityUUID)  { return crystals.containsKey(entityUUID); }

    /** Returns true if this faction has any crystal with the given display name. */
    public static boolean hasCrystalWithName(String factionName, String crystalName) {
        Map<UUID, AtlasCrystal> map = factionCrystals.get(factionName);
        if (map == null) return false;
        return map.values().stream().anyMatch(c -> crystalName.equals(c.getName()));
    }

    /** Returns the first crystal in this faction with the given display name, or null. */
    public static AtlasCrystal getCrystalByName(String factionName, String crystalName) {
        Map<UUID, AtlasCrystal> map = factionCrystals.get(factionName);
        if (map == null) return null;
        return map.values().stream().filter(c -> crystalName.equals(c.getName())).findFirst().orElse(null);
    }

    /** Returns all named crystals for a faction in insertion order. */
    public static Collection<AtlasCrystal> getFactionCrystals(String factionName) {
        Map<UUID, AtlasCrystal> map = factionCrystals.get(factionName);
        return map == null ? Collections.emptyList() : Collections.unmodifiableCollection(map.values());
    }

    /** Returns the home of the first crystal with a home set for this faction, or null if none. */
    public static Location getFirstHome(String factionName) {
        Map<UUID, AtlasCrystal> map = factionCrystals.get(factionName);
        if (map != null) {
            for (AtlasCrystal c : map.values()) {
                if (c.getHome() != null) return c.getHome();
            }
        }
        LinkedHashMap<UUID, Location> homes = factionHomes.get(factionName);
        if (homes != null && !homes.isEmpty()) return homes.values().iterator().next();
        return null;
    }

    // ── Pending naming ────────────────────────────────────────────────────────

    public static void setPendingNaming(UUID playerUUID, AtlasCrystal crystal) { pendingNaming.put(playerUUID, crystal); }
    public static AtlasCrystal getPendingNaming(UUID playerUUID)               { return pendingNaming.get(playerUUID); }
    public static void clearPendingNaming(UUID playerUUID)                     { pendingNaming.remove(playerUUID); }

    // ── Mutations ─────────────────────────────────────────────────────────────

    /**
     * Updates the faction name stored on every crystal that belongs to {@code oldName}.
     */
    public static void renameFactionCrystals(String oldName, String newName) {
        for (AtlasCrystal crystal : crystals.values()) {
            if (crystal.getFactionName().equals(oldName)) {
                crystal.setFactionName(newName);
                crystal.getEntity().getPersistentDataContainer()
                        .set(getKeyFaction(), PersistentDataType.STRING, newName);
                crystal.updateNametag();
            }
        }
        Map<UUID, AtlasCrystal> map = factionCrystals.remove(oldName);
        if (map != null) factionCrystals.put(newName, map);
        LinkedHashMap<UUID, Location> homes = factionHomes.remove(oldName);
        if (homes != null) factionHomes.put(newName, homes);
    }

    /**
     * Renames a crystal within a faction.
     * Returns false if no crystal with oldName is found in this faction.
     */
    public static boolean renameCrystal(String factionName, String oldName, String newName) {
        Map<UUID, AtlasCrystal> map = factionCrystals.get(factionName);
        if (map == null) return false;
        AtlasCrystal found = null;
        for (AtlasCrystal c : map.values()) if (oldName.equals(c.getName())) { found = c; break; }
        if (found == null) return false;
        found.setName(newName);
        found.getEntity().getPersistentDataContainer().set(getKeyName(), PersistentDataType.STRING, newName);
        found.updateNametag();
        return true;
    }

    /** Removes crystal from tracking WITHOUT applying destruction consequences. */
    public static void remove(UUID entityUUID) {
        AtlasCrystal crystal = crystals.remove(entityUUID);
        if (crystal == null) return;
        removeNametagDisplay(crystal);
        Map<UUID, AtlasCrystal> map = factionCrystals.get(crystal.getFactionName());
        if (map != null) map.remove(entityUUID);
        LinkedHashMap<UUID, Location> homes = factionHomes.get(crystal.getFactionName());
        if (homes != null) homes.remove(entityUUID);
    }

    /** Removes and despawns all crystals belonging to a faction. Called when a faction is disbanded. */
    public static void removeAllForFaction(String factionName) {
        Map<UUID, AtlasCrystal> map = factionCrystals.remove(factionName);
        if (map == null) return;
        for (AtlasCrystal crystal : map.values()) {
            crystals.remove(crystal.getEntity().getUniqueId());
            removeNametagDisplay(crystal);
            if (!crystal.getEntity().isDead()) crystal.getEntity().remove();
        }
        factionHomes.remove(factionName);
    }

    // ── Regen scheduler ───────────────────────────────────────────────────────

    /** Starts the 1-second regen ticker. Call once from Atlas.onEnable. */
    public static void schedule(Plugin plugin) {
        new BukkitRunnable() {
            @Override public void run() {
                Iterator<Map.Entry<UUID, AtlasCrystal>> iter = crystals.entrySet().iterator();
                while (iter.hasNext()) {
                    AtlasCrystal crystal = iter.next().getValue();
                    if (crystal.getEntity().isDead()) {
                        removeNametagDisplay(crystal);
                        Map<UUID, AtlasCrystal> m = factionCrystals.get(crystal.getFactionName());
                        if (m != null) m.remove(crystal.getEntity().getUniqueId());
                        iter.remove();
                        continue;
                    }
                    if (crystal.canRegen() && crystal.getHp() < crystal.getMaxHp()) {
                        crystal.regen(regenPerSecond);
                        crystal.getEntity().getPersistentDataContainer()
                                .set(getKeyHp(), PersistentDataType.DOUBLE, crystal.getHp());
                    }
                    // Passive de-escalation: when the defeat window expires step defeatMul back down by one factor
                    long tickNow = System.currentTimeMillis();
                    if (crystal.getDefeatWindowEndMs() > 0 && tickNow > crystal.getDefeatWindowEndMs()) {
                        if (crystal.getDefeatMul() > 1) {
                            int newMul = Math.max(1, crystal.getDefeatMul() / immunityMultiplierBase);
                            crystal.setDefeatMul(newMul);
                            // Set next window so de-escalation continues step-by-step
                            long nextWindow = crystal.getPurchasedProtectionMs() > 0
                                    ? crystal.getPurchasedProtectionMs() * (long) newMul * 2 : 0;
                            crystal.setDefeatWindowEndMs(nextWindow > 0 ? tickNow + nextWindow : 0);
                        } else {
                            crystal.setDefeatWindowEndMs(0);
                        }
                        persistCrystalState(crystal);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String encodeHome(Location home) {
        return home.getWorld().getName() + "," + home.getX() + "," + home.getY() + "," + home.getZ()
                + "," + home.getYaw() + "," + home.getPitch();
    }

    private static Location decodeHome(String encoded) {
        String[] parts = encoded.split(",", 6);
        if (parts.length != 6) return null;
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        try {
            return new Location(world, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]),
                    Double.parseDouble(parts[3]), Float.parseFloat(parts[4]), Float.parseFloat(parts[5]));
        } catch (NumberFormatException e) { return null; }
    }
}
