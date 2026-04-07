package org.minecraft.atlas.job;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads job XP source definitions from jobs.yml and provides lookups at runtime.
 * Structure: Job → category → sourceKey → JobSource
 */
public class JobRegistry {

    private static final Map<Job, Map<String, Map<String, JobSource>>> registry = new EnumMap<>(Job.class);

    private static double xpBase = 100.0;
    private static double xpMultiplier = 1.09;
    private static int maxLevel = 100;

    public static void load(Plugin plugin) {
        plugin.saveResource("jobs.yml", false);
        File file = new File(plugin.getDataFolder(), "jobs.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        xpBase = config.getDouble("xp-curve.base", 100.0);
        xpMultiplier = config.getDouble("xp-curve.multiplier", 1.09);
        maxLevel = config.getInt("xp-curve.max-level", 100);

        registry.clear();
        ConfigurationSection jobsSection = config.getConfigurationSection("jobs");
        if (jobsSection == null) return;

        for (String jobName : jobsSection.getKeys(false)) {
            Job job;
            try {
                job = Job.valueOf(jobName);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[JobRegistry] Unknown job in jobs.yml: " + jobName);
                continue;
            }

            Map<String, Map<String, JobSource>> categories = new LinkedHashMap<>();
            ConfigurationSection jobSection = jobsSection.getConfigurationSection(jobName);
            if (jobSection == null) continue;

            for (String category : jobSection.getKeys(false)) {
                Map<String, JobSource> sources = new LinkedHashMap<>();
                ConfigurationSection catSection = jobSection.getConfigurationSection(category);
                if (catSection == null) continue;

                for (String sourceKey : catSection.getKeys(false)) {
                    ConfigurationSection src = catSection.getConfigurationSection(sourceKey);
                    if (src == null) continue;
                    sources.put(sourceKey, new JobSource(
                            sourceKey,
                            src.getString("display", sourceKey),
                            src.getInt("unlock", 1),
                            src.getDouble("xp", 1.0),
                            src.getInt("cutoff", maxLevel)
                    ));
                }
                categories.put(category, Collections.unmodifiableMap(sources));
            }
            registry.put(job, Collections.unmodifiableMap(categories));
        }
    }

    public static double getXpBase() { return xpBase; }
    public static double getXpMultiplier() { return xpMultiplier; }
    public static int getMaxLevel() { return maxLevel; }

    /** Returns the source for the given job/category/key, or null if not found. */
    public static JobSource getSource(Job job, String category, String key) {
        Map<String, Map<String, JobSource>> cats = registry.get(job);
        if (cats == null) return null;
        Map<String, JobSource> sources = cats.get(category);
        if (sources == null) return null;
        return sources.get(key);
    }

    /** Returns all categories and their sources for a job (insertion order preserved). */
    public static Map<String, Map<String, JobSource>> getCategories(Job job) {
        return registry.getOrDefault(job, Map.of());
    }
}
