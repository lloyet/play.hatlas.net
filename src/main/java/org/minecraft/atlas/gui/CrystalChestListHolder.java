package org.minecraft.atlas.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.minecraft.atlas.Atlas;
import org.minecraft.atlas.faction.Faction;
import org.minecraft.atlas.faction.FactionLevelManager;
import org.minecraft.atlas.faction.FactionManager;
import org.minecraft.atlas.util.GuiUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CrystalChestListHolder implements AtlasHolder {

    private final String factionName;
    private final UUID crystalEntityUUID;
    private final Inventory inventory;

    public CrystalChestListHolder(Player player, Faction faction, UUID crystalEntityUUID) {
        this.factionName = faction.getName();
        this.crystalEntityUUID = crystalEntityUUID;

        int available = FactionLevelManager.getAvailableChests(faction.getLevel());
        this.inventory = Atlas.instance.getServer().createInventory(this, 27,
                Component.text(faction.getName() + " - Chests", faction.getColor()));

        int[] slots = GuiUtil.chestListSlots(available);
        for (int i = 0; i < available; i++) {
            this.inventory.setItem(slots[i], buildChestListItem(i, faction));
        }

        GuiUtil.fillGray(this.inventory);
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

        String fn = FactionManager.getPlayerFaction(player.getUniqueId());
        if (fn == null) { player.closeInventory(); return; }

        Faction faction  = FactionManager.getFaction(fn);
        int available    = FactionLevelManager.getAvailableChests(faction.getLevel());
        int[] chestSlots = GuiUtil.chestListSlots(available);

        int slot = event.getRawSlot();
        int chestIndex = -1;
        for (int i = 0; i < chestSlots.length; i++) {
            if (chestSlots[i] == slot) { chestIndex = i; break; }
        }
        if (chestIndex < 0) return;

        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
        CrystalChestViewHolder.open(player, faction, chestIndex, crystalEntityUUID);
    }

    // ── Item builder ──────────────────────────────────────────────────────────

    private static ItemStack buildChestListItem(int index, Faction faction) {
        ItemStack[] contents = faction.getChestContents(index);
        int chestSize = FactionLevelManager.getChestSize(index);
        int itemCount = 0;
        for (ItemStack stack : contents) {
            if (stack != null && stack.getType() != Material.AIR) itemCount++;
        }

        ItemStack item = new ItemStack(Material.BARREL);
        ItemMeta meta  = item.getItemMeta();
        meta.displayName(Component.text("Chest #" + (index + 1), NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(GuiUtil.loreLine("Items", itemCount + " / " + chestSize, NamedTextColor.WHITE));
        lore.add(Component.empty());
        lore.add(Component.text("  Click to open", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
