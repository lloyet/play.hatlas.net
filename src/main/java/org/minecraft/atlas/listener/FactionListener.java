package org.minecraft.atlas.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.minecraft.atlas.faction.Faction;
import org.minecraft.atlas.faction.FactionManager;

import java.util.Objects;

public class FactionListener implements Listener {

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.OFF_HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInOffHand();
        if (!isCrystalOfTheEnd(item)) return;

        event.setCancelled(true);

        Block block = event.getClickedBlock();
        if (block == null) return;

        Location loc = block.getLocation().add(0.5, 1.0, 0.5);
        block.getWorld().spawn(loc, EnderCrystal.class, crystal -> crystal.setShowingBottom(false));

        player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
    }

    private static boolean isCrystalOfTheEnd(ItemStack item) {
        if (item == null || item.getType() != Material.END_CRYSTAL) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return false;
        String name = PlainTextComponentSerializer.plainText().serialize(Objects.requireNonNull(meta.displayName()));
        return name.equals("Crystal of the End");
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String factionName = FactionManager.getPlayerFaction(player.getUniqueId());

        if (factionName == null) return;

        Faction faction = FactionManager.getFaction(factionName);

        if (faction == null) return;

        event.renderer((source, sourceDisplayName, message, viewer) ->
                Component.text("[")
                        .append(Component.text(factionName, faction.getColor()))
                        .append(Component.text("] "))
                        .append(sourceDisplayName)
                        .append(Component.text(": "))
                        .append(message)
        );
    }
}

