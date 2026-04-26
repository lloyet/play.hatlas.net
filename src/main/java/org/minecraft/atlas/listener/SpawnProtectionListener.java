package org.minecraft.atlas.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.minecraft.atlas.faction.SpawnManager;

public class SpawnProtectionListener implements Listener {

    private static double pvpRadius = 64.0;

    public static void loadConfig(FileConfiguration config) {
        pvpRadius = config.getDouble("spawn_protection.pvp_radius", 64.0);
    }

    public static double getRadius() { return pvpRadius; }

    public static boolean isInSpawnProtection(Location location) {
        Location spawn = SpawnManager.getSpawn(location.getWorld());
        return location.distanceSquared(spawn) <= pvpRadius * pvpRadius;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isInSpawnProtection(player.getLocation())) return;
        event.setCancelled(true);
        if (event instanceof EntityDamageByEntityEvent byEntity
                && byEntity.getDamager() instanceof Player attacker) {
            attacker.sendActionBar(Component.text(
                    "PvP is disabled within " + (int) pvpRadius + " blocks of spawn!", NamedTextColor.RED));
        }
    }

    // Handles PvP when attacker is inside spawn protection but victim is outside
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player)) return;
        if (isInSpawnProtection(event.getEntity().getLocation())) return;
        if (!isInSpawnProtection(attacker.getLocation())) return;
        event.setCancelled(true);
        attacker.sendActionBar(Component.text(
                "PvP is disabled within " + (int) pvpRadius + " blocks of spawn!", NamedTextColor.RED));
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!isInSpawnProtection(event.getBlock().getLocation())) return;
        event.setCancelled(true);
        event.getPlayer().sendActionBar(Component.text(
                "Block breaking is disabled in spawn protection.", NamedTextColor.RED));
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.PHYSICAL) return;
        if (event.getClickedBlock() == null) return;
        if (!isInSpawnProtection(event.getClickedBlock().getLocation())) return;
        event.setCancelled(true);
        if (action == Action.RIGHT_CLICK_BLOCK) {
            event.getPlayer().sendActionBar(Component.text(
                    "Interactions are disabled in spawn protection.", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> isInSpawnProtection(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> isInSpawnProtection(block.getLocation()));
    }
}
