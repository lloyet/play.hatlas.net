package org.minecraft.atlas.spawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ProtectionManager {

    /** name → Protection (insertion-ordered for /protection list). */
    private static final Map<String, Protection>    protections  = new LinkedHashMap<>();
    /** playerUUID → set of protection names the player has visited. */
    private static final Map<UUID, Set<String>>     playerVisits = new ConcurrentHashMap<>();

    // ── Area management ────────────────────────────────────────────────────────

    /** Creates a new protection area. Returns false if the name is already taken. */
    public static boolean create(String name) {
        String key = name.toLowerCase();
        if (protections.containsKey(key)) return false;
        protections.put(key, new Protection(key));
        return true;
    }

    public static Protection get(String name) {
        return name == null ? null : protections.get(name.toLowerCase());
    }

    public static Collection<Protection> getAll() {
        return Collections.unmodifiableCollection(protections.values());
    }

    // ── Location queries ───────────────────────────────────────────────────────

    /** Returns true if the location is inside any protection area. */
    public static boolean isProtected(Location loc) {
        return getAt(loc) != null;
    }

    /** Returns the first protection area that contains the location, or null. */
    public static Protection getAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        for (Protection p : protections.values()) {
            if (p.contains(loc)) return p;
        }
        return null;
    }

    // ── Visit tracking ─────────────────────────────────────────────────────────

    public static void recordVisit(UUID uuid, String protectionName) {
        playerVisits.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet())
                .add(protectionName.toLowerCase());
    }

    public static boolean hasVisited(UUID uuid, String protectionName) {
        Set<String> visited = playerVisits.get(uuid);
        return visited != null && visited.contains(protectionName.toLowerCase());
    }

    // ── Persistence ────────────────────────────────────────────────────────────

    public static void saveConfig(FileConfiguration config) {
        config.set("protections",       null);
        config.set("protection_visits", null);

        if (!protections.isEmpty()) {
            ConfigurationSection root = config.createSection("protections");
            for (Protection p : protections.values()) {
                ConfigurationSection sec = root.createSection(p.getName());
                sec.set("chunks", new ArrayList<>(p.getChunks()));
                if (p.getSpawnPoint() != null)
                    sec.set("spawn_point", encodeLocation(p.getSpawnPoint()));
            }
        }

        if (!playerVisits.isEmpty()) {
            ConfigurationSection visits = config.createSection("protection_visits");
            for (Map.Entry<UUID, Set<String>> e : playerVisits.entrySet()) {
                if (!e.getValue().isEmpty())
                    visits.set(e.getKey().toString(), new ArrayList<>(e.getValue()));
            }
        }
    }

    public static void loadConfig(FileConfiguration config) {
        protections.clear();
        playerVisits.clear();

        ConfigurationSection root = config.getConfigurationSection("protections");
        if (root != null) {
            for (String name : root.getKeys(false)) {
                Protection p = new Protection(name.toLowerCase());
                ConfigurationSection sec = root.getConfigurationSection(name);
                if (sec != null) {
                    p.getChunks().addAll(sec.getStringList("chunks"));
                    String sp = sec.getString("spawn_point");
                    if (sp != null) {
                        Location loc = decodeLocation(sp);
                        if (loc != null) p.setSpawnPoint(loc);
                    }
                }
                protections.put(p.getName(), p);
            }
        }

        ConfigurationSection visits = config.getConfigurationSection("protection_visits");
        if (visits != null) {
            for (String uuidStr : visits.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    List<String> list = visits.getStringList(uuidStr);
                    if (!list.isEmpty())
                        playerVisits.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).addAll(list);
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    // ── Encoding helpers ───────────────────────────────────────────────────────

    private static String encodeLocation(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getX() + ":" + loc.getY() + ":" + loc.getZ()
                + ":" + loc.getYaw() + ":" + loc.getPitch();
    }

    private static Location decodeLocation(String s) {
        String[] p = s.split(":");
        if (p.length < 4) return null;
        try {
            World world = Bukkit.getWorld(p[0]);
            if (world == null) return null;
            double x = Double.parseDouble(p[1]);
            double y = Double.parseDouble(p[2]);
            double z = Double.parseDouble(p[3]);
            float yaw   = p.length > 4 ? Float.parseFloat(p[4]) : 0f;
            float pitch = p.length > 5 ? Float.parseFloat(p[5]) : 0f;
            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
