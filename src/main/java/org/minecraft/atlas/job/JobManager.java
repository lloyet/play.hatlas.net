package org.minecraft.atlas.job;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.minecraft.atlas.Atlas;
import org.minecraft.atlas.faction.Faction;
import org.minecraft.atlas.faction.FactionManager;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class JobManager {

    // ── PDC key (stored on Villager entities) ─────────────────────────────────

    private static NamespacedKey KEY_NPC_JOB;

    public static NamespacedKey getKeyNpcJob() {
        if (KEY_NPC_JOB == null)
            KEY_NPC_JOB = new NamespacedKey(Atlas.instance, "job_npc_type");
        return KEY_NPC_JOB;
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private static final Map<UUID, PlayerJobData>     playerJobs    = new HashMap<>();
    private static final Map<Job, List<TaskTemplate>> taskTemplates = new EnumMap<>(Job.class);
    private static final Map<UUID, Long>              jobCooldowns  = new HashMap<>();
    private static int baseExpReward = 10;

    private static final long JOB_RESET_COOLDOWN_MS = 24L * 3_600_000L;

    // ── Task template access ──────────────────────────────────────────────────

    public static List<TaskTemplate> getTasksForJob(Job job) {
        return taskTemplates.getOrDefault(job, List.of());
    }

    public static TaskTemplate getTaskTemplate(String taskId) {
        for (List<TaskTemplate> tasks : taskTemplates.values()) {
            for (TaskTemplate t : tasks) {
                if (t.getId().equals(taskId)) return t;
            }
        }
        return null;
    }

    public static int getBaseExpReward() { return baseExpReward; }

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

    // ── Active quest management ───────────────────────────────────────────────

    public static List<ActiveQuest> getActiveQuests(UUID uuid) {
        PlayerJobData data = playerJobs.get(uuid);
        return data == null ? List.of() : List.copyOf(data.activeQuests);
    }

    /** Reconstructs a QuestTemplate directly from the tasks stored in an ActiveQuest. */
    public static QuestTemplate resolveQuest(ActiveQuest aq) {
        List<GeneratedTask> tasks = aq.getTasks();
        return tasks.isEmpty() ? null : new QuestTemplate(aq.getQuestId(), tasks);
    }

    private static boolean addActiveQuest(UUID uuid, String questId,
                                          List<GeneratedTask> tasks, long timeLimitMs) {
        PlayerJobData data = playerJobs.get(uuid);
        if (data == null || data.activeQuests.size() >= 2) return false;
        for (ActiveQuest aq : data.activeQuests) {
            if (aq.getQuestId().equals(questId)) return false;
        }
        data.activeQuests.add(new ActiveQuest(questId, tasks,
                System.currentTimeMillis() + timeLimitMs));
        return true;
    }

    public static boolean completeQuest(UUID uuid, String questId, Player player) {
        PlayerJobData data = playerJobs.get(uuid);
        if (data == null) return false;

        ActiveQuest found = null;
        for (ActiveQuest aq : data.activeQuests) {
            if (aq.getQuestId().equals(questId) && !aq.isExpired()) { found = aq; break; }
        }
        if (found == null) return false;

        data.activeQuests.remove(found);

        QuestTemplate qt = resolveQuest(found);
        if (qt == null) return true;

        String factionName = FactionManager.getPlayerFaction(uuid);
        if (factionName != null) {
            Faction faction = FactionManager.getFaction(factionName);
            int factionLevel = faction != null ? faction.getLevel() : 0;
            int expReward = qt.calculateExpReward(factionLevel, baseExpReward);
            FactionManager.addExpToFaction(factionName, expReward);
        }

        for (ItemStack reward : qt.getItemRewards()) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(reward.clone());
            for (ItemStack overflow : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), overflow);
            }
        }

        return true;
    }

    // ── Task progress tracking ────────────────────────────────────────────────

    public static void onTargetGathered(UUID playerUUID, String actionType,
                                        String targetName, Player player) {
        onTargetGathered(playerUUID, actionType, targetName, 1, player);
    }

    public static void onTargetGathered(UUID playerUUID, String actionType,
                                        String targetName, int amount, Player player) {
        PlayerJobData data = playerJobs.get(playerUUID);
        if (data == null || data.activeQuests.isEmpty()) return;

        List<ActiveQuest> toComplete = new ArrayList<>();

        for (ActiveQuest aq : data.activeQuests) {
            if (aq.isExpired()) continue;

            for (GeneratedTask task : aq.getTasks()) {
                if (!task.getActionType().equalsIgnoreCase(actionType)) continue;
                boolean matches = false;
                for (String t : task.getTargets()) {
                    if (t.equalsIgnoreCase(targetName)) { matches = true; break; }
                }
                if (!matches) continue;

                int current = aq.addTaskProgress(task.getTaskId(), amount);
                int required = task.getAmount();
                if (current <= required) {
                    player.sendActionBar(Component.text(
                            "[" + task.getName() + "] " + current + "/" + required,
                            current >= required ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
                }
            }

            boolean allDone = aq.getTasks().stream()
                    .allMatch(t -> aq.getTaskProgress(t.getTaskId()) >= t.getAmount());
            if (allDone) toComplete.add(aq);
        }

        for (ActiveQuest aq : toComplete) {
            if (completeQuest(playerUUID, aq.getQuestId(), player)) {
                player.sendMessage(Component.text("✔ Quest completed! Rewards granted.", NamedTextColor.GREEN));
            }
        }
    }

    // ── Daily quest management ────────────────────────────────────────────────

    public static List<QuestTemplate> getDailyOfferedQuests(UUID uuid) {
        PlayerJobData data = playerJobs.get(uuid);
        if (data == null) return List.of();

        long today = LocalDate.now().toEpochDay();
        if (data.dailyResetEpochDay != today || data.dailyOfferedQuests.isEmpty()) {
            data.dailyOfferedQuests.clear();
            data.dailySelectedIds.clear();
            data.activeQuests.clear();
            data.dailyResetEpochDay = today;
            generateDailyQuests(data);
        }

        List<QuestTemplate> result = new ArrayList<>();
        for (Map.Entry<String, List<GeneratedTask>> entry : data.dailyOfferedQuests.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                result.add(new QuestTemplate(entry.getKey(), entry.getValue()));
            }
        }
        return result;
    }

    public static boolean isDailyLocked(UUID uuid) {
        PlayerJobData data = playerJobs.get(uuid);
        if (data == null) return false;
        return data.dailyResetEpochDay == LocalDate.now().toEpochDay()
                && data.dailySelectedIds.size() >= 2;
    }

    public static int getDailySelectedCount(UUID uuid) {
        PlayerJobData data = playerJobs.get(uuid);
        if (data == null) return 0;
        if (data.dailyResetEpochDay != LocalDate.now().toEpochDay()) return 0;
        return data.dailySelectedIds.size();
    }

    public static boolean isDailyQuestSelected(UUID uuid, String questId) {
        PlayerJobData data = playerJobs.get(uuid);
        return data != null && data.dailySelectedIds.contains(questId);
    }

    public static boolean selectDailyQuest(UUID uuid, String questId) {
        if (isDailyLocked(uuid)) return false;
        PlayerJobData data = playerJobs.get(uuid);
        if (data == null || data.dailySelectedIds.contains(questId)) return false;

        List<GeneratedTask> tasks = data.dailyOfferedQuests.get(questId);
        if (tasks == null || tasks.isEmpty()) return false;

        long timeLimitMs = tasks.stream().mapToLong(GeneratedTask::getTimeLimitMs).sum();
        if (timeLimitMs == 0) timeLimitMs = 3_600_000L;

        if (!addActiveQuest(uuid, questId, tasks, timeLimitMs)) return false;
        data.dailySelectedIds.add(questId);
        return true;
    }

    // ── Quest generation ──────────────────────────────────────────────────────

    private static void generateDailyQuests(PlayerJobData data) {
        List<TaskTemplate> pool = new ArrayList<>(getTasksForJob(data.getJob()));
        if (pool.isEmpty()) return;

        int jobLevel = data.getLevel();

        for (int q = 0; q < 3; q++) {
            List<TaskTemplate> shuffled = new ArrayList<>(pool);
            Collections.shuffle(shuffled);
            int taskCount = Math.min(1 + ThreadLocalRandom.current().nextInt(2), shuffled.size());

            List<GeneratedTask> generated = new ArrayList<>();
            for (int i = 0; i < taskCount; i++) {
                int difficulty = ThreadLocalRandom.current().nextInt(1, 5); // 1–4
                generated.add(GeneratedTask.generate(shuffled.get(i), difficulty, jobLevel));
            }

            data.dailyOfferedQuests.put(UUID.randomUUID().toString(), generated);
        }
    }

    // ── Expiry scheduler ──────────────────────────────────────────────────────

    public static void scheduleExpiry(Plugin plugin) {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (PlayerJobData data : playerJobs.values()) {
                    data.activeQuests.removeIf(ActiveQuest::isExpired);
                }
            }
        }.runTaskTimer(plugin, 20L * 60, 20L * 60);
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    public static void saveJobs(FileConfiguration config) {
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

            s.set("daily_reset_epoch_day", data.dailyResetEpochDay);

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

    public static void loadJobs(FileConfiguration config) {
        playerJobs.clear();
        taskTemplates.clear();
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

        // ── Load quest config section ──────────────────────────────────────────
        ConfigurationSection questSection = config.getConfigurationSection("quests");
        baseExpReward = questSection != null ? questSection.getInt("exp_base_reward", 10) : 10;

        // ── Load task templates ────────────────────────────────────────────────
        List<?> taskList = questSection != null ? questSection.getList("tasks") : null;
        if (taskList != null) {
            for (Object obj : taskList) {
                if (!(obj instanceof Map<?, ?> tMap)) continue;
                String id      = mapStr(tMap, "id");
                String name    = mapStr(tMap, "name");
                String desc    = mapStr(tMap, "description");
                String jobName = mapStr(tMap, "job");
                String action  = mapStr(tMap, "action_type");
                if (id == null || name == null || desc == null || jobName == null || action == null) continue;

                Job job;
                try { job = Job.valueOf(jobName.toUpperCase()); }
                catch (IllegalArgumentException e) { continue; }

                List<String> targets = new ArrayList<>();
                Object targetsObj = tMap.get("targets");
                if (targetsObj instanceof List<?> tList) {
                    for (Object t : tList) if (t != null) targets.add(t.toString());
                }

                List<ItemStack> baseRewards = new ArrayList<>();
                Object rewardsObj = tMap.get("item_rewards");
                if (rewardsObj instanceof List<?> rList) {
                    for (Object rObj : rList) {
                        if (!(rObj instanceof Map<?, ?> rMap)) continue;
                        String matStr = mapStr(rMap, "material");
                        if (matStr == null) continue;
                        Material mat = Material.matchMaterial(matStr);
                        if (mat == null) continue;
                        baseRewards.add(new ItemStack(mat, mapInt(rMap, "amount", 1)));
                    }
                }

                taskTemplates.computeIfAbsent(job, k -> new ArrayList<>())
                        .add(new TaskTemplate(id, name, desc, job, action, targets, baseRewards));
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

            data.dailyResetEpochDay = s.getLong("daily_reset_epoch_day", 0);

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
