package org.minecraft.atlas.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class SpawnProtectionListener implements Listener {

    private static double pvpRadius = 64.0;

    public static void loadConfig(FileConfiguration config) {
        pvpRadius = config.getDouble("spawn_protection.pvp_radius", 64.0);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        Location spawn = victim.getWorld().getSpawnLocation();
        double radiusSq = pvpRadius * pvpRadius;

        if (attacker.getLocation().distanceSquared(spawn) <= radiusSq
                || victim.getLocation().distanceSquared(spawn) <= radiusSq) {
            event.setCancelled(true);
            attacker.sendActionBar(Component.text(
                    "PvP is disabled within " + (int) pvpRadius + " blocks of spawn!", NamedTextColor.RED));
        }
    }
}
