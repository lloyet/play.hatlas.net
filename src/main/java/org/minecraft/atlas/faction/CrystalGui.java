package org.minecraft.atlas.faction;

import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.minecraft.atlas.gui.CrystalDisbandHolder;
import org.minecraft.atlas.gui.CrystalMainHolder;

public class CrystalGui implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractCrystal(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof EnderCrystal entity)) return;

        AtlasCrystal crystal = AtlasCrystalManager.getCrystal(entity.getUniqueId());
        if (crystal == null) return;

        Player player = event.getPlayer();
        String playerFaction = FactionManager.getPlayerFaction(player.getUniqueId());
        if (playerFaction == null) return;
        if (!playerFaction.equals(crystal.getFactionName())) return;

        event.setCancelled(true);

        Faction faction = FactionManager.getFaction(playerFaction);
        new CrystalMainHolder(player, faction, crystal).open(player);
    }

    /** Opens the disband confirmation GUI (called from FactionCommand). */
    public static void openDisbandConfirmMenu(Player player, Faction faction) {
        CrystalDisbandHolder.open(player, faction);
    }
}
