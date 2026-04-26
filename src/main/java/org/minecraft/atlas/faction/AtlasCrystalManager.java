package org.minecraft.atlas.faction;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.minecraft.atlas.Atlas;

import org.bukkit.configuration.ConfigurationSection;

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
    private static NamespacedKey KEY_NAMETAG_DISPLAY;

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

    private static NamespacedKey getKeyNametagDisplay() {
        if (KEY_NAMETAG_DISPLAY == null)
            KEY_NAMETAG_DISPLAY = new NamespacedKey(Atlas.instance, "atlas_crystal_nametag_display");
        return KEY_NAMETAG_DISPLAY;
    }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    /** HP regenerated per second when the crystal is eligible (loaded from config). */
    public static double regenPerSecond         = 1.5;
    /** Base immunity in ms for the first crystal defeat (loaded from config). */
    public static long   immunityBaseMs         = 18_000_000L;
    /** Escalation multiplier applied per repeat defeat within the active window (loaded from config). */
    public static int    immunityMultiplierBase = 2;
    /** Minimum ms since the last hit before a crystal can regenerate HP (loaded from config). */
    public static long   regenTimeoutMs         = 60_000L;

    // -------------------------------------------------------------------------
    // Config loading
    // -------------------------------------------------------------------------

    public static void loadConfig(FileConfiguration config) {
        regenPerSecond         = config.getDouble("crystal.regen_per_second", 1.5);
        immunityBaseMs         = config.getLong("crystal.immunity_base_seconds", 18000L) * 1000L;
        immunityMultiplierBase = config.getInt("crystal.immunity_multiplier", 2);
        regenTimeoutMs         = config.getLong("crystal.regen_timeout_seconds", 60L) * 1000L;
        loadCrystalHomes(config);
    }

    public static void loadCrystalHomes(FileConfiguration config) {
        factionHomes.clear();
        ConfigurationSection sec = config.getConfigurationSection("crystal_homes");
        if (sec == null) return;
        for (String uuidStr : sec.getKeys(false)) {
            ConfigurationSection entry = sec.getConfigurationSection(uuidStr);
            if (entry == null) continue;
            String factionName = entry.getString("faction");
            String homeStr = entry.getString("home");
            if (factionName == null || homeStr == null) continue;
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                continue;
            }
            Location home = decodeHome(homeStr);
            if (home == null) continue;
            factionHomes.computeIfAbsent(factionName, k -> new LinkedHashMap<>()).put(uuid, home);
        }
    }

    public static void saveCrystalHomes(FileConfiguration config) {
        config.set("crystal_homes", null);
        if (factionHomes.isEmpty()) return;
        ConfigurationSection sec = config.createSection("crystal_homes");
        for (Map.Entry<String, LinkedHashMap<UUID, Location>> fEntry : factionHomes.entrySet()) {
            for (Map.Entry<UUID, Location> hEntry : fEntry.getValue().entrySet()) {
                ConfigurationSection entry = sec.createSection(hEntry.getKey().toString());
                entry.set("faction", fEntry.getKey());
                entry.set("home", encodeHome(hEntry.getValue()));
            }
        }
    }

    /** entityUUID → AtlasCrystal */
    private static final Map<UUID, AtlasCrystal> crystals = new HashMap<>();

    /**
     * factionName → (crystalEntityUUID → AtlasCrystal).
     * UUID is the crystal entity's UUID — unique and stable, names are cosmetic only.
     * LinkedHashMap preserves insertion order so the "first" crystal is stable.
     */
    private static final Map<String, Map<UUID, AtlasCrystal>> factionCrystals = new HashMap<>();

    /** playerUUID → AtlasCrystal awaiting a name from the naming dialog. */
    private static final Map<UUID, AtlasCrystal> pendingNaming = new ConcurrentHashMap<>();

    /**
     * factionName → (crystalEntityUUID → home location).
     * Persisted to config.yml so homes are accessible even when the crystal entity is unloaded
     * (e.g. the player is in a different world or the chunk is unloaded).
     */
    private static final Map<String, LinkedHashMap<UUID, Location>> factionHomes = new LinkedHashMap<>();

    // -------------------------------------------------------------------------
    // Register / restore
    // -------------------------------------------------------------------------

    /** Registers a newly spawned atlas crystal with base HP and max HP of 50. */
    public static AtlasCrystal register(EnderCrystal entity, String factionName) {
        double base = AtlasCrystal.BASE_MAX_HP;

        AtlasCrystal crystal = new AtlasCrystal(entity, factionName, base, base);
        entity.setCustomNameVisible(false);
        entity.getPersistentDataContainer().set(getKeyFaction(), PersistentDataType.STRING, factionName);
        entity.getPersistentDataContainer().set(getKeyHp(), PersistentDataType.DOUBLE, base);
        entity.getPersistentDataContainer().set(getKeyMaxHp(), PersistentDataType.DOUBLE, base);
        crystals.put(entity.getUniqueId(), crystal);
        crystal.updateNametag();

        return crystal;
    }

    /** Finalises the crystal's name after the player provides it via dialog. */
    public static void assignName(AtlasCrystal crystal, String name) {
        crystal.setName(name);
        factionCrystals
                .computeIfAbsent(crystal.getFactionName(), k -> new LinkedHashMap<>())
                .put(crystal.getEntity().getUniqueId(), crystal);
        crystal.getEntity().getPersistentDataContainer()
                .set(getKeyName(), PersistentDataType.STRING, name);
        crystal.updateNametag();
    }

    /**
     * Persists a crystal's home location to PDC and to config.yml. Call after setting crystal.setHome().
     */
    public static void saveHome(AtlasCrystal crystal) {
        Location home = crystal.getHome();

        if (home == null || home.getWorld() == null) return;

        String encoded = encodeHome(home);
        crystal.getEntity().getPersistentDataContainer()
                .set(getKeyHome(), PersistentDataType.STRING, encoded);

        factionHomes.computeIfAbsent(crystal.getFactionName(), k -> new LinkedHashMap<>())
                .put(crystal.getEntity().getUniqueId(), home);
        saveCrystalHomes(Atlas.factionsConfig);
        Atlas.saveFactionsConfig();
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

        // Encode applied upgrades as comma-separated upgrade levels (e.g. "1,3,5")
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
        entity.setCustomNameVisible(false);

        // Restore home
        String homeStr = entity.getPersistentDataContainer().get(getKeyHome(), PersistentDataType.STRING);
        if (homeStr != null) {
            Location home = decodeHome(homeStr);
            crystal.setHome(home);
            if (home != null) {
                factionHomes.computeIfAbsent(factionName, k -> new LinkedHashMap<>())
                        .put(entity.getUniqueId(), home);
            }
        }

        // Restore applied upgrades (stored as comma-separated upgrade levels)
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

        // Restore TextDisplay UUID
        String displayUUIDStr = entity.getPersistentDataContainer().get(getKeyNametagDisplay(), PersistentDataType.STRING);
        if (displayUUIDStr != null) {
            try { crystal.setTextDisplayUUID(UUID.fromString(displayUUIDStr)); }
            catch (IllegalArgumentException ignored) {}
        }

        crystals.put(entity.getUniqueId(), crystal);

        if (!savedName.isEmpty()) {
            factionCrystals
                    .computeIfAbsent(factionName, k -> new LinkedHashMap<>())
                    .put(entity.getUniqueId(), crystal);
        }

        crystal.updateNametag();

        return crystal;
    }

    // -------------------------------------------------------------------------
    // Nametag display (TextDisplay)
    // -------------------------------------------------------------------------

    /**
     * Spawns or updates the TextDisplay entity used as this crystal's overhead nametag.
     * Stores the display's UUID in the crystal and its PDC for persistence.
     */
    public static void spawnOrUpdateNametagDisplay(AtlasCrystal crystal) {
        Location loc = crystal.getEntity().getLocation().add(0, 2.5, 0);

        TextDisplay display = null;
        UUID existingUUID = crystal.getTextDisplayUUID();
        if (existingUUID != null) {
            Entity e = Bukkit.getEntity(existingUUID);
            if (e instanceof TextDisplay td) display = td;
        }

        if (display == null) {
            if (!loc.isChunkLoaded()) return;
            display = loc.getWorld().spawn(loc, TextDisplay.class, td -> {
                td.setBillboard(Display.Billboard.CENTER);
                td.setPersistent(true);
                td.setInvulnerable(true);
                td.setGravity(false);
            });
            crystal.setTextDisplayUUID(display.getUniqueId());
            crystal.getEntity().getPersistentDataContainer()
                    .set(getKeyNametagDisplay(), PersistentDataType.STRING, display.getUniqueId().toString());
        }

        Component text = crystal.buildNametagComponent();
        display.text(text);
    }

    /** Removes the TextDisplay nametag entity for a crystal, if present. */
    private static void removeNametagDisplay(AtlasCrystal crystal) {
        UUID displayUUID = crystal.getTextDisplayUUID();
        if (displayUUID == null) return;
        Entity e = Bukkit.getEntity(displayUUID);
        if (e != null) e.remove();
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

    /** Returns true if this faction has any crystal with the given display name. */
    public static boolean hasCrystalWithName(String factionName, String crystalName) {
        Map<UUID, AtlasCrystal> map = factionCrystals.get(factionName);
        if (map == null) return false;
        for (AtlasCrystal c : map.values()) {
            if (crystalName.equals(c.getName())) return true;
        }
        return false;
    }

    /** Returns the first crystal in this faction with the given display name, or null. */
    public static AtlasCrystal getCrystalByName(String factionName, String crystalName) {
        Map<UUID, AtlasCrystal> map = factionCrystals.get(factionName);
        if (map == null) return null;
        for (AtlasCrystal c : map.values()) {
            if (crystalName.equals(c.getName())) return c;
        }
        return null;
    }

    /** Returns all named crystals for a faction in insertion order. */
    public static Collection<AtlasCrystal> getFactionCrystals(String factionName) {
        Map<UUID, AtlasCrystal> map = factionCrystals.get(factionName);

        if (map == null) return Collections.emptyList();

        return map.values();
    }

    /** Returns the home of the first crystal with a home set for this faction, or null if none. */
    public static Location getFirstHome(String factionName) {
        // Check loaded crystals first
        Map<UUID, AtlasCrystal> map = factionCrystals.get(factionName);
        if (map != null) {
            for (AtlasCrystal c : map.values()) {
                if (c.getHome() != null) return c.getHome();
            }
        }
        // Fall back to persisted homes (covers unloaded chunks / different worlds)
        LinkedHashMap<UUID, Location> homes = factionHomes.get(factionName);
        if (homes != null && !homes.isEmpty()) {
            return homes.values().iterator().next();
        }
        return null;
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
     * Also, re-keys the factionCrystals map so that lookups by new name work correctly.
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
        Map<UUID, AtlasCrystal> map = factionCrystals.remove(oldName);
        if (map != null) factionCrystals.put(newName, map);

        // Re-key the factionHomes map
        LinkedHashMap<UUID, Location> homes = factionHomes.remove(oldName);
        if (homes != null) factionHomes.put(newName, homes);
    }

    /**
     * Renames a crystal within a faction. Names are cosmetic — duplicates are allowed.
     * Returns false if no crystal with oldName is found in this faction.
     */
    public static boolean renameCrystal(String factionName, String oldName, String newName) {
        Map<UUID, AtlasCrystal> map = factionCrystals.get(factionName);
        if (map == null) return false;

        AtlasCrystal found = null;
        for (AtlasCrystal c : map.values()) {
            if (oldName.equals(c.getName())) { found = c; break; }
        }
        if (found == null) return false;

        found.setName(newName);
        found.getEntity().getPersistentDataContainer()
                .set(getKeyName(), PersistentDataType.STRING, newName);
        found.updateNametag();

        return true;
    }

    public static void remove(UUID entityUUID) {
        AtlasCrystal crystal = crystals.remove(entityUUID);

        if (crystal == null) return;

        removeNametagDisplay(crystal);

        Map<UUID, AtlasCrystal> map = factionCrystals.get(crystal.getFactionName());
        if (map != null) map.remove(entityUUID);

        LinkedHashMap<UUID, Location> homes = factionHomes.get(crystal.getFactionName());
        if (homes != null) homes.remove(entityUUID);
    }

    /** Removes and despawns all crystals belonging to a faction. Called when a faction is disbanded. */
    public static void removeAllForFaction(String factionName) {
        Map<UUID, AtlasCrystal> map = factionCrystals.remove(factionName);

        if (map == null) return;

        for (AtlasCrystal crystal : map.values()) {
            crystals.remove(crystal.getEntity().getUniqueId());
            removeNametagDisplay(crystal);

            if (!crystal.getEntity().isDead()) {
                crystal.getEntity().remove();
            }
        }

        factionHomes.remove(factionName);
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
                        removeNametagDisplay(crystal);
                        Map<UUID, AtlasCrystal> map = factionCrystals.get(crystal.getFactionName());
                        if (map != null) map.remove(crystal.getEntity().getUniqueId());
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

    private static String encodeHome(Location home) {
        return home.getWorld().getName() + ","
                + home.getX() + "," + home.getY() + "," + home.getZ() + ","
                + home.getYaw() + "," + home.getPitch();
    }

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
