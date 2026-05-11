package org.minecraft.atlas.job;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.minecraft.atlas.Atlas;
import org.minecraft.atlas.quest.ActiveQuest;
import org.minecraft.atlas.quest.GeneratedTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class JobManager {

    // ── PDC key (stored on Villager entities) ─────────────────────────────────

    private static NamespacedKey KEY_NPC_JOB;

    public static NamespacedKey getKeyNpcJob() {
        if (KEY_NPC_JOB == null)
            KEY_NPC_JOB = new NamespacedKey(Atlas.instance, "job_npc_type");
        return KEY_NPC_JOB;
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private static final Map<UUID, PlayerJobData> playerJobs   = new HashMap<>();
    private static final Map<UUID, Long>          jobCooldowns = new HashMap<>();

    private static final long JOB_RESET_COOLDOWN_MS = 24L * 3_600_000L;

    // ── Job reset cooldown ────────────────────────────────────────────────────

    public static void startJobResetCooldown(UUID uuid) {
        jobCooldowns.put(uuid, System.currentTimeMillis() + JOB_RESET_COOLDOWN_MS);
    }

    public static boolean isOnJobCooldown(UUID uuid) {
        Long until = jobCooldowns.get(uuid);
        if (until == null) return false;
        if (System.currentTimeMillis() >= until) { jobCooldowns.remove(uuid); return false; }
        return true;
    }

    public static long getJobCooldownRemaining(UUID uuid) {
        Long until = jobCooldowns.get(uuid);
        if (until == null) return 0L;
        return Math.max(0L, until - System.currentTimeMillis());
    }

    // ── Player job queries ────────────────────────────────────────────────────

    public static boolean hasJob(UUID playerUUID) {
        return playerJobs.containsKey(playerUUID);
    }

    public static PlayerJobData getJobData(UUID playerUUID) {
        return playerJobs.get(playerUUID);
    }

    public static boolean setJob(UUID playerUUID, Job job) {
        if (playerJobs.containsKey(playerUUID)) return false;
        playerJobs.put(playerUUID, new PlayerJobData(job));
        return true;
    }

    public static boolean removeJob(UUID playerUUID) {
        return playerJobs.remove(playerUUID) != null;
    }

    public static void forceSetJob(UUID playerUUID, Job job) {
        playerJobs.put(playerUUID, new PlayerJobData(job));
    }

    // ── Collection access ─────────────────────────────────────────────────────

    public static Collection<PlayerJobData> getAllJobData() { return playerJobs.values(); }

    // ── Admin level / XP helpers ──────────────────────────────────────────────

    public static boolean adminSetLevel(UUID uuid, int level) {
        PlayerJobData d = playerJobs.get(uuid); if (d == null) return false;
        d.setLevel(level); return true;
    }
    public static boolean adminAddLevel(UUID uuid, int amount) {
        PlayerJobData d = playerJobs.get(uuid); if (d == null) return false;
        d.setLevel(d.getLevel() + amount); return true;
    }
    public static boolean adminRemoveLevel(UUID uuid, int amount) {
        PlayerJobData d = playerJobs.get(uuid); if (d == null) return false;
        d.setLevel(d.getLevel() - amount); return true;
    }
    public static boolean adminSetXp(UUID uuid, int xp) {
        PlayerJobData d = playerJobs.get(uuid); if (d == null) return false;
        d.setProgress(xp); return true;
    }
    public static boolean adminAddXp(UUID uuid, int amount, Player player) {
        return addProgress(uuid, amount, player);
    }
    public static boolean adminRemoveXp(UUID uuid, int amount) {
        PlayerJobData d = playerJobs.get(uuid); if (d == null) return false;
        d.setProgress(d.getProgress() - amount); return true;
    }

    public static boolean addProgress(UUID playerUUID, int amount, Player player) {
        PlayerJobData data = playerJobs.get(playerUUID);
        if (data == null) return false;
        if (data.addProgress(amount)) {
            player.sendMessage(
                Component.text("You leveled up your ", NamedTextColor.GOLD)
                    .append(Component.text(data.getJob().getDisplayName(), data.getJob().getColor()))
                    .append(Component.text(" job to level " + data.getLevel() + "!", NamedTextColor.GOLD)));
            return true;
        }
        return false;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    public static void saveJobData(FileConfiguration config) {
        config.set("job_cooldowns", null);
        ConfigurationSection cdSection = config.createSection("job_cooldowns");
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> e : jobCooldowns.entrySet()) {
            if (e.getValue() > now) cdSection.set(e.getKey().toString(), e.getValue());
        }

        config.set("jobs", null);
        ConfigurationSection section = config.createSection("jobs");

        for (Map.Entry<UUID, PlayerJobData> entry : playerJobs.entrySet()) {
            ConfigurationSection s = section.createSection(entry.getKey().toString());
            PlayerJobData data = entry.getValue();
            s.set("job",      data.getJob().name());
            s.set("level",    data.getLevel());
            s.set("progress", data.getProgress());

            // Active quests
            List<Map<String, Object>> questList = new ArrayList<>();
            for (ActiveQuest aq : data.activeQuests) {
                Map<String, Object> qMap = new LinkedHashMap<>();
                qMap.put("quest_id",      aq.getQuestId());
                qMap.put("tasks",         aq.getTasks().stream().map(GeneratedTask::serialize).toList());
                qMap.put("expires_at",    aq.getExpiresAt());
                qMap.put("task_progress", new HashMap<>(aq.getTaskProgressMap()));
                questList.add(qMap);
            }
            s.set("active_quests", questList);

            s.set("last_daily_reset_ms", data.lastDailyResetMs);

            // Daily offered quests
            List<Map<String, Object>> offeredList = new ArrayList<>();
            for (Map.Entry<String, List<GeneratedTask>> e : data.dailyOfferedQuests.entrySet()) {
                Map<String, Object> oMap = new LinkedHashMap<>();
                oMap.put("quest_id", e.getKey());
                oMap.put("tasks", e.getValue().stream().map(GeneratedTask::serialize).toList());
                offeredList.add(oMap);
            }
            s.set("daily_offered_quests", offeredList);
            s.set("daily_selected", new ArrayList<>(data.dailySelectedIds));
        }
    }

    public static void loadJobData(FileConfiguration config) {
        playerJobs.clear();
        jobCooldowns.clear();

        ConfigurationSection cdSection = config.getConfigurationSection("job_cooldowns");
        if (cdSection != null) {
            long now = System.currentTimeMillis();
            for (String uuidStr : cdSection.getKeys(false)) {
                try {
                    UUID uuid  = UUID.fromString(uuidStr);
                    long until = cdSection.getLong(uuidStr);
                    if (until > now) jobCooldowns.put(uuid, until);
                } catch (IllegalArgumentException ignored) {}
            }
        }

        // ── Load player data ───────────────────────────────────────────────────
        ConfigurationSection playerSection = config.getConfigurationSection("jobs");
        if (playerSection == null) return;

        for (String uuidStr : playerSection.getKeys(false)) {
            ConfigurationSection s = playerSection.getConfigurationSection(uuidStr);
            if (s == null) continue;
            String jobName = s.getString("job");
            if (jobName == null) continue;
            Job job;
            try { job = Job.valueOf(jobName); }
            catch (IllegalArgumentException e) { continue; }

            int level    = s.getInt("level", 1);
            int progress = s.getInt("progress", 0);
            UUID uuid;
            try { uuid = UUID.fromString(uuidStr); }
            catch (IllegalArgumentException e) { continue; }

            PlayerJobData data = new PlayerJobData(job, level, progress);

            // Active quests
            List<?> aqList = s.getList("active_quests");
            if (aqList != null) {
                for (Object obj : aqList) {
                    if (!(obj instanceof Map<?, ?> qMap)) continue;
                    String questId    = mapStr(qMap, "quest_id");
                    Object expiresObj = qMap.get("expires_at");
                    if (questId == null || expiresObj == null) continue;
                    long expiresAt = ((Number) expiresObj).longValue();
                    if (expiresAt <= System.currentTimeMillis()) continue;

                    List<GeneratedTask> tasks = deserializeTasks(qMap.get("tasks"));
                    if (tasks.isEmpty()) continue;

                    Map<String, Integer> taskProgress = new HashMap<>();
                    Object progressObj = qMap.get("task_progress");
                    if (progressObj instanceof Map<?, ?> pMap) {
                        for (Map.Entry<?, ?> e : pMap.entrySet()) {
                            if (e.getKey() != null && e.getValue() instanceof Number n) {
                                taskProgress.put(e.getKey().toString(), n.intValue());
                            }
                        }
                    }
                    data.activeQuests.add(new ActiveQuest(questId, tasks, expiresAt, taskProgress));
                }
            }

            data.lastDailyResetMs = s.getLong("last_daily_reset_ms", 0);

            // Daily offered quests
            List<?> offeredList = s.getList("daily_offered_quests");
            if (offeredList != null) {
                for (Object obj : offeredList) {
                    if (!(obj instanceof Map<?, ?> oMap)) continue;
                    String questId = mapStr(oMap, "quest_id");
                    if (questId == null) continue;
                    List<GeneratedTask> tasks = deserializeTasks(oMap.get("tasks"));
                    if (!tasks.isEmpty()) data.dailyOfferedQuests.put(questId, tasks);
                }
            }

            data.dailySelectedIds.addAll(s.getStringList("daily_selected"));
            playerJobs.put(uuid, data);
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private static List<GeneratedTask> deserializeTasks(Object raw) {
        List<GeneratedTask> result = new ArrayList<>();
        if (!(raw instanceof List<?> list)) return result;
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                GeneratedTask gt = GeneratedTask.deserialize(m);
                if (gt != null) result.add(gt);
            }
        }
        return result;
    }

    private static String mapStr(Map<?, ?> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private static int mapInt(Map<?, ?> map, String key, int def) {
        Object v = map.get(key);
        return v instanceof Number n ? n.intValue() : def;
    }
}
