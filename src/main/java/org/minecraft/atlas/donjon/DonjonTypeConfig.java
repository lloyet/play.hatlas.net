package org.minecraft.atlas.donjon;

import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class DonjonTypeConfig {

    private final String displayName;
    private final String structureFilename;
    private final List<String> biomes;
    private final List<String> nameAdjectives;
    private final List<String> nameNouns;
    private final int protectionRadiusChunks;
    private final List<String> mobTypes;
    private final List<String> bossTypes;
    private final int minWaves;
    private final int maxWaves;
    private final int minMobsPerWave;
    private final int maxMobsPerWave;
    private final double baseHpMultiplier;
    private final double maxHpMultiplier;
    private final double baseAttackMultiplier;
    private final double maxAttackMultiplier;
    private final double bossHpMultiplier;
    private final double bossAttackMultiplier;
    private final double bossSpeedMultiplier;
    private final int bossCountMin;
    private final int bossCountMax;
    private final long minExpReward;
    private final long maxExpReward;
    /** Per-rarity boss drop tables. A rarity with no entry uses an empty loot table. */
    private final Map<DonjonRarity, BossDropConfig> bossDrops;

    public DonjonTypeConfig(String displayName, String structureFilename, List<String> biomes,
                            List<String> nameAdjectives, List<String> nameNouns,
                            int protectionRadiusChunks, List<String> mobTypes, List<String> bossTypes,
                            int minWaves, int maxWaves, int minMobsPerWave, int maxMobsPerWave,
                            double baseHpMultiplier, double maxHpMultiplier,
                            double baseAttackMultiplier, double maxAttackMultiplier,
                            double bossHpMultiplier, double bossAttackMultiplier, double bossSpeedMultiplier,
                            int bossCountMin, int bossCountMax,
                            long minExpReward, long maxExpReward,
                            Map<DonjonRarity, BossDropConfig> bossDrops) {
        this.displayName = displayName;
        this.structureFilename = structureFilename;
        this.biomes = biomes;
        this.nameAdjectives = nameAdjectives;
        this.nameNouns = nameNouns;
        this.protectionRadiusChunks = protectionRadiusChunks;
        this.mobTypes = mobTypes;
        this.bossTypes = bossTypes;
        this.minWaves = minWaves;
        this.maxWaves = maxWaves;
        this.minMobsPerWave = minMobsPerWave;
        this.maxMobsPerWave = maxMobsPerWave;
        this.baseHpMultiplier = baseHpMultiplier;
        this.maxHpMultiplier = maxHpMultiplier;
        this.baseAttackMultiplier = baseAttackMultiplier;
        this.maxAttackMultiplier = maxAttackMultiplier;
        this.bossHpMultiplier = bossHpMultiplier;
        this.bossAttackMultiplier = bossAttackMultiplier;
        this.bossSpeedMultiplier = bossSpeedMultiplier;
        this.bossCountMin = bossCountMin;
        this.bossCountMax = bossCountMax;
        this.minExpReward = minExpReward;
        this.maxExpReward = maxExpReward;
        this.bossDrops = bossDrops;
    }

    public String getDisplayName() { return displayName; }
    public String getStructureFilename() { return structureFilename; }
    public List<String> getBiomes() { return biomes; }
    public List<String> getNameAdjectives() { return nameAdjectives; }
    public List<String> getNameNouns() { return nameNouns; }
    public int getProtectionRadiusChunks() { return protectionRadiusChunks; }
    public List<String> getMobTypes() { return mobTypes; }
    public List<String> getBossTypes() { return bossTypes; }
    public int getMinWaves() { return minWaves; }
    public int getMaxWaves() { return maxWaves; }
    public int getMinMobsPerWave() { return minMobsPerWave; }
    public int getMaxMobsPerWave() { return maxMobsPerWave; }
    public double getBaseHpMultiplier() { return baseHpMultiplier; }
    public double getMaxHpMultiplier() { return maxHpMultiplier; }
    public double getBaseAttackMultiplier() { return baseAttackMultiplier; }
    public double getMaxAttackMultiplier() { return maxAttackMultiplier; }
    public double getBossHpMultiplier() { return bossHpMultiplier; }
    public double getBossAttackMultiplier() { return bossAttackMultiplier; }
    public double getBossSpeedMultiplier() { return bossSpeedMultiplier; }
    public int getBossCountMin() { return bossCountMin; }
    public int getBossCountMax() { return bossCountMax; }
    public long getMinExpReward() { return minExpReward; }
    public long getMaxExpReward() { return maxExpReward; }
    public Map<DonjonRarity, BossDropConfig> getBossDrops() { return bossDrops; }

    public static DonjonTypeConfig load(ConfigurationSection s) {
        String displayName = s.getString("display_name", "Unknown");
        String structureFilename = s.getString("structure_filename", "");
        List<String> biomes = s.getStringList("biomes");
        List<String> adjectives = s.getStringList("name_adjectives");
        List<String> nouns = s.getStringList("name_nouns");
        int radius = s.getInt("protection_radius_chunks", 3);
        List<String> mobTypes = s.getStringList("mob_types");
        List<String> bossTypes = s.getStringList("boss_types");
        int minWaves = s.getInt("min_waves", 3);
        int maxWaves = s.getInt("max_waves", 10);
        int minMobs = s.getInt("min_mobs_per_wave", 5);
        int maxMobs = s.getInt("max_mobs_per_wave", 50);
        double baseHp = s.getDouble("base_hp_multiplier", 1.0);
        double maxHp = s.getDouble("max_hp_multiplier", 5.0);
        double baseAtk = s.getDouble("base_attack_multiplier", 1.0);
        double maxAtk = s.getDouble("max_attack_multiplier", 3.0);
        double bossHp = s.getDouble("boss_hp_multiplier", 10.0);
        double bossAtk = s.getDouble("boss_attack_multiplier", 2.0);
        double bossSpd = s.getDouble("boss_speed_multiplier", 0.5);
        int bossMin = s.getInt("boss_count_min", 1);
        int bossMax = s.getInt("boss_count_max", 3);
        long minExp = s.getLong("min_exp_reward", 500L);
        long maxExp = s.getLong("max_exp_reward", 10000L);

        // Boss drop tables — one section per rarity name (lowercase)
        Map<DonjonRarity, BossDropConfig> bossDrops = new EnumMap<>(DonjonRarity.class);
        ConfigurationSection dropsSec = s.getConfigurationSection("boss_drops");
        if (dropsSec != null) {
            for (DonjonRarity rarity : DonjonRarity.values()) {
                ConfigurationSection raritySec = dropsSec.getConfigurationSection(rarity.name().toLowerCase());
                if (raritySec != null) {
                    bossDrops.put(rarity, BossDropConfig.load(raritySec));
                }
            }
        }

        return new DonjonTypeConfig(displayName, structureFilename, biomes, adjectives, nouns, radius,
                mobTypes, bossTypes, minWaves, maxWaves, minMobs, maxMobs,
                baseHp, maxHp, baseAtk, maxAtk, bossHp, bossAtk, bossSpd,
                bossMin, bossMax, minExp, maxExp, bossDrops);
    }
}
