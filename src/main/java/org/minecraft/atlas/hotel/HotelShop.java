package org.minecraft.atlas.hotel;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.UUID;

public class HotelShop {

    public enum Type {
        /** Shop sells items — player pays Rubis and receives items. */
        SELL,
        /** Shop buys items — player gives items and receives Rubis. */
        BUY
    }

    private final String   id;
    private final Type     type;
    private final Material material;
    private final int      amountPerDeal;
    private final int      pricePerDeal;
    private int            stock;        // -1 = unlimited
    private final UUID     ownerUUID;    // null = server shop
    private final String   ownerName;
    private final Location location;

    public HotelShop(String id, Type type, Material material, int amountPerDeal,
                     int pricePerDeal, int stock, UUID ownerUUID, String ownerName,
                     Location location) {
        this.id            = id;
        this.type          = type;
        this.material      = material;
        this.amountPerDeal = amountPerDeal;
        this.pricePerDeal  = pricePerDeal;
        this.stock         = stock;
        this.ownerUUID     = ownerUUID;
        this.ownerName     = ownerName;
        this.location      = location;
    }

    public String   getId()           { return id; }
    public Type     getType()         { return type; }
    public Material getMaterial()     { return material; }
    public int      getAmountPerDeal(){ return amountPerDeal; }
    public int      getPricePerDeal() { return pricePerDeal; }
    public int      getStock()        { return stock; }
    public boolean  isUnlimited()     { return stock < 0; }
    public UUID     getOwnerUUID()    { return ownerUUID; }
    public String   getOwnerName()    { return ownerName != null ? ownerName : "Serveur"; }
    public Location getLocation()     { return location; }

    public boolean hasStock(int units) {
        return stock < 0 || stock >= units;
    }

    public void addStock(int amount) {
        if (stock >= 0) stock = Math.max(0, stock + amount);
    }

    public void consumeStock(int units) {
        if (stock >= 0) stock = Math.max(0, stock - units);
    }
}
