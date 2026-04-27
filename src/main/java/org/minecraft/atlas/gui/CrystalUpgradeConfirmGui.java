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
import org.minecraft.atlas.faction.AtlasCrystal;
import org.minecraft.atlas.faction.AtlasCrystalManager;
import org.minecraft.atlas.faction.Faction;
import org.minecraft.atlas.faction.FactionLevelManager;
import org.minecraft.atlas.faction.FactionManager;
import org.minecraft.atlas.util.GuiUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CrystalUpgradeConfirmGui implements AtlasGui {

    private final String factionName;
    private final UUID crystalEntityUUID;
    private final int upgradeLevel;
    private final Inventory inventory;

    public CrystalUpgradeConfirmGui(Player player, Faction faction, UUID crystalEntityUUID, int upgradeLevel) {
        this.factionName       = faction.getName();
        this.crystalEntityUUID = crystalEntityUUID;
        this.upgradeLevel      = upgradeLevel;

        this.inventory = Atlas.instance.getServer().createInventory(this, 27,
                Component.text(faction.getName() + " - Confirm Upgrade?", NamedTextColor.GOLD));

        ItemStack green = GuiUtil.labeledPane(Material.GREEN_STAINED_GLASS_PANE,
                Component.text("✔ Confirm", NamedTextColor.GREEN));
        ItemStack red   = GuiUtil.labeledPane(Material.RED_STAINED_GLASS_PANE,
                Component.text("✘ Cancel", NamedTextColor.RED));
        ItemStack gray  = GuiUtil.emptyPane();

        for (int slot : GuiUtil.CONFIRM_GREEN) this.inventory.setItem(slot, green);
        for (int slot : GuiUtil.CONFIRM_RED)   this.inventory.setItem(slot, red);
        this.inventory.setItem(4,  gray);
        this.inventory.setItem(22, gray);
        this.inventory.setItem(GuiUtil.SLOT_CONFIRM_INFO, buildConfirmInfoItem(upgradeLevel));
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

        int slot = event.getRawSlot();

        if (GuiUtil.CONFIRM_GREEN.contains(slot)) {
            FactionManager.ApplyUpgradeResult result =
                    FactionManager.applyUpgrade(player.getUniqueId(), upgradeLevel, crystalEntityUUID);

            switch (result) {
                case SUCCESS -> {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    double bonus = FactionLevelManager.getUpgradeHp(upgradeLevel);
                    player.sendMessage(
                            Component.text("Upgrade applied! Crystal gained ", NamedTextColor.GREEN)
                                    .append(Component.text("+" + (int) bonus + " ♥", NamedTextColor.RED))
                                    .append(Component.text(" max HP.", NamedTextColor.GREEN)));
                }
                case NO_PERMISSION ->
                    player.sendMessage(Component.text(
                            "You don't have permission to apply upgrades.", NamedTextColor.RED));
                case UPGRADE_NOT_PENDING ->
                    player.sendMessage(Component.text(
                            "This upgrade is no longer pending.", NamedTextColor.YELLOW));
                case CRYSTAL_NOT_FOUND ->
                    player.sendMessage(Component.text(
                            "Crystal not found. Make sure it is still placed.", NamedTextColor.RED));
                default -> {}
            }
            player.closeInventory();

        } else if (GuiUtil.CONFIRM_RED.contains(slot)) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);
            String fn = FactionManager.getPlayerFaction(player.getUniqueId());
            if (fn == null) { player.closeInventory(); return; }
            Faction faction  = FactionManager.getFaction(fn);
            AtlasCrystal crystal = AtlasCrystalManager.getCrystal(crystalEntityUUID);
            if (crystal == null) { player.closeInventory(); return; }
            new CrystalUpgradeGui(player, faction, crystalEntityUUID).open(player);
        }
    }

    // ── Item builder ──────────────────────────────────────────────────────────

    private static ItemStack buildConfirmInfoItem(int upgradeLevel) {
        double bonusHp     = FactionLevelManager.getUpgradeHp(upgradeLevel);
        int    bonusChests = FactionLevelManager.getUpgradeChests(upgradeLevel);

        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta  = item.getItemMeta();
        meta.displayName(Component.text("Apply Lv." + upgradeLevel + " Upgrade?", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("  Bonuses:", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(GuiUtil.loreLine("  Crystal HP", "+" + (int) bonusHp + " ♥", NamedTextColor.RED));
        if (bonusChests > 0) {
            lore.add(GuiUtil.loreLine("  Chests", "+" + bonusChests, NamedTextColor.YELLOW));
        }
        lore.add(Component.empty());
        lore.add(Component.text("  This action is permanent.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
