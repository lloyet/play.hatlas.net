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
import org.minecraft.atlas.hotel.HotelShop;
import org.minecraft.atlas.hotel.HotelShopManager;
import org.minecraft.atlas.hotel.Rubis;
import org.minecraft.atlas.util.GuiUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shop GUI for the Hotel des Vents.
 *
 * Layout (27 slots):
 *   Slot  4: item being traded (info display)
 *   Slot 10: ×1
 *   Slot 11: ×4
 *   Slot 12: ×8
 *   Slot 14: ×16
 *   Slot 15: ×32
 *   Slot 20: stock indicator
 *   Slot 26: back (finishGui)
 */
public class HotelShopGui implements AtlasGui {

    private static final int   SLOT_ITEM  = 4;
    private static final int   SLOT_STOCK = 20;
    private static final int[] QTY_SLOTS  = {10, 11, 12, 14, 15};
    private static final int[] QUANTITIES = {1,  4,  8, 16, 32};

    private final HotelShop shop;
    private final Inventory inventory;

    public HotelShopGui(HotelShop shop) {
        this.shop = shop;
        this.inventory = Atlas.instance.getServer().createInventory(this, 27,
                Component.text("Boutique — " + shop.getOwnerName(), NamedTextColor.GOLD));
        refresh();
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    @Override
    public @NotNull Inventory getInventory() { return inventory; }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();
        if (slot == inventory.getSize() - 1) { GuiNavigator.back(player); return; }

        for (int i = 0; i < QTY_SLOTS.length; i++) {
            if (slot == QTY_SLOTS[i]) {
                executeTransaction(player, QUANTITIES[i]);
                return;
            }
        }
    }

    // ── Transaction ───────────────────────────────────────────────────────────

    private void executeTransaction(Player player, int units) {
        int itemTotal  = shop.getAmountPerDeal() * units;
        int rubisTotal = shop.getPricePerDeal()  * units;

        if (!shop.hasStock(itemTotal)) {
            player.sendActionBar(Component.text("Stock insuffisant !", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
            return;
        }

        if (shop.getType() == HotelShop.Type.SELL) {
            // Player pays Rubis → receives items
            if (!Rubis.take(player, rubisTotal)) {
                player.sendActionBar(Component.text(
                        "Rubis insuffisants ! (besoin de " + rubisTotal + " ✦)", NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
                return;
            }
            Map<Integer, ItemStack> leftover = player.getInventory()
                    .addItem(new ItemStack(shop.getMaterial(), itemTotal));
            for (ItemStack overflow : leftover.values())
                player.getWorld().dropItemNaturally(player.getLocation(), overflow);

        } else {
            // Player gives items → receives Rubis
            if (countMaterial(player, shop.getMaterial()) < itemTotal) {
                player.sendActionBar(Component.text(
                        "Pas assez de " + formatMat(shop.getMaterial()) + " dans votre inventaire !",
                        NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
                return;
            }
            removeMaterial(player, shop.getMaterial(), itemTotal);
            Rubis.give(player, rubisTotal);
        }

        shop.consumeStock(itemTotal);
        player.sendActionBar(Component.text("Transaction réussie !", NamedTextColor.GREEN));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);

        HotelShopManager.save(Atlas.hotelDataConfig);
        Atlas.saveHotelDataConfig();
        refresh();
    }

    private void refresh() {
        inventory.setItem(SLOT_ITEM,  buildItemDisplay());
        inventory.setItem(SLOT_STOCK, buildStockDisplay());
        for (int i = 0; i < QTY_SLOTS.length; i++) {
            inventory.setItem(QTY_SLOTS[i], buildQtyButton(QUANTITIES[i]));
        }
        finishGui();
    }

    // ── Item builders ─────────────────────────────────────────────────────────

    private ItemStack buildItemDisplay() {
        Material mat = shop.getMaterial();
        ItemStack item = mat.isItem() ? new ItemStack(mat) : new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(formatMat(mat), shop.getType() == HotelShop.Type.SELL
                ? NamedTextColor.AQUA : NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        if (shop.getType() == HotelShop.Type.SELL) {
            lore.add(Component.text("  Type : ", NamedTextColor.GRAY)
                    .append(Component.text("Vente", NamedTextColor.GREEN))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  Vous achetez les items.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("  Type : ", NamedTextColor.GRAY)
                    .append(Component.text("Rachat", NamedTextColor.GOLD))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  Vous vendez vos items.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
        lore.add(GuiUtil.loreLine("Quantité / achat", String.valueOf(shop.getAmountPerDeal()), NamedTextColor.WHITE));
        lore.add(GuiUtil.loreLine("Prix / achat", shop.getPricePerDeal() + " ✦ Rubis", NamedTextColor.RED));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildStockDisplay() {
        ItemStack item = new ItemStack(Material.BARREL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Stock", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        String stockStr = shop.isUnlimited() ? "Illimité" : String.valueOf(shop.getStock());
        NamedTextColor stockColor = shop.isUnlimited() ? NamedTextColor.AQUA
                : (shop.getStock() > 0 ? NamedTextColor.GREEN : NamedTextColor.RED);
        meta.lore(List.of(
                Component.empty(),
                Component.text("  " + stockStr, stockColor).decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildQtyButton(int qty) {
        int rubis = shop.getPricePerDeal() * qty;
        int items = shop.getAmountPerDeal() * qty;
        boolean canAfford;
        Material pane;

        if (shop.getType() == HotelShop.Type.SELL) {
            pane      = Material.LIME_STAINED_GLASS_PANE;
            canAfford = shop.hasStock(items);
        } else {
            pane      = Material.YELLOW_STAINED_GLASS_PANE;
            canAfford = shop.hasStock(items);
        }
        if (!canAfford) pane = Material.RED_STAINED_GLASS_PANE;

        ItemStack btn = new ItemStack(pane);
        ItemMeta meta = btn.getItemMeta();
        String label = (shop.getType() == HotelShop.Type.SELL ? "Acheter" : "Vendre") + " ×" + qty;
        meta.displayName(Component.text(label, canAfford ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        if (shop.getType() == HotelShop.Type.SELL) {
            lore.add(Component.text("  Coût : " + rubis + " ✦ Rubis", NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  Vous recevez : " + items + "× " + formatMat(shop.getMaterial()), NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("  Vous donnez : " + items + "× " + formatMat(shop.getMaterial()), NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  Vous recevez : " + rubis + " ✦ Rubis", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        btn.setItemMeta(meta);
        return btn;
    }

    // ── Inventory helpers ─────────────────────────────────────────────────────

    private static int countMaterial(Player player, Material mat) {
        int total = 0;
        for (ItemStack s : player.getInventory().getContents()) {
            if (s != null && s.getType() == mat && !Rubis.isRubis(s)) total += s.getAmount();
        }
        return total;
    }

    private static void removeMaterial(Player player, Material mat, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            if (contents[i] == null || contents[i].getType() != mat || Rubis.isRubis(contents[i])) continue;
            int stackAmt = contents[i].getAmount();
            if (stackAmt <= remaining) {
                remaining -= stackAmt;
                contents[i] = null;
            } else {
                contents[i].setAmount(stackAmt - remaining);
                remaining = 0;
            }
        }
        player.getInventory().setContents(contents);
        player.updateInventory();
    }

    private static String formatMat(Material mat) {
        String name = mat.name().replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String word : name.split(" ")) {
            if (!word.isEmpty()) sb.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase()).append(" ");
        }
        return sb.toString().trim();
    }
}
