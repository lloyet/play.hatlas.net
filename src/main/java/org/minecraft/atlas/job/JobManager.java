package org.minecraft.atlas.job;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class JobManager {

    private static final Map<UUID, PlayerJobData> playerJobs = new HashMap<>();

    public static boolean hasJob(UUID playerUUID) {
        return playerJobs.containsKey(playerUUID);
    }

    public static PlayerJobData getJobData(UUID playerUUID) {
        return playerJobs.get(playerUUID);
    }

    /**
     * Assigns a job to a player. Jobs are permanent — returns false if one is already set.
     */
    public static boolean setJob(UUID playerUUID, Job job) {
        if (playerJobs.containsKey(playerUUID)) return false;
        playerJobs.put(playerUUID, new PlayerJobData(job));
        return true;
    }

    /** Removes a player's job entirely. Returns false if they had no job. */
    public static boolean removeJob(UUID playerUUID) {
        return playerJobs.remove(playerUUID) != null;
    }

    /** Sets a player's job, replacing any existing one (admin override). */
    public static void forceSetJob(UUID playerUUID, Job job) {
        playerJobs.put(playerUUID, new PlayerJobData(job));
    }

    public static boolean adminSetLevel(UUID playerUUID, int level) {
        PlayerJobData data = playerJobs.get(playerUUID);
        if (data == null) return false;
        data.setLevel(level);
        return true;
    }

    public static boolean adminAddLevel(UUID playerUUID, int amount) {
        PlayerJobData data = playerJobs.get(playerUUID);
        if (data == null) return false;
        data.setLevel(data.getLevel() + amount);
        return true;
    }

    public static boolean adminRemoveLevel(UUID playerUUID, int amount) {
        PlayerJobData data = playerJobs.get(playerUUID);
        if (data == null) return false;
        data.setLevel(data.getLevel() - amount);
        return true;
    }

    public static boolean adminSetXp(UUID playerUUID, int xp) {
        PlayerJobData data = playerJobs.get(playerUUID);
        if (data == null) return false;
        data.setProgress(xp);
        return true;
    }

    public static boolean adminAddXp(UUID playerUUID, int amount, Player player) {
        return addProgress(playerUUID, amount, player);
    }

    public static boolean adminRemoveXp(UUID playerUUID, int amount) {
        PlayerJobData data = playerJobs.get(playerUUID);
        if (data == null) return false;
        data.setProgress(data.getProgress() - amount);
        return true;
    }

    /**
     * Adds progression progress for a player and sends a level-up notification if applicable.
     * @return true if the player leveled up.
     */
    public static boolean addProgress(UUID playerUUID, int amount, Player player) {
        PlayerJobData data = playerJobs.get(playerUUID);
        if (data == null) return false;

        if (data.addProgress(amount)) {
            player.sendMessage(
                Component.text("You leveled up your ", NamedTextColor.GOLD)
                    .append(Component.text(data.getJob().getDisplayName(), data.getJob().getColor()))
                    .append(Component.text(" job to level " + data.getLevel() + "!", NamedTextColor.GOLD))
            );
            return true;
        }
        return false;
    }

    public static void saveJobs(FileConfiguration config) {
        config.set("jobs", null);
        ConfigurationSection section = config.createSection("jobs");

        for (Map.Entry<UUID, PlayerJobData> entry : playerJobs.entrySet()) {
            ConfigurationSection s = section.createSection(entry.getKey().toString());
            PlayerJobData data = entry.getValue();
            s.set("job", data.getJob().name());
            s.set("level", data.getLevel());
            s.set("progress", data.getProgress());
        }
    }

    public static void loadJobs(FileConfiguration config) {
        playerJobs.clear();
        ConfigurationSection section = config.getConfigurationSection("jobs");
        if (section == null) return;

        for (String uuidStr : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(uuidStr);
            if (s == null) continue;

            String jobName = s.getString("job");
            if (jobName == null) continue;

            try {
                Job job = Job.valueOf(jobName);
                int level = s.getInt("level", 1);
                int progress = s.getInt("progress", 0);
                playerJobs.put(UUID.fromString(uuidStr), new PlayerJobData(job, level, progress));
            } catch (IllegalArgumentException ignored) {}
        }
    }
}
