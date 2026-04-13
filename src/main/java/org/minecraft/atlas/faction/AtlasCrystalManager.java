package org.minecraft.atlas.faction;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.minecraft.atlas.Atlas;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AtlasCrystalManager {

    // -------------------------------------------------------------------------
    // PDC keys (lazy-init)
    // -------------------------------------------------------------------------

    private static NamespacedKey KEY_FACTION;
    private static NamespacedKey KEY_HP;
    private static NamespacedKey KEY_MAX_HP;
    private static NamespacedKey KEY_NAME;
    private static NamespacedKey KEY_HOME;
    private static NamespacedKey KEY_UPGRADES;
    private static NamespacedKey KEY_IMMUNE_UNTIL;

    public static NamespacedKey getKeyFaction() {
        if (KEY_FACTION == null) KEY_FACTION = new NamespacedKey(Atlas.instance, "atlas_crystal_faction");
        return KEY_FACTION;
    }

    private static NamespacedKey getKeyHp() {
        if (KEY_HP == null) KEY_HP = new NamespacedKey(Atlas.instance, "atlas_crystal_hp");
        return KEY_HP;
    }

    private static NamespacedKey getKeyMaxHp() {
        if (KEY_MAX_HP == null) KEY_MAX_HP = new NamespacedKey(Atlas.instance, "atlas_crystal_max_hp");
        return KEY_MAX_HP;
    }

    public static NamespacedKey getKeyName() {
        if (KEY_NAME == null) KEY_NAME = new NamespacedKey(Atlas.instance, "atlas_crystal_name");
        return KEY_NAME;
    }

    private static NamespacedKey getKeyHome() {
        if (KEY_HOME == null) KEY_HOME = new NamespacedKey(Atlas.instance, "atlas_crystal_home");
        return KEY_HOME;
    }

    private static NamespacedKey getKeyUpgrades() {
        if (KEY_UPGRADES == null)
            KEY_UPGRADES = new NamespacedKey(Atlas.instance, "atlas_crystal_upgrades");
        return KEY_UPGRADES;
    }

    private static NamespacedKey getKeyImmuneUntil() {
        if (KEY_IMMUNE_UNTIL == null)
            KEY_IMMUNE_UNTIL = new NamespacedKey(Atlas.instance, "atlas_crystal_immune_until");
        return KEY_IMMUNE_UNTIL;
    }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    /** HP regenerated per second when the crystal is eligible (loaded from config). */
    public static double regenPerSecond      = 1.5;
    /** Immunity duration in ms granted after a checkpoint level drop (loaded from config). */
    public static long   immunityDurationMs  = 3_600_000L;
    /** Minimum ms since the last hit before a crystal can regenerate HP (loaded from config). */
    public static long   regenTimeoutMs      = 60_000L;

    // -------------------------------------------------------------------------
    // Config loading
    // -------------------------------------------------------------------------

    public static void loadConfig(FileConfiguration config) {
        regenPerSecond     = config.getDouble("crystal.regen_per_second", 1.5);
        immunityDurationMs = config.getLong("crystal.immunity_duration_seconds", 3600L) * 1000L;
        regenTimeoutMs     = config.getLong("crystal.regen_timeout_seconds", 60L) * 1000L;
    }

    /** entityUUID → AtlasCrystal */
    private static final Map<UUID, AtlasCrystal> crystals = new HashMap<>();

    /**
     * factionName → (crystalName → AtlasCrystal).
     * LinkedHashMap preserves insertion order so the "first" crystal is stable.
     */
    private static final Map<String, Map<String, AtlasCrystal>> factionCrystals = new HashMap<>();

    /** playerUUID → AtlasCrystal awaiting a name from the naming dialog. */
    private static final Map<UUID, AtlasCrystal> pendingNaming = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Register / restore
    // -------------------------------------------------------------------------

    /** Registers a newly spawned atlas crystal with base HP and max HP of 50. */
    public static AtlasCrystal register(EnderCrystal entity, String factionName) {
        double base = AtlasCrystal.BASE_MAX_HP;

        AtlasCrystal crystal = new AtlasCrystal(entity, factionName, base, base);
        crystal.updateNametag();
        entity.getPersistentDataContainer().set(getKeyFaction(), PersistentDataType.STRING, factionName);
        entity.getPersistentDataContainer().set(getKeyHp(), PersistentDataType.DOUBLE, base);
        entity.getPersistentDataContainer().set(getKeyMaxHp(), PersistentDataType.DOUBLE, base);
        crystals.put(entity.getUniqueId(), crystal);

        return crystal;
    }

    /** Finalises the crystal's name after the player provides it via dialog. */
    public static void assignName(AtlasCrystal crystal, String name) {
        crystal.setName(name);
        factionCrystals
                .computeIfAbsent(crystal.getFactionName(), k -> new LinkedHashMap<>())
                .put(name, crystal);
        crystal.getEntity().getPersistentDataContainer()
                .set(getKeyName(), PersistentDataType.STRING, name);
        crystal.updateNametag();
    }

    /** Persists a crystal's home location to PDC. Call after setting crystal.setHome(). */
    public static void saveHome(AtlasCrystal crystal) {
        Location home = crystal.getHome();

        if (home == null || home.getWorld() == null) return;

        String encoded = home.getWorld().getName() + ","
                + home.getX() + "," + home.getY() + "," + home.getZ() + ","
                + home.getYaw() + "," + home.getPitch();
        crystal.getEntity().getPersistentDataContainer()
                .set(getKeyHome(), PersistentDataType.STRING, encoded);
    }

    /**
     * Persists the full mutable state of a crystal to PDC (hp, maxHp, upgrades, immuneUntil).
     * Call after any significant state change (damage, level drop, upgrade applied).
     */
    public static void persistCrystalState(AtlasCrystal crystal) {
        var pdc = crystal.getEntity().getPersistentDataContainer();
        pdc.set(getKeyHp(), PersistentDataType.DOUBLE, crystal.getHp());
        pdc.set(getKeyMaxHp(), PersistentDataType.DOUBLE, crystal.getMaxHp());
        pdc.set(getKeyImmuneUntil(), PersistentDataType.LONG, crystal.getImmuneUntilMillis());

        // Encode applied upgrades as comma-separated checkpoint levels (e.g. "1,3,5")
        StringBuilder sb = new StringBuilder();
        for (int cp : crystal.getAppliedUpgrades()) {
            if (!sb.isEmpty()) sb.append(',');
            sb.append(cp);
        }
        pdc.set(getKeyUpgrades(), PersistentDataType.STRING, sb.toString());
    }

    /** Restores an atlas crystal from PDC when its chunk is loaded. */
    public static AtlasCrystal restore(EnderCrystal entity) {
        String factionName = entity.getPersistentDataContainer().get(getKeyFaction(), PersistentDataType.STRING);
        if (factionName == null) return null;

        Double savedHp = entity.getPersistentDataContainer().get(getKeyHp(), PersistentDataType.DOUBLE);
        double hp = savedHp != null ? savedHp : AtlasCrystal.BASE_MAX_HP;

        Double savedMaxHp = entity.getPersistentDataContainer().get(getKeyMaxHp(), PersistentDataType.DOUBLE);
        double maxHp = savedMaxHp != null ? savedMaxHp : AtlasCrystal.BASE_MAX_HP;

        String savedName = entity.getPersistentDataContainer().get(getKeyName(), PersistentDataType.STRING);
        if (savedName == null) savedName = "";

        AtlasCrystal crystal = new AtlasCrystal(entity, factionName, hp, maxHp);
        crystal.setName(savedName);

        // Restore home
        String homeStr = entity.getPersistentDataContainer().get(getKeyHome(), PersistentDataType.STRING);
        if (homeStr != null) {
            Location home = decodeHome(homeStr);
            crystal.setHome(home);
        }

        // Restore applied upgrades (stored as comma-separated checkpoint levels)
        String upgradesStr = entity.getPersistentDataContainer().get(getKeyUpgrades(), PersistentDataType.STRING);
        if (upgradesStr != null && !upgradesStr.isBlank()) {
            for (String part : upgradesStr.split(",")) {
                try {
                    crystal.restoreUpgrade(Integer.parseInt(part.trim()));
                } catch (NumberFormatException ignored) {}
            }
        }

        // Restore immunity timestamp
        Long savedImmune = entity.getPersistentDataContainer().get(getKeyImmuneUntil(), PersistentDataType.LONG);
        if (savedImmune != null) {
            crystal.setImmuneUntilMillis(savedImmune);
        }

        crystal.updateNametag();
        crystals.put(entity.getUniqueId(), crystal);

        if (!savedName.isEmpty()) {
            factionCrystals
                    .computeIfAbsent(factionName, k -> new LinkedHashMap<>())
                    .put(savedName, crystal);
        }

        return crystal;
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    public static AtlasCrystal getCrystal(UUID entityUUID) {
        return crystals.get(entityUUID);
    }

    public static boolean isAtlasCrystal(UUID entityUUID) {
        return crystals.containsKey(entityUUID);
    }

    public static boolean hasCrystalWithName(String factionName, String crystalName) {
        Map<String, AtlasCrystal> map = factionCrystals.get(factionName);

        return map != null && map.containsKey(crystalName);
    }

    public static AtlasCrystal getCrystalByName(String factionName, String crystalName) {
        Map<String, AtlasCrystal> map = factionCrystals.get(factionName);

        if (map == null) return null;

        return map.get(crystalName);
    }

    /** Returns all named crystals for a faction in insertion order. */
    public static Collection<AtlasCrystal> getFactionCrystals(String factionName) {
        Map<String, AtlasCrystal> map = factionCrystals.get(factionName);

        if (map == null) return Collections.emptyList();

        return map.values();
    }

    /** Returns the home of the first (oldest) named crystal placed by this faction, or null if none. */
    public static Location getFirstHome(String factionName) {
        Map<String, AtlasCrystal> map = factionCrystals.get(factionName);

        if (map == null || map.isEmpty()) return null;

        return map.values().iterator().next().getHome();
    }

    // -------------------------------------------------------------------------
    // Pending naming
    // -------------------------------------------------------------------------

    public static void setPendingNaming(UUID playerUUID, AtlasCrystal crystal) {
        pendingNaming.put(playerUUID, crystal);
    }

    public static AtlasCrystal getPendingNaming(UUID playerUUID) {
        return pendingNaming.get(playerUUID);
    }

    public static void clearPendingNaming(UUID playerUUID) {
        pendingNaming.remove(playerUUID);
    }

    // -------------------------------------------------------------------------
    // Mutations
    // -------------------------------------------------------------------------

    /**
     * Updates the faction name stored on every crystal that belongs to {@code oldName}.
     * Also re-keys the factionCrystals map so that lookups by new name work correctly.
     * Call this after the faction has already been renamed in FactionManager.
     */
    public static void renameFactionCrystals(String oldName, String newName) {
        // Update every registered crystal whose faction matches the old name
        for (AtlasCrystal crystal : crystals.values()) {
            if (crystal.getFactionName().equals(oldName)) {
                crystal.setFactionName(newName);
                crystal.getEntity().getPersistentDataContainer()
                        .set(getKeyFaction(), PersistentDataType.STRING, newName);
                crystal.updateNametag();
            }
        }
        // Re-key the factionCrystals map
        Map<String, AtlasCrystal> map = factionCrystals.remove(oldName);

        if (map != null) factionCrystals.put(newName, map);
    }

    /** Renames a crystal within a faction. Returns false if old name not found or new name taken. */
    public static boolean renameCrystal(String factionName, String oldName, String newName) {
        Map<String, AtlasCrystal> map = factionCrystals.get(factionName);

        if (map == null || !map.containsKey(oldName)) return false;
        if (map.containsKey(newName)) return false;

        AtlasCrystal crystal = map.remove(oldName);
        crystal.setName(newName);
        map.put(newName, crystal);
        crystal.getEntity().getPersistentDataContainer()
                .set(getKeyName(), PersistentDataType.STRING, newName);
        crystal.updateNametag();

        return true;
    }

    public static void remove(UUID entityUUID) {
        AtlasCrystal crystal = crystals.remove(entityUUID);

        if (crystal == null) return;

        String name = crystal.getName();

        if (name != null && !name.isEmpty()) {
            Map<String, AtlasCrystal> map = factionCrystals.get(crystal.getFactionName());
            if (map != null) map.remove(name);
        }
    }

    /** Removes and despawns all crystals belonging to a faction. Called when a faction is disbanded. */
    public static void removeAllForFaction(String factionName) {
        Map<String, AtlasCrystal> map = factionCrystals.remove(factionName);

        if (map == null) return;

        for (AtlasCrystal crystal : map.values()) {
            crystals.remove(crystal.getEntity().getUniqueId());

            if (!crystal.getEntity().isDead()) {
                crystal.getEntity().remove();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Regen scheduler
    // -------------------------------------------------------------------------

    /** Starts the 1-second regen ticker. Call once from Atlas.onEnable. */
    public static void schedule(Plugin plugin) {
        new BukkitRunnable() {
            @Override
            public void run() {
                Iterator<Map.Entry<UUID, AtlasCrystal>> iter = crystals.entrySet().iterator();
                while (iter.hasNext()) {
                    AtlasCrystal crystal = iter.next().getValue();

                    if (crystal.getEntity().isDead()) {
                        String name = crystal.getName();

                        if (name != null && !name.isEmpty()) {
                            Map<String, AtlasCrystal> map = factionCrystals.get(crystal.getFactionName());
                            if (map != null) map.remove(name);
                        }
                        iter.remove();

                        continue;
                    }

                    if (crystal.canRegen() && crystal.getHp() < crystal.getMaxHp()) {
                        crystal.regen(regenPerSecond);
                        crystal.getEntity().getPersistentDataContainer()
                                .set(getKeyHp(), PersistentDataType.DOUBLE, crystal.getHp());
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Location decodeHome(String encoded) {
        String[] parts = encoded.split(",", 6);
        if (parts.length != 6) return null;
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        try {
            return new Location(world,
                    Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]),
                    Double.parseDouble(parts[3]),
                    Float.parseFloat(parts[4]),
                    Float.parseFloat(parts[5]));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
