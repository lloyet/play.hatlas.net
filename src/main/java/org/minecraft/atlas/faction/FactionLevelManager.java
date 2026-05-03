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

    public record HpTier(double bonus, int cost) {}
    public record ClaimTier(int amount, int cost) {}
    public record ChestTier(int size, int cost) {}
    public record ProtectionTier(long durationMs, int cost) {}
    public record OutpostTier(int cost) {}

    private static List<HpTier>         hpTiers         = new ArrayList<>();
    private static List<ClaimTier>      claimTiers      = new ArrayList<>();
    private static List<ChestTier>      chestTiers      = new ArrayList<>();
    private static List<ProtectionTier> protectionTiers = new ArrayList<>();
    private static OutpostTier          outpostTier     = new OutpostTier(15);

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

    /** Loads skill-point upgrade tiers from factions.yml. Call once from Atlas.onEnable. */
    public static void loadUpgrades(FileConfiguration config) {
        skillPointsPerLevel = config.getInt("skill_points_per_level", 1);
        hpTiers.clear(); claimTiers.clear(); chestTiers.clear(); protectionTiers.clear();

        ConfigurationSection skills = config.getConfigurationSection("skills");
        if (skills != null) {
            for (Map<?, ?> m : skills.getMapList("hp")) {
                if (m.get("bonus") instanceof Number b && m.get("cost") instanceof Number c)
                    hpTiers.add(new HpTier(b.doubleValue(), c.intValue()));
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
            ConfigurationSection outpostSection = skills.getConfigurationSection("outpost");
            if (outpostSection != null) {
                outpostTier = new OutpostTier(outpostSection.getInt("cost", 15));
            }
        }

        // Defaults if empty
        if (hpTiers.isEmpty())
            hpTiers = new ArrayList<>(List.of(
                    new HpTier(30, 1), new HpTier(50, 2), new HpTier(100, 3), new HpTier(200, 5)));
        if (claimTiers.isEmpty())
            claimTiers = new ArrayList<>(List.of(
                    new ClaimTier(3, 3), new ClaimTier(5, 4), new ClaimTier(10, 6)));
        if (chestTiers.isEmpty())
            chestTiers = new ArrayList<>(List.of(
                    new ChestTier(9, 1), new ChestTier(27, 2), new ChestTier(54, 3)));
        if (protectionTiers.isEmpty())
            protectionTiers = new ArrayList<>(List.of(
                    new ProtectionTier(30 * 60_000L, 1), new ProtectionTier(60 * 60_000L, 2),
                    new ProtectionTier(120 * 60_000L, 4), new ProtectionTier(240 * 60_000L, 6)));
    }
}
