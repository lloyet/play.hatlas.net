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
import org.minecraft.atlas.crystal.AtlasCrystal;
import org.minecraft.atlas.crystal.AtlasCrystalManager;
import org.minecraft.atlas.faction.Faction;
import org.minecraft.atlas.faction.FactionLevelManager;
import org.minecraft.atlas.faction.FactionManager;
import org.minecraft.atlas.util.GuiUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Skill-point upgrade GUI for an atlas crystal.
 * Layout (54 slots, 7 rows):
 *   Row 0 center (slot 4):            faction skill points info
 *   Row 1 (slots 9-17):               empty separator
 *   Row 2 (slots 20-23):              HP tiers
 *   Row 3 (slots 28-30 | 32):         Claim tiers (left) | Outpost (right)
 *   Row 4 (slots 36-44):              empty separator
 *   Row 5 (slots 46-48 | 50-53):      Chest tiers | Protection tiers
 *   Row 6 (slots 54-62):              empty  |  62: back
 */
public class CrystalSkillListGui implements AtlasGui {

    private final String factionName;
    private final UUID crystalEntityUUID;
    private final Inventory inventory;

    // Slot assignments
    private static final int   SLOT_SP_INFO     = 4;
    private static final int[] SLOTS_HP         = {10, 11, 12, 13};   // row 2, cols 2-5
    private static final int[] SLOTS_CLAIMS     = {19, 20, 21};        // row 3, cols 1-3
    private static final int   SLOT_OUTPOST     = 46;                  // row 3, col 5
    private static final int[] SLOTS_CHEST      = {28, 29, 30};        // row 5, cols 1-3
    private static final int[] SLOTS_PROTECTION = {37, 38, 39, 40};   // row 5, cols 5-8

    public CrystalSkillListGui(Player player, Faction faction, UUID crystalEntityUUID) {
        this.factionName       = faction.getName();
        this.crystalEntityUUID = crystalEntityUUID;

        this.inventory = Atlas.instance.getServer().createInventory(this, 54,
                Component.text("Skills - " + GuiUtil.truncateFactionName(faction.getName()), NamedTextColor.LIGHT_PURPLE));

        int sp = faction.getSkillPoints();

        // Skill points info item
        this.inventory.setItem(SLOT_SP_INFO, buildSpInfoItem(faction));

        // HP upgrades
        List<FactionLevelManager.HpTier> hpTiers = FactionLevelManager.getHpTiers();
        for (int i = 0; i < hpTiers.size() && i < SLOTS_HP.length; i++) {
            this.inventory.setItem(SLOTS_HP[i], buildHpTierItem(hpTiers.get(i), i, sp));
        }

        // Claim upgrades
        List<FactionLevelManager.ClaimTier> claimTiers = FactionLevelManager.getClaimTiers();
        for (int i = 0; i < claimTiers.size() && i < SLOTS_CLAIMS.length; i++) {
            this.inventory.setItem(SLOTS_CLAIMS[i], buildClaimTierItem(claimTiers.get(i), i, sp));
        }

        // Chest upgrades
        List<FactionLevelManager.ChestTier> chestTiers = FactionLevelManager.getChestTiers();
        for (int i = 0; i < chestTiers.size() && i < SLOTS_CHEST.length; i++) {
            this.inventory.setItem(SLOTS_CHEST[i], buildChestTierItem(chestTiers.get(i), i, sp));
        }

        // Protection upgrades — pass the crystal so already-purchased tiers render as "owned".
        AtlasCrystal protCrystal = AtlasCrystalManager.getCrystal(crystalEntityUUID);
        List<FactionLevelManager.ProtectionTier> protTiers = FactionLevelManager.getProtectionTiers();
        for (int i = 0; i < protTiers.size() && i < SLOTS_PROTECTION.length; i++) {
            this.inventory.setItem(SLOTS_PROTECTION[i],
                    buildProtectionTierItem(protTiers.get(i), i, sp, protCrystal));
        }

        // Outpost skill (one-time unlock)
        this.inventory.setItem(SLOT_OUTPOST, buildOutpostItem(FactionLevelManager.getOutpostTier(), sp, faction.isOutpostUnlocked()));

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
            GuiNavigator.back(player);
            return;
        }

