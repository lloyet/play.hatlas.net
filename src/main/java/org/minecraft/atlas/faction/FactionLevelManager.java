package org.minecraft.atlas.faction;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

/**
 * Handles faction level thresholds and upgrade checkpoint configuration.
 * Level 0 is the starting state; level 100 is the maximum.
 * EXP required: u(1) = 100, u(n) = u(n-1) * 1.09 (rounded up to nearest int).
 */
public class FactionLevelManager {

    public static final int MAX_LEVEL = 100;
    private static final double BASE_EXP = 100.0;
    private static final double EXP_MULTIPLIER = 1.09;

    // expForLevel[n] = exp required to advance from level (n-1) to level n (1-indexed, 1..100)
    private static final int[] expForLevel = new int[MAX_LEVEL + 1];

    // Checkpoint level -> HP upgrade granted when a faction reaches that checkpoint
    private static final Map<Integer, Double> upgradeHpMap = new LinkedHashMap<>();
    // Sorted ascending list of checkpoint levels
    private static final List<Integer> checkpoints = new ArrayList<>();

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

    /** Loads upgrade checkpoint HP values from checkpoints.yml. Call once from Atlas.onEnable. */
    public static void loadCheckpoints(FileConfiguration config) {
        upgradeHpMap.clear();
        checkpoints.clear();

        List<?> list = config.getList("checkpoints");
        if (list == null) return;

        for (Object obj : list) {
            if (obj instanceof Map<?, ?> map) {
                Object lvl = map.get("level");
                Object bonus = map.get("bonus_hp");
                if (lvl instanceof Integer level && bonus instanceof Number hp) {
                    upgradeHpMap.put(level, hp.doubleValue());
                    checkpoints.add(level);
                }
            }
        }
        Collections.sort(checkpoints);
    }

    public static boolean isCheckpoint(int level) {
        return upgradeHpMap.containsKey(level);
    }

    /** Returns the HP upgrade granted at the given checkpoint level. */
    public static double getUpgradeHp(int level) {
        return upgradeHpMap.getOrDefault(level, 0.0);
    }

    /** Returns an unmodifiable sorted list of all checkpoint levels. */
    public static List<Integer> getCheckpoints() {
        return Collections.unmodifiableList(checkpoints);
    }

    /**
     * Returns the largest checkpoint level strictly less than currentLevel,
     * or -1 if no such checkpoint exists (meaning the next state is level 0).
     */
    public static int getPreviousCheckpoint(int currentLevel) {
        int prev = -1;
        for (int cp : checkpoints) {
            if (cp < currentLevel) prev = cp;
            else break;
        }
        return prev;
    }

    /** Returns an unmodifiable map of checkpoint level -> HP upgrade amount. */
    public static Map<Integer, Double> getUpgradeBonusMap() {
        return Collections.unmodifiableMap(upgradeHpMap);
    }
}
