package org.minecraft.atlas.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
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
import org.minecraft.atlas.teleport.HomeTeleportManager;
import org.minecraft.atlas.util.GuiUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class CrystalListGui implements AtlasGui {


    private final String factionName;
    private final UUID mainCrystalUUID;
    private final List<AtlasCrystal> crystalList = new ArrayList<>();
    private final Inventory inventory;

    public CrystalListGui(Player player, Faction faction, UUID mainCrystalUUID) {
        this.factionName     = faction.getName();
        this.mainCrystalUUID = mainCrystalUUID;

        this.inventory = Atlas.instance.getServer().createInventory(this, 54,
                Component.text("Crystals - " + GuiUtil.truncateFactionName(faction.getName()), faction.getColor()));

        Collection<AtlasCrystal> crystals = AtlasCrystalManager.getFactionCrystals(factionName);
        int[] slots = GuiUtil.contentSlots54();
        int i = 0;
        for (AtlasCrystal crystal : crystals) {
            if (i >= slots.length) break;
            crystalList.add(crystal);
            this.inventory.setItem(slots[i], buildCrystalItem(crystal, faction));
            i++;
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
            GuiNavigator.back(player);
            return;
        }

        int[] slots = GuiUtil.contentSlots54();
        int crystalIndex = -1;
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) { crystalIndex = i; break; }
        }
        if (crystalIndex < 0 || crystalIndex >= crystalList.size()) return;

        AtlasCrystal target = crystalList.get(crystalIndex);

        Location home = target.getHome();
        if (home == null) {
            player.sendMessage(Component.text("This crystal has no home location set.", NamedTextColor.RED));
            return;
        }

        player.closeInventory();
        String label = target.getName().isEmpty() ? factionName : target.getName();
        HomeTeleportManager.startTeleport(player, home, label);
    }

    // ── Item builder ──────────────────────────────────────────────────────────

    private static ItemStack buildCrystalItem(AtlasCrystal crystal, Faction faction) {
        ItemStack item = new ItemStack(Material.END_CRYSTAL);
        ItemMeta meta  = item.getItemMeta();

        String displayName = crystal.getName().isEmpty() ? "Unnamed Crystal" : crystal.getName();
        meta.displayName(Component.text(displayName, faction.getColor())
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(GuiUtil.loreLine("HP", (int) crystal.getHp() + " / " + (int) crystal.getMaxHp() + " ♥",
                NamedTextColor.RED));

        if (crystal.isImmune()) {
            long remainingSec = (crystal.getImmuneUntilMillis() - System.currentTimeMillis()) / 1000;
            lore.add(GuiUtil.loreLine("Immune", remainingSec + "s", NamedTextColor.AQUA));
        }

        Location home = crystal.getHome();
        if (home != null) {
            lore.add(GuiUtil.loreLine("Home",
                    (int) home.getX() + ", " + (int) home.getY() + ", " + (int) home.getZ(),
                    NamedTextColor.GRAY));
            lore.add(Component.empty());
            lore.add(Component.text("  Click to teleport", NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.empty());
            lore.add(Component.text("  No home set", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
