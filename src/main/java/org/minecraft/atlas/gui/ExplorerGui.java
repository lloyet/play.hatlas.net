package org.minecraft.atlas.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.minecraft.atlas.Atlas;
import org.minecraft.atlas.safezone.SafeZone;
import org.minecraft.atlas.safezone.SafeZoneManager;
import org.minecraft.atlas.util.GuiUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ExplorerGui implements AtlasGui {

    private final Inventory inventory;
    private final List<SafeZone> entries = new ArrayList<>();

    public ExplorerGui(Player player) {
        UUID uuid = player.getUniqueId();
        for (SafeZone z : SafeZoneManager.getAll()) {
            if (z.getSpawnPoint() == null) continue;
            if (!SafeZoneManager.hasVisited(uuid, z.getName())) continue;
            entries.add(z);
        }

        this.inventory = Atlas.instance.getServer().createInventory(this, 54,
                Component.text("Explorer — Safe Zones", NamedTextColor.AQUA));

        int[] slots = GuiUtil.contentSlots54();
        for (int i = 0; i < entries.size() && i < slots.length; i++) {
            this.inventory.setItem(slots[i], buildZoneItem(entries.get(i)));
        }

        finishGui();
    }

    public void open(Player player) {
        player.openInventory(this.inventory);
    }

    @Override
    public @NotNull Inventory getInventory() { return inventory; }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        int slot = event.getRawSlot();

        if (slot == inventory.getSize() - 1) {
            player.closeInventory();
            return;
        }

        int[] slots = GuiUtil.contentSlots54();
        int idx = -1;
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) { idx = i; break; }
        }
        if (idx < 0 || idx >= entries.size()) return;

        SafeZone zone = entries.get(idx);
        Location dest = zone.getSpawnPoint();
        if (dest == null) {
            player.sendMessage(Component.text("This safe zone has no teleport point.", NamedTextColor.RED));
            return;
        }

        player.closeInventory();
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        player.teleport(dest);
        player.sendMessage(Component.text("Teleported to ", NamedTextColor.GREEN)
                .append(Component.text(SafeZoneManager.capitalizedName(zone.getName()), NamedTextColor.AQUA))
                .append(Component.text(".", NamedTextColor.GREEN)));
    }

    // ── Item builder ──────────────────────────────────────────────────────────

    private static ItemStack buildZoneItem(SafeZone zone) {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta  = item.getItemMeta();

        meta.displayName(Component.text(SafeZoneManager.capitalizedName(zone.getName()), NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));

        Location dest = zone.getSpawnPoint();
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        if (dest != null && dest.getWorld() != null) {
            lore.add(GuiUtil.loreLine("World", dest.getWorld().getName(), NamedTextColor.WHITE));
            lore.add(GuiUtil.loreLine("Location",
                    dest.getBlockX() + ", " + dest.getBlockY() + ", " + dest.getBlockZ(),
                    NamedTextColor.GRAY));
        }
        lore.add(Component.empty());
        lore.add(Component.text("  Click to teleport", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
