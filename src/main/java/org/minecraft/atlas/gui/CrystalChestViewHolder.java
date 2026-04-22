package org.minecraft.atlas.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import org.minecraft.atlas.faction.Faction;
import org.minecraft.atlas.faction.FactionManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CrystalChestViewHolder implements AtlasHolder {

    /** Key "factionName:chestIndex" → shared holder instance. */
    private static final Map<String, CrystalChestViewHolder> openChests = new HashMap<>();

    private final String factionName;
    private final UUID crystalEntityUUID;
    private final int chestIndex;
    private final Set<UUID> viewers = new HashSet<>();
    private final Inventory inventory;

    private CrystalChestViewHolder(Faction faction, int chestIndex, UUID crystalEntityUUID) {
        this.factionName       = faction.getName();
        this.crystalEntityUUID = crystalEntityUUID;
        this.chestIndex        = chestIndex;

        this.inventory = Bukkit.createInventory(this, 54,
                Component.text(faction.getName() + " - Chest #" + (chestIndex + 1), faction.getColor()));
        this.inventory.setContents(faction.getChestContents(chestIndex));
    }

    /**
     * Opens the chest view for a player. Reuses an existing holder if another
     * faction member already has this chest open.
     */
    public static void open(Player player, Faction faction, int chestIndex, UUID crystalEntityUUID) {
        String key = faction.getName() + ":" + chestIndex;
        CrystalChestViewHolder holder = openChests.computeIfAbsent(key,
                k -> new CrystalChestViewHolder(faction, chestIndex, crystalEntityUUID));
        holder.viewers.add(player.getUniqueId());
        player.openInventory(holder.inventory);
    }

    @Override
    public @NotNull Inventory getInventory() { return inventory; }

    @Override
    public void handleClick(InventoryClickEvent event) {
        // Free interaction — do NOT cancel. Allow all item movement.
    }

    @Override
    public void handleDrag(InventoryDragEvent event) {
        // Free interaction — do NOT cancel drags either.
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();

        // Save chest contents
        String fn = FactionManager.getPlayerFaction(uuid);
        if (fn != null) {
            Faction faction = FactionManager.getFaction(fn);
            if (faction != null) {
                faction.setChestContents(chestIndex, event.getInventory().getContents().clone());
            }
        }

        viewers.remove(uuid);
        if (viewers.isEmpty()) {
            String key = factionName + ":" + chestIndex;
            openChests.remove(key);
        }
    }
}
