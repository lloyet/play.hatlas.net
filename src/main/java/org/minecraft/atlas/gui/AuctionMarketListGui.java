package org.minecraft.atlas.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import org.minecraft.atlas.Atlas;
import org.minecraft.atlas.auction.AuctionListing;
import org.minecraft.atlas.auction.AuctionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Global market view — every listing in the auction except those owned by the viewer.
 * Layout / paging identical to {@link AuctionSellListGui}; clicking a listing opens
 * {@link AuctionConfirmGui} in BUY mode.
 */
public class AuctionMarketListGui implements AtlasGui {

    private final UUID viewerUUID;
    private final Inventory inventory;
    private final int page;
    private final List<AuctionListing> pageListings;

    public AuctionMarketListGui(Player player) {
        this(player, 0);
    }

    public AuctionMarketListGui(Player player, int page) {
        this.viewerUUID = player.getUniqueId();

        List<AuctionListing> all = AuctionManager.getAllExcept(viewerUUID);
        int totalPages = Math.max(1, (all.size() + AuctionSellListGui.ITEMS_PER_PAGE - 1)
                / AuctionSellListGui.ITEMS_PER_PAGE);
        this.page = ((page % totalPages) + totalPages) % totalPages;

        this.inventory = Atlas.instance.getServer().createInventory(this, 54,
                Component.text("Market [Page " + (this.page + 1) + "/" + totalPages + "]",
                        NamedTextColor.GOLD));

        int from = this.page * AuctionSellListGui.ITEMS_PER_PAGE;
        int to   = Math.min(from + AuctionSellListGui.ITEMS_PER_PAGE, all.size());
        this.pageListings = new ArrayList<>(all.subList(from, to));

        for (int i = 0; i < pageListings.size(); i++) {
            inventory.setItem(AuctionSellListGui.ITEM_AREA_START + i,
                    AuctionSellListGui.renderListing(pageListings.get(i), true));
        }
        if (totalPages > 1) {
            inventory.setItem(AuctionSellListGui.SLOT_NEXT_ARROW,
                    AuctionSellListGui.buildNextArrow(this.page, totalPages));
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
        if (slot == AuctionSellListGui.SLOT_NEXT_ARROW) {
            List<AuctionListing> all = AuctionManager.getAllExcept(viewerUUID);
            int totalPages = Math.max(1, (all.size() + AuctionSellListGui.ITEMS_PER_PAGE - 1)
                    / AuctionSellListGui.ITEMS_PER_PAGE);
            if (totalPages <= 1) return;
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            new AuctionMarketListGui(player, page + 1).open(player);
            return;
        }
        if (slot < AuctionSellListGui.ITEM_AREA_START || slot > AuctionSellListGui.ITEM_AREA_END) return;
        int idx = slot - AuctionSellListGui.ITEM_AREA_START;
        if (idx >= pageListings.size()) return;

        AuctionListing listing = pageListings.get(idx);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        GuiNavigator.push(player.getUniqueId(), this);
        new AuctionConfirmGui(player, listing, AuctionConfirmGui.Mode.BUY).open(player);
    }
}
