package org.minecraft.atlas.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.minecraft.atlas.faction.AtlasCrystal;
import org.minecraft.atlas.faction.AtlasCrystalManager;
import org.minecraft.atlas.faction.Faction;
import org.minecraft.atlas.faction.FactionLevelManager;
import org.minecraft.atlas.faction.FactionManager;
import org.minecraft.atlas.faction.HomeTeleportManager;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FactionListener implements Listener {

    /**
     * Tracks last hit time (ms) per attacker to enforce 1-hit-per-second anti-spam.
     */
    private static final Map<UUID, Long> lastHitTime = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Crystal naming fallback on disconnect
    // -------------------------------------------------------------------------

    /**
     * If a player disconnects while the naming dialog is open, assign a generated fallback name.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        AtlasCrystal pending = AtlasCrystalManager.getPendingNaming(player.getUniqueId());
        if (pending == null) return;

        AtlasCrystalManager.clearPendingNaming(player.getUniqueId());
        String fallback = "Crystal_" + pending.getEntity().getUniqueId().toString().substring(0, 6);
        String candidate = fallback;
        int i = 1;
        while (AtlasCrystalManager.hasCrystalWithName(pending.getFactionName(), candidate)) {
            candidate = fallback + "_" + i++;
        }
        AtlasCrystalManager.assignName(pending, candidate);
    }

    // -------------------------------------------------------------------------
    // Teleport cancel on damage
    // -------------------------------------------------------------------------

    /**
     * Cancels an active /faction home countdown if the player takes damage.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamagedCancelTeleport(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (HomeTeleportManager.cancelTeleport(player.getUniqueId())) {
            player.sendActionBar(Component.text("Teleport cancelled! (took damage)", NamedTextColor.RED));
            player.sendMessage(Component.text(
                    "Teleport to faction home cancelled because you took damage.", NamedTextColor.RED));
        }
    }

    // -------------------------------------------------------------------------
    // Atlas Crystal damage handling
    // -------------------------------------------------------------------------

    /** Cancels all non-entity damage to atlas crystals (fire, explosions, etc.). */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal crystal)) return;
        if (!AtlasCrystalManager.isAtlasCrystal(crystal.getUniqueId())) return;
        if (event instanceof EntityDamageByEntityEvent) return; // handled separately
        event.setCancelled(true);
    }

    /** Applies our HP system when a player hits an atlas crystal. */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onAtlasCrystalDamageByPlayer(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal crystal)) return;
        AtlasCrystal atlasCrystal = AtlasCrystalManager.getCrystal(crystal.getUniqueId());
        if (atlasCrystal == null) return;

        event.setCancelled(true);

        Entity damager = event.getDamager();
        if (!(damager instanceof Player attacker)) return;

        // Players with no faction cannot damage atlas crystals
        String attackerFaction = FactionManager.getPlayerFaction(attacker.getUniqueId());
        if (attackerFaction == null) {
            attacker.sendMessage(Component.text(
                    "You must be in a faction to attack an Atlas Crystal.", NamedTextColor.RED));
            return;
        }

        // Faction members cannot damage their own crystal
        String crystalFaction = atlasCrystal.getFactionName();
        if (crystalFaction.equals(attackerFaction)) return;

        // Immune crystal: show message and bail
        if (atlasCrystal.isImmune()) {
            attacker.sendActionBar(Component.text("This crystal is immune to damage!", NamedTextColor.AQUA)
                    .decorate(TextDecoration.BOLD));
            return;
        }

        // Anti-spam: allow at most 1 hit per second per attacker
        long now = System.currentTimeMillis();
        Long last = lastHitTime.get(attacker.getUniqueId());
        if (last != null && now - last < 1000L) return;
        lastHitTime.put(attacker.getUniqueId(), now);

        double damage = event.getDamage();
        boolean died = atlasCrystal.damage(damage);

        crystal.getPersistentDataContainer()
                .set(AtlasCrystalManager.getKeyFaction(), PersistentDataType.STRING, crystalFaction);

        // Sound and particles on hit
        crystal.getWorld().playSound(
                crystal.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_HURT, SoundCategory.HOSTILE, 1.0f, 1.0f);

        Location loc = crystal.getLocation();
        crystal.getWorld().spawnParticle(Particle.CRIT, loc, 30, 0.4, 0.4, 0.4, 0.25);
        crystal.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, loc, 10, 0.3, 0.3, 0.3, 0.0);
        crystal.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc, 20, 0.5, 0.5, 0.5, 0.1);
        crystal.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 15, 0.4, 0.4, 0.4, 0.05);
        crystal.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 8, 0.3, 0.3, 0.3, 0.02);
        crystal.getWorld().spawnParticle(Particle.EXPLOSION, loc, 2, 0.2, 0.2, 0.2, 0.0);

        attacker.sendActionBar(Component.text("-" + (int) damage, NamedTextColor.RED)
                .decorate(TextDecoration.BOLD));

        if (!died) return;

        // ── Crystal HP reached 0 ──────────────────────────────────────────────
        Faction faction = FactionManager.getFaction(crystalFaction);
        int factionLevel = faction != null ? faction.getLevel() : 0;

        if (factionLevel == 0) {
            // Level 0: crystal is permanently destroyed — disband the faction
            AtlasCrystalManager.remove(crystal.getUniqueId());
            crystal.getWorld().playSound(
                    crystal.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.BLOCKS, 1.0f, 1.0f);
            crystal.getWorld().createExplosion(crystal.getLocation(), 6.0f, true, true);
            crystal.remove();
            FactionManager.broadcastToFaction(crystalFaction,
                    Component.text("The faction has been destroyed by enemy players!", NamedTextColor.DARK_RED)
                            .decorate(TextDecoration.BOLD), null);
            FactionManager.disbandFaction(crystalFaction);

        } else {
            // Level > 0: drop to the previous checkpoint, restore crystal HP, grant 1h immunity
            int prevCheckpoint = FactionLevelManager.getPreviousCheckpoint(factionLevel);
            int newLevel = Math.max(0, prevCheckpoint); // -1 means drop to 0

            faction.setLevel(newLevel);
            faction.setExp(0);

            // Strip checkpoint bonuses above newLevel from all named faction crystals
            Map<Integer, Double> bonusMap = FactionLevelManager.getUpgradeBonusMap();
            Collection<AtlasCrystal> allCrystals = AtlasCrystalManager.getFactionCrystals(crystalFaction);
            for (AtlasCrystal fc : allCrystals) {
                fc.stripUpgradesAbove(newLevel, bonusMap);
                fc.updateNametag();
                AtlasCrystalManager.persistCrystalState(fc);
            }

            // Restore the attacked crystal's HP to full and grant immunity
            atlasCrystal.setHp(atlasCrystal.getMaxHp());
            atlasCrystal.setImmuneFor(3_600_000L); // 1 hour
            atlasCrystal.updateNametag();
            AtlasCrystalManager.persistCrystalState(atlasCrystal);

            // Effects (smaller explosion, no fire)
            crystal.getWorld().playSound(
                    crystal.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.BLOCKS, 1.0f, 0.5f);
            crystal.getWorld().createExplosion(crystal.getLocation(), 3.0f, false, false);

            // Broadcast
            String levelStr = newLevel == 0 ? "0 (last stand!)" : String.valueOf(newLevel);
            FactionManager.broadcastToFaction(crystalFaction,
                    Component.text("⚠ Your Atlas Crystal was weakened! Faction level dropped to "
                            + levelStr + ". Crystal is immune to damage for 1 hour!", NamedTextColor.RED)
                            .decorate(TextDecoration.BOLD),
                    null);
            attacker.sendMessage(Component.text("You weakened the " + crystalFaction
                            + " faction to level " + newLevel + "! Their crystal is immune for 1 hour.",
                    NamedTextColor.YELLOW));

            if (newLevel == 0) {
                FactionManager.broadcastToFaction(crystalFaction,
                        Component.text("⚠ WARNING: Your faction is at level 0! One more crystal defeat will disband the faction!",
                                NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD),
                        null);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Restore atlas crystals after chunk load / server restart
    // -------------------------------------------------------------------------

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity instanceof EnderCrystal crystal
                    && crystal.getPersistentDataContainer()
                    .has(AtlasCrystalManager.getKeyFaction(), PersistentDataType.STRING)
                    && !AtlasCrystalManager.isAtlasCrystal(crystal.getUniqueId())) {
                AtlasCrystalManager.restore(crystal);
            }
        }
    }
}
