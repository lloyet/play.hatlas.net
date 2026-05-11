package org.minecraft.atlas.faction;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

public class FactionLevelManager {

    public static final int MAX_LEVEL = 99;
    private static final double BASE_EXP = 100.0;
    private static final double EXP_MULTIPLIER = 1.09;

    private static final int[] expForLevel = new int[MAX_LEVEL + 1];
    private static int skillPointsPerLevel = 1;

    public record HpTier(double bonus, double regen, int cost) {}
    public record ClaimTier(int amount, int cost) {}
    public record ChestTier(int size, int cost) {}
    public record ProtectionTier(long durationMs, int cost) {}
    public record OutpostTier(int cost) {}
    public record HomeTier(int amount, int cost) {}

    private static List<HpTier>         hpTiers         = new ArrayList<>();
    private static List<ClaimTier>      claimTiers      = new ArrayList<>();
    private static List<ChestTier>      chestTiers      = new ArrayList<>();
    private static List<ProtectionTier> protectionTiers = new ArrayList<>();
    private static OutpostTier          outpostTier     = null;
    private static List<HomeTier>       homeTiers       = new ArrayList<>();

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

    public static int getSkillPointsPerLevel() { return skillPointsPerLevel; }
    public static List<HpTier>         getHpTiers()         { return Collections.unmodifiableList(hpTiers); }
    public static List<ClaimTier>      getClaimTiers()      { return Collections.unmodifiableList(claimTiers); }
    public static List<ChestTier>      getChestTiers()      { return Collections.unmodifiableList(chestTiers); }
    public static List<ProtectionTier> getProtectionTiers() { return Collections.unmodifiableList(protectionTiers); }
    public static OutpostTier          getOutpostTier()     { return outpostTier; }
    public static List<HomeTier>       getHomeTiers()       { return Collections.unmodifiableList(homeTiers); }

    /**
     * Loads skill-point upgrade tiers from factions.yml. Call once from Atlas.onEnable.
     * Tiers are populated <em>only</em> from the file — a skill section that is missing
     * or empty results in an empty tier list, so the skill GUI hides that row entirely.
     */
    public static void loadUpgrades(FileConfiguration config) {
        skillPointsPerLevel = config.getInt("skill_points_per_level", 1);
        hpTiers.clear(); claimTiers.clear(); chestTiers.clear(); protectionTiers.clear(); homeTiers.clear();
        outpostTier = null;

        ConfigurationSection skills = config.getConfigurationSection("skills");
        if (skills == null) return;

        for (Map<?, ?> m : skills.getMapList("hp")) {
            if (m.get("bonus") instanceof Number b && m.get("cost") instanceof Number c) {
                double regen = m.get("regen") instanceof Number r ? r.doubleValue() : 0d;
                hpTiers.add(new HpTier(b.doubleValue(), regen, c.intValue()));
            }
        }
        for (Map<?, ?> m : skills.getMapList("claims")) {
            if (m.get("amount") instanceof Number a && m.get("cost") instanceof Number c)
                claimTiers.add(new ClaimTier(a.intValue(), c.intValue()));
        }
        for (Map<?, ?> m : skills.getMapList("chest")) {
            if (m.get("size") instanceof Number s && m.get("cost") instanceof Number c)
                chestTiers.add(new ChestTier(s.intValue(), c.intValue()));
        }
        for (Map<?, ?> m : skills.getMapList("protection")) {
            if (m.get("minutes") instanceof Number min && m.get("cost") instanceof Number c)
                protectionTiers.add(new ProtectionTier(min.longValue() * 60_000L, c.intValue()));
        }
        for (Map<?, ?> m : skills.getMapList("homes")) {
            if (m.get("amount") instanceof Number a && m.get("cost") instanceof Number c)
                homeTiers.add(new HomeTier(a.intValue(), c.intValue()));
        }
        ConfigurationSection outpostSection = skills.getConfigurationSection("outpost");
        if (outpostSection != null && outpostSection.contains("cost")) {
            outpostTier = new OutpostTier(outpostSection.getInt("cost"));
        }
    }
}
