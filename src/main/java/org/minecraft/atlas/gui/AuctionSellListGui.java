package org.minecraft.atlas.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.minecraft.atlas.Atlas;
import org.minecraft.atlas.auction.AuctionListing;
import org.minecraft.atlas.auction.AuctionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Paged view of the viewer's own auction listings. Item rows occupy slots 9-44
 * (36 per page); next-page arrow at slot 8; back-barrier at slot 53.
 *
 * <p>Clicking a listing opens {@link AuctionConfirmGui} configured as REMOVE — confirm
 * cancels the listing and returns the item to the player.
 */
public class AuctionSellListGui implements AtlasGui {

    static final int SLOT_NEXT_ARROW = 8;
    static final int ITEMS_PER_PAGE  = 36;
    static final int ITEM_AREA_START = 9;  // first item slot (row 1, col 0)
    static final int ITEM_AREA_END   = 44; // last item slot (row 4, col 8) inclusive

    private final UUID viewerUUID;
    private final Inventory inventory;
    private final int page;
    private final List<AuctionListing> pageListings;

    public AuctionSellListGui(Player player) {
        this(player, 0);
    }

    public AuctionSellListGui(Player player, int page) {
        this.viewerUUID = player.getUniqueId();

        List<AuctionListing> all = AuctionManager.getBySeller(viewerUUID);
        int totalPages = Math.max(1, (all.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
        this.page = ((page % totalPages) + totalPages) % totalPages;

        this.inventory = Atlas.instance.getServer().createInventory(this, 54,
                Component.text("My Listings [Page " + (this.page + 1) + "/" + totalPages + "]",
                        NamedTextColor.AQUA));

        int from = this.page * ITEMS_PER_PAGE;
        int to   = Math.min(from + ITEMS_PER_PAGE, all.size());
        this.pageListings = new ArrayList<>(all.subList(from, to));

        for (int i = 0; i < pageListings.size(); i++) {
            inventory.setItem(ITEM_AREA_START + i, renderListing(pageListings.get(i), false));
        }
        if (totalPages > 1) {
            inventory.setItem(SLOT_NEXT_ARROW, buildNextArrow(this.page, totalPages));
        }
        finishGui();
    }

    public void open(Player player) { player.openInventory(this.inventory); }

    @Override public @NotNull Inventory getInventory() { return inventory; }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();

        if (slot == inventory.getSize() - 1) {
            GuiNavigator.back(player);
            return;
        }
        if (slot == SLOT_NEXT_ARROW) {
            List<AuctionListing> all = AuctionManager.getBySeller(viewerUUID);
            int totalPages = Math.max(1, (all.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
            if (totalPages <= 1) return;
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            new AuctionSellListGui(player, page + 1).open(player);
            return;
        }
        if (slot < ITEM_AREA_START || slot > ITEM_AREA_END) return;
        int idx = slot - ITEM_AREA_START;
        if (idx >= pageListings.size()) return;

        AuctionListing listing = pageListings.get(idx);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        GuiNavigator.push(player.getUniqueId(), this);
        new AuctionConfirmGui(player, listing, AuctionConfirmGui.Mode.REMOVE).open(player);
    }

    /**
     * Builds a display-only copy of {@code listing.item()} with price + seller + description
     * + action hint appended to lore. Original name and components are preserved.
     */
    static ItemStack renderListing(AuctionListing listing, boolean showSeller) {
        ItemStack display = listing.item().clone();
        ItemMeta meta = display.getItemMeta();
        if (meta == null) return display;

        List<Component> lore = meta.lore();
        if (lore == null) lore = new ArrayList<>();
        else lore = new ArrayList<>(lore);

        lore.add(Component.empty());
        lore.add(Component.text("  Price: ", NamedTextColor.GRAY)
                .append(Component.text(listing.price() + " ruby", NamedTextColor.RED))
                .decoration(TextDecoration.ITALIC, false));
        if (showSeller) {
            String name = sellerName(listing.seller());
            lore.add(Component.text("  Seller: ", NamedTextColor.GRAY)
                    .append(Component.text(name, NamedTextColor.YELLOW))
                    .decoration(TextDecoration.ITALIC, false));
        }
        if (!listing.description().isEmpty()) {
            lore.add(Component.empty());
            for (String line : listing.description().split("\\\\n")) {
                lore.add(Component.text("  " + line, NamedTextColor.WHITE)
                        .decoration(TextDecoration.ITALIC, true));
            }
        }
        lore.add(Component.empty());
        lore.add(Component.text(showSeller ? "  Click to buy" : "  Click to retrieve",
                        NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
    }

    private static String sellerName(UUID uuid) {
        var online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        String off = Bukkit.getOfflinePlayer(uuid).getName();
        return off != null ? off : uuid.toString().substring(0, 8);
    }

    static ItemStack buildNextArrow(int currentPage, int totalPages) {
        int nextPage = ((currentPage + 1) % totalPages) + 1;
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Next Page →", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("  Click to view page " + nextPage + "/" + totalPages,
                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
