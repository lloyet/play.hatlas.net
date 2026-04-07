package org.minecraft.atlas.job;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.minecraft.atlas.faction.Faction;
import org.minecraft.atlas.faction.FactionManager;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class JobManager {

    // UUID → (Job → data), insertion order preserved so "first job" is deterministic
    private static final Map<UUID, Map<Job, PlayerJobData>> playerJobs = new HashMap<>();
    private static final Map<UUID, JobSettings> playerSettings = new HashMap<>();

    // -------------------------------------------------------------------------
    // Existence checks
    // -------------------------------------------------------------------------

    public static boolean hasJob(UUID uuid) {
        Map<Job, PlayerJobData> jobs = playerJobs.get(uuid);
        return jobs != null && !jobs.isEmpty();
    }

    public static boolean hasJob(UUID uuid, Job job) {
        Map<Job, PlayerJobData> jobs = playerJobs.get(uuid);
        return jobs != null && jobs.containsKey(job);
    }

    // -------------------------------------------------------------------------
    // Data access
    // -------------------------------------------------------------------------

    /**
     * Returns data for the player's primary (first) job, or null if they have none.
     * Admin commands use this for backward-compatible single-job operations.
     */
    public static PlayerJobData getJobData(UUID uuid) {
        Map<Job, PlayerJobData> jobs = playerJobs.get(uuid);
        if (jobs == null || jobs.isEmpty()) return null;
        return jobs.values().iterator().next();
    }

    public static PlayerJobData getJobData(UUID uuid, Job job) {
        Map<Job, PlayerJobData> jobs = playerJobs.get(uuid);
        if (jobs == null) return null;
        return jobs.get(job);
    }

    public static Map<Job, PlayerJobData> getAllJobData(UUID uuid) {
        return playerJobs.getOrDefault(uuid, Collections.emptyMap());
    }

    public static int getJobCount(UUID uuid) {
        Map<Job, PlayerJobData> jobs = playerJobs.get(uuid);
        return jobs == null ? 0 : jobs.size();
    }

    public static int getMasteredJobCount(UUID uuid) {
        Map<Job, PlayerJobData> jobs = playerJobs.get(uuid);
        if (jobs == null) return 0;
        return (int) jobs.values().stream().filter(PlayerJobData::isMastered).count();
    }

    /**
     * Returns true if the player may add another job.
     * Rule: allowedJobs = masteredJobCount + 1.
     * A player with no jobs may always add their first one.
     */
    public static boolean canAddJob(UUID uuid) {
        return getMasteredJobCount(uuid) >= getJobCount(uuid);
    }

    // -------------------------------------------------------------------------
    // Job assignment
    // -------------------------------------------------------------------------

    /**
     * Adds a job to the player (permanent). Returns false if they already have this job
     * or are not yet eligible to add another one.
     */
    public static boolean setJob(UUID uuid, Job job) {
        if (hasJob(uuid, job)) return false;
        if (!canAddJob(uuid)) return false;
        playerJobs.computeIfAbsent(uuid, k -> new LinkedHashMap<>()).put(job, new PlayerJobData(job));
        return true;
    }

    /** Removes all jobs from a player. Returns false if they had none. */
    public static boolean removeJob(UUID uuid) {
        Map<Job, PlayerJobData> jobs = playerJobs.remove(uuid);
        return jobs != null && !jobs.isEmpty();
    }

    /** Removes one specific job from a player. Returns false if they don't have that job. */
    public static boolean removeJob(UUID uuid, Job job) {
        Map<Job, PlayerJobData> jobs = playerJobs.get(uuid);
        if (jobs == null || !jobs.containsKey(job)) return false;
        jobs.remove(job);
        if (jobs.isEmpty()) playerJobs.remove(uuid);
        return true;
    }

    /** Sets (or replaces) a specific job for a player — admin override, bypasses eligibility. */
    public static void forceSetJob(UUID uuid, Job job) {
        playerJobs.computeIfAbsent(uuid, k -> new LinkedHashMap<>()).put(job, new PlayerJobData(job));
    }

    // -------------------------------------------------------------------------
    // Settings
    // -------------------------------------------------------------------------

    public static JobSettings getSettings(UUID uuid) {
        return playerSettings.computeIfAbsent(uuid, k -> new JobSettings());
    }

    // -------------------------------------------------------------------------
    // Milestone achievements (real Minecraft advancements, faction-scoped)
    // -------------------------------------------------------------------------

    private static final int[] MILESTONE_LEVELS = {25, 50, 75, 100};
    private static final String[] MILESTONE_ROMAN = {"I", "II", "III", "IV"};

    /** Grants all earned milestone and mastery advancements not yet obtained, then notifies faction. */
    public static void checkMilestoneRewards(Player player, Job job) {
        PlayerJobData data = getJobData(player.getUniqueId(), job);
        if (data == null) return;
        int level = data.getLevel();
        String factionName = FactionManager.getPlayerFaction(player.getUniqueId());

        for (int i = 0; i < MILESTONE_LEVELS.length; i++) {
            if (level >= MILESTONE_LEVELS[i] && !JobAdvancementManager.hasMilestone(player, job, i)) {
                JobAdvancementManager.awardMilestone(player, job, i);
                if (factionName != null) {
                    broadcastAchievement(player, job, factionName,
                            "Milestone " + MILESTONE_ROMAN[i] + " " + job.getDisplayName());
                }
            }
        }

        if (level >= JobRegistry.getMaxLevel() && !JobAdvancementManager.hasMastery(player, job)) {
            JobAdvancementManager.awardMastery(player, job);
            if (factionName != null) {
                broadcastAchievement(player, job, factionName, "Master " + job.getDisplayName());
            }
        }
    }

    private static void broadcastAchievement(Player player, Job job, String factionName, String achieveName) {
        Faction faction = FactionManager.getFaction(factionName);
        if (faction == null) return;
        Component message = Component.text("[", NamedTextColor.DARK_GRAY)
                .append(Component.text(factionName, faction.getColor()))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                .append(Component.text(player.getName(), NamedTextColor.WHITE))
                .append(Component.text(" earned ", NamedTextColor.GOLD))
                .append(Component.text("[" + achieveName + "]", job.getColor())
                        .decorate(TextDecoration.BOLD));
        // Notify all online faction members (null = no exclusions, player included)
        FactionManager.broadcastToFaction(factionName, message, null);
    }

    // -------------------------------------------------------------------------
    // XP (used by listeners)
    // -------------------------------------------------------------------------

    /**
     * Awards XP to a player for a specific job and sends appropriate notifications.
     * Notifications for post-mastery levels are gated by the player's extra_levels setting.
     *
     * @return true if the player leveled up at least once.
     */
    public static boolean addXp(UUID uuid, Job job, double amount, Player player) {
        Map<Job, PlayerJobData> jobs = playerJobs.get(uuid);
        if (jobs == null) return false;
        PlayerJobData data = jobs.get(job);
        if (data == null) return false;

        int oldLevel = data.getLevel();
        boolean wasMastered = data.isMastered();
        boolean leveledUp = data.addXp(amount);
        boolean nowMastered = data.isMastered();

        boolean extraLevels = getSettings(uuid).isExtraLevels();

        if (leveledUp) {
            try {
                checkMilestoneRewards(player, job);
            } catch (Exception e) {
                Bukkit.getLogger().warning("[Atlas] checkMilestoneRewards failed for " + player.getName() + ": " + e.getMessage());
            }
            JobRewardManager.applyMinorRewards(player, job, oldLevel, data.getLevel());

            if (!wasMastered && nowMastered) {
                // First-time mastery — always notify regardless of extra_levels
                player.sendMessage(Component.text("Your ", NamedTextColor.GOLD)
                        .append(Component.text(data.getJob().getDisplayName(), data.getJob().getColor()))
                        .append(Component.text(" job has been ", NamedTextColor.GOLD))
                        .append(Component.text("MASTERED", NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
                        .append(Component.text("! You may now select an additional job.", NamedTextColor.GOLD)));
            } else if (!nowMastered || extraLevels) {
                // Regular level-up: only show if below mastery, or extra_levels is enabled
                player.sendMessage(Component.text("Your ", NamedTextColor.GOLD)
                        .append(Component.text(data.getJob().getDisplayName(), data.getJob().getColor()))
                        .append(Component.text(" job advanced to level " + data.getLevel() + "!", NamedTextColor.GOLD)));
            }
        }

        // Action bar XP indicator — suppressed for mastered jobs when extra_levels is off
        if (!nowMastered || extraLevels) {
            String xpText = String.format("%,d/%,d XP", (int) data.getXp(), data.getXpRequired());
            player.sendActionBar(
                    Component.text(xpText, data.getJob().getColor()).decorate(TextDecoration.BOLD)
            );
        }

        return leveledUp;
    }

    // -------------------------------------------------------------------------
    // Admin — level/XP (job-specific)
    // -------------------------------------------------------------------------

    public static boolean adminSetLevel(UUID uuid, Job job, int level) {
        PlayerJobData data = getJobData(uuid, job);
        if (data == null) return false;
        data.setLevel(level);
        return true;
    }

    public static boolean adminAddLevel(UUID uuid, Job job, int amount) {
        PlayerJobData data = getJobData(uuid, job);
        if (data == null) return false;
        data.setLevel(data.getLevel() + amount);
        return true;
    }

    public static boolean adminRemoveLevel(UUID uuid, Job job, int amount) {
        PlayerJobData data = getJobData(uuid, job);
        if (data == null) return false;
        data.setLevel(data.getLevel() - amount);
        return true;
    }

    public static boolean adminSetXp(UUID uuid, Job job, double xp) {
        PlayerJobData data = getJobData(uuid, job);
        if (data == null) return false;
        data.setXp(xp);
        return true;
    }

    public static boolean adminAddXp(UUID uuid, Job job, double amount, Player player) {
        return addXp(uuid, job, amount, player);
    }

    public static boolean adminRemoveXp(UUID uuid, Job job, double amount) {
        PlayerJobData data = getJobData(uuid, job);
        if (data == null) return false;
        data.setXp(data.getXp() - amount);
        return true;
    }

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    public static void saveJobs(FileConfiguration config) {
        config.set("jobs", null);
        config.set("job-settings", null);

        ConfigurationSection jobsSection = config.createSection("jobs");
        for (Map.Entry<UUID, Map<Job, PlayerJobData>> entry : playerJobs.entrySet()) {
            ConfigurationSection playerSection = jobsSection.createSection(entry.getKey().toString());
            for (PlayerJobData data : entry.getValue().values()) {
                ConfigurationSection s = playerSection.createSection(data.getJob().name());
                s.set("level", data.getLevel());
                s.set("xp", data.getXp());
            }
        }

        ConfigurationSection settingsSection = config.createSection("job-settings");
        for (Map.Entry<UUID, JobSettings> entry : playerSettings.entrySet()) {
            ConfigurationSection s = settingsSection.createSection(entry.getKey().toString());
            s.set("extra-levels", entry.getValue().isExtraLevels());
        }
    }

    public static void loadJobs(FileConfiguration config) {
        playerJobs.clear();
        playerSettings.clear();

        ConfigurationSection section = config.getConfigurationSection("jobs");
        if (section != null) {
            for (String uuidStr : section.getKeys(false)) {
                UUID uuid;
                try { uuid = UUID.fromString(uuidStr); } catch (IllegalArgumentException e) { continue; }

                ConfigurationSection playerSection = section.getConfigurationSection(uuidStr);
                if (playerSection == null) continue;

                Map<Job, PlayerJobData> jobs = new LinkedHashMap<>();

                if (playerSection.contains("job")) {
                    // Legacy single-job format: { job: MINER, level: 45, xp: 1234.5 }
                    String jobName = playerSection.getString("job");
                    if (jobName != null) {
                        try {
                            Job job = Job.valueOf(jobName);
                            int level = playerSection.getInt("level", 1);
                            double xp = playerSection.getDouble("xp", 0.0);
                            jobs.put(job, new PlayerJobData(job, level, xp));
                        } catch (IllegalArgumentException ignored) {}
                    }
                } else {
                    // New multi-job format: keys are Job enum names
                    for (String jobName : playerSection.getKeys(false)) {
                        try {
                            Job job = Job.valueOf(jobName);
                            ConfigurationSection s = playerSection.getConfigurationSection(jobName);
                            if (s == null) continue;
                            int level = s.getInt("level", 1);
                            double xp = s.getDouble("xp", 0.0);
                            jobs.put(job, new PlayerJobData(job, level, xp));
                        } catch (IllegalArgumentException ignored) {}
                    }
                }

                if (!jobs.isEmpty()) playerJobs.put(uuid, jobs);
            }
        }

        ConfigurationSection settingsSection = config.getConfigurationSection("job-settings");
        if (settingsSection != null) {
            for (String uuidStr : settingsSection.getKeys(false)) {
                UUID uuid;
                try { uuid = UUID.fromString(uuidStr); } catch (IllegalArgumentException e) { continue; }
                ConfigurationSection s = settingsSection.getConfigurationSection(uuidStr);
                if (s == null) continue;
                JobSettings settings = new JobSettings();
                settings.setExtraLevels(s.getBoolean("extra-levels", false));
                playerSettings.put(uuid, settings);
            }
        }
    }
}
