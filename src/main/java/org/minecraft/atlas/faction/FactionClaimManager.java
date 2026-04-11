package org.minecraft.atlas.faction;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.minecraft.atlas.donjon.DonjonManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages territory claims for factions.
 *
 * Layout:
 *  Ring 0  → the single center chunk (claimed when the faction is created)
 *  Ring r  → all chunks at Chebyshev distance exactly r from the center
 *             (ring 1 = 8 chunks, ring 2 = 16 chunks, ring r = 8*r chunks)
 *
 * Each checkpoint level reached adds one more ring.
 * Each checkpoint level lost removes the outermost ring.
 * Disbanding removes every ring including the center.
 */
public class FactionClaimManager {

    /** "worldName:chunkX:chunkZ" → owning faction name */
    private static final Map<String, String> claimedChunks = new HashMap<>();

    /** factionName → {worldName, chunkX (String), chunkZ (String)} */
    private static final Map<String, String[]> factionCenters = new HashMap<>();

    /** factionName → current outer ring radius (0 = only center chunk) */
    private static final Map<String, Integer> factionRingCount = new HashMap<>();

    // -------------------------------------------------------------------------
    // Claim lifecycle
    // -------------------------------------------------------------------------

    /** Claims the initial chunk when a faction is first created. */
    public static void initializeClaim(String factionName, String worldName, int chunkX, int chunkZ) {
        factionCenters.put(factionName, new String[]{worldName, String.valueOf(chunkX), String.valueOf(chunkZ)});
        factionRingCount.put(factionName, 0);
        claimedChunks.put(key(worldName, chunkX, chunkZ), factionName);
    }

    /**
     * Expands claims by one ring outward.
     * Call once each time a checkpoint level is reached.
     */
    public static void expandClaims(String factionName) {
        String[] center = factionCenters.get(factionName);
        if (center == null) return;

        int newRing = factionRingCount.getOrDefault(factionName, 0) + 1;
        factionRingCount.put(factionName, newRing);
        addRing(center[0], Integer.parseInt(center[1]), Integer.parseInt(center[2]), newRing, factionName);
    }

    /**
     * Shrinks claims down to {@code targetRings} by removing the outermost rings.
     * targetRings = number of checkpoints whose level is ≤ the faction's new level.
     */
    public static void shrinkClaimsTo(String factionName, int targetRings) {
        String[] center = factionCenters.get(factionName);
        if (center == null) return;

        String worldName = center[0];
        int cx = Integer.parseInt(center[1]);
        int cz = Integer.parseInt(center[2]);

        int current = factionRingCount.getOrDefault(factionName, 0);
        while (current > targetRings) {
            removeRing(worldName, cx, cz, current);
            current--;
        }
        factionRingCount.put(factionName, targetRings);
    }

    /** Removes all chunk claims when a faction is disbanded. */
    public static void removeAllClaims(String factionName) {
        factionCenters.remove(factionName);
        factionRingCount.remove(factionName);
        claimedChunks.values().removeIf(fn -> fn.equals(factionName));
    }

    /** Updates faction name in all claim maps after a rename. */
    public static void renameFactionClaims(String oldName, String newName) {
        String[] center = factionCenters.remove(oldName);
        if (center != null) factionCenters.put(newName, center);
        Integer rings = factionRingCount.remove(oldName);
        if (rings != null) factionRingCount.put(newName, rings);
        claimedChunks.replaceAll((k, v) -> v.equals(oldName) ? newName : v);
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    /** Returns the faction name that owns the given chunk, or null if unclaimed. */
    public static String getClaimingFaction(String worldName, int chunkX, int chunkZ) {
        return claimedChunks.get(key(worldName, chunkX, chunkZ));
    }

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    public static void saveClaims(FileConfiguration config) {
        config.set("claims", null);
        ConfigurationSection claimsSection = config.createSection("claims");

        for (Map.Entry<String, String[]> entry : factionCenters.entrySet()) {
            String factionName = entry.getKey();
            String[] center = entry.getValue();
            ConfigurationSection s = claimsSection.createSection(factionName);
            s.set("world", center[0]);
            s.set("chunk_x", Integer.parseInt(center[1]));
            s.set("chunk_z", Integer.parseInt(center[2]));
            s.set("rings", factionRingCount.getOrDefault(factionName, 0));
        }
    }

    public static void loadClaims(FileConfiguration config) {
        claimedChunks.clear();
        factionCenters.clear();
        factionRingCount.clear();

        ConfigurationSection claimsSection = config.getConfigurationSection("claims");
        if (claimsSection == null) return;

        for (String factionName : claimsSection.getKeys(false)) {
            ConfigurationSection s = claimsSection.getConfigurationSection(factionName);
            if (s == null) continue;

            String worldName = s.getString("world");
            if (worldName == null) continue;
            int chunkX = s.getInt("chunk_x");
            int chunkZ = s.getInt("chunk_z");
            int rings = s.getInt("rings", 0);

            factionCenters.put(factionName, new String[]{worldName, String.valueOf(chunkX), String.valueOf(chunkZ)});
            factionRingCount.put(factionName, rings);

            // Reconstruct all claimed chunks from center + rings
            claimedChunks.put(key(worldName, chunkX, chunkZ), factionName);
            for (int r = 1; r <= rings; r++) {
                addRing(worldName, chunkX, chunkZ, r, factionName);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static String key(String world, int cx, int cz) {
        return world + ":" + cx + ":" + cz;
    }

    private static void addRing(String world, int cx, int cz, int r, String factionName) {
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                if (Math.max(Math.abs(x - cx), Math.abs(z - cz)) == r) {
                    // Skip chunks that are part of a donjon's protected area
                    if (DonjonManager.isChunkInDonjon(world, x, z)) continue;
                    claimedChunks.put(key(world, x, z), factionName);
                }
            }
        }
    }

    private static void removeRing(String world, int cx, int cz, int r) {
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                if (Math.max(Math.abs(x - cx), Math.abs(z - cz)) == r) {
                    claimedChunks.remove(key(world, x, z));
                }
            }
        }
    }
}
