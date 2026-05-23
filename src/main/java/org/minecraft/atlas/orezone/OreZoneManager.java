package org.minecraft.atlas.orezone;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Owns all {@link OreZone} instances. Holds the default ore-weight template
 * loaded from {@code orezones.yml}, drives the periodic refill scheduler at
 * {@link #intervalResetHours}, and serializes runtime state to
 * {@code orezones-data.yml}.
 */
public final class OreZoneManager {

    private static final Map<String, OreZone> zones = new LinkedHashMap<>();

    /** Default ore-weight template, loaded from orezones.yml. */
    private static final Map<String, Integer> defaultOreWeights = new LinkedHashMap<>();

    /** Refill interval, in hours. Defaults to 1 if the config is missing. */
    private static long intervalResetHours = 1L;

    private OreZoneManager() {}

    // ── Config (orezones.yml) ──────────────────────────────────────────────────

    public static void loadConfig(FileConfiguration config) {
        defaultOreWeights.clear();
        intervalResetHours = Math.max(1L, config.getLong("interval_reset_hours", 1L));

        ConfigurationSection sec = config.getConfigurationSection("default_ore_weights");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                int weight = sec.getInt(key, 0);
                if (weight > 0 && OreType.fromKey(key) != null) {
                    defaultOreWeights.put(key.toLowerCase(), weight);
                }
            }
        }
        // Safety net: if the config was empty or malformed, fall back to
        // every known ore at weight 1 so newly created zones still work.
        if (defaultOreWeights.isEmpty()) {
            for (OreType t : OreType.values()) defaultOreWeights.put(t.key(), 1);
        }
    }

    public static long getIntervalResetHours() { return intervalResetHours; }

    public static Map<String, Integer> getDefaultOreWeights() {
        return Collections.unmodifiableMap(defaultOreWeights);
    }

    // ── CRUD ───────────────────────────────────────────────────────────────────

    /** Creates a zone with the default weight template. Returns null on name collision. */
    public static OreZone create(String name) {
        String key = name.toLowerCase();
        if (zones.containsKey(key)) return null;
        OreZone z = new OreZone(key);
        z.getOreWeights().putAll(defaultOreWeights);
        zones.put(key, z);
        return z;
    }

    public static OreZone get(String name) {
        return name == null ? null : zones.get(name.toLowerCase());
    }

    public static boolean remove(String name) {
        if (name == null) return false;
        return zones.remove(name.toLowerCase()) != null;
    }

    public static Collection<OreZone> getAll() {
        return Collections.unmodifiableCollection(zones.values());
    }

    // ── Fill ───────────────────────────────────────────────────────────────────

    /**
     * Re-fills every block of the zone's cuboid with a randomly-rolled ore.
     * Returns the number of blocks placed, or -1 if the zone has no bounds
     * or its world is unloaded.
     */
    public static long fill(OreZone zone) {
        if (zone == null || !zone.hasBounds()) return -1;
        World world = zone.getWorld();
        if (world == null) return -1;

        Map<String, Integer> weights = zone.getOreWeights();
        if (weights.isEmpty()) return 0;

        int totalWeight = 0;
        for (int w : weights.values()) totalWeight += Math.max(0, w);
        if (totalWeight <= 0) return 0;

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        long placed = 0;
        for (int x = zone.getMinX(); x <= zone.getMaxX(); x++) {
            for (int y = zone.getMinY(); y <= zone.getMaxY(); y++) {
                for (int z = zone.getMinZ(); z <= zone.getMaxZ(); z++) {
                    OreType pick = pickWeighted(weights, totalWeight, rng);
                    if (pick == null) continue;
                    Block block = world.getBlockAt(x, y, z);
                    pick.placeAt(block);
                    placed++;
                }
            }
        }
        return placed;
    }

    /** Re-fills every zone. Called by the scheduler. */
    public static void fillAll() {
        for (OreZone z : zones.values()) {
            if (z.hasBounds() && z.getWorld() != null) {
                fill(z);
            }
        }
    }

    private static OreType pickWeighted(Map<String, Integer> weights, int totalWeight, ThreadLocalRandom rng) {
        int roll = rng.nextInt(totalWeight);
        int acc = 0;
        for (Map.Entry<String, Integer> e : weights.entrySet()) {
            acc += Math.max(0, e.getValue());
            if (roll < acc) return OreType.fromKey(e.getKey());
        }
        return null;
    }

    // ── Scheduler ──────────────────────────────────────────────────────────────

    public static void schedule(Plugin plugin) {
        // 20 ticks/sec × 60 sec × 60 min × intervalResetHours
        long periodTicks = 20L * 60L * 60L * intervalResetHours;
        Bukkit.getScheduler().runTaskTimer(plugin, OreZoneManager::fillAll, periodTicks, periodTicks);
    }

    // ── Persistence (orezones-data.yml) ────────────────────────────────────────

    public static void load(FileConfiguration data) {
        zones.clear();
        ConfigurationSection root = data.getConfigurationSection("orezones");
        if (root == null) return;

        for (String name : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(name);
            if (sec == null) continue;

            OreZone zone = new OreZone(name.toLowerCase());

            String worldName = sec.getString("world");
            if (worldName != null && sec.contains("min_x")) {
                zone.setRawBounds(worldName,
                        sec.getInt("min_x"), sec.getInt("min_y"), sec.getInt("min_z"),
                        sec.getInt("max_x"), sec.getInt("max_y"), sec.getInt("max_z"));
            }

            ConfigurationSection weightsSec = sec.getConfigurationSection("ore_weights");
            if (weightsSec != null) {
                for (String key : weightsSec.getKeys(false)) {
                    int w = weightsSec.getInt(key, 0);
                    if (w > 0 && OreType.fromKey(key) != null) {
                        zone.getOreWeights().put(key.toLowerCase(), w);
                    }
                }
            }
            // If a saved zone has no weights (corrupted file, hand-edit, etc.),
            // re-seed with the defaults so the next reset still produces blocks.
            if (zone.getOreWeights().isEmpty()) {
                zone.getOreWeights().putAll(defaultOreWeights);
            }

            zones.put(zone.getName(), zone);
        }
    }

    public static void save(FileConfiguration data) {
        data.set("orezones", null);
        if (zones.isEmpty()) return;

        ConfigurationSection root = data.createSection("orezones");
        for (OreZone z : zones.values()) {
            ConfigurationSection sec = root.createSection(z.getName());
            if (z.hasBounds()) {
                sec.set("world", z.getWorldName());
                sec.set("min_x", z.getMinX());
                sec.set("min_y", z.getMinY());
                sec.set("min_z", z.getMinZ());
                sec.set("max_x", z.getMaxX());
                sec.set("max_y", z.getMaxY());
                sec.set("max_z", z.getMaxZ());
            }
            ConfigurationSection weightsSec = sec.createSection("ore_weights");
            for (Map.Entry<String, Integer> e : z.getOreWeights().entrySet()) {
                weightsSec.set(e.getKey(), e.getValue());
            }
        }
    }
}
