package org.minecraft.atlas.job;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.minecraft.atlas.Atlas;
import org.minecraft.atlas.donjon.DonjonKey;
import org.minecraft.atlas.quest.ActiveQuest;
import org.minecraft.atlas.quest.GeneratedTask;
import org.minecraft.atlas.quest.QuestManager;
import org.minecraft.atlas.quest.TaskTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages the Jokeyrini side-quest system, independent of the player's regular job.
 * Offers one quest per day per player with 2 hard+ tasks; rewards a Donjon Key on completion.
 */
public class JokeyriniManager {

    private static NamespacedKey KEY_NPC;

    public static NamespacedKey getKeyNpc() {
        if (KEY_NPC == null) KEY_NPC = new NamespacedKey(Atlas.instance, "jokeyrini_npc");
        return KEY_NPC;
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private static final List<TaskTemplate>              taskPool     = new ArrayList<>();
    private static final Map<UUID, Long>                 dailyReset   = new HashMap<>();
    private static final Map<UUID, List<GeneratedTask>>  dailyOffer   = new HashMap<>();
    private static final Map<UUID, ActiveQuest>          activeQuests = new HashMap<>();

    private static double specialQuestChance       = 0.15;
    private static long   jokeyriniResetIntervalMs = 24 * 3_600_000L;

    // ── Task templates ────────────────────────────────────────────────────────

    public static List<TaskTemplate> getTaskPool() { return List.copyOf(taskPool); }

    // ── Daily offer ───────────────────────────────────────────────────────────

    /**
     * Returns today's daily offer for the player.
     * Empty list means the player already accepted the offer today.
     */
    public static List<GeneratedTask> getDailyOffer(UUID uuid, int playerLevel) {
        long now = System.currentTimeMillis();
        Long lastReset = dailyReset.get(uuid);
        boolean needsReset = lastReset == null || (now - lastReset) >= jokeyriniResetIntervalMs;
        if (needsReset) {
            dailyReset.put(uuid, now);
            boolean isSpecial = ThreadLocalRandom.current().nextDouble() < specialQuestChance;
            dailyOffer.put(uuid, isSpecial ? generateSpecialQuest(playerLevel) : generateQuest(playerLevel));
        }
        return dailyOffer.getOrDefault(uuid, List.of());
    }

    /** Returns true if the current daily offer for this player is a Legendary special quest. */
    public static boolean isDailyOfferSpecial(UUID uuid) {
        List<GeneratedTask> offer = dailyOffer.get(uuid);
        if (offer == null || offer.isEmpty()) return false;
        return offer.stream().anyMatch(t -> t.getDifficulty() >= 5);
    }

    private static List<GeneratedTask> generateQuest(int playerLevel) {
        if (taskPool.size() < 2) return List.of();
        List<TaskTemplate> shuffled = new ArrayList<>(taskPool);
        Collections.shuffle(shuffled);
        List<GeneratedTask> tasks = new ArrayList<>();
        int count = Math.min(2 + ThreadLocalRandom.current().nextInt(2), shuffled.size()); // 2 or 3 tasks
        for (int i = 0; i < count; i++) {
            int difficulty = 3 + ThreadLocalRandom.current().nextInt(2); // 3 (Hard) or 4 (Hardcore)
            tasks.add(GeneratedTask.generate(shuffled.get(i), difficulty, playerLevel));
        }
        return tasks;
    }

    private static List<GeneratedTask> generateSpecialQuest(int playerLevel) {
        if (taskPool.isEmpty()) return List.of();
        List<TaskTemplate> shuffled = new ArrayList<>(taskPool);
        Collections.shuffle(shuffled);
        int count = Math.min(3, shuffled.size()); // always 3 tasks for Legendary
        List<GeneratedTask> tasks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            tasks.add(GeneratedTask.generate(shuffled.get(i), 5, playerLevel));
        }
        return tasks;
    }

    // ── Quest acceptance ──────────────────────────────────────────────────────

    public static boolean hasAcceptedQuest(UUID uuid) {
        ActiveQuest aq = activeQuests.get(uuid);
        if (aq != null && aq.isExpired()) { activeQuests.remove(uuid); return false; }
        return aq != null;
    }

