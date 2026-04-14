package org.minecraft.atlas.job;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
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

public class JobManager {

    // -------------------------------------------------------------------------
    // PDC key — stored on the Villager entity
    // -------------------------------------------------------------------------

    private static NamespacedKey KEY_NPC_JOB;

    public static NamespacedKey getKeyNpcJob() {
        if (KEY_NPC_JOB == null)
            KEY_NPC_JOB = new NamespacedKey(Atlas.instance, "job_npc_type");
        return KEY_NPC_JOB;
    }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private static final Map<UUID, PlayerJobData>      playerJobs     = new HashMap<>();
    private static final Map<Job, List<TaskTemplate>>  taskTemplates  = new EnumMap<>(Job.class);
    private static int baseExpReward = 10;

    // -------------------------------------------------------------------------
    // Task template access
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Player job queries
    // -------------------------------------------------------------------------

    public static boolean hasJob(UUID playerUUID) {
        return playerJobs.containsKey(playerUUID);
    }

    public static PlayerJobData getJobData(UUID playerUUID) {
        return playerJobs.get(playerUUID);
    }

    /**
     * Assigns a job to a player. Jobs are permanent per faction membership —
     * returns false if the player already has a job.
     */
    public static boolean setJob(UUID playerUUID, Job job) {
        if (playerJobs.containsKey(playerUUID)) return false;
        playerJobs.put(playerUUID, new PlayerJobData(job));
        return true;
    }

    /**
     * Removes the player's job, level, progress, and all quest data.
     * Called when a player leaves or is kicked from their faction.
     */
    public static boolean removeJob(UUID playerUUID) {
        return playerJobs.remove(playerUUID) != null;
    }

    /** Sets a player's job, replacing any existing one (admin override). */
    public static void forceSetJob(UUID playerUUID, Job job) {
        playerJobs.put(playerUUID, new PlayerJobData(job));
    }

    // ── Admin level / XP helpers ─────────────────────────────────────────────

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

    /**
     * Adds job progress and notifies the player if they level up.
     * @return true if the player levelled up.
     */
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

    // -------------------------------------------------------------------------
    // Active quest management
    // -------------------------------------------------------------------------

    public static List<ActiveQuest> getActiveQuests(UUID uuid) {
        PlayerJobData data = playerJobs.get(uuid);
        return data == null ? List.of() : List.copyOf(data.activeQuests);
    }

    /**
     * Reconstructs a {@link QuestTemplate} from an {@link ActiveQuest}'s stored task IDs.
     * Returns null if any task ID is no longer valid.
     */
    public static QuestTemplate resolveQuest(ActiveQuest aq) {
        List<TaskTemplate> tasks = new ArrayList<>();
        for (String taskId : aq.getTaskIds()) {
            TaskTemplate t = getTaskTemplate(taskId);
            if (t == null) return null;
            tasks.add(t);
        }
        return tasks.isEmpty() ? null : new QuestTemplate(aq.getQuestId(), tasks);
    }

    /**
     * Adds a quest to the player's active list (max 2).
     * Returns false if already at max, quest already active, or the quest has no tasks.
     */
    public static boolean addActiveQuest(UUID uuid, QuestTemplate qt) {
        PlayerJobData data = playerJobs.get(uuid);
        if (data == null || data.activeQuests.size() >= 2) return false;
        for (ActiveQuest aq : data.activeQuests) {
            if (aq.getQuestId().equals(qt.getId())) return false;
        }
        List<String> taskIds = new ArrayList<>();
        for (TaskTemplate t : qt.getTasks()) taskIds.add(t.getId());
        data.activeQuests.add(new ActiveQuest(qt.getId(), taskIds,
                System.currentTimeMillis() + qt.getTimeLimitMs()));
        return true;
    }

    /**
     * Completes a quest: removes it, grants faction EXP (calculated from difficulty + faction level),
     * and gives item rewards. Returns false if the quest is not active or has expired.
     */
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

        // Grant faction EXP based on difficulty sum and faction level
        String factionName = FactionManager.getPlayerFaction(uuid);
        if (factionName != null) {
            Faction faction = FactionManager.getFaction(factionName);
            int factionLevel = faction != null ? faction.getLevel() : 0;
            int expReward = qt.calculateExpReward(factionLevel, baseExpReward);
            FactionManager.addExpToFaction(factionName, expReward);
        }

