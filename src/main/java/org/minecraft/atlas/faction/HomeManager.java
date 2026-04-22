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

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class HomeManager {

    /**
     * UUID → (home name → location), insertion-ordered so "first home" is deterministic.
     */
    private static final Map<UUID, LinkedHashMap<String, Location>> homes = new HashMap<>();

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

            ConfigurationSection playerSec = sec.getConfigurationSection(key);
            if (playerSec == null) continue;

            LinkedHashMap<String, Location> playerHomes = new LinkedHashMap<>();

            if (playerSec.contains("world")) {
                // Legacy single-home format: migrate transparently
                Location loc = readLocation(playerSec);
                if (loc != null) {
                    String name = playerSec.getString("name", "home");
                    playerHomes.put(name, loc);
                }
            } else {
                // Current format: each sub-key is a named home
                for (String homeName : playerSec.getKeys(false)) {
                    ConfigurationSection hs = playerSec.getConfigurationSection(homeName);
                    if (hs == null) continue;
                    Location loc = readLocation(hs);
                    if (loc != null) playerHomes.put(homeName, loc);
                }
            }

            if (!playerHomes.isEmpty()) homes.put(uuid, playerHomes);
        }
    }

    public static void saveHomes(FileConfiguration config) {
        config.set("player_homes", null);
        if (homes.isEmpty()) return;

        ConfigurationSection sec = config.createSection("player_homes");
        for (Map.Entry<UUID, LinkedHashMap<String, Location>> entry : homes.entrySet()) {
            ConfigurationSection playerSec = sec.createSection(entry.getKey().toString());
            for (Map.Entry<String, Location> homeEntry : entry.getValue().entrySet()) {
                ConfigurationSection hs = playerSec.createSection(homeEntry.getKey());
                writeLocation(hs, homeEntry.getValue());
            }
        }
    }

    private static Location readLocation(ConfigurationSection s) {
        String worldName = s.getString("world");
        if (worldName == null) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world,
                s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
    }

    private static void writeLocation(ConfigurationSection s, Location loc) {
        s.set("world", loc.getWorld().getName());
        s.set("x", loc.getX());
        s.set("y", loc.getY());
        s.set("z", loc.getZ());
        s.set("yaw", loc.getYaw());
        s.set("pitch", loc.getPitch());
    }

    // -------------------------------------------------------------------------
    // Home management
    // -------------------------------------------------------------------------

    public static void setHome(UUID playerUUID, String name, Location location) {
        homes.computeIfAbsent(playerUUID, k -> new LinkedHashMap<>())
                .put(name, location.clone());
    }

    public static boolean hasAnyHome(UUID playerUUID) {
        LinkedHashMap<String, Location> m = homes.get(playerUUID);
        return m != null && !m.isEmpty();
    }

    public static boolean hasHome(UUID playerUUID, String name) {
        LinkedHashMap<String, Location> m = homes.get(playerUUID);
        return m != null && m.containsKey(name);
    }

    public static Location getHomeLocation(UUID playerUUID, String name) {
        LinkedHashMap<String, Location> m = homes.get(playerUUID);
        return m != null ? m.get(name) : null;
    }

    public static String getFirstHomeName(UUID playerUUID) {
        LinkedHashMap<String, Location> m = homes.get(playerUUID);
        if (m == null || m.isEmpty()) return null;
        return m.keySet().iterator().next();
    }

    public static Set<String> getHomeNames(UUID playerUUID) {
        LinkedHashMap<String, Location> m = homes.get(playerUUID);
        return m != null ? Collections.unmodifiableSet(m.keySet()) : Collections.emptySet();
    }

    // -------------------------------------------------------------------------
    // Teleport
    // -------------------------------------------------------------------------

    /**
     * Starts the teleport countdown.
     * Pass {@code null} for {@code homeName} to use the first (oldest) home.
     */
    public static boolean startTeleport(Player player, String homeName) {
        UUID uuid = player.getUniqueId();

        if (!hasAnyHome(uuid)) {
            player.sendMessage(Component.text(
                    "You have no home set. Use /sethome <name> first.", NamedTextColor.RED));
            return false;
        }

        String resolvedName = (homeName == null) ? getFirstHomeName(uuid) : homeName;
        Location dest = getHomeLocation(uuid, resolvedName);
        if (dest == null) {
            player.sendMessage(Component.text(
                    "Home '" + resolvedName + "' not found.", NamedTextColor.RED));
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

        Location startLocation = player.getLocation().clone();
        String displayName = resolvedName;

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
                            "Teleporting to '" + displayName + "' in " + remaining + "s…",
                            NamedTextColor.YELLOW));
                    remaining--;
                } else {
                    activeTeleports.remove(uuid);
                    cancel();
                    player.teleport(dest);
                    player.sendActionBar(Component.text(
                            "Teleported to '" + displayName + "'!", NamedTextColor.GREEN));
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
