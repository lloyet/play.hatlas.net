package org.minecraft.atlas.auction;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.minecraft.atlas.Atlas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * In-memory registry of all open auction listings, with YAML persistence to
 * {@code auctions-data.yml}. Listings have global scope (every player sees the market)
 * and are keyed by their own UUID. Insertion order is preserved so paging stays stable.
 */
public final class AuctionManager {

    private static final Map<UUID, AuctionListing> listings = new LinkedHashMap<>();

    /** Loaded from {@code auctions.yml}. */
    private static int maxListingsPerPlayer = 5;

    private AuctionManager() {}

    public static int getMaxListingsPerPlayer() { return maxListingsPerPlayer; }

    /** Loads tuning values from {@code auctions.yml}. */
    public static void loadConfig(FileConfiguration config) {
        maxListingsPerPlayer = Math.max(1, config.getInt("max_listings_per_player", 5));
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    public static void addListing(AuctionListing listing) {
        if (listing == null) return;
        listings.put(listing.id(), listing);
        persist();
    }

    /** Removes and returns the listing with the given id, or null. Persists on success. */
    public static AuctionListing removeListing(UUID id) {
        AuctionListing removed = listings.remove(id);
        if (removed != null) persist();
        return removed;
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public static AuctionListing getListing(UUID id) {
        return listings.get(id);
    }

    /** All listings owned by {@code seller}, in insertion order. */
    public static List<AuctionListing> getBySeller(UUID seller) {
        List<AuctionListing> out = new ArrayList<>();
        for (AuctionListing l : listings.values()) {
            if (l.seller().equals(seller)) out.add(l);
        }
        return out;
    }

    /** All listings NOT owned by {@code viewer}, in insertion order. */
    public static List<AuctionListing> getAllExcept(UUID viewer) {
        List<AuctionListing> out = new ArrayList<>();
        for (AuctionListing l : listings.values()) {
            if (!l.seller().equals(viewer)) out.add(l);
        }
        return out;
    }

    public static List<AuctionListing> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(listings.values()));
    }

    // ── Persistence (auctions-data.yml) ───────────────────────────────────────

    /** Writes current state into the {@code listings} section of the data config. */
    public static void saveData(FileConfiguration config) {
        config.set("listings", null);
        if (listings.isEmpty()) return;
        ConfigurationSection root = config.createSection("listings");
        for (AuctionListing l : listings.values()) {
            ConfigurationSection s = root.createSection(l.id().toString());
            s.set("seller", l.seller().toString());
            s.set("item", l.item());
            s.set("price", l.price());
            if (!l.description().isEmpty()) s.set("description", l.description());
            s.set("created_at", l.createdAtMs());
        }
    }

    public static void loadData(FileConfiguration config) {
        listings.clear();
        ConfigurationSection root = config.getConfigurationSection("listings");
        if (root == null) return;
        for (String idStr : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(idStr);
            if (s == null) continue;
            try {
                UUID id = UUID.fromString(idStr);
                UUID seller = UUID.fromString(s.getString("seller", ""));
                ItemStack item = s.getItemStack("item");
                if (item == null) continue;
                int price = s.getInt("price", 0);
                String description = s.getString("description", "");
                long createdAt = s.getLong("created_at", System.currentTimeMillis());
                listings.put(id, new AuctionListing(id, seller, item, price, description, createdAt));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private static void persist() {
        saveData(Atlas.auctionsDataConfig);
        Atlas.saveAuctionsDataConfig();
    }
}
