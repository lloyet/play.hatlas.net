package org.minecraft.atlas.faction;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

/**
 * Handles faction level thresholds and upgrade configuration.
 * Level 0 is the starting state; level 100 is the maximum.
 * EXP required: u(1) = 100, u(n) = u(n-1) * 1.09 (rounded up to nearest int).
 */
public class FactionLevelManager {

    public static final int MAX_LEVEL = 99;
    private static final double BASE_EXP = 100.0;
    private static final double EXP_MULTIPLIER = 1.09;

    // expForLevel[n] = exp required to advance from level (n-1) to level n (1-indexed, 1..100)
    private static final int[] expForLevel = new int[MAX_LEVEL + 1];

    // Upgrade level -> HP bonus granted when a faction reaches that upgrade level
    private static final Map<Integer, Double> upgradeHpMap = new LinkedHashMap<>();
    // Upgrade level -> number of virtual chests unlocked at that upgrade level
    private static final Map<Integer, Integer> upgradeChestMap = new LinkedHashMap<>();
    // Upgrade level -> inventory size (27 or 54) for chests unlocked at that level
    private static final Map<Integer, Integer> upgradeChestSizeMap = new LinkedHashMap<>();
    // Upgrade level -> number of freeclaims granted when this upgrade is applied
    private static final Map<Integer, Integer> upgradeClaimsMap = new LinkedHashMap<>();
    // Sorted ascending list of upgrade levels
    private static final List<Integer> upgradeLevels = new ArrayList<>();

    static {
        expForLevel[0] = 0;
        double raw = BASE_EXP;
        expForLevel[1] = (int) Math.ceil(raw);
        for (int i = 2; i <= MAX_LEVEL; i++) {
            raw *= EXP_MULTIPLIER;
            expForLevel[i] = (int) Math.ceil(raw);
        }
    }

    /** Returns the exp required to go from level (n-1) to level n. Returns 0 for invalid n. */
    public static int getExpRequiredForLevel(int n) {
        if (n <= 0 || n > MAX_LEVEL) return 0;
        return expForLevel[n];
    }

    /** Loads upgrade values from config.yml. Call once from Atlas.onEnable. */
    public static void loadUpgrades(FileConfiguration config) {
        upgradeHpMap.clear();
        upgradeChestMap.clear();
        upgradeChestSizeMap.clear();
        upgradeClaimsMap.clear();
        upgradeLevels.clear();

        List<?> list = config.getList("upgrades");
        if (list == null) return;

        for (Object obj : list) {
            if (obj instanceof Map<?, ?> map) {
                Object lvl = map.get("level");
                Object bonus = map.get("bonus_hp");
                if (lvl instanceof Integer level && bonus instanceof Number hp) {
                    upgradeHpMap.put(level, hp.doubleValue());
                    Object chestBonus = map.get("bonus_chest");
                    int chestCount = chestBonus instanceof Number ? ((Number) chestBonus).intValue() : 0;
                    upgradeChestMap.put(level, chestCount);
                    if (chestCount > 0) {
                        Object chestSize = map.get("bonus_chest_size");
                        upgradeChestSizeMap.put(level,
                                chestSize instanceof Number ? ((Number) chestSize).intValue() : 27);
                    }
                    Object claimsBonus = map.get("bonus_claims");
                    upgradeClaimsMap.put(level, claimsBonus instanceof Number ? ((Number) claimsBonus).intValue() : 0);
                    upgradeLevels.add(level);
                }
            }
        }
        Collections.sort(upgradeLevels);
    }

    public static boolean isUpgrade(int level) {
        return upgradeHpMap.containsKey(level);
    }

    /** Returns the HP bonus granted at the given upgrade level. */
    public static double getUpgradeHp(int level) {
        return upgradeHpMap.getOrDefault(level, 0.0);
    }

    /** Returns the number of virtual chests unlocked at the given upgrade level (0 if none). */
    public static int getUpgradeChests(int level) {
        return upgradeChestMap.getOrDefault(level, 0);
    }

    /** Returns the slot count (27 = single, 54 = double) of the chest(s) unlocked at this upgrade level. */
    public static int getUpgradeChestSize(int level) {
        return upgradeChestSizeMap.getOrDefault(level, 27);
    }

    /**
     * Returns the number of chests available based on which upgrade levels have actually
     * been applied to a crystal (i.e. confirmed by the player).
     */
    public static int getAvailableChestsFromApplied(java.util.Set<Integer> appliedUpgrades) {
        int total = 0;
        for (Map.Entry<Integer, Integer> e : upgradeChestMap.entrySet()) {
            if (appliedUpgrades.contains(e.getKey())) total += e.getValue();
        }
        return total;
    }

    /**
     * Returns the slot count for a chest at {@code chestIndex} counting only
     * upgrade levels present in {@code appliedUpgrades}.
     */
    public static int getChestSizeFromApplied(int chestIndex, java.util.Set<Integer> appliedUpgrades) {
        int count = 0;
        for (int upgradeLevel : upgradeLevels) {
            if (!appliedUpgrades.contains(upgradeLevel)) continue;
            int chestsAtLevel = upgradeChestMap.getOrDefault(upgradeLevel, 0);
            for (int i = 0; i < chestsAtLevel; i++) {
                if (count == chestIndex) return upgradeChestSizeMap.getOrDefault(upgradeLevel, 27);
                count++;
            }
        }
        return 27;
    }

    /** Returns the number of freeclaims granted when the given upgrade level is applied (0 if none). */
    public static int getUpgradeClaims(int level) {
        return upgradeClaimsMap.getOrDefault(level, 0);
    }

    /** Returns an unmodifiable sorted list of all upgrade levels. */
    public static List<Integer> getUpgradeLevels() {
        return Collections.unmodifiableList(upgradeLevels);
    }

    /**
     * Returns the largest upgrade level strictly less than currentLevel,
     * or -1 if no such level exists (meaning the next state is level 0).
     */
    public static int getPreviousUpgrade(int currentLevel) {
        int prev = -1;
        for (int ul : upgradeLevels) {
            if (ul < currentLevel) prev = ul;
            else break;
        }
        return prev;
    }

    /** Returns an unmodifiable map of upgrade level -> HP bonus amount. */
    public static Map<Integer, Double> getUpgradeBonusMap() {
        return Collections.unmodifiableMap(upgradeHpMap);
    }

    /**
     * Returns the total number of virtual chests available to a faction at the given level.
     * Sums {@code bonus_chest} for every upgrade level ≤ {@code factionLevel}.
     */
    public static int getAvailableChests(int factionLevel) {
        int total = 0;
        for (Map.Entry<Integer, Integer> entry : upgradeChestMap.entrySet()) {
            if (entry.getKey() <= factionLevel) total += entry.getValue();
        }
        return total;
    }

    /**
     * Returns the inventory size (27 for single chest, 54 for double chest) for the
     * 0-based chest at {@code chestIndex}. Iterates upgrade levels in ascending order,
     * counting cumulative chests until the target index is reached.
     */
    public static int getChestSize(int chestIndex) {
        int count = 0;
        for (int upgradeLevel : upgradeLevels) {
            int chestsAtLevel = upgradeChestMap.getOrDefault(upgradeLevel, 0);
            for (int i = 0; i < chestsAtLevel; i++) {
                if (count == chestIndex) {
                    return upgradeChestSizeMap.getOrDefault(upgradeLevel, 27);
                }
                count++;
            }
        }
        return 27;
    }
}
