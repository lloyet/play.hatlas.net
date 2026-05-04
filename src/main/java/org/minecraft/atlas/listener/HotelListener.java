package org.minecraft.atlas.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.minecraft.atlas.gui.HotelShopGui;
import org.minecraft.atlas.hotel.HotelShop;
import org.minecraft.atlas.hotel.HotelShopManager;

public class HotelListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getClickedBlock() == null) return;

        HotelShop shop = HotelShopManager.getAt(event.getClickedBlock().getLocation());
        if (shop == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        new HotelShopGui(shop).open(player);
    }
}