    public static ActiveQuest getActiveQuest(UUID uuid) {
        ActiveQuest aq = activeQuests.get(uuid);
        if (aq != null && aq.isExpired()) { activeQuests.remove(uuid); return null; }
        return aq;
    }

    public static boolean acceptDailyOffer(UUID uuid, int playerLevel) {
        if (hasAcceptedQuest(uuid)) return false;
        List<GeneratedTask> tasks = getDailyOffer(uuid, playerLevel);
        if (tasks.isEmpty()) return false;
        long timeLimit = tasks.stream().mapToLong(GeneratedTask::getTimeLimitMs).sum();
        if (timeLimit == 0) timeLimit = 3_600_000L;
        activeQuests.put(uuid, new ActiveQuest(UUID.randomUUID().toString(), tasks,
                System.currentTimeMillis() + timeLimit));
        // Remove offer so the player can't re-accept it and the GUI shows "come back tomorrow"
        dailyOffer.remove(uuid);
        return true;
    }

    // ── Progress tracking ─────────────────────────────────────────────────────

    public static void onTargetGathered(UUID uuid, String actionType, String targetName, Player player) {
        onTargetGathered(uuid, actionType, targetName, 1, player);
    }

    public static void onTargetGathered(UUID uuid, String actionType, String targetName, int amount, Player player) {
        ActiveQuest aq = getActiveQuest(uuid);
        if (aq == null) return;

        for (GeneratedTask task : aq.getTasks()) {
            if (!task.getActionType().equalsIgnoreCase(actionType)) continue;
            boolean matches = task.getTargets().stream()
                    .anyMatch(t -> t.equalsIgnoreCase(targetName));
            if (!matches) continue;

            int current = aq.addTaskProgress(task.getTaskId(), amount);
            int required = task.getAmount();
            if (current <= required) {
                player.sendActionBar(Component.text(
                        "[Jokeyrini] " + task.getName() + " " + current + "/" + required,
                        current >= required ? NamedTextColor.GOLD : NamedTextColor.YELLOW));
            }
        }

        boolean allDone = aq.getTasks().stream()
                .allMatch(t -> aq.getTaskProgress(t.getTaskId()) >= t.getAmount());
        if (allDone) {
            activeQuests.remove(uuid);
            int maxDiff   = aq.getTasks().stream().mapToInt(GeneratedTask::getDifficulty).max().orElse(3);
            int keyCount  = maxDiff >= 5 ? 3 : maxDiff >= 4 ? 2 : 1;
            ItemStack key = DonjonKey.create(keyCount);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(key);
            for (ItemStack overflow : leftover.values())
                player.getWorld().dropItemNaturally(player.getLocation(), overflow);
            player.sendMessage(Component.text("✔ Jokeyrini's quest completed! You received ", NamedTextColor.GOLD)
                    .append(Component.text(keyCount + " Donjon Key" + (keyCount > 1 ? "s" : "") + "!",
                            NamedTextColor.LIGHT_PURPLE)));
        }
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    public static void saveJokeyriniData(FileConfiguration config) {
        config.set("jokeyrini_player_data", null);
        ConfigurationSection root = config.createSection("jokeyrini_player_data");
        long now = System.currentTimeMillis();

        for (UUID uuid : allTrackedPlayers()) {
            ConfigurationSection s = root.createSection(uuid.toString());

            Long reset = dailyReset.get(uuid);
            if (reset != null) s.set("last_daily_reset_ms", reset);

            List<GeneratedTask> offer = dailyOffer.get(uuid);
            if (offer != null && !offer.isEmpty()) {
                s.set("daily_offer", offer.stream().map(GeneratedTask::serialize).toList());
            }

            ActiveQuest aq = activeQuests.get(uuid);
            if (aq != null && aq.getExpiresAt() > now) {
                Map<String, Object> qMap = new LinkedHashMap<>();
                qMap.put("quest_id",      aq.getQuestId());
                qMap.put("tasks",         aq.getTasks().stream().map(GeneratedTask::serialize).toList());
                qMap.put("expires_at",    aq.getExpiresAt());
                qMap.put("task_progress", new HashMap<>(aq.getTaskProgressMap()));
                s.set("active_quest", qMap);
            }
        }
    }

    public static void loadJokeyriniConfig(FileConfiguration config) {
        taskPool.clear();

        ConfigurationSection jokSection = config.getConfigurationSection("jokeyrini");
        if (jokSection != null) {
            specialQuestChance       = jokSection.getDouble("special_quest_chance", 0.15);
            double resetHours        = jokSection.getDouble("daily_reset_hours", 24.0);
            jokeyriniResetIntervalMs = (long)(resetHours * 3_600_000L);
            List<?> taskList = jokSection.getList("tasks");
            if (taskList != null) {
                for (Object obj : taskList) {
                    if (!(obj instanceof Map<?, ?> tMap)) continue;
                    TaskTemplate t = parseTask(tMap);
                    if (t != null) taskPool.add(t);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static void loadJokeyriniData(FileConfiguration config) {
        dailyReset.clear();
        dailyOffer.clear();
        activeQuests.clear();

        ConfigurationSection playerSection = config.getConfigurationSection("jokeyrini_player_data");
        if (playerSection == null) return;

        long now = System.currentTimeMillis();
        for (String uuidStr : playerSection.getKeys(false)) {
            UUID uuid;
            try { uuid = UUID.fromString(uuidStr); }
            catch (IllegalArgumentException e) { continue; }

            ConfigurationSection s = playerSection.getConfigurationSection(uuidStr);
            if (s == null) continue;

            long reset = s.getLong("last_daily_reset_ms", 0);
            if (reset > 0) dailyReset.put(uuid, reset);

            List<?> offerRaw = s.getList("daily_offer");
            if (offerRaw != null) {
                List<GeneratedTask> tasks = deserializeTasks(offerRaw);
                if (!tasks.isEmpty()) dailyOffer.put(uuid, tasks);
            }

            Object aqObj = s.get("active_quest");
            if (aqObj instanceof Map<?, ?> qMap) {
                String questId    = qMap.get("quest_id") != null ? qMap.get("quest_id").toString() : null;
                Object expiresObj = qMap.get("expires_at");
                if (questId != null && expiresObj != null) {
                    long expiresAt = ((Number) expiresObj).longValue();
                    if (expiresAt > now) {
                        List<GeneratedTask> tasks = deserializeTasks(qMap.get("tasks"));
                        if (!tasks.isEmpty()) {
                            Map<String, Integer> progress = new HashMap<>();
                            Object pObj = qMap.get("task_progress");
                            if (pObj instanceof Map<?, ?> pMap) {
                                for (Map.Entry<?, ?> e : pMap.entrySet())
                                    if (e.getValue() instanceof Number n)
                                        progress.put(e.getKey().toString(), n.intValue());
                            }
                            activeQuests.put(uuid, new ActiveQuest(questId, tasks, expiresAt, progress));
                        }
                    }
                }
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static List<UUID> allTrackedPlayers() {
        java.util.Set<UUID> all = new java.util.HashSet<>();
        all.addAll(dailyReset.keySet());
        all.addAll(dailyOffer.keySet());
        all.addAll(activeQuests.keySet());
        return List.copyOf(all);
    }

    private static TaskTemplate parseTask(Map<?, ?> tMap) {
        String id     = str(tMap, "id");
        String name   = str(tMap, "name");
        String desc   = str(tMap, "description");
        String action = str(tMap, "action_type");
        if (id == null || name == null || desc == null || action == null) return null;

        List<String> targets = new ArrayList<>();
        Object tObj = tMap.get("targets");
        if (tObj instanceof List<?> tList) for (Object t : tList) if (t != null) targets.add(t.toString());

        List<ItemStack> rewards = new ArrayList<>();
        Object rObj = tMap.get("item_rewards");
        if (rObj instanceof List<?> rList) {
            for (Object rItem : rList) {
                if (!(rItem instanceof Map<?, ?> rMap)) continue;
                String matStr = str(rMap, "material");
                if (matStr == null) continue;
                Material mat = Material.matchMaterial(matStr);
                if (mat != null) rewards.add(new ItemStack(mat, 1));
            }
        }
        TaskTemplate.ExpMultiplier expMult = QuestManager.parseExpMultiplier(tMap.get("exp_multiplier"));
        return new TaskTemplate(id, name, desc, Job.JOKEYRINI, action, targets, rewards, expMult);
    }

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

    private static String str(Map<?, ?> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }
}
