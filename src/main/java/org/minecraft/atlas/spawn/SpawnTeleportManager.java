package org.minecraft.atlas.spawn;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.minecraft.atlas.Atlas;
import org.minecraft.atlas.util.TitleUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SpawnTeleportManager {

    private static final Map<UUID, BukkitRunnable> activeTeleports = new HashMap<>();
    private static final Map<UUID, Long> cooldownExpiry = new HashMap<>();

    private static final int COUNTDOWN_SECONDS = 10;
    private static long cooldownMs = 30_000L;

    public static void loadConfig(FileConfiguration config) {
        cooldownMs = config.getLong("spawn_teleport.cooldown_seconds", 30L) * 1000L;
    }

    public static boolean startTeleport(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        Long expiry = cooldownExpiry.get(uuid);
        if (!player.isOp() && expiry != null && now < expiry) {
            long secsLeft = (expiry - now + 999) / 1000;
            player.sendMessage(Component.text(
                    "You must wait " + secsLeft + "s before using /spawn again.", NamedTextColor.RED));
            return false;
        }

        if (activeTeleports.containsKey(uuid)) {
            player.sendMessage(Component.text("A teleport is already in progress.", NamedTextColor.RED));
            return false;
        }

        Location dest = SpawnManager.getSpawn(player.getWorld());

        if (player.isOp()) {
            player.teleport(dest);
            player.sendActionBar(Component.text("Teleported to spawn!", NamedTextColor.GREEN));
            return true;
        }

        Location startLocation = player.getLocation().clone();

        BukkitRunnable task = new BukkitRunnable() {
            int remaining = COUNTDOWN_SECONDS;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    activeTeleports.remove(uuid);
                    cancel();
                    return;
                }
                Location current = player.getLocation();
                double delta = Math.abs(current.getX() - startLocation.getX())
                        + Math.abs(current.getY() - startLocation.getY())
                        + Math.abs(current.getZ() - startLocation.getZ());
                if (delta > 0.1) {
                    activeTeleports.remove(uuid);
                    cancel();
                    TitleUtil.notify(player, "Teleport cancelled — you moved!", NamedTextColor.RED);
                    return;
                }
                if (remaining > 0) {
                    player.sendActionBar(Component.text(
                            "Teleporting to spawn in " + remaining + "s… Don't move!", NamedTextColor.YELLOW));
                    remaining--;
                } else {
                    activeTeleports.remove(uuid);
                    cancel();
                    player.teleport(dest);
                    player.sendActionBar(Component.text("Teleported to spawn!", NamedTextColor.GREEN));
                    if (!player.isOp()) cooldownExpiry.put(uuid, System.currentTimeMillis() + cooldownMs);
                }
            }
        };

        activeTeleports.put(uuid, task);
        task.runTaskTimer(Atlas.instance, 0L, 20L);
        return true;
    }

    public static boolean cancelTeleport(UUID playerUUID) {
        BukkitRunnable task = activeTeleports.remove(playerUUID);
        if (task == null) return false;
        task.cancel();
        return true;
    }
}
