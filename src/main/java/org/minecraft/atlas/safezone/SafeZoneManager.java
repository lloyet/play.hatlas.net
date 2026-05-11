package org.minecraft.atlas.safezone;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SafeZoneManager {

    /** name → SafeZone (insertion-ordered for /safezone list). */
    private static final Map<String, SafeZone>      safeZones    = new LinkedHashMap<>();
    /** playerUUID → set of safe zone names the player has visited. */
    private static final Map<UUID, Set<String>>     playerVisits = new ConcurrentHashMap<>();

    // ── Area management ────────────────────────────────────────────────────────

    /** Creates a new safe zone. Returns false if the name is already taken. */
    public static boolean create(String name) {
        String key = name.toLowerCase();
        if (safeZones.containsKey(key)) return false;
        safeZones.put(key, new SafeZone(key));
        return true;
    }

    public static SafeZone get(String name) {
        return name == null ? null : safeZones.get(name.toLowerCase());
    }

    /**
     * Permanently removes a safe zone — its claimed chunks and spawn point go away with it.
     * Visit history for the zone is also cleared. Returns true if a zone was removed.
     */
    public static boolean delete(String name) {
        if (name == null) return false;
        String key = name.toLowerCase();
        SafeZone removed = safeZones.remove(key);
        if (removed == null) return false;
        for (Set<String> visits : playerVisits.values()) visits.remove(key);
        return true;
    }

    public static Collection<SafeZone> getAll() {
        return Collections.unmodifiableCollection(safeZones.values());
    }

    // ── Location queries ───────────────────────────────────────────────────────

    /** Returns true if the location is inside any safe zone. */
    public static boolean isProtected(Location loc) {
        return getAt(loc) != null;
    }

    /** Returns the first safe zone that contains the location, or null. */
    public static SafeZone getAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        for (SafeZone p : safeZones.values()) {
            if (p.contains(loc)) return p;
        }
        return null;
    }

    // ── Visit tracking ─────────────────────────────────────────────────────────

    public static void recordVisit(UUID uuid, String safeZoneName) {
        playerVisits.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet())
                .add(safeZoneName.toLowerCase());
    }

    public static boolean hasVisited(UUID uuid, String safeZoneName) {
        Set<String> visited = playerVisits.get(uuid);
        return visited != null && visited.contains(safeZoneName.toLowerCase());
    }

    // ── Display helpers ────────────────────────────────────────────────────────

    /**
     * Returns a display-friendly form of a safe zone name: underscores become spaces
     * and each word is capitalized. {@code "spawn_island"} → {@code "Spawn Island"}.
     */
    public static String capitalizedName(String name) {
        if (name == null || name.isEmpty()) return name;
        StringBuilder sb = new StringBuilder();
        for (String part : name.split("_")) {
            if (part.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) sb.append(part.substring(1));
        }
        return sb.toString();
    }

    // ── Persistence ────────────────────────────────────────────────────────────

    public static void saveConfig(FileConfiguration safezonesDataConfig) {
        safezonesDataConfig.set("safezones",       null);
        safezonesDataConfig.set("safezone_visits", null);

        if (!safeZones.isEmpty()) {
            ConfigurationSection root = safezonesDataConfig.createSection("safezones");
            for (SafeZone p : safeZones.values()) {
                ConfigurationSection sec = root.createSection(p.getName());
                Map<String, List<String>> byWorld = groupChunkKeysByWorld(p.getChunks());
                if (!byWorld.isEmpty()) {
                    ConfigurationSection chunksSec = sec.createSection("chunks");
                    for (Map.Entry<String, List<String>> e : byWorld.entrySet()) {
                        chunksSec.set(e.getKey(), e.getValue());
                    }
                }
                if (p.getSpawnPoint() != null)
                    sec.set("spawn_point", encodeLocation(p.getSpawnPoint()));
                if (!p.getDescription().isEmpty())
                    sec.set("description", p.getDescription());
            }
        }

        if (!playerVisits.isEmpty()) {
            ConfigurationSection visits = safezonesDataConfig.createSection("safezone_visits");
            for (Map.Entry<UUID, Set<String>> e : playerVisits.entrySet()) {
                if (!e.getValue().isEmpty())
                    visits.set(e.getKey().toString(), new ArrayList<>(e.getValue()));
            }
        }
    }

    public static void loadConfig(FileConfiguration safezonesDataConfig) {
        safeZones.clear();
        playerVisits.clear();

        ConfigurationSection root = safezonesDataConfig.getConfigurationSection("safezones");
        if (root != null) {
            for (String name : root.getKeys(false)) {
                SafeZone p = new SafeZone(name.toLowerCase());
                ConfigurationSection sec = root.getConfigurationSection(name);
                if (sec != null) {
                    ConfigurationSection chunksSec = sec.getConfigurationSection("chunks");
                    if (chunksSec != null) {
                        for (String worldName : chunksSec.getKeys(false)) {
                            for (String keyStr : chunksSec.getStringList(worldName)) {
                                try {
                                    long key = Long.parseLong(keyStr);
                                    int cx = (int) key;
                                    int cz = (int) (key >> 32);
                                    p.getChunks().add(worldName + ":" + cx + ":" + cz);
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                    }
                    String sp = sec.getString("spawn_point");
                    if (sp != null) {
                        Location loc = decodeLocation(sp);
                        if (loc != null) p.setSpawnPoint(loc);
                    }
                    p.setDescription(sec.getString("description", ""));
                }
                safeZones.put(p.getName(), p);
            }
        }

        ConfigurationSection visits = safezonesDataConfig.getConfigurationSection("safezone_visits");
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

    /**
     * Splits the runtime "world:cx:cz" chunk-key strings into a map of
     * {@code worldName → list of stringified chunk-key longs}, mirroring the donjon
     * protected_chunks layout but nested per world.
     */
    private static Map<String, List<String>> groupChunkKeysByWorld(Set<String> runtimeKeys) {
        Map<String, List<String>> byWorld = new LinkedHashMap<>();
        for (String chunkKey : runtimeKeys) {
            String[] parts = chunkKey.split(":");
            if (parts.length != 3) continue;
            try {
                int cx = Integer.parseInt(parts[1]);
                int cz = Integer.parseInt(parts[2]);
                byWorld.computeIfAbsent(parts[0], w -> new ArrayList<>())
                        .add(String.valueOf(Chunk.getChunkKey(cx, cz)));
            } catch (NumberFormatException ignored) {}
        }
        return byWorld;
    }

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
