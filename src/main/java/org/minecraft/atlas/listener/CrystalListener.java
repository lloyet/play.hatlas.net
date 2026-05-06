package org.minecraft.atlas.listener;

import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.minecraft.atlas.crystal.AtlasCrystal;
import org.minecraft.atlas.crystal.AtlasCrystalManager;
import org.minecraft.atlas.faction.Faction;
import org.minecraft.atlas.faction.FactionManager;
import org.minecraft.atlas.gui.CrystalMainGui;

public class CrystalListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractAtlasCrystal(PlayerInteractAtEntityEvent event) {
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
        new CrystalMainGui(player, faction, crystal).open(player);
    }
}