        String fn = FactionManager.getPlayerFaction(player.getUniqueId());
        if (fn == null) { player.closeInventory(); return; }
        Faction faction = FactionManager.getFaction(fn);

        // HP upgrade slots
        for (int i = 0; i < SLOTS_HP.length; i++) {
            if (slot == SLOTS_HP[i]) {
                List<FactionLevelManager.HpTier> tiers = FactionLevelManager.getHpTiers();
                if (i >= tiers.size()) return;
                FactionLevelManager.HpTier tier = tiers.get(i);
                if (faction.getSkillPoints() < tier.cost()) {
                    player.sendMessage(Component.text("Not enough skill points (need " + tier.cost() + " SP).", NamedTextColor.RED));
                    return;
                }
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                GuiNavigator.push(player.getUniqueId(), this);
                new CrystalSkillConfirmGui(player, fn, crystalEntityUUID,
                        CrystalSkillConfirmGui.SkillPurchaseType.HP, i).open(player);
                return;
            }
        }

        // Claim upgrade slots
        for (int i = 0; i < SLOTS_CLAIMS.length; i++) {
            if (slot == SLOTS_CLAIMS[i]) {
                List<FactionLevelManager.ClaimTier> tiers = FactionLevelManager.getClaimTiers();
                if (i >= tiers.size()) return;
                FactionLevelManager.ClaimTier tier = tiers.get(i);
                if (faction.getSkillPoints() < tier.cost()) {
                    player.sendMessage(Component.text("Not enough skill points (need " + tier.cost() + " SP).", NamedTextColor.RED));
                    return;
                }
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                GuiNavigator.push(player.getUniqueId(), this);
                new CrystalSkillConfirmGui(player, fn, crystalEntityUUID,
                        CrystalSkillConfirmGui.SkillPurchaseType.CLAIMS, i).open(player);
                return;
            }
        }

        // Chest upgrade slots
        for (int i = 0; i < SLOTS_CHEST.length; i++) {
            if (slot == SLOTS_CHEST[i]) {
                List<FactionLevelManager.ChestTier> tiers = FactionLevelManager.getChestTiers();
                if (i >= tiers.size()) return;
                FactionLevelManager.ChestTier tier = tiers.get(i);
                if (faction.getSkillPoints() < tier.cost()) {
                    player.sendMessage(Component.text("Not enough skill points (need " + tier.cost() + " SP).", NamedTextColor.RED));
                    return;
                }
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                GuiNavigator.push(player.getUniqueId(), this);
                new CrystalSkillConfirmGui(player, fn, crystalEntityUUID,
                        CrystalSkillConfirmGui.SkillPurchaseType.CHEST, i).open(player);
                return;
            }
        }

        // Protection upgrade slots
        for (int i = 0; i < SLOTS_PROTECTION.length; i++) {
            if (slot == SLOTS_PROTECTION[i]) {
                List<FactionLevelManager.ProtectionTier> tiers = FactionLevelManager.getProtectionTiers();
                if (i >= tiers.size()) return;
                FactionLevelManager.ProtectionTier tier = tiers.get(i);
                AtlasCrystal target = AtlasCrystalManager.getCrystal(crystalEntityUUID);
                if (target != null && target.hasPurchasedProtection(tier.durationMs())) {
                    player.sendMessage(Component.text(
                            "This protection tier is already purchased on this crystal.", NamedTextColor.YELLOW));
                    return;
                }
                if (faction.getSkillPoints() < tier.cost()) {
                    player.sendMessage(Component.text("Not enough skill points (need " + tier.cost() + " SP).", NamedTextColor.RED));
                    return;
                }
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                GuiNavigator.push(player.getUniqueId(), this);
                new CrystalSkillConfirmGui(player, fn, crystalEntityUUID,
                        CrystalSkillConfirmGui.SkillPurchaseType.PROTECTION, i).open(player);
                return;
            }
        }

