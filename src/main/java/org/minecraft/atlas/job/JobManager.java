package org.minecraft.atlas.job;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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

    /** Assigns a job to a player. Jobs are permanent — returns false if one is already set. */
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

    // -------------------------------------------------------------------------
    // XP (used by listeners)
    // -------------------------------------------------------------------------

    /**
     * Awards XP to a player and sends a level-up message if applicable.
     * @return true if the player leveled up.
     */
    public static boolean addXp(UUID playerUUID, double amount, Player player) {
        PlayerJobData data = playerJobs.get(playerUUID);
        if (data == null) return false;

        boolean leveledUp = data.addXp(amount);

        if (leveledUp) {
            boolean maxed = data.getLevel() >= JobRegistry.getMaxLevel();
            Component msg = Component.text("Your ", NamedTextColor.GOLD)
                    .append(Component.text(data.getJob().getDisplayName(), data.getJob().getColor()))
                    .append(maxed
                            ? Component.text(" job reached max level (" + data.getLevel() + ")!", NamedTextColor.GOLD)
                            : Component.text(" job advanced to level " + data.getLevel() + "!", NamedTextColor.GOLD));
            player.sendMessage(msg);
        }

        // Action bar XP indicator (hidden at max level)
        if (data.getLevel() < JobRegistry.getMaxLevel()) {
            String xpText = String.format("%,d/%,d XP", (int) data.getXp(), data.getXpRequired());
            player.sendActionBar(
                    Component.text(xpText, data.getJob().getColor()).decorate(TextDecoration.BOLD)
            );
        }

        return leveledUp;
    }

    // -------------------------------------------------------------------------
    // Admin — level
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Admin — XP
    // -------------------------------------------------------------------------

    public static boolean adminSetXp(UUID playerUUID, double xp) {
        PlayerJobData data = playerJobs.get(playerUUID);
        if (data == null) return false;
        data.setXp(xp);
        return true;
    }

    public static boolean adminAddXp(UUID playerUUID, double amount, Player player) {
        return addXp(playerUUID, amount, player);
    }

    public static boolean adminRemoveXp(UUID playerUUID, double amount) {
        PlayerJobData data = playerJobs.get(playerUUID);
        if (data == null) return false;
        data.setXp(data.getXp() - amount);
        return true;
    }

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    public static void saveJobs(FileConfiguration config) {
        config.set("jobs", null);
        ConfigurationSection section = config.createSection("jobs");

        for (Map.Entry<UUID, PlayerJobData> entry : playerJobs.entrySet()) {
            ConfigurationSection s = section.createSection(entry.getKey().toString());
            PlayerJobData data = entry.getValue();
            s.set("job", data.getJob().name());
            s.set("level", data.getLevel());
            s.set("xp", data.getXp());
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
                double xp = s.getDouble("xp", 0.0);
                playerJobs.put(UUID.fromString(uuidStr), new PlayerJobData(job, level, xp));
            } catch (IllegalArgumentException ignored) {}
        }
    }
}
