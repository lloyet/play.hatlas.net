package org.minecraft.atlas.combat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Combat-log protection: when a player is hit by another player (melee or projectile),
 * both attacker and victim are flagged for {@code timeoutSeconds}. If they disconnect
 * before the timer expires, they are killed and their inventory is dropped at their
 * last location — preventing escape from PvP via /quit.
 */
public class CombatLogManager implements Listener {

    private static int timeoutSeconds = 20;
    private static final Map<UUID, Long> combatUntil = new ConcurrentHashMap<>();
    private static volatile boolean shuttingDown = false;

    public static void loadConfig(FileConfiguration config) {
        timeoutSeconds = config.getInt("combat_log.timeout_seconds", 20);
    }

    /**
     * Restores in-combat timers from {@code combats-data.yml}. Expired entries are skipped
     * so a long server outage does not resurrect stale fights.
     */
    public static void loadCombatData(YamlConfiguration config) {
        combatUntil.clear();
        ConfigurationSection section = config.getConfigurationSection("combat_log.in_combat");
        if (section == null) return;
        long now = System.currentTimeMillis();
        for (String key : section.getKeys(false)) {
            long until = section.getLong(key);
            if (until <= now) continue;
            try {
                combatUntil.put(UUID.fromString(key), until);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    /**
     * Persists in-combat timers (UUID → until-millis) so a server restart does not erase
     * an active fight. Expired entries are dropped on save.
     */
    public static void saveCombatData(YamlConfiguration config) {
        config.set("combat_log.in_combat", null);
        long now = System.currentTimeMillis();
        Map<String, Long> snapshot = new HashMap<>();
        for (Map.Entry<UUID, Long> e : combatUntil.entrySet()) {
            if (e.getValue() > now) snapshot.put(e.getKey().toString(), e.getValue());
        }
        if (!snapshot.isEmpty()) {
            for (Map.Entry<String, Long> e : snapshot.entrySet()) {
                config.set("combat_log.in_combat." + e.getKey(), e.getValue());
            }
        }
    }

    /** Suppresses the combat-logout kill during a legitimate server shutdown. */
    public static void setShuttingDown(boolean value) {
        shuttingDown = value;
    }

    public static int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public static boolean isInCombat(UUID uuid) {
        Long until = combatUntil.get(uuid);
        if (until == null) return false;
        return System.currentTimeMillis() < until;
    }

    /** Periodic tick that retires expired entries and sends the "combat ended" action bar. */
    public static void schedule(Plugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, CombatLogManager::tick, 20L, 20L);
    }

    private static void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = combatUntil.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            if (now < entry.getValue()) continue;
            it.remove();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                player.sendActionBar(Component.text(
                        "Combat ended — safe to disconnect.", NamedTextColor.GREEN));
            }
        }
    }

    private static void mark(Player player) {
        boolean wasInCombat = isInCombat(player.getUniqueId());
        combatUntil.put(player.getUniqueId(), System.currentTimeMillis() + timeoutSeconds * 1000L);
        if (!wasInCombat) {
            player.sendActionBar(Component.text(
                    "In combat — disconnecting for " + timeoutSeconds + "s will kill you.",
                    NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile proj
                && proj.getShooter() instanceof Player shooter) {
            attacker = shooter;
        }
        if (attacker == null) return;
        if (attacker.getUniqueId().equals(victim.getUniqueId())) return;

        mark(attacker);
        mark(victim);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCombatLogout(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!isInCombat(uuid)) return;
        // Server is stopping — preserve combat state in the file, do not penalise the player.
        if (shuttingDown) return;
        combatUntil.remove(uuid);

        Location loc = player.getLocation();
        World world = loc.getWorld();
        PlayerInventory inv = player.getInventory();

        if (world != null) {
            for (ItemStack item : inv.getContents()) {
                if (item != null && !item.getType().isAir()) {
                    world.dropItemNaturally(loc, item);
                }
            }
            for (ItemStack armor : inv.getArmorContents()) {
                if (armor != null && !armor.getType().isAir()) {
                    world.dropItemNaturally(loc, armor);
                }
            }
        }
        inv.clear();
        player.setHealth(0);

        Bukkit.broadcast(Component.text(
                player.getName() + " disconnected during combat and was killed.",
                NamedTextColor.RED));
    }
}
