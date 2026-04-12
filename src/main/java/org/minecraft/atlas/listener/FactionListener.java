package org.minecraft.atlas.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.minecraft.atlas.Atlas;
import org.minecraft.atlas.donjon.DonjonManager;
import org.minecraft.atlas.util.TitleUtil;
import org.minecraft.atlas.faction.AtlasCrystal;
import org.minecraft.atlas.faction.AtlasCrystalManager;
import org.minecraft.atlas.faction.Faction;
import org.minecraft.atlas.faction.FactionClaimManager;
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
            TitleUtil.notify(player, "Teleport cancelled — you took damage!", NamedTextColor.RED);
        }
    }

    // -------------------------------------------------------------------------
    // Faction territory protection
    // -------------------------------------------------------------------------

    /**
     * Returns true when the player is allowed to act in the given chunk —
     * i.e. the chunk is unclaimed, or the player belongs to the owning faction.
     */
    private boolean isAllowedInChunk(Player player, Chunk chunk) {
        String owner = FactionClaimManager.getClaimingFaction(
                chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        if (owner == null) return true;
        return owner.equals(FactionManager.getPlayerFaction(player.getUniqueId()));
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (isAllowedInChunk(player, event.getBlock().getChunk())) return;
        event.setCancelled(true);
        player.sendActionBar(Component.text("⚔ Enemy territory — can't break!", NamedTextColor.RED));
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (isAllowedInChunk(player, event.getBlock().getChunk())) return;
        event.setCancelled(true);
        player.sendActionBar(Component.text("⚔ Enemy territory — can't place!", NamedTextColor.RED));
    }

    /** Blocks right-click interactions (chests, doors, buttons…) and pressure-plate triggers in claimed chunks. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.PHYSICAL) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        Player player = event.getPlayer();
        if (isAllowedInChunk(player, block.getChunk())) return;
        event.setCancelled(true);
        if (action == Action.RIGHT_CLICK_BLOCK) {
            player.sendActionBar(Component.text("⚔ Enemy territory — can't interact!", NamedTextColor.RED));
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
            TitleUtil.notify(attacker, "You must be in a faction to attack an Atlas Crystal.", NamedTextColor.RED);
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

        // Show damage as hit feedback: instant appear, brief stay, slow fade-out
        // — the abrupt appearance + gradual dissolution simulates text drifting upward.
        TitleUtil.hitFeedback(attacker, "-" + (int) damage + " points", NamedTextColor.RED);

        // Alert faction members that their crystal is under attack
        String displayName = atlasCrystal.getName().isEmpty()
                ? "A crystal" : "'" + atlasCrystal.getName() + "'";
        TitleUtil.broadcastAlertBold(
                FactionManager.getOnlineFactionMembers(crystalFaction, null),
                "⚔ " + displayName + " is under attack by " + attackerFaction + "!",
                NamedTextColor.RED);

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

            // Chat to faction members
            FactionManager.broadcastToFaction(crystalFaction,
                    Component.text("☠ Your faction has been destroyed by " + attackerFaction + "!", NamedTextColor.RED),
                    null);

            // Server-wide chat announcement
            Component serverMsg = Component.text(
                    "☠ [" + crystalFaction + "] was destroyed by [" + attackerFaction + "]!", NamedTextColor.RED);
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.sendMessage(serverMsg);
            }

            FactionManager.disbandFaction(crystalFaction);

        } else {
            // Level > 0: drop to the previous checkpoint, restore crystal HP, grant 1h immunity
            int prevCheckpoint = FactionLevelManager.getPreviousCheckpoint(factionLevel);
            int newLevel = Math.max(0, prevCheckpoint); // -1 means drop to 0

            faction.setLevel(newLevel);
            faction.setExp(0);

            // Shrink territory claims: keep only rings for checkpoints ≤ newLevel
            int targetRings = 0;
            for (int cp : FactionLevelManager.getCheckpoints()) {
                if (cp <= newLevel) targetRings++;
                else break;
            }
            FactionClaimManager.shrinkClaimsTo(crystalFaction, targetRings);

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
            atlasCrystal.setImmuneFor(Atlas.crystalImmunityDurationMs);
            atlasCrystal.updateNametag();
            AtlasCrystalManager.persistCrystalState(atlasCrystal);

            // Effects (smaller explosion, no fire)
            crystal.getWorld().playSound(
                    crystal.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.BLOCKS, 1.0f, 0.5f);
            crystal.getWorld().createExplosion(crystal.getLocation(), 3.0f, false, false);

            // Broadcast
            String levelStr = newLevel == 0 ? "0 (last stand!)" : String.valueOf(newLevel);
            long immunitySeconds = Atlas.crystalImmunityDurationMs / 1000;
            TitleUtil.broadcastAlertBold(FactionManager.getOnlineFactionMembers(crystalFaction, null),
                    "⚠ Crystal weakened! Lv." + levelStr + ". Immune " + immunitySeconds + "s!",
                    NamedTextColor.RED);
            TitleUtil.notify(attacker,
                    "Weakened " + crystalFaction + " to Lv." + newLevel
                            + "! Immune " + immunitySeconds + "s.",
                    NamedTextColor.YELLOW);

            if (newLevel == 0) {
                TitleUtil.broadcastAlertBold(FactionManager.getOnlineFactionMembers(crystalFaction, null),
                        "⚠ Lv.0! Next defeat disbands the faction!",
                        NamedTextColor.DARK_RED);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Player move — enter/leave own faction territory
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to   = event.getTo();

        int fromCX = from.getBlockX() >> 4, fromCZ = from.getBlockZ() >> 4;
        int toCX   = to.getBlockX()   >> 4, toCZ   = to.getBlockZ()   >> 4;
        if (fromCX == toCX && fromCZ == toCZ) return; // same chunk, skip

        Player player = event.getPlayer();
        String worldName = player.getWorld().getName();

        String fromFaction = FactionClaimManager.getClaimingFaction(worldName, fromCX, fromCZ);
        String toFaction   = FactionClaimManager.getClaimingFaction(worldName, toCX,   toCZ);
        boolean fromDonjon = DonjonManager.isChunkInDonjon(worldName, fromCX, fromCZ);
        boolean toDonjon   = DonjonManager.isChunkInDonjon(worldName, toCX,   toCZ);

        String playerFaction = FactionManager.getPlayerFaction(player.getUniqueId());

        // Entering a faction chunk
        if (toFaction != null) {
            boolean wasAlreadyInSame = toFaction.equals(fromFaction);

            if (!wasAlreadyInSame) {
                Faction faction = FactionManager.getFaction(toFaction);
                NamedTextColor color = faction != null ? faction.getColor() : NamedTextColor.WHITE;

                TitleUtil.alert(player, toFaction + "\nYou enter " + toFaction, color);

                if (!toFaction.equals(playerFaction)) {
                    player.playSound(player.getLocation(),
                            Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.BLOCKS, 0.6f, 1.0f);
                }
            }

            return;
        }

        // Entering wilderness (unclaimed, non-donjon) from any claimed area
        if (!toDonjon && (fromFaction != null || fromDonjon)) {
            TitleUtil.notify(player, "Wilderness\nEnter the Wilderness", NamedTextColor.GREEN);
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
