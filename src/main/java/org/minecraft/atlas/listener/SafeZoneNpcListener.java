package org.minecraft.atlas.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.minecraft.atlas.gui.ExplorerGui;
import org.minecraft.atlas.safezone.SafeZoneNpcManager;

public class SafeZoneNpcListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!SafeZoneNpcManager.isExplorer(event.getRightClicked())) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (JobListener.denyIfNoFaction(player)) return;
        new ExplorerGui(player).open(player);
    }
}
