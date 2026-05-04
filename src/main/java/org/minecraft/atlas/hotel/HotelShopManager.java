package org.minecraft.atlas.hotel;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

public class HotelShopManager {

    private static final Map<String, HotelShop>   byId       = new LinkedHashMap<>();
    private static final Map<String, HotelShop>   byLocation = new HashMap<>();

    // ── Location key ──────────────────────────────────────────────────────────

    private static String locKey(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    // ── API ───────────────────────────────────────────────────────────────────

    public static HotelShop getById(String id)        { return byId.get(id); }
    public static HotelShop getAt(Location loc)       { return byLocation.get(locKey(loc)); }
    public static Collection<HotelShop> getAll()      { return Collections.unmodifiableCollection(byId.values()); }

    public static HotelShop create(HotelShop.Type type, Material mat,
                                   int amount, int price, int stock,
                                   UUID ownerUUID, String ownerName,
                                   Location location) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        HotelShop shop = new HotelShop(id, type, mat, amount, price, stock, ownerUUID, ownerName, location);
        register(shop);
        return shop;
    }

    private static void register(HotelShop shop) {
        byId.put(shop.getId(), shop);
        byLocation.put(locKey(shop.getLocation()), shop);
    }

    public static boolean deleteAt(Location loc) {
        HotelShop shop = byLocation.remove(locKey(loc));
        if (shop == null) return false;
        byId.remove(shop.getId());
        return true;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    public static void save(FileConfiguration config) {
        config.set("shops", null);
        if (byId.isEmpty()) return;
        ConfigurationSection root = config.createSection("shops");
        for (HotelShop s : byId.values()) {
            ConfigurationSection sec = root.createSection(s.getId());
            sec.set("type",            s.getType().name());
            sec.set("material",        s.getMaterial().name());
            sec.set("amount_per_deal", s.getAmountPerDeal());
            sec.set("price_per_deal",  s.getPricePerDeal());
            sec.set("stock",           s.getStock());
            if (s.getOwnerUUID() != null) sec.set("owner",      s.getOwnerUUID().toString());
            if (s.getOwnerName() != null) sec.set("owner_name", s.getOwnerName());
            Location loc = s.getLocation();
            sec.set("location", loc.getWorld().getName() + ":"
                    + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ());
        }
    }

    public static void load(FileConfiguration config) {
        byId.clear();
        byLocation.clear();
        ConfigurationSection root = config.getConfigurationSection("shops");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) continue;
            try {
                HotelShop.Type type = HotelShop.Type.valueOf(sec.getString("type", "SELL").toUpperCase());
                Material mat  = Material.valueOf(sec.getString("material", "AIR").toUpperCase());
                int amount    = sec.getInt("amount_per_deal", 1);
                int price     = sec.getInt("price_per_deal", 1);
                int stock     = sec.getInt("stock", -1);
                UUID ownerUUID = null;
                String ownerStr = sec.getString("owner");
                if (ownerStr != null) ownerUUID = UUID.fromString(ownerStr);
                String ownerName = sec.getString("owner_name");
                String locStr    = sec.getString("location");
                if (locStr == null) continue;
                String[] parts = locStr.split(":");
                if (parts.length < 4) continue;
                World world = Bukkit.getWorld(parts[0]);
                if (world == null) continue;
                Location loc = new Location(world,
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]),
                        Integer.parseInt(parts[3]));
                register(new HotelShop(id, type, mat, amount, price, stock, ownerUUID, ownerName, loc));
            } catch (Exception ignored) {}
        }
    }
}
