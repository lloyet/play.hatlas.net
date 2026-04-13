package org.minecraft.atlas.faction;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
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

public class CrystalGui implements Listener {

    // -------------------------------------------------------------------------
    // Screen tracking
    // -------------------------------------------------------------------------

    private enum Screen { MAIN, UPGRADE, CONFIRM, COLOR, CHEST_LIST, CHEST_VIEW, DISBAND_CONFIRM }

    /** Tracks which menu screen each player currently has open. */
    private static final Map<UUID, Screen> activeScreen = new HashMap<>();

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    /** Players who currently have one of the managed GUIs open. */
    private static final Set<UUID>          managedMenus       = new HashSet<>();
    /** playerUUID → entity UUID of the crystal that was right-clicked to open the GUI. */
    private static final Map<UUID, UUID>    activeCrystalUUID  = new HashMap<>();
    /** UUID → upgrade level pending confirmation. */
    private static final Map<UUID, Integer> pendingConfirm    = new HashMap<>();
    /** UUID → chest index currently open in CHEST_VIEW. */
    private static final Map<UUID, Integer> activeChestIndex  = new HashMap<>();

    /**
     * Key "factionName:chestIndex" → shared Inventory instance.
     * When multiple players open the same chest the same object is reused so
     * edits are immediately visible to all viewers.
     */
    private static final Map<String, Inventory>    openChestInventories = new HashMap<>();
    /** Key "factionName:chestIndex" → set of player UUIDs currently viewing that chest. */
    private static final Map<String, Set<UUID>>    chestViewers         = new HashMap<>();

    // -------------------------------------------------------------------------
    // Main-menu slot indices (54-slot double-chest inventory)
    // Row 1 (info):   col 2 = CRYSTAL_INFO, col 4 = EXP_INFO,   col 6 = COLOR_INFO
    // Row 3 (action): col 3 = CHEST_BTN,    col 5 = UPGRADES_BTN
    // Each interactive slot has ≥1 empty slot on every side.
    // -------------------------------------------------------------------------

    private static final int SLOT_CRYSTAL_INFO = 11;  // row 1, col 2
    private static final int SLOT_EXP_INFO     = 13;  // row 1, col 4
    private static final int SLOT_COLOR_INFO   = 15;  // row 1, col 6
    private static final int SLOT_CHEST_BTN    = 30;  // row 3, col 3
    private static final int SLOT_UPGRADES_BTN = 32;  // row 3, col 5

    // -------------------------------------------------------------------------
    // Confirmation-menu slot sets (27-slot inventory)
    // -------------------------------------------------------------------------

    private static final Set<Integer> CONFIRM_GREEN =
            Set.of(0, 1, 2, 3, 9, 10, 11, 12, 18, 19, 20, 21);
    private static final Set<Integer> CONFIRM_RED =
            Set.of(5, 6, 7, 8, 14, 15, 16, 17, 23, 24, 25, 26);
    private static final int SLOT_CONFIRM_INFO = 13;

    // -------------------------------------------------------------------------
    // Color picker — all 16 faction colors in display order (slots 0–15)
    // -------------------------------------------------------------------------

    private static final NamedTextColor[] ALL_COLORS = {
        NamedTextColor.WHITE,        NamedTextColor.GRAY,       NamedTextColor.DARK_GRAY,  NamedTextColor.BLACK,
        NamedTextColor.YELLOW,       NamedTextColor.GOLD,       NamedTextColor.RED,        NamedTextColor.DARK_RED,
        NamedTextColor.GREEN,        NamedTextColor.DARK_GREEN, NamedTextColor.AQUA,       NamedTextColor.DARK_AQUA,
        NamedTextColor.BLUE,         NamedTextColor.DARK_BLUE,  NamedTextColor.LIGHT_PURPLE, NamedTextColor.DARK_PURPLE
    };

