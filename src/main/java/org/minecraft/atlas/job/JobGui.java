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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class JobGui implements Listener {

    private static final String TITLE = "Choose Your Job";
    // Slots in the middle row of a 3-row (27-slot) chest
    private static final int[] JOB_SLOTS = {10, 12, 14, 16};

    private static final Set<UUID> openMenus = new HashSet<>();

    public static void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text(TITLE, NamedTextColor.GOLD));

        Job[] jobs = Job.values();
        for (int i = 0; i < jobs.length && i < JOB_SLOTS.length; i++) {
            inv.setItem(JOB_SLOTS[i], buildJobItem(jobs[i]));
        }

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.empty());
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < 27; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, filler);
        }

        openMenus.add(player.getUniqueId());
        player.openInventory(inv);
    }

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
            openMenus.remove(player.getUniqueId());
            player.closeInventory();

            if (JobManager.setJob(player.getUniqueId(), selected)) {
                player.sendMessage(
                    Component.text("You have chosen the job: ", NamedTextColor.GREEN)
                        .append(Component.text(selected.getDisplayName(), selected.getColor()))
                        .append(Component.text("!", NamedTextColor.GREEN))
                );
            } else {
                player.sendMessage(Component.text("You already have a job.", NamedTextColor.RED));
            }
            return;
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (title.equals(TITLE)) {
            openMenus.remove(player.getUniqueId());
        }
    }
}