        // Give item rewards — overflow drops at player location
        for (ItemStack reward : qt.getItemRewards()) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(reward.clone());
            for (ItemStack overflow : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), overflow);
            }
        }

        return true;
    }

    // -------------------------------------------------------------------------
    // Daily quest management
    // -------------------------------------------------------------------------

    /**
     * Returns the 3 quests offered to this player today, generating them if
     * the daily reset has not happened yet.
     * Tasks are filtered by the player's current faction level so only
     * level-appropriate tasks are included.
     */
    public static List<QuestTemplate> getDailyOfferedQuests(UUID uuid) {
        PlayerJobData data = playerJobs.get(uuid);
        if (data == null) return List.of();

        long today = LocalDate.now().toEpochDay();
        if (data.dailyResetEpochDay != today || data.dailyOfferedQuestTasks.isEmpty()) {
            data.dailyOfferedQuestTasks.clear();
            data.dailySelectedIds.clear();
            // Replace old active quests so the player starts fresh each day
            data.activeQuests.clear();
            data.dailyResetEpochDay = today;

            // Resolve faction level to filter eligible tasks
            int factionLevel = 0;
            String factionName = FactionManager.getPlayerFaction(uuid);
            if (factionName != null) {
                Faction faction = FactionManager.getFaction(factionName);
                if (faction != null) factionLevel = faction.getLevel();
            }

            List<TaskTemplate> eligible = new ArrayList<>();
            for (TaskTemplate t : getTasksForJob(data.getJob())) {
                if (factionLevel >= t.getMinLevel() && factionLevel <= t.getMaxLevel()) {
                    eligible.add(t);
                }
            }

            if (!eligible.isEmpty()) {
                // Always generate exactly 3 quests by sampling independently (with replacement).
                // Each quest gets 1–2 tasks drawn from a fresh shuffle of the eligible pool.
                for (int q = 0; q < 3; q++) {
                    List<TaskTemplate> shuffled = new ArrayList<>(eligible);
                    Collections.shuffle(shuffled);
                    int taskCount = Math.min(1 + (int)(Math.random() * 2), shuffled.size());
                    List<String> taskIds = new ArrayList<>();
                    for (int i = 0; i < taskCount; i++) taskIds.add(shuffled.get(i).getId());
                    data.dailyOfferedQuestTasks.put(UUID.randomUUID().toString(), taskIds);
                }
            }
        }

        // Reconstruct QuestTemplate objects from stored task IDs
        List<QuestTemplate> result = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : data.dailyOfferedQuestTasks.entrySet()) {
            List<TaskTemplate> tasks = new ArrayList<>();
            for (String taskId : entry.getValue()) {
                TaskTemplate t = getTaskTemplate(taskId);
                if (t != null) tasks.add(t);
            }
            if (!tasks.isEmpty()) result.add(new QuestTemplate(entry.getKey(), tasks));
        }
        return result;
    }

    /** Returns true if the player has already selected 2 quests today. */
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

    /**
     * Selects one of today's offered quests and adds it to active quests.
     * Returns false if the daily selection is already full or the quest was already selected.
     */
    public static boolean selectDailyQuest(UUID uuid, String questId) {
        if (isDailyLocked(uuid)) return false;
        PlayerJobData data = playerJobs.get(uuid);
        if (data == null || data.dailySelectedIds.contains(questId)) return false;

        // Reconstruct the QuestTemplate for this quest ID
        List<String> taskIds = data.dailyOfferedQuestTasks.get(questId);
        if (taskIds == null) return false;
        List<TaskTemplate> tasks = new ArrayList<>();
        for (String tid : taskIds) {
            TaskTemplate t = getTaskTemplate(tid);
            if (t != null) tasks.add(t);
        }
        if (tasks.isEmpty()) return false;

        QuestTemplate qt = new QuestTemplate(questId, tasks);
        if (!addActiveQuest(uuid, qt)) return false;
        data.dailySelectedIds.add(questId);
        return true;
    }

    // -------------------------------------------------------------------------
    // Expiry scheduler
    // -------------------------------------------------------------------------

    /** Starts a 60-second ticker that removes expired quests. Call once from Atlas.onEnable. */
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

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    public static void saveJobs(FileConfiguration config) {
        config.set("jobs", null);
        ConfigurationSection section = config.createSection("jobs");

        for (Map.Entry<UUID, PlayerJobData> entry : playerJobs.entrySet()) {
            ConfigurationSection s = section.createSection(entry.getKey().toString());
            PlayerJobData data = entry.getValue();
            s.set("job",      data.getJob().name());
            s.set("level",    data.getLevel());
            s.set("progress", data.getProgress());

            // Active quests — store questId + taskIds + expiry
            List<Map<String, Object>> questList = new ArrayList<>();
            for (ActiveQuest aq : data.activeQuests) {
                questList.add(Map.of(
                    "quest_id",   aq.getQuestId(),
                    "task_ids",   new ArrayList<>(aq.getTaskIds()),
                    "expires_at", aq.getExpiresAt()));
            }
            s.set("active_quests", questList);

            s.set("daily_reset_epoch_day", data.dailyResetEpochDay);

            // Daily offered quests — store as questId -> list<taskId> pairs
            List<Map<String, Object>> offeredList = new ArrayList<>();
            for (Map.Entry<String, List<String>> e : data.dailyOfferedQuestTasks.entrySet()) {
                offeredList.add(Map.of("quest_id", e.getKey(), "task_ids", new ArrayList<>(e.getValue())));
            }
            s.set("daily_offered_quests", offeredList);
            s.set("daily_selected",       new ArrayList<>(data.dailySelectedIds));
        }
    }

    public static void loadJobs(FileConfiguration config) {
        playerJobs.clear();
        taskTemplates.clear();

        // ── Load quest config section ──────────────────────────────────────────
        ConfigurationSection questSection = config.getConfigurationSection("quests");
        baseExpReward = questSection != null ? questSection.getInt("exp_base_reward", 10) : 10;

        // ── Load task templates ────────────────────────────────────────────────
        List<?> taskList = questSection != null ? questSection.getList("tasks") : null;
        if (taskList != null) {
            for (Object obj : taskList) {
                if (!(obj instanceof Map<?, ?> tMap)) continue;
                String id       = mapStr(tMap, "id");
                String name     = mapStr(tMap, "name");
                String desc     = mapStr(tMap, "description");
                String jobName  = mapStr(tMap, "job");
                String action   = mapStr(tMap, "action_type");
                if (id == null || name == null || desc == null || jobName == null || action == null) continue;

                Job job;
                try { job = Job.valueOf(jobName.toUpperCase()); }
                catch (IllegalArgumentException e) { continue; }

                int minLevel  = mapInt(tMap, "min_level", 0);
                int maxLevel  = mapInt(tMap, "max_level", 100);
                int difficulty = Math.max(1, Math.min(3, mapInt(tMap, "difficulty", 1)));
                long timeLimitMs = (long) mapInt(tMap, "time_limit_seconds", 3600) * 1000L;

                List<String> targets = new ArrayList<>();
                Object targetsObj = tMap.get("targets");
                if (targetsObj instanceof List<?> tList) {
                    for (Object t : tList) if (t != null) targets.add(t.toString());
                }

                List<ItemStack> rewards = new ArrayList<>();
                Object rewardsObj = tMap.get("item_rewards");
                if (rewardsObj instanceof List<?> rList) {
                    for (Object rObj : rList) {
                        if (!(rObj instanceof Map<?, ?> rMap)) continue;
                        String matStr = mapStr(rMap, "material");
                        if (matStr == null) continue;
                        Material mat = Material.matchMaterial(matStr);
                        if (mat == null) continue;
                        rewards.add(new ItemStack(mat, mapInt(rMap, "amount", 1)));
                    }
                }

                taskTemplates.computeIfAbsent(job, k -> new ArrayList<>())
                        .add(new TaskTemplate(id, name, desc, job, action, minLevel, maxLevel,
                                difficulty, targets, rewards, timeLimitMs));
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
                    String questId  = mapStr(qMap, "quest_id");
                    Object expiresObj = qMap.get("expires_at");
                    if (questId == null || expiresObj == null) continue;
                    long expiresAt = ((Number) expiresObj).longValue();
                    if (expiresAt <= System.currentTimeMillis()) continue; // skip expired

                    List<String> taskIds = new ArrayList<>();
                    Object tidObj = qMap.get("task_ids");
                    if (tidObj instanceof List<?> tidList) {
                        for (Object t : tidList) if (t != null) taskIds.add(t.toString());
                    }
                    data.activeQuests.add(new ActiveQuest(questId, taskIds, expiresAt));
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
                    List<String> taskIds = new ArrayList<>();
                    Object tidObj = oMap.get("task_ids");
                    if (tidObj instanceof List<?> tidList) {
                        for (Object t : tidList) if (t != null) taskIds.add(t.toString());
                    }
                    if (!taskIds.isEmpty()) data.dailyOfferedQuestTasks.put(questId, taskIds);
                }
            }

            data.dailySelectedIds.addAll(s.getStringList("daily_selected"));

            playerJobs.put(uuid, data);
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private static String mapStr(Map<?, ?> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private static int mapInt(Map<?, ?> map, String key, int def) {
        Object v = map.get(key);
        return v instanceof Number n ? n.intValue() : def;
    }
}
