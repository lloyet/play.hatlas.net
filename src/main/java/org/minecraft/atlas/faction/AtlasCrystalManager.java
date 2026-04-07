package org.minecraft.atlas.faction;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.minecraft.atlas.Atlas;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class AtlasCrystalManager {

    // Lazy-init so Atlas.instance is guaranteed non-null when first used
    private static NamespacedKey KEY_FACTION;
    private static NamespacedKey KEY_HP;

    public static NamespacedKey getKeyFaction() {
        if (KEY_FACTION == null) KEY_FACTION = new NamespacedKey(Atlas.instance, "atlas_crystal_faction");
        return KEY_FACTION;
    }

    private static NamespacedKey getKeyHp() {
        if (KEY_HP == null) KEY_HP = new NamespacedKey(Atlas.instance, "atlas_crystal_hp");
        return KEY_HP;
    }

    // entityUUID → AtlasCrystal
    private static final Map<UUID, AtlasCrystal> crystals = new HashMap<>();
    // factionName → AtlasCrystal (at most one crystal per faction)
    private static final Map<String, AtlasCrystal> factionCrystals = new HashMap<>();

    /**
     * Registers a newly spawned atlas crystal with full HP.
     */
    public static AtlasCrystal register(EnderCrystal entity, String factionName) {
        AtlasCrystal crystal = new AtlasCrystal(entity, factionName, 50.0, 50.0);
        crystal.updateNametag();
        entity.getPersistentDataContainer().set(getKeyFaction(), PersistentDataType.STRING, factionName);
        entity.getPersistentDataContainer().set(getKeyHp(), PersistentDataType.DOUBLE, 50.0);
        crystals.put(entity.getUniqueId(), crystal);
        factionCrystals.put(factionName, crystal);
        return crystal;
    }

    /**
     * Restores an atlas crystal loaded from disk (via EntitiesLoadEvent).
     */
    public static AtlasCrystal restore(EnderCrystal entity) {
        String factionName = entity.getPersistentDataContainer().get(getKeyFaction(), PersistentDataType.STRING);
        if (factionName == null) return null;
        Double savedHp = entity.getPersistentDataContainer().get(getKeyHp(), PersistentDataType.DOUBLE);
        double hp = savedHp != null ? savedHp : 50.0;
        AtlasCrystal crystal = new AtlasCrystal(entity, factionName, hp, 50.0);
        crystal.updateNametag();
        crystals.put(entity.getUniqueId(), crystal);
        factionCrystals.put(factionName, crystal);
        return crystal;
    }

    public static AtlasCrystal getCrystal(UUID entityUUID) {
        return crystals.get(entityUUID);
    }

    /**
     * Returns the active atlas crystal for a faction, or null if none.
     */
    public static AtlasCrystal getFactionCrystal(String factionName) {
        return factionCrystals.get(factionName);
    }

    public static boolean isAtlasCrystal(UUID entityUUID) {
        return crystals.containsKey(entityUUID);
    }

    public static void remove(UUID entityUUID) {
        AtlasCrystal crystal = crystals.remove(entityUUID);
        if (crystal != null) factionCrystals.remove(crystal.getFactionName());
    }

    /**
     * Starts the 1-second regen ticker. Call once from Atlas.onEnable.
     */
    public static void schedule(Plugin plugin) {
        new BukkitRunnable() {
            @Override
            public void run() {
                Iterator<Map.Entry<UUID, AtlasCrystal>> iter = crystals.entrySet().iterator();
                while (iter.hasNext()) {
                    AtlasCrystal crystal = iter.next().getValue();
                    if (crystal.getEntity().isDead()) {
                        factionCrystals.remove(crystal.getFactionName());
                        iter.remove();
                        continue;
                    }
                    if (crystal.canRegen() && crystal.getHp() < crystal.getMaxHp()) {
                        crystal.regen(1.5);
                        crystal.getEntity().getPersistentDataContainer()
                                .set(getKeyHp(), PersistentDataType.DOUBLE, crystal.getHp());
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }
}
