package org.minecraft.atlas.faction;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.minecraft.atlas.Atlas;
import org.minecraft.atlas.util.TitleUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HomeManager {

    private record PlayerHome(String name, Location location) {
    }

    private static final Map<UUID, PlayerHome> homes = new HashMap<>();
    private static final Map<UUID, BukkitRunnable> activeTeleports = new HashMap<>();
    private static final Map<UUID, Long> cooldownExpiry = new HashMap<>();

    private static final int COUNTDOWN_SECONDS = 5;
    private static final long COOLDOWN_MS = 30_000L;

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    public static void loadHomes(FileConfiguration config) {
        homes.clear();
        ConfigurationSection sec = config.getConfigurationSection("player_homes");
        if (sec == null) return;

        for (String key : sec.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            ConfigurationSection hs = sec.getConfigurationSection(key);
            if (hs == null) continue;

            String worldName = hs.getString("world");
            if (worldName == null) continue;
            World world = Bukkit.getWorld(worldName);
            if (world == null) continue;

            String name = hs.getString("name", "home");
            Location loc = new Location(world,
                    hs.getDouble("x"), hs.getDouble("y"), hs.getDouble("z"),
                    (float) hs.getDouble("yaw"), (float) hs.getDouble("pitch"));
            homes.put(uuid, new PlayerHome(name, loc));
        }
    }

    public static void saveHomes(FileConfiguration config) {
        config.set("player_homes", null);
        if (homes.isEmpty()) return;

        ConfigurationSection sec = config.createSection("player_homes");
        for (Map.Entry<UUID, PlayerHome> entry : homes.entrySet()) {
            ConfigurationSection hs = sec.createSection(entry.getKey().toString());
            PlayerHome ph = entry.getValue();
            Location loc = ph.location();
            hs.set("name", ph.name());
            hs.set("world", loc.getWorld().getName());
            hs.set("x", loc.getX());
            hs.set("y", loc.getY());
            hs.set("z", loc.getZ());
            hs.set("yaw", loc.getYaw());
            hs.set("pitch", loc.getPitch());
        }
    }

    // -------------------------------------------------------------------------
    // Home management
    // -------------------------------------------------------------------------

    public static void setHome(UUID playerUUID, String name, Location location) {
        homes.put(playerUUID, new PlayerHome(name, location.clone()));
    }

    public static boolean hasHome(UUID playerUUID) {
        return homes.containsKey(playerUUID);
    }

    public static String getHomeName(UUID playerUUID) {
        PlayerHome ph = homes.get(playerUUID);
        return ph != null ? ph.name() : null;
    }

    public static Location getHomeLocation(UUID playerUUID) {
        PlayerHome ph = homes.get(playerUUID);
        return ph != null ? ph.location() : null;
    }

    // -------------------------------------------------------------------------
    // Teleport
    // -------------------------------------------------------------------------

    public static boolean startTeleport(Player player) {
        UUID uuid = player.getUniqueId();

        if (!hasHome(uuid)) {
            player.sendMessage(Component.text(
                    "You have no home set. Use /sethome <name> to set one.", NamedTextColor.RED));
            return false;
        }

        long now = System.currentTimeMillis();
        Long expiry = cooldownExpiry.get(uuid);
        if (expiry != null && now < expiry) {
            long secsLeft = (expiry - now + 999) / 1000;
            player.sendMessage(Component.text(
                    "You must wait " + secsLeft + "s before using /home again.", NamedTextColor.RED));
            return false;
        }

        if (activeTeleports.containsKey(uuid)) {
            player.sendMessage(Component.text("A teleport is already in progress.", NamedTextColor.RED));
            return false;
        }

        Location dest = getHomeLocation(uuid);
        String homeName = getHomeName(uuid);
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
                            "Teleporting to home '" + homeName + "' in " + remaining + "s…",
                            NamedTextColor.YELLOW));
                    remaining--;
                } else {
                    activeTeleports.remove(uuid);
                    cancel();
                    player.teleport(dest);
                    player.sendActionBar(Component.text(
                            "Teleported to home '" + homeName + "'!", NamedTextColor.GREEN));
                    cooldownExpiry.put(uuid, System.currentTimeMillis() + COOLDOWN_MS);
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
