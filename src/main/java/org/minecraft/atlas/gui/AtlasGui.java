package org.minecraft.atlas.gui;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

public interface AtlasGui extends InventoryHolder {
    void handleClick(InventoryClickEvent event);
    default void handleClose(InventoryCloseEvent event) {}
    default void handleDrag(InventoryDragEvent event) { event.setCancelled(true); }
}