        // Outpost skill slot
        if (slot == SLOT_OUTPOST) {
            if (faction.isOutpostUnlocked()) {
                player.sendMessage(Component.text("Outpost skill is already unlocked!", NamedTextColor.GOLD));
                return;
            }
            FactionLevelManager.OutpostTier tier = FactionLevelManager.getOutpostTier();
            if (faction.getSkillPoints() < tier.cost()) {
                player.sendMessage(Component.text("Not enough skill points (need " + tier.cost() + " SP).", NamedTextColor.RED));
                return;
            }
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            GuiNavigator.push(player.getUniqueId(), this);
            new CrystalSkillConfirmGui(player, fn, crystalEntityUUID,
                    CrystalSkillConfirmGui.SkillPurchaseType.OUTPOST, 0).open(player);
        }
    }

    // ── Item builders ─────────────────────────────────────────────────────────

    private static ItemStack buildOutpostItem(FactionLevelManager.OutpostTier tier, int availableSp, boolean unlocked) {
        ItemStack item = new ItemStack(Material.END_CRYSTAL);
        ItemMeta meta = item.getItemMeta();
        NamedTextColor color = unlocked ? NamedTextColor.AQUA : (availableSp >= tier.cost() ? NamedTextColor.GREEN : NamedTextColor.RED);
        if (unlocked) meta.setEnchantmentGlintOverride(true);
        meta.displayName(Component.text("Outpost Skill", color)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("  Unlocks the /faction outpost command,", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  allowing placement of an outpost crystal.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        if (unlocked) {
            lore.add(Component.text("  ✔ Already unlocked", NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(GuiUtil.loreLine("Cost", tier.cost() + " SP", NamedTextColor.LIGHT_PURPLE));
            lore.add(Component.empty());
            lore.add(Component.text(availableSp >= tier.cost() ? "  Click to purchase" : "  Not enough skill points", color)
                    .decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildSpInfoItem(Faction faction) {
        int sp = faction.getSkillPoints();
        int level = faction.getLevel();
        int exp = faction.getExp();
        int nextExp = FactionLevelManager.getExpRequiredForLevel(level + 1);

        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Skill Points", NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(GuiUtil.loreLine("Available SP", String.valueOf(sp),
                sp > 0 ? NamedTextColor.GREEN : NamedTextColor.RED));
        lore.add(GuiUtil.loreLine("Faction Level", "Lv." + level, NamedTextColor.YELLOW));
        if (level < FactionLevelManager.MAX_LEVEL) {
            lore.add(GuiUtil.loreLine("EXP", exp + " / " + nextExp, NamedTextColor.AQUA));
        } else {
            lore.add(GuiUtil.loreLine("EXP", "MAX LEVEL", NamedTextColor.GOLD));
        }
        lore.add(Component.empty());
        lore.add(Component.text("  Earn SP by leveling up your faction.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  Spend SP on upgrades below.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildHpTierItem(FactionLevelManager.HpTier tier, int index, int availableSp) {
        boolean canAfford = availableSp >= tier.cost();
        NamedTextColor color = canAfford ? NamedTextColor.GREEN : NamedTextColor.RED;

        Material mat;
        boolean enchanted;
        if (tier.bonus() >= 200) {
            mat = Material.ENCHANTED_GOLDEN_APPLE;
            enchanted = true;
        } else if (tier.bonus() >= 100) {
            mat = Material.GOLDEN_APPLE;
            enchanted = false;
        } else {
            mat = Material.APPLE;
            enchanted = false;
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (enchanted) meta.setEnchantmentGlintOverride(true);
        meta.displayName(Component.text("HP Upgrade #" + (index + 1), color)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(GuiUtil.loreLine("Bonus HP",   "+" + (int) tier.bonus() + " ♥", NamedTextColor.RED));
        lore.add(GuiUtil.loreLine("Cost",       tier.cost() + " SP", NamedTextColor.LIGHT_PURPLE));
        lore.add(Component.empty());
        lore.add(Component.text(canAfford ? "  Click to purchase" : "  Not enough skill points", color)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildClaimTierItem(FactionLevelManager.ClaimTier tier, int index, int availableSp) {
        boolean canAfford = availableSp >= tier.cost();
        NamedTextColor color = canAfford ? NamedTextColor.GREEN : NamedTextColor.RED;

        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Claim Upgrade #" + (index + 1), color)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(GuiUtil.loreLine("Bonus Claims", "+" + tier.amount() + " chunks", NamedTextColor.GREEN));
        lore.add(GuiUtil.loreLine("Cost", tier.cost() + " SP", NamedTextColor.LIGHT_PURPLE));
        lore.add(Component.empty());
        lore.add(Component.text(canAfford ? "  Click to purchase" : "  Not enough skill points", color)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildChestTierItem(FactionLevelManager.ChestTier tier, int index, int availableSp) {
        boolean canAfford = availableSp >= tier.cost();
        NamedTextColor color = canAfford ? NamedTextColor.GREEN : NamedTextColor.RED;
        Material mat;
        if (tier.size() > 64) mat = Material.ENDER_CHEST;
        else if (tier.size() >= 54) mat = Material.OXIDIZED_COPPER_CHEST;
        else if (tier.size() >= 27) mat = Material.COPPER_CHEST;
        else mat = Material.CHEST;

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Chest Upgrade #" + (index + 1), color)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(GuiUtil.loreLine("Chest Size", tier.size() + " slots",
                tier.size() >= 54 ? NamedTextColor.GOLD : NamedTextColor.YELLOW));
        lore.add(GuiUtil.loreLine("Cost", tier.cost() + " SP", NamedTextColor.LIGHT_PURPLE));
        lore.add(Component.empty());
        lore.add(Component.text(canAfford ? "  Click to purchase" : "  Not enough skill points", color)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildProtectionTierItem(FactionLevelManager.ProtectionTier tier, int index,
                                                     int availableSp, AtlasCrystal crystal) {
        long ms   = tier.durationMs();
        long secs = ms / 1000L;
        String timeStr = secs >= 3600 ? (secs / 3600) + "h" : (secs / 60) + "m";

        boolean owned     = crystal != null && crystal.hasPurchasedProtection(ms);
        boolean broken    = owned && crystal.isProtectionBroken(ms);
        boolean canAfford = availableSp >= tier.cost();
        NamedTextColor color =
                owned     ? NamedTextColor.AQUA  :
                canAfford ? NamedTextColor.GREEN : NamedTextColor.RED;

        long mins = ms / 60_000L;
        Material mat;
        boolean enchanted = false;
        if (mins <= 30) {
            mat = Material.LEATHER_CHESTPLATE;
        } else if (mins <= 60) {
            mat = Material.IRON_CHESTPLATE;
        } else if (mins <= 120) {
            mat = Material.GOLDEN_CHESTPLATE;
        } else if (mins <= 240) {
            mat = Material.DIAMOND_CHESTPLATE;
        } else if (mins <= 480) {
            mat = Material.NETHERITE_CHESTPLATE;
        } else {
            mat = Material.NETHERITE_CHESTPLATE;
            enchanted = true;
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (enchanted || owned) meta.setEnchantmentGlintOverride(true);
        meta.displayName(Component.text("Protection Upgrade #" + (index + 1), color)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(GuiUtil.loreLine("Duration", timeStr + " immunity", NamedTextColor.AQUA));
        lore.add(GuiUtil.loreLine("Cost", tier.cost() + " SP", NamedTextColor.LIGHT_PURPLE));
        lore.add(Component.empty());
        if (owned) {
            lore.add(Component.text("  ✔ Already purchased", NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            if (broken) {
                lore.add(Component.text("  Currently broken — regenerating", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
        } else {
            lore.add(Component.text("  Each tier can be purchased once.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(canAfford ? "  Click to purchase" : "  Not enough skill points", color)
                    .decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
