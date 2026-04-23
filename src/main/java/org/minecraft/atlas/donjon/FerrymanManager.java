package org.minecraft.atlas.donjon;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.persistence.PersistentDataType;
import org.minecraft.atlas.Atlas;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FerrymanManager {

    private static NamespacedKey KEY_FERRYMAN;
    private static long cooldownMs = 10_000L;

    private static final Map<UUID, Long> cooldownExpiry = new HashMap<>();

    public static void init() {
        KEY_FERRYMAN = new NamespacedKey(Atlas.instance, "ferryman_npc");
    }

    public static void loadConfig(FileConfiguration config) {
        cooldownMs = config.getLong("donjon.ferryman_teleport_cooldown_seconds", 10L) * 1000L;
    }

    public static NamespacedKey getKey() {
        return KEY_FERRYMAN;
    }

    public static void spawnFerryman(Location location) {
        location.getWorld().spawn(location, WanderingTrader.class, trader -> {
            trader.setAI(false);
            trader.setInvulnerable(true);
            trader.setGravity(true);
            trader.customName(Component.text("⚓ Ferryman", NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            trader.setCustomNameVisible(true);
            trader.getPersistentDataContainer().set(KEY_FERRYMAN, PersistentDataType.STRING, "FERRYMAN");
        });
    }

    public static boolean isFerrymanNpc(Entity entity) {
        if (!(entity instanceof WanderingTrader trader)) return false;
        return "FERRYMAN".equals(trader.getPersistentDataContainer()
                .get(KEY_FERRYMAN, PersistentDataType.STRING));
    }

    public static boolean isOnCooldown(UUID playerUUID) {
        Long expiry = cooldownExpiry.get(playerUUID);
        if (expiry == null) return false;
        if (System.currentTimeMillis() >= expiry) {
            cooldownExpiry.remove(playerUUID);
            return false;
        }
        return true;
    }

    public static long getRemainingCooldown(UUID playerUUID) {
        Long expiry = cooldownExpiry.get(playerUUID);
        if (expiry == null) return 0L;
        return Math.max(0L, expiry - System.currentTimeMillis());
    }

    public static void startCooldown(UUID playerUUID) {
        cooldownExpiry.put(playerUUID, System.currentTimeMillis() + cooldownMs);
    }
}