    // -------------------------------------------------------------------------
    // Open trigger — right-click own faction crystal
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractCrystal(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof EnderCrystal entity)) return;

        AtlasCrystal crystal = AtlasCrystalManager.getCrystal(entity.getUniqueId());
        if (crystal == null) return;

        Player player = event.getPlayer();
        String playerFaction = FactionManager.getPlayerFaction(player.getUniqueId());
        if (playerFaction == null) return;
        if (!playerFaction.equals(crystal.getFactionName())) return;

        event.setCancelled(true);

        Faction faction = FactionManager.getFaction(playerFaction);
        managedMenus.add(player.getUniqueId());
        activeCrystalUUID.put(player.getUniqueId(), crystal.getEntity().getUniqueId());
        openMainMenu(player, faction, crystal);
    }

    // -------------------------------------------------------------------------
    // Menu builders
    // -------------------------------------------------------------------------

    private static void openMainMenu(Player player, Faction faction, AtlasCrystal crystal) {
        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text(faction.getName(), faction.getColor()));

        inv.setItem(SLOT_CHEST_BTN,    buildChestButton(faction));
        inv.setItem(SLOT_CRYSTAL_INFO, buildCrystalInfoItem(faction, crystal));
        inv.setItem(SLOT_EXP_INFO,     buildExpItem(faction));
        inv.setItem(SLOT_COLOR_INFO,   buildColorItem(faction));
        inv.setItem(SLOT_UPGRADES_BTN, buildUpgradesButton(faction));

        fillGray(inv);
        activeScreen.put(player.getUniqueId(), Screen.MAIN);
        player.openInventory(inv);
        managedMenus.add(player.getUniqueId());
    }

    private static void openUpgradeMenu(Player player, Faction faction, AtlasCrystal crystal) {
        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text(faction.getName() + " - Upgrades", NamedTextColor.GOLD));

        int[] slots = contentSlots54();
        List<Integer> upgradeLevels = FactionLevelManager.getUpgradeLevels();
        Set<Integer> applied = crystal.getAppliedUpgrades();
        List<Integer> pending = faction.getPendingUpgrades();
        int factionLevel = faction.getLevel();

        for (int i = 0; i < upgradeLevels.size() && i < slots.length; i++) {
            int ul = upgradeLevels.get(i);
            inv.setItem(slots[i], buildUpgradeItem(ul, applied.contains(ul),
                    pending.contains(ul), factionLevel >= ul));
        }

        fillGray(inv);
        activeScreen.put(player.getUniqueId(), Screen.UPGRADE);
        player.openInventory(inv);
        managedMenus.add(player.getUniqueId());
    }

    private static void openConfirmMenu(Player player, Faction faction, int upgradeLevel) {
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text(faction.getName() + " - Confirm Upgrade?", NamedTextColor.GOLD));

        ItemStack green = labeledPane(Material.GREEN_STAINED_GLASS_PANE,
                Component.text("✔ Confirm", NamedTextColor.GREEN));
        ItemStack red   = labeledPane(Material.RED_STAINED_GLASS_PANE,
                Component.text("✘ Cancel",  NamedTextColor.RED));
        ItemStack gray  = emptyPane();

        for (int slot : CONFIRM_GREEN) inv.setItem(slot, green);
        for (int slot : CONFIRM_RED)   inv.setItem(slot, red);

        inv.setItem(4,  gray);
        inv.setItem(22, gray);
        inv.setItem(SLOT_CONFIRM_INFO, buildConfirmInfoItem(upgradeLevel));

        activeScreen.put(player.getUniqueId(), Screen.CONFIRM);
        player.openInventory(inv);
        managedMenus.add(player.getUniqueId());
    }

    private static void openColorMenu(Player player, Faction faction) {
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text(faction.getName() + " - Color", NamedTextColor.GOLD));

        for (int i = 0; i < ALL_COLORS.length; i++) {
            inv.setItem(i, buildColorPickerItem(ALL_COLORS[i], ALL_COLORS[i].equals(faction.getColor())));
        }

        fillGray(inv);
        activeScreen.put(player.getUniqueId(), Screen.COLOR);
        player.openInventory(inv);
        managedMenus.add(player.getUniqueId());
    }

    private static void openChestListMenu(Player player, Faction faction) {
        int available = FactionLevelManager.getAvailableChests(faction.getLevel());
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text(faction.getName() + " - Chests", faction.getColor()));

        int[] slots = chestListSlots(available);
        for (int i = 0; i < available; i++) {
            inv.setItem(slots[i], buildChestListItem(i, faction));
        }

        fillGray(inv);
        activeScreen.put(player.getUniqueId(), Screen.CHEST_LIST);
        player.openInventory(inv);
        managedMenus.add(player.getUniqueId());
    }

    private static void openChestViewMenu(Player player, Faction faction, int chestIndex) {
        String key = faction.getName() + ":" + chestIndex;

        // Reuse shared inventory if another faction member already has this chest open
        Inventory inv = openChestInventories.get(key);
        if (inv == null) {
            inv = Bukkit.createInventory(null, 54,
                    Component.text(faction.getName() + " - Chest #" + (chestIndex + 1), faction.getColor()));
            inv.setContents(faction.getChestContents(chestIndex));
            openChestInventories.put(key, inv);
        }

        chestViewers.computeIfAbsent(key, k -> new HashSet<>()).add(player.getUniqueId());
        activeChestIndex.put(player.getUniqueId(), chestIndex);
        activeScreen.put(player.getUniqueId(), Screen.CHEST_VIEW);
        player.openInventory(inv);
        managedMenus.add(player.getUniqueId());
    }

    // -------------------------------------------------------------------------
    // Item builders
    // -------------------------------------------------------------------------

    private static ItemStack buildChestButton(Faction faction) {
        int available = FactionLevelManager.getAvailableChests(faction.getLevel());
        ItemStack item = new ItemStack(available > 0 ? Material.BARREL : Material.CHEST);
        ItemMeta meta  = item.getItemMeta();
        meta.displayName(Component.text("Faction Chests", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(loreLine("Available", String.valueOf(available),
                available > 0 ? NamedTextColor.GREEN : NamedTextColor.RED));
        lore.add(Component.empty());
        lore.add(Component.text(available > 0
                        ? "  Click to open chests"
                        : "  Reach an upgrade level to unlock",
                        NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildChestListItem(int index, Faction faction) {
        ItemStack[] contents = faction.getChestContents(index);
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
        lore.add(loreLine("Items", itemCount + " / 54", NamedTextColor.WHITE));
        lore.add(Component.empty());
        lore.add(Component.text("  Click to open", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildCrystalInfoItem(Faction faction, AtlasCrystal crystal) {
        ItemStack item = new ItemStack(Material.END_CRYSTAL);
        ItemMeta meta  = item.getItemMeta();

        String displayName = crystal.getName().isEmpty() ? faction.getName() : crystal.getName();
        meta.displayName(Component.text(displayName, faction.getColor())
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(loreLine("Faction", faction.getName(), faction.getColor()));
        lore.add(loreLine("Level",   "Lv." + faction.getLevel(), NamedTextColor.YELLOW));
        lore.add(loreLine("HP",      (int) crystal.getHp() + " / " + (int) crystal.getMaxHp() + " ♥",
                NamedTextColor.RED));

        if (crystal.isImmune()) {
            long remainingSec = (crystal.getImmuneUntilMillis() - System.currentTimeMillis()) / 1000;
            lore.add(loreLine("Immune", remainingSec + "s", NamedTextColor.AQUA));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildExpItem(Faction faction) {
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta  = item.getItemMeta();
        meta.displayName(Component.text("Faction Experience", NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));

        int level      = faction.getLevel();
        int currentExp = faction.getExp();
        int nextExp    = FactionLevelManager.getExpRequiredForLevel(level + 1);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(loreLine("Current EXP", String.valueOf(currentExp), NamedTextColor.GREEN));
        if (level < FactionLevelManager.MAX_LEVEL) {
            lore.add(loreLine("Next Level", nextExp + " EXP needed", NamedTextColor.YELLOW));
        } else {
            lore.add(Component.text("  MAX LEVEL REACHED", NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildColorItem(Faction faction) {
        ItemStack item = new ItemStack(colorToTerracotta(faction.getColor()));
        ItemMeta meta  = item.getItemMeta();
        meta.displayName(Component.text("Faction Color", NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("  " + colorDisplayName(faction.getColor()), faction.getColor())
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("  Click to change color", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildColorPickerItem(NamedTextColor color, boolean selected) {
        ItemStack item = new ItemStack(colorToTerracotta(color));
        ItemMeta meta  = item.getItemMeta();

        meta.displayName(Component.text(colorDisplayName(color),
                selected ? NamedTextColor.YELLOW : color)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        if (selected) {
            lore.add(Component.text("  ✔ Current color", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("  Click to select", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildUpgradesButton(Faction faction) {
        int pendingCount = faction.getPendingUpgrades().size();
        NamedTextColor nameColor = pendingCount > 0 ? NamedTextColor.YELLOW : NamedTextColor.WHITE;

        ItemStack item = new ItemStack(Material.ANVIL);
        ItemMeta meta  = item.getItemMeta();
        meta.displayName(Component.text("Upgrades", nameColor)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(loreLine("Pending", String.valueOf(pendingCount),
                pendingCount > 0 ? NamedTextColor.YELLOW : NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("  Click to view all upgrades", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

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

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(loreLine("Reach Level", String.valueOf(upgradeLevel), NamedTextColor.AQUA));
        lore.add(Component.empty());
        lore.add(Component.text("  Bonuses:", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(loreLine("  Crystal HP", "+" + (int) bonusHp + " ♥", NamedTextColor.RED));
        if (bonusChests > 0) {
            lore.add(loreLine("  Chests", "+" + bonusChests, NamedTextColor.YELLOW));
        }
        lore.add(Component.empty());
        lore.add(Component.text("  " + statusText, nameColor)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

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
        lore.add(loreLine("  Crystal HP", "+" + (int) bonusHp + " ♥", NamedTextColor.RED));
        if (bonusChests > 0) {
            lore.add(loreLine("  Chests", "+" + bonusChests, NamedTextColor.YELLOW));
        }
        lore.add(Component.empty());
        lore.add(Component.text("  This action is permanent.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // -------------------------------------------------------------------------
    // Click handling
    // -------------------------------------------------------------------------

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!managedMenus.contains(player.getUniqueId())) return;

        Screen screen = activeScreen.get(player.getUniqueId());
        if (screen == null) return;

        // Chest view: allow free inventory interaction — do not cancel
        if (screen == Screen.CHEST_VIEW) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        int slot = event.getRawSlot();
        switch (screen) {
            case MAIN            -> handleMainClick(player, slot);
            case UPGRADE         -> handleUpgradeClick(player, slot);
            case CONFIRM         -> handleConfirmClick(player, slot);
            case COLOR           -> handleColorClick(player, slot);
            case CHEST_LIST      -> handleChestListClick(player, slot);
            case DISBAND_CONFIRM -> handleDisbandConfirmClick(player, slot);
            default -> {}
        }
    }

    private void handleMainClick(Player player, int slot) {
        String factionName = FactionManager.getPlayerFaction(player.getUniqueId());
        if (factionName == null) { player.closeInventory(); return; }

        Faction faction = FactionManager.getFaction(factionName);

        if (slot == SLOT_CHEST_BTN) {
            int available = FactionLevelManager.getAvailableChests(faction.getLevel());
            if (available == 0) {
                player.sendMessage(Component.text(
                        "Your faction has no chests yet. Reach an upgrade level to unlock one.",
                        NamedTextColor.YELLOW));
                return;
            }
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
            openChestListMenu(player, faction);
            return;
        }

        if (slot == SLOT_COLOR_INFO) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            openColorMenu(player, faction);
            return;
        }

        if (slot == SLOT_UPGRADES_BTN) {
            UUID crystalEntityUUID = activeCrystalUUID.get(player.getUniqueId());
            AtlasCrystal crystal   = AtlasCrystalManager.getCrystal(crystalEntityUUID);
            if (crystal == null) {
                player.sendMessage(Component.text("Crystal not found in this faction.", NamedTextColor.RED));
                player.closeInventory();
                return;
            }
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            openUpgradeMenu(player, faction, crystal);
        }
    }

    private void handleChestListClick(Player player, int slot) {
        String factionName = FactionManager.getPlayerFaction(player.getUniqueId());
        if (factionName == null) { player.closeInventory(); return; }

        Faction faction   = FactionManager.getFaction(factionName);
        int available     = FactionLevelManager.getAvailableChests(faction.getLevel());
        int[] chestSlots  = chestListSlots(available);

        int chestIndex = -1;
        for (int i = 0; i < chestSlots.length; i++) {
            if (chestSlots[i] == slot) { chestIndex = i; break; }
        }
        if (chestIndex < 0) return; // filler slot clicked

        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
        openChestViewMenu(player, faction, chestIndex);
    }

    private void handleUpgradeClick(Player player, int slot) {
        int[] slots = contentSlots54();
        int idx = -1;
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) { idx = i; break; }
        }
        if (idx < 0) return;

        List<Integer> upgradeLevels = FactionLevelManager.getUpgradeLevels();
        if (idx >= upgradeLevels.size()) return;

        int upgradeLevel = upgradeLevels.get(idx);

        String factionName = FactionManager.getPlayerFaction(player.getUniqueId());
        if (factionName == null) { player.closeInventory(); return; }

        Faction faction = FactionManager.getFaction(factionName);
        if (!faction.getPendingUpgrades().contains(upgradeLevel)) return;

        boolean isOwner  = faction.getOwner().equals(player.getUniqueId());
        boolean isLeader = !isOwner && faction.getRole(player.getUniqueId()) == FactionRole.LEADER;
        if (!isOwner && !isLeader) {
            player.sendMessage(Component.text(
                    "Only the owner or a leader can apply upgrades.", NamedTextColor.RED));
            return;
        }

        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        pendingConfirm.put(player.getUniqueId(), upgradeLevel);
        openConfirmMenu(player, faction, upgradeLevel);
    }

    private void handleConfirmClick(Player player, int slot) {
        if (CONFIRM_GREEN.contains(slot)) {
            Integer upgradeLevel = pendingConfirm.remove(player.getUniqueId());
            if (upgradeLevel == null) { player.closeInventory(); return; }

            UUID crystalEntityUUID = activeCrystalUUID.get(player.getUniqueId());
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

        } else if (CONFIRM_RED.contains(slot)) {
            pendingConfirm.remove(player.getUniqueId());
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);

            String factionName = FactionManager.getPlayerFaction(player.getUniqueId());
            if (factionName == null) { player.closeInventory(); return; }

            Faction faction         = FactionManager.getFaction(factionName);
            UUID crystalEntityUUID = activeCrystalUUID.get(player.getUniqueId());
            AtlasCrystal crystal   = AtlasCrystalManager.getCrystal(crystalEntityUUID);
            if (crystal == null) { player.closeInventory(); return; }

            openUpgradeMenu(player, faction, crystal);
        }
    }

    // -------------------------------------------------------------------------
    // Disband confirmation
    // -------------------------------------------------------------------------

    /** Opens a standalone disband-confirmation GUI (no crystal required). */
    public static void openDisbandConfirmMenu(Player player, Faction faction) {
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text(faction.getName() + " - Confirm Disband?", NamedTextColor.RED));

        ItemStack green = labeledPane(Material.GREEN_STAINED_GLASS_PANE,
                Component.text("✔ Disband", NamedTextColor.GREEN));
        ItemStack red   = labeledPane(Material.RED_STAINED_GLASS_PANE,
                Component.text("✘ Cancel",  NamedTextColor.RED));

        ItemStack info = new ItemStack(Material.BARRIER);
        ItemMeta meta  = info.getItemMeta();
        meta.displayName(Component.text("⚠ Disband faction?", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("This action is permanent and", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("cannot be undone!", NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false)));
        info.setItemMeta(meta);

        for (int slot : CONFIRM_GREEN) inv.setItem(slot, green);
        for (int slot : CONFIRM_RED)   inv.setItem(slot, red);
        inv.setItem(4,  emptyPane());
        inv.setItem(22, emptyPane());
        inv.setItem(SLOT_CONFIRM_INFO, info);

        activeScreen.put(player.getUniqueId(), Screen.DISBAND_CONFIRM);
        player.openInventory(inv);
        managedMenus.add(player.getUniqueId());
    }

    private void handleDisbandConfirmClick(Player player, int slot) {
        if (CONFIRM_GREEN.contains(slot)) {
            String factionName = FactionManager.getPlayerFaction(player.getUniqueId());
            if (factionName == null) { player.closeInventory(); return; }
            Faction faction = FactionManager.getFaction(factionName);
            if (!faction.getOwner().equals(player.getUniqueId())) {
                player.sendMessage(Component.text("You are no longer the Owner.", NamedTextColor.RED));
                player.closeInventory();
                return;
            }
            player.closeInventory();
            FactionManager.broadcastToFaction(factionName,
                    Component.text("The faction has been disbanded by " + player.getName() + ".", NamedTextColor.RED),
                    player.getUniqueId());
            FactionManager.deleteFaction(player.getUniqueId());
            player.sendMessage(Component.text("Your faction has been disbanded.", NamedTextColor.GREEN));

        } else if (CONFIRM_RED.contains(slot)) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);
            player.closeInventory();
            player.sendMessage(Component.text("Disband cancelled.", NamedTextColor.YELLOW));
        }
    }

    private void handleColorClick(Player player, int slot) {
        if (slot < 0 || slot >= ALL_COLORS.length) return;

        if (!player.hasPermission("atlas.faction.color")) {
            player.sendMessage(Component.text(
                    "You don't have permission to change the faction color.", NamedTextColor.RED));
            return;
        }

        NamedTextColor chosen = ALL_COLORS[slot];
        boolean changed = FactionManager.setFactionColor(player.getUniqueId(), chosen);

        if (changed) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            player.sendMessage(Component.text("Faction color changed to ", NamedTextColor.GREEN)
                    .append(Component.text(colorDisplayName(chosen), chosen))
                    .append(Component.text(".", NamedTextColor.GREEN)));
        } else {
            player.sendMessage(Component.text(
                    "You don't have permission to change the faction color.", NamedTextColor.RED));
        }
        player.closeInventory();
    }

    // -------------------------------------------------------------------------
    // Close handling
    // -------------------------------------------------------------------------

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        if (!managedMenus.contains(uuid)) return;

        Screen screen = activeScreen.get(uuid);
        managedMenus.remove(uuid);

        // Always save chest contents and release the viewer slot when a chest view closes
        // (this applies even with OPEN_NEW so contents are persisted before any new menu opens)
        if (screen == Screen.CHEST_VIEW) {
            Integer chestIdx   = activeChestIndex.get(uuid);
            String factionName = FactionManager.getPlayerFaction(uuid);
            if (chestIdx != null && factionName != null) {
                Faction faction = FactionManager.getFaction(factionName);
                if (faction != null) {
                    faction.setChestContents(chestIdx, event.getInventory().getContents().clone());
                }
                String key = factionName + ":" + chestIdx;
                Set<UUID> viewers = chestViewers.get(key);
                if (viewers != null) {
                    viewers.remove(uuid);
                    if (viewers.isEmpty()) {
                        chestViewers.remove(key);
                        openChestInventories.remove(key);
                    }
                }
            }
        }

        // OPEN_NEW: navigating to another managed menu — keep crystal/confirm state alive
        if (event.getReason() == InventoryCloseEvent.Reason.OPEN_NEW) return;

        // Final close: full cleanup
        activeCrystalUUID.remove(uuid);
        pendingConfirm.remove(uuid);
        activeScreen.remove(uuid);
        activeChestIndex.remove(uuid);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns slot indices for the chest list, centered in the middle row (slots 9–17)
     * of a 27-slot inventory. For more than 9 chests, falls back to sequential slots from 0.
     */
    private static int[] chestListSlots(int count) {
        if (count <= 9) {
            int[] slots = new int[count];
            int start = 9 + (9 - count) / 2;
            for (int i = 0; i < count; i++) slots[i] = start + i;
            return slots;
        }
        int capped = Math.min(count, 27);
        int[] slots = new int[capped];
        for (int i = 0; i < capped; i++) slots[i] = i;
        return slots;
    }

    /** Fills all empty slots with transparent gray glass panes. */
    private static void fillGray(Inventory inv) {
        ItemStack pane = emptyPane();
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, pane);
        }
    }

    private static ItemStack emptyPane() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta  = pane.getItemMeta();
        meta.displayName(Component.empty());
        pane.setItemMeta(meta);
        return pane;
    }

    private static ItemStack labeledPane(Material mat, Component name) {
        ItemStack pane = new ItemStack(mat);
        ItemMeta meta  = pane.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        pane.setItemMeta(meta);
        return pane;
    }

    private static Component loreLine(String label, String value, NamedTextColor valueColor) {
        return Component.text("  " + label + ": ", NamedTextColor.GRAY)
                .append(Component.text(value, valueColor))
                .decoration(TextDecoration.ITALIC, false);
    }

    /**
     * Returns the interior content slots of a 54-slot inventory (rows 1–4, cols 1–7),
     * giving 28 usable slots in left-to-right, top-to-bottom order.
     */
    private static int[] contentSlots54() {
        List<Integer> list = new ArrayList<>();
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                list.add(row * 9 + col);
            }
        }
        return list.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Maps a {@link NamedTextColor} to the closest terracotta {@link Material}. */
    private static Material colorToTerracotta(NamedTextColor c) {
        if (c == NamedTextColor.WHITE)        return Material.WHITE_TERRACOTTA;
        if (c == NamedTextColor.BLACK)        return Material.BLACK_TERRACOTTA;
        if (c == NamedTextColor.DARK_BLUE)    return Material.BLUE_TERRACOTTA;
        if (c == NamedTextColor.DARK_GREEN)   return Material.GREEN_TERRACOTTA;
        if (c == NamedTextColor.DARK_AQUA)    return Material.CYAN_TERRACOTTA;
        if (c == NamedTextColor.DARK_RED)     return Material.RED_TERRACOTTA;
        if (c == NamedTextColor.DARK_PURPLE)  return Material.PURPLE_TERRACOTTA;
        if (c == NamedTextColor.GOLD)         return Material.ORANGE_TERRACOTTA;
        if (c == NamedTextColor.GRAY)         return Material.LIGHT_GRAY_TERRACOTTA;
        if (c == NamedTextColor.DARK_GRAY)    return Material.GRAY_TERRACOTTA;
        if (c == NamedTextColor.BLUE)         return Material.LIGHT_BLUE_TERRACOTTA;
        if (c == NamedTextColor.GREEN)        return Material.LIME_TERRACOTTA;
        if (c == NamedTextColor.AQUA)         return Material.LIGHT_BLUE_TERRACOTTA;
        if (c == NamedTextColor.RED)          return Material.RED_TERRACOTTA;
        if (c == NamedTextColor.LIGHT_PURPLE) return Material.MAGENTA_TERRACOTTA;
        if (c == NamedTextColor.YELLOW)       return Material.YELLOW_TERRACOTTA;
        return Material.WHITE_TERRACOTTA;
    }

    /** Converts a {@link NamedTextColor} key like {@code "dark_blue"} to {@code "Dark Blue"}. */
    private static String colorDisplayName(NamedTextColor color) {
        String key = NamedTextColor.NAMES.key(color);
        if (key == null) return "Unknown";
        String[] parts = key.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)));
            sb.append(part.substring(1));
        }
        return sb.toString();
    }
}
