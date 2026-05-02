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
import org.minecraft.atlas.faction.FactionRole;
import org.minecraft.atlas.util.GuiUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class CrystalUpgradeGui implements AtlasGui {

    private final String factionName;
    private final UUID crystalEntityUUID;
    private final Inventory inventory;

    public CrystalUpgradeGui(Player player, Faction faction, UUID crystalEntityUUID) {
        this.factionName       = faction.getName();
        this.crystalEntityUUID = crystalEntityUUID;

        AtlasCrystal crystal = AtlasCrystalManager.getCrystal(crystalEntityUUID);

        this.inventory = Atlas.instance.getServer().createInventory(this, 54,
                Component.text(GuiUtil.truncateFactionName(faction.getName()) + " - Upgrades", NamedTextColor.GOLD));

        if (crystal != null) {
            int[] slots = GuiUtil.contentSlots54();
            List<Integer> upgradeLevels = FactionLevelManager.getUpgradeLevels();
            Set<Integer> applied        = crystal.getAppliedUpgrades();
            List<Integer> pending       = faction.getPendingUpgrades();
            int factionLevel            = faction.getLevel();

            for (int i = 0; i < upgradeLevels.size() && i < slots.length; i++) {
                int ul = upgradeLevels.get(i);
                this.inventory.setItem(slots[i], buildUpgradeItem(ul, applied.contains(ul),
                        pending.contains(ul), factionLevel >= ul));
            }
        }

        // Level-up button in bottom-center if a level-up is waiting for confirmation
        if (faction.isLevelUpReady()) {
            this.inventory.setItem(49, buildLevelUpButton(faction));
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

        int slot = event.getRawSlot();

        // Level-up button
        if (slot == SLOT_LEVEL_UP) {
            handleLevelUpClick(player);
            return;
        }

        int[] slots = GuiUtil.contentSlots54();
        int idx = -1;
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) { idx = i; break; }
        }
        if (idx < 0) return;

        List<Integer> upgradeLevels = FactionLevelManager.getUpgradeLevels();
        if (idx >= upgradeLevels.size()) return;

        int upgradeLevel = upgradeLevels.get(idx);

        String fn = FactionManager.getPlayerFaction(player.getUniqueId());
        if (fn == null) { player.closeInventory(); return; }

        Faction faction = FactionManager.getFaction(fn);
        if (!faction.getPendingUpgrades().contains(upgradeLevel)) return;

        boolean isOwner  = faction.getOwner().equals(player.getUniqueId());
        boolean isLeader = !isOwner && faction.getRole(player.getUniqueId()) == FactionRole.LEADER;
        if (!isOwner && !isLeader) {
            player.sendMessage(Component.text(
                    "Only the owner or a leader can apply upgrades.", NamedTextColor.RED));
            return;
        }

        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        new CrystalUpgradeConfirmGui(player, faction, crystalEntityUUID, upgradeLevel).open(player);
    }

    private static final int SLOT_LEVEL_UP = 49;

    private void handleLevelUpClick(Player player) {
        boolean isOwner  = false;
        boolean isLeader = false;
        String fn = FactionManager.getPlayerFaction(player.getUniqueId());
        if (fn != null) {
            Faction f = FactionManager.getFaction(fn);
            isOwner  = f.getOwner().equals(player.getUniqueId());
            isLeader = !isOwner && f.getRole(player.getUniqueId()) == FactionRole.LEADER;
        }
        if (!isOwner && !isLeader) {
            player.sendMessage(Component.text("Only the Owner or a Leader can confirm a level-up.", NamedTextColor.RED));
            return;
        }
        FactionManager.ApplyLevelUpResult result = FactionManager.applyLevelUp(player.getUniqueId());
        switch (result) {
            case SUCCESS -> {
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                Faction f = FactionManager.getFaction(fn);
                player.sendMessage(Component.text(
                        "Faction reached level " + f.getLevel() + "!", NamedTextColor.GOLD));
                if (f.hasPendingUpgrade()) {
                    player.sendMessage(Component.text(
                            "⚡ Upgrade available! Apply it to an Atlas Crystal.", NamedTextColor.LIGHT_PURPLE));
                }
                player.closeInventory();
            }
            case NOT_READY -> player.sendMessage(Component.text("Level-up not ready yet.", NamedTextColor.RED));
            case NO_PERMISSION -> player.sendMessage(Component.text("Only the Owner or a Leader can do this.", NamedTextColor.RED));
            case MAX_LEVEL -> player.sendMessage(Component.text("Your faction is already at max level.", NamedTextColor.YELLOW));
            default -> {}
        }
    }

    // ── Item builders ─────────────────────────────────────────────────────────

    private static ItemStack buildUpgradeItem(int upgradeLevel, boolean applied,
                                              boolean pending, boolean levelReached) {
        Material mat;
        NamedTextColor nameColor;
        String statusText;

        if (applied) {
            mat        = Material.ENCHANTED_BOOK;
            nameColor  = NamedTextColor.GREEN;
            statusText = "✔ Applied";
        } else if (pending) {
            mat        = Material.BOOK;
            nameColor  = NamedTextColor.YELLOW;
            statusText = "⚡ Pending — Click to Apply";
        } else if (levelReached) {
            mat        = Material.BOOK;
            nameColor  = NamedTextColor.GOLD;
            statusText = "⚠ Reached — pending list not updated";
        } else {
            mat        = Material.WRITABLE_BOOK;
            nameColor  = NamedTextColor.RED;
            statusText = "✘ Locked";
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta  = item.getItemMeta();
        meta.displayName(Component.text("Level " + upgradeLevel + " Upgrade", nameColor)
                .decoration(TextDecoration.ITALIC, false));

        double bonusHp     = FactionLevelManager.getUpgradeHp(upgradeLevel);
        int    bonusChests = FactionLevelManager.getUpgradeChests(upgradeLevel);
        int claimsGained = FactionLevelManager.getUpgradeClaims(upgradeLevel);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(GuiUtil.loreLine("Reach Level", String.valueOf(upgradeLevel), NamedTextColor.AQUA));
        lore.add(Component.empty());
        lore.add(Component.text("  Bonuses:", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(GuiUtil.loreLine("  Crystal HP", "+" + (int) bonusHp + " ♥", NamedTextColor.RED));
        lore.add(GuiUtil.loreLine("  Claims", "+" + claimsGained + " chunks", NamedTextColor.GREEN));
        if (bonusChests > 0) {
            int chestSlots = FactionLevelManager.getUpgradeChestSize(upgradeLevel);
            lore.add(GuiUtil.loreLine("  Chests", "+" + bonusChests + " (" + chestSlots + " slots)", NamedTextColor.YELLOW));
        }
        if (upgradeLevel == 21) {
            lore.add(GuiUtil.loreLine("  Outpost", "+1 Atlas Outpost", NamedTextColor.AQUA));
        }
        lore.add(Component.empty());
        lore.add(Component.text("  " + statusText, nameColor)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildLevelUpButton(Faction faction) {
        int nextLevel = faction.getLevel() + 1;
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("⬆ Level Up → Lv." + nextLevel, NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("  Exp threshold reached!", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        if (faction.getPendingLevelExp() > 0) {
            lore.add(GuiUtil.loreLine("  Carry-over exp", String.valueOf(faction.getPendingLevelExp()), NamedTextColor.AQUA));
        }
        lore.add(Component.empty());
        if (FactionLevelManager.isUpgrade(nextLevel)) {
            lore.add(Component.text("  ⚡ Unlocks an upgrade bonus!", NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
        }
        lore.add(Component.text("  Click to confirm level-up (owner/leader)", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
