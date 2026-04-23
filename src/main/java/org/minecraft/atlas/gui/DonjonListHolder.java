package org.minecraft.atlas.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.minecraft.atlas.donjon.Donjon;
import org.minecraft.atlas.donjon.DonjonManager;
import org.minecraft.atlas.donjon.DonjonStatus;
import org.minecraft.atlas.donjon.SmugglerManager;
import org.minecraft.atlas.util.GuiUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DonjonListHolder implements AtlasHolder {

    private final Inventory inventory;
    private final List<String> donjonIds = new ArrayList<>();

    public DonjonListHolder(Player player) {
        List<Donjon> donjons = new ArrayList<>(DonjonManager.getDonjons().values());
        int size = donjons.isEmpty() ? 9 : Math.min(54, (int) Math.ceil(donjons.size() / 9.0) * 9);

        this.inventory = Bukkit.createInventory(this, size,
                Component.text("⚓ Smuggler's Chart", NamedTextColor.AQUA));

        UUID playerUUID = player.getUniqueId();
        for (int i = 0; i < donjons.size() && i < size; i++) {
            Donjon donjon = donjons.get(i);
            donjonIds.add(donjon.getId());
            boolean visited = DonjonManager.hasPlayerVisitedDonjon(playerUUID, donjon.getId());
            this.inventory.setItem(i, buildDonjonItem(donjon, visited));
        }

        GuiUtil.fillGray(this.inventory);
    }

    public void open(Player player) {
        player.openInventory(this.inventory);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= donjonIds.size()) return;

        String donjonId = donjonIds.get(slot);
        Donjon donjon = DonjonManager.getDonjon(donjonId);
        if (donjon == null) return;

        UUID uuid = player.getUniqueId();

        if (!DonjonManager.hasPlayerVisitedDonjon(uuid, donjonId)) {
            player.sendMessage(Component.text(
                            "You must visit ", NamedTextColor.RED)
                    .append(Component.text(donjon.getName(), donjon.getRarity().getColor()))
                    .append(Component.text(" first before using the teleporter!", NamedTextColor.RED)));
            return;
        }

        if (!player.isOp() && SmugglerManager.isOnCooldown(uuid)) {
            long remaining = (SmugglerManager.getRemainingCooldown(uuid) + 999) / 1000;
            player.sendMessage(Component.text(
                    "The smuggler's map is on cooldown. " + remaining + "s remaining.",
                    NamedTextColor.RED));
            return;
        }

        player.closeInventory();
        Location dest = donjon.getTeleportSpawn() != null ? donjon.getTeleportSpawn() : donjon.getCenter();
        player.teleport(dest);
        if (!player.isOp()) SmugglerManager.startCooldown(uuid);
        player.sendMessage(Component.text("The smuggler has sent you to ", NamedTextColor.AQUA)
                .append(Component.text(donjon.getName(), donjon.getRarity().getColor()))
                .append(Component.text("!", NamedTextColor.AQUA)));
    }

    private static ItemStack buildDonjonItem(Donjon donjon, boolean visited) {
        boolean active = donjon.getStatus() == DonjonStatus.ACTIVE;
        Material mat = active ? Material.VAULT : Material.TRIAL_SPAWNER;

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(donjon.getName(), donjon.getRarity().getColor())
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(GuiUtil.loreLine("Type", donjon.getType().getDisplayName(), NamedTextColor.WHITE));
        lore.add(GuiUtil.loreLine("Level", String.valueOf(donjon.getLevel()), NamedTextColor.YELLOW));
        lore.add(GuiUtil.loreLine("Rarity", donjon.getRarity().getDisplayName(), donjon.getRarity().getColor()));
        lore.add(GuiUtil.loreLine("Status",
                active ? "Active" : "Idle",
                active ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
        lore.add(Component.empty());
        if (visited) {
            lore.add(Component.text("  ✦ Click to teleport", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("  ⚠ You must visit this dungeon first", NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  to unlock teleportation.", NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
