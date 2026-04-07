package org.minecraft.atlas.job;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class JobGui implements Listener {

    private static final String TITLE = "Choose Your Job";
    private static final int[] JOB_SLOTS = {10, 12, 14, 16};

    private static final Set<UUID> openMenus = new HashSet<>();
    /** Players whose job selection should bypass the eligibility check (admin /job set). */
    private static final Set<UUID> forceOverride = new HashSet<>();

    /** Opens the job selection GUI. Normal flow: respects mastery eligibility rules. */
    public static void open(Player player) {
        openMenus.add(player.getUniqueId());
        player.openInventory(buildInventory(player));
    }

    /** Opens the job selection GUI for a player, bypassing all eligibility checks (admin use). */
    public static void openAdmin(Player target) {
        forceOverride.add(target.getUniqueId());
        open(target);
    }

    private static Inventory buildInventory(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text(TITLE, NamedTextColor.GOLD));

        Job[] jobs = Job.values();
        for (int i = 0; i < jobs.length && i < JOB_SLOTS.length; i++) {
            inv.setItem(JOB_SLOTS[i], buildJobItem(jobs[i], player));
        }

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.empty());
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < 27; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, filler);
        }
        return inv;
    }

    private static ItemStack buildJobItem(Job job, Player player) {
        UUID uuid = player.getUniqueId();
        boolean owned = JobManager.hasJob(uuid, job);
        PlayerJobData data = owned ? JobManager.getJobData(uuid, job) : null;
        boolean mastered = data != null && data.isMastered();
        boolean canAdd = JobManager.canAddJob(uuid);

        ItemStack item = new ItemStack(job.getIcon());
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = new ArrayList<>();

        if (owned && mastered) {
            // Mastered job: enchant glow + mastery badge
            meta.displayName(Component.text(job.getDisplayName(), job.getColor())
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("✦ MASTERED", NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Level " + data.getLevel(), NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            meta.addEnchant(Enchantment.PROTECTION, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        } else if (owned) {
            // Owned but not yet mastered
            meta.displayName(Component.text(job.getDisplayName(), job.getColor())
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Level " + data.getLevel() + " / " + JobRegistry.getMaxLevel(),
                    NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Not yet mastered.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        } else if (canAdd) {
            // Unowned and eligible to select
            meta.displayName(Component.text(job.getDisplayName(), job.getColor())
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Click to select this job.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("This choice is permanent!", NamedTextColor.DARK_RED)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            // Unowned but locked (must master current jobs first)
            meta.displayName(Component.text(job.getDisplayName(), NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Master your current job(s) first.", NamedTextColor.DARK_RED)
                    .decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!openMenus.contains(player.getUniqueId())) return;

        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (!title.equals(TITLE)) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR
                || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        int slot = event.getRawSlot();
        Job[] jobs = Job.values();
        for (int i = 0; i < jobs.length && i < JOB_SLOTS.length; i++) {
            if (JOB_SLOTS[i] != slot) continue;

            Job selected = jobs[i];
            UUID uuid = player.getUniqueId();

            // Already owns this job
            if (JobManager.hasJob(uuid, selected)) return;

            boolean isForce = forceOverride.remove(uuid);
            openMenus.remove(uuid);
            player.closeInventory();

            if (isForce) {
                JobManager.forceSetJob(uuid, selected);
            } else {
                if (!JobManager.canAddJob(uuid)) {
                    player.sendMessage(Component.text(
                            "You must master your current job(s) before selecting another.",
                            NamedTextColor.RED));
                    return;
                }
                JobManager.setJob(uuid, selected);
            }

            player.sendMessage(
                Component.text("You have chosen the job: ", NamedTextColor.GREEN)
                    .append(Component.text(selected.getDisplayName(), selected.getColor()))
                    .append(Component.text("!", NamedTextColor.GREEN))
            );
            return;
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (title.equals(TITLE)) {
            openMenus.remove(player.getUniqueId());
            forceOverride.remove(player.getUniqueId());
        }
    }
}
