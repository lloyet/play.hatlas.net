package org.minecraft.atlas.auction;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * One item listed on the auction market. Immutable — to mutate, remove and re-create.
 *
 * @param id           per-listing UUID (generated at creation, used as the YAML section key)
 * @param seller       UUID of the listing player
 * @param item         the item being sold (may carry full data components / NBT)
 * @param price        ruby-gem cost
 * @param description  optional seller-supplied note (empty string if none)
 * @param createdAtMs  epoch ms when the listing was created
 */
public record AuctionListing(UUID id, UUID seller, ItemStack item, int price,
                             String description, long createdAtMs) {

    public static AuctionListing create(UUID seller, ItemStack item, int price, String description) {
        return new AuctionListing(UUID.randomUUID(), seller, item, price,
                description == null ? "" : description, System.currentTimeMillis());
    }
}
