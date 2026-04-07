package org.minecraft.atlas.job;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;

public class JobAdvancementManager {

    private static final String NAMESPACE = "atlas";
    private static final String CRITERION = "criterion";

    private static final String[] MILESTONE_NAMES = {"I", "II", "III", "IV"};
    private static final int[] MILESTONE_LEVELS = {25, 50, 75, 100};

    // -------------------------------------------------------------------------
    // Registration (called once on plugin enable)
    // -------------------------------------------------------------------------

    @SuppressWarnings("deprecation")
    public static void register() {
        for (Job job : Job.values()) {
            String prevKey = null;
            for (int i = 0; i < MILESTONE_NAMES.length; i++) {
                NamespacedKey key = milestoneKey(job, i);
                if (Bukkit.getAdvancement(key) == null) {
                    String title = "Milestone " + MILESTONE_NAMES[i] + " " + job.getDisplayName();
                    String desc = "Reached level " + MILESTONE_LEVELS[i] + " as a " + job.getDisplayName();
                    String json = buildJson(title, desc, job, "task", prevKey, i == 0);
                    try {
                        Bukkit.getUnsafe().loadAdvancement(key, json);
                    } catch (Exception e) {
                        Bukkit.getLogger().warning("[Atlas] Failed to register advancement " + key + ": " + e.getMessage());
                    }
                }
                prevKey = NAMESPACE + ":" + key.getKey();
            }

            NamespacedKey masteryKey = masteryKey(job);
            if (Bukkit.getAdvancement(masteryKey) == null) {
                String parentKey = NAMESPACE + ":" + milestoneKey(job, 3).getKey();
                String json = buildJson(
                        "Master " + job.getDisplayName(),
                        "Mastered the " + job.getDisplayName() + " job at level 100",
                        job, "challenge", parentKey, false);
                try {
                    Bukkit.getUnsafe().loadAdvancement(masteryKey, json);
                } catch (Exception e) {
                    Bukkit.getLogger().warning("[Atlas] Failed to register mastery advancement " + masteryKey + ": " + e.getMessage());
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Awarding
    // -------------------------------------------------------------------------

    public static void awardMilestone(Player player, Job job, int milestoneIndex) {
        award(player, milestoneKey(job, milestoneIndex));
    }

    public static void awardMastery(Player player, Job job) {
        award(player, masteryKey(job));
    }

    // -------------------------------------------------------------------------
    // Checks
    // -------------------------------------------------------------------------

    public static boolean hasMilestone(Player player, Job job, int milestoneIndex) {
        return isDone(player, milestoneKey(job, milestoneIndex));
    }

    public static boolean hasMastery(Player player, Job job) {
        return isDone(player, masteryKey(job));
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private static void award(Player player, NamespacedKey key) {
        Advancement adv = Bukkit.getAdvancement(key);
        if (adv == null) return;
        AdvancementProgress progress = player.getAdvancementProgress(adv);
        if (!progress.isDone()) {
            progress.awardCriteria(CRITERION);
        }
    }

    private static boolean isDone(Player player, NamespacedKey key) {
        Advancement adv = Bukkit.getAdvancement(key);
        if (adv == null) return false;
        return player.getAdvancementProgress(adv).isDone();
    }

    private static NamespacedKey milestoneKey(Job job, int index) {
        return new NamespacedKey(NAMESPACE, job.name().toLowerCase() + "_milestone_" + (index + 1));
    }

    private static NamespacedKey masteryKey(Job job) {
        return new NamespacedKey(NAMESPACE, job.name().toLowerCase() + "_mastery");
    }

    private static String buildJson(String title, String desc, Job job, String frame, String parent, boolean isRoot) {
        String iconId = "minecraft:" + job.getIcon().name().toLowerCase();
        StringBuilder sb = new StringBuilder("{");

        if (parent != null) {
            sb.append("\"parent\":\"").append(parent).append("\",");
        }

        sb.append("\"display\":{");
        sb.append("\"icon\":{\"id\":\"").append(iconId).append("\"},");
        if (isRoot) {
            sb.append("\"background\":\"minecraft:textures/block/stone.png\",");
        }
        sb.append("\"title\":{\"text\":\"").append(escape(title)).append("\"},");
        sb.append("\"description\":{\"text\":\"").append(escape(desc)).append("\"},");
        sb.append("\"frame\":\"").append(frame).append("\",");
        sb.append("\"show_toast\":true,");
        sb.append("\"announce_to_chat\":false,");
        sb.append("\"hidden\":false},");
        sb.append("\"criteria\":{\"").append(CRITERION).append("\":{\"trigger\":\"minecraft:impossible\"}}}");

        return sb.toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
