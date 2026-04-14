package org.minecraft.atlas.job;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class JobGui implements Listener {

    // ── Screen routing ────────────────────────────────────────────────────────
    private enum Screen { JOB_SELECT, JOB_MAIN, QUEST_LIST, DAILY_QUESTS }
    private static final Map<UUID, Screen> activeScreen = new HashMap<>();

    // ── Static titles ─────────────────────────────────────────────────────────
    private static final String TITLE_SELECT     = "Choose Your Job";
    private static final String TITLE_QUEST_LIST = "My Quests";
    private static final String TITLE_DAILY      = "Daily Quests";

    /** Players whose job selection should bypass the permanent-job check (admin /job set). */
    private static final Set<UUID> forceOverride = new HashSet<>();

    // ── Slot constants ────────────────────────────────────────────────────────
    private static final int SLOT_MY_QUESTS    = 11;
    private static final int SLOT_DAILY_QUESTS = 15;
    /** Bottom-right slot of a 27-slot (3-row) inventory. */
    private static final int SLOT_BACK         = 26;

    private static final int[] JOB_SLOTS        = {10, 12, 14, 16};
    private static final int[] QUEST_ITEM_SLOTS = {11, 15};
    private static final int[] DAILY_ITEM_SLOTS = {10, 13, 16};

    // ── Dynamic title helpers ─────────────────────────────────────────────────

    /** "Miner - Quests", "Hunter - Quests", etc. */
    private static String getJobMainTitle(Player player) {
        PlayerJobData data = JobManager.getJobData(player.getUniqueId());
        return data != null ? data.getJob().getDisplayName() + " - Quests" : "Job Menu";
    }

    // ── Public open methods ───────────────────────────────────────────────────

    /** Opens the job selection GUI. Respects the permanent-job rule. */
    public static void open(Player player) {
        activeScreen.put(player.getUniqueId(), Screen.JOB_SELECT);
        player.openInventory(buildSelectInventory());
    }

    /** Opens the job selection GUI, overriding any existing job (admin use). */
    public static void openAdmin(Player target) {
        forceOverride.add(target.getUniqueId());
        open(target);
    }

    /** Opens the NPC job main menu (called from JobListener after NPC right-click). */
    public static void openJobMain(Player player) {
        activeScreen.put(player.getUniqueId(), Screen.JOB_MAIN);
        player.openInventory(buildJobMainInventory(player));
    }

    // ── Inventory builders ────────────────────────────────────────────────────

    private static Inventory buildSelectInventory() {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text(TITLE_SELECT, NamedTextColor.GOLD));
        Job[] jobs = Job.values();
        for (int i = 0; i < jobs.length && i < JOB_SLOTS.length; i++) {
            inv.setItem(JOB_SLOTS[i], buildJobItem(jobs[i]));
        }
        fillGlass(inv, 27);
        return inv;
    }

    private static Inventory buildJobMainInventory(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text(getJobMainTitle(player), NamedTextColor.DARK_AQUA));

        // My Quests item
        ItemStack myQuests = new ItemStack(Material.PAPER);
        ItemMeta mqMeta = myQuests.getItemMeta();
        mqMeta.displayName(Component.text("My Quests", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        List<ActiveQuest> active = JobManager.getActiveQuests(player.getUniqueId());
        List<Component> mqLore = new ArrayList<>();
        if (active.isEmpty()) {
            mqLore.add(Component.text("No active quests.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        } else {
            mqLore.add(Component.text(active.size() + "/2 active quests.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        mqLore.add(Component.text("Click to view.", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        mqMeta.lore(mqLore);
        myQuests.setItemMeta(mqMeta);
        inv.setItem(SLOT_MY_QUESTS, myQuests);

        // Daily Quests item
        ItemStack daily = new ItemStack(Material.CLOCK);
        ItemMeta dMeta = daily.getItemMeta();
        dMeta.displayName(Component.text("Daily Quests", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> dLore = new ArrayList<>();
        if (JobManager.isDailyLocked(player.getUniqueId())) {
            dLore.add(Component.text("You have chosen your 2 daily quests.", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            dLore.add(Component.text("Come back tomorrow!", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        } else {
            int selected = JobManager.getDailySelectedCount(player.getUniqueId());
            dLore.add(Component.text("Select up to 2 quests per day.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            dLore.add(Component.text(selected + "/2 selected today.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            dLore.add(Component.text("Click to choose.", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        }
        dMeta.lore(dLore);
        daily.setItemMeta(dMeta);
        inv.setItem(SLOT_DAILY_QUESTS, daily);

        inv.setItem(SLOT_BACK, buildBackItem("Close"));
        fillGlass(inv, 27);
        return inv;
    }

    private static Inventory buildQuestListInventory(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text(TITLE_QUEST_LIST, NamedTextColor.YELLOW));

        List<ActiveQuest> quests = JobManager.getActiveQuests(player.getUniqueId());
        for (int i = 0; i < quests.size() && i < QUEST_ITEM_SLOTS.length; i++) {
            ActiveQuest aq = quests.get(i);
            QuestTemplate qt = JobManager.resolveQuest(aq);
            inv.setItem(QUEST_ITEM_SLOTS[i], buildActiveQuestItem(aq, qt));
        }

        inv.setItem(SLOT_BACK, buildBackItem("Back"));
        fillGlass(inv, 27);
        return inv;
    }

    private static Inventory buildDailyQuestsInventory(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text(TITLE_DAILY, NamedTextColor.GOLD));

        List<QuestTemplate> offered = JobManager.getDailyOfferedQuests(player.getUniqueId());
        boolean locked = JobManager.isDailyLocked(player.getUniqueId());

        for (int i = 0; i < offered.size() && i < DAILY_ITEM_SLOTS.length; i++) {
            QuestTemplate qt = offered.get(i);
            boolean selected = JobManager.isDailyQuestSelected(player.getUniqueId(), qt.getId());
            if (!selected) {
                // Show as available or locked (grayed out when 2 already selected)
                inv.setItem(DAILY_ITEM_SLOTS[i], buildDailyQuestItem(qt, locked));
            }
            // Selected slots are left empty — fillGlass will fill them with the background pane
        }

        inv.setItem(SLOT_BACK, buildBackItem("Back"));
        fillGlass(inv, 27);
        return inv;
    }

    // ── Item builders ─────────────────────────────────────────────────────────

    private static ItemStack buildJobItem(Job job) {
        ItemStack item = new ItemStack(job.getIcon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(job.getDisplayName(), job.getColor()).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
            Component.text("Click to select this job.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
            Component.text("This choice is permanent!", NamedTextColor.DARK_RED).decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildActiveQuestItem(ActiveQuest aq, QuestTemplate qt) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (qt != null) {
            meta.displayName(Component.text("Active Quest", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            long remaining = aq.getExpiresAt() - System.currentTimeMillis();
            lore.add(label("Time remaining: ").append(value(formatTime(remaining))));
            lore.add(Component.empty());
            lore.add(label("Tasks:"));
            for (TaskTemplate task : qt.getTasks()) {
                lore.add(Component.text("  \u2022 ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(task.getName(), NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                        .append(Component.text(" [" + difficultyLabel(task.getDifficulty()) + "]",
                                difficultyColor(task.getDifficulty())).decoration(TextDecoration.ITALIC, false)));
                lore.add(Component.text("    " + task.getDescription(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
            List<ItemStack> rewards = qt.getItemRewards();
            if (!rewards.isEmpty()) {
                lore.add(Component.empty());
                lore.add(label("Item Rewards:"));
                for (ItemStack reward : rewards) {
                    lore.add(Component.text("  \u2022 " + reward.getAmount() + "x " + formatMaterial(reward.getType().name()),
                            NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
                }
            }
            meta.lore(lore);
        } else {
            meta.displayName(Component.text("Unknown Quest", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        }
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Builds a daily quest item.
     * @param locked true when the player has already selected 2 quests today
     */
    private static ItemStack buildDailyQuestItem(QuestTemplate qt, boolean locked) {
        Material mat = locked ? Material.GRAY_DYE : Material.PAPER;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        NamedTextColor nameColor = locked ? NamedTextColor.DARK_GRAY : NamedTextColor.YELLOW;
        meta.displayName(Component.text("Daily Quest", nameColor).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(label("Tasks (" + qt.getTasks().size() + "):"));
        for (TaskTemplate task : qt.getTasks()) {
            lore.add(Component.text("  \u2022 ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(task.getName(), NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                    .append(Component.text(" [" + difficultyLabel(task.getDifficulty()) + "]",
                            difficultyColor(task.getDifficulty())).decoration(TextDecoration.ITALIC, false)));
        }
        lore.add(Component.empty());
        lore.add(label("Difficulty: ").append(value(qt.getSumDifficulty() + " pts")));
        lore.add(label("Time Limit: ").append(value(formatTime(qt.getTimeLimitMs()))));
        List<ItemStack> rewards = qt.getItemRewards();
        if (!rewards.isEmpty()) {
            lore.add(label("Item Rewards:"));
            for (ItemStack reward : rewards) {
                lore.add(Component.text("  \u2022 " + reward.getAmount() + "x " + formatMaterial(reward.getType().name()),
                        NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            }
        }
        lore.add(Component.empty());
        if (locked) {
            lore.add(Component.text("Selection locked for today.", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("Left-click to select.", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildBackItem(String label) {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(label, NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    // ── Event handlers ────────────────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Screen screen = activeScreen.get(player.getUniqueId());
        if (screen == null) return;

        // Click is inside our custom GUI — always cancel to prevent item theft.
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());

        switch (screen) {
            case JOB_SELECT   -> handleJobSelectClick(player, event, title);
            case JOB_MAIN     -> handleJobMainClick(player, event, title);
            case QUEST_LIST   -> handleQuestListClick(player, event, title);
            case DAILY_QUESTS -> handleDailyClick(player, event, title);
            default -> {}
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        activeScreen.remove(player.getUniqueId());
        forceOverride.remove(player.getUniqueId());
    }

    // ── Click handlers ────────────────────────────────────────────────────────

    private void handleJobSelectClick(Player player, InventoryClickEvent event, String title) {
        if (!title.equals(TITLE_SELECT)) return;

        int slot = event.getRawSlot();
        Job[] jobs = Job.values();
        for (int i = 0; i < jobs.length && i < JOB_SLOTS.length; i++) {
            if (JOB_SLOTS[i] != slot) continue;

            Job selected = jobs[i];
            boolean isForce = forceOverride.remove(player.getUniqueId());
            activeScreen.remove(player.getUniqueId());
            player.closeInventory();

            if (isForce) {
                JobManager.forceSetJob(player.getUniqueId(), selected);
            } else {
                JobManager.setJob(player.getUniqueId(), selected);
            }
            player.sendMessage(
                Component.text("You have chosen the job: ", NamedTextColor.GREEN)
                    .append(Component.text(selected.getDisplayName(), selected.getColor()))
                    .append(Component.text("!", NamedTextColor.GREEN))
            );
            return;
        }
    }

    private void handleJobMainClick(Player player, InventoryClickEvent event, String title) {
        if (!title.equals(getJobMainTitle(player))) return;

        int slot = event.getRawSlot();

        if (slot == SLOT_BACK) {
            activeScreen.remove(player.getUniqueId());
            player.closeInventory();
            return;
        }

        if (slot == SLOT_MY_QUESTS) {
            activeScreen.put(player.getUniqueId(), Screen.QUEST_LIST);
            player.openInventory(buildQuestListInventory(player));
            return;
        }

        if (slot == SLOT_DAILY_QUESTS) {
            if (JobManager.isDailyLocked(player.getUniqueId())) {
                player.sendMessage(Component.text("You have already selected your 2 daily quests. Come back tomorrow!", NamedTextColor.RED));
                return;
            }
            activeScreen.put(player.getUniqueId(), Screen.DAILY_QUESTS);
            player.openInventory(buildDailyQuestsInventory(player));
        }
    }

    private void handleQuestListClick(Player player, InventoryClickEvent event, String title) {
        if (!title.equals(TITLE_QUEST_LIST)) return;

        if (event.getRawSlot() == SLOT_BACK) {
            activeScreen.put(player.getUniqueId(), Screen.JOB_MAIN);
            player.openInventory(buildJobMainInventory(player));
        }
    }

    private void handleDailyClick(Player player, InventoryClickEvent event, String title) {
        if (!title.equals(TITLE_DAILY)) return;

        int slot = event.getRawSlot();

        if (slot == SLOT_BACK) {
            activeScreen.put(player.getUniqueId(), Screen.JOB_MAIN);
            player.openInventory(buildJobMainInventory(player));
            return;
        }

        // Ignore quest clicks if the daily is fully locked
        if (JobManager.isDailyLocked(player.getUniqueId())) return;

        List<QuestTemplate> offered = JobManager.getDailyOfferedQuests(player.getUniqueId());
        for (int i = 0; i < offered.size() && i < DAILY_ITEM_SLOTS.length; i++) {
            if (DAILY_ITEM_SLOTS[i] != slot) continue;
            QuestTemplate qt = offered.get(i);

            // Slot is already selected (glass filler) — ignore
            if (JobManager.isDailyQuestSelected(player.getUniqueId(), qt.getId())) return;

            if (JobManager.selectDailyQuest(player.getUniqueId(), qt.getId())) {
                int taskCount = qt.getTasks().size();
                player.sendMessage(
                    Component.text("Quest accepted! ", NamedTextColor.GREEN)
                        .append(Component.text("(" + taskCount + " task" + (taskCount > 1 ? "s" : "") + ")",
                                NamedTextColor.YELLOW))
                );
            }
            // Refresh daily screen
            activeScreen.put(player.getUniqueId(), Screen.DAILY_QUESTS);
            player.openInventory(buildDailyQuestsInventory(player));
            return;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void fillGlass(Inventory inv, int size) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.empty());
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < size; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, filler);
        }
    }

    private static Component label(String text) {
        return Component.text(text, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false);
    }

    private static Component value(String text) {
        return Component.text(text, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false);
    }

    private static String formatTime(long ms) {
        if (ms <= 0) return "Expired";
        long seconds = ms / 1000;
        long hours   = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    private static String formatMaterial(String name) {
        String[] parts = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)));
            sb.append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    private static String difficultyLabel(int difficulty) {
        return switch (difficulty) {
            case 1 -> "Easy";
            case 2 -> "Normal";
            case 3 -> "Hard";
            default -> "?";
        };
    }

    private static NamedTextColor difficultyColor(int difficulty) {
        return switch (difficulty) {
            case 1 -> NamedTextColor.GREEN;
            case 2 -> NamedTextColor.YELLOW;
            case 3 -> NamedTextColor.RED;
            default -> NamedTextColor.GRAY;
        };
    }
}
