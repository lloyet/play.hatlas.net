package org.minecraft.atlas.listener;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.ChunkPopulateEvent;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.Action;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.BoundingBox;
import org.minecraft.atlas.donjon.Donjon;
import org.minecraft.atlas.donjon.DonjonManager;
import org.minecraft.atlas.donjon.DonjonStatus;
import org.minecraft.atlas.donjon.DonjonType;
import org.minecraft.atlas.faction.FactionManager;
import org.minecraft.atlas.util.TitleUtil;

public class DonjonListener implements Listener {

    /** 5 trial-spawner ambient-ominous sounds played at random when entering a donjon. */
    private static final Sound[] ENTER_DONJON_SOUNDS = {
            Sound.BLOCK_TRIAL_SPAWNER_AMBIENT_OMINOUS,
            Sound.BLOCK_TRIAL_SPAWNER_AMBIENT,
            Sound.BLOCK_TRIAL_SPAWNER_DETECT_PLAYER,
            Sound.BLOCK_TRIAL_SPAWNER_OMINOUS_ACTIVATE,
            Sound.BLOCK_TRIAL_SPAWNER_EJECT_ITEM
    };

    // -------------------------------------------------------------------------
    // Block protection
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getPlayer().hasPermission("atlas.donjon.admin")) return;

        Location loc = event.getBlock().getLocation();
        long key = Chunk.getChunkKey(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        Donjon donjon = DonjonManager.getDonjonAtChunk(loc.getWorld(), key);
        if (donjon == null) return;

        event.setCancelled(true);
        TitleUtil.subtitle(event.getPlayer(), "You cannot break blocks in a donjon!", NamedTextColor.RED);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getPlayer().hasPermission("atlas.donjon.admin")) return;

        Location loc = event.getBlock().getLocation();
        long key = Chunk.getChunkKey(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        Donjon donjon = DonjonManager.getDonjonAtChunk(loc.getWorld(), key);
        if (donjon == null) return;

        event.setCancelled(true);
        TitleUtil.subtitle(event.getPlayer(), "You cannot place blocks in a donjon!", NamedTextColor.RED);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> {
            long key = Chunk.getChunkKey(block.getX() >> 4, block.getZ() >> 4);
            return DonjonManager.getDonjonAtChunk(block.getWorld(), key) != null;
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> {
            long key = Chunk.getChunkKey(block.getX() >> 4, block.getZ() >> 4);
            return DonjonManager.getDonjonAtChunk(block.getWorld(), key) != null;
        });
    }

    // -------------------------------------------------------------------------
    // Totem interaction — start donjon
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getClickedBlock() == null) return;
        if (event.getClickedBlock().getType() != Material.RESPAWN_ANCHOR) return;

        String donjonId = DonjonManager.getDonjonIdAtTotem(event.getClickedBlock().getLocation());
        if (donjonId == null) return;

        // Always cancel to prevent vanilla charge/explode behaviour
        event.setCancelled(true);

        Donjon donjon = DonjonManager.getDonjon(donjonId);
        if (donjon == null) return;

        Player player = event.getPlayer();

        if (donjon.getStatus() != DonjonStatus.ACTIVE) {
            TitleUtil.notify(player, "This donjon is not active yet.", NamedTextColor.RED);
            return;
        }

        if (donjon.isInProgress()) {
            TitleUtil.notify(player, "Donjon run already in progress!", NamedTextColor.YELLOW);
            return;
        }

        String factionName = FactionManager.getPlayerFaction(player.getUniqueId());
        if (factionName == null) {
            TitleUtil.notify(player, "Join a faction to start a donjon!", NamedTextColor.RED);
            return;
        }

        DonjonManager.startDonjon(donjon, player, factionName);
    }

    // -------------------------------------------------------------------------
    // Entity death
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity living = event.getEntity();
        if (!DonjonManager.isDonjonEntity(living)) return;

        // Clear vanilla drops for donjon mobs
        event.getDrops().clear();
        event.setDroppedExp(0);

        DonjonManager.onEntityDeath(living);
    }

    // -------------------------------------------------------------------------
    // Boss damage tracking
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        if (!DonjonManager.isBossEntity(target)) return;

        Player player = null;
        if (event.getDamager() instanceof Player p) {
            player = p;
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            player = p;
        }

        if (player == null) return;

        String factionName = FactionManager.getPlayerFaction(player.getUniqueId());
        if (factionName == null) return;

        DonjonManager.onBossDamage(target, event.getFinalDamage(), factionName);
    }

    // -------------------------------------------------------------------------
    // Player move — enter/exit donjon title
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to   = event.getTo();

        int fromCX = from.getBlockX() >> 4, fromCZ = from.getBlockZ() >> 4;
        int toCX   = to.getBlockX()   >> 4, toCZ   = to.getBlockZ()   >> 4;
        if (fromCX == toCX && fromCZ == toCZ) return; // same chunk

        long fromKey = Chunk.getChunkKey(fromCX, fromCZ);
        long toKey = Chunk.getChunkKey(toCX,   toCZ);

        Player player = event.getPlayer();

        for (Donjon donjon : DonjonManager.getDonjons().values()) {
            if (donjon.getStatus() != DonjonStatus.ACTIVE) continue;
            if (!player.getWorld().equals(donjon.getCenter().getWorld())) continue;

            boolean wasIn = donjon.getProtectedChunkKeys().contains(fromKey);
            boolean isIn  = donjon.getProtectedChunkKeys().contains(toKey);

            if (!wasIn && isIn) {
                TitleUtil.alert(player,
                        donjon.getName() + " Lv." + donjon.getLevel()
                                + " [" + donjon.getRarity().getDisplayName() + "]",
                        donjon.getRarity().getColor());
                Sound enterSound = ENTER_DONJON_SOUNDS[ThreadLocalRandom.current().nextInt(ENTER_DONJON_SOUNDS.length)];
                player.playSound(player.getLocation(), enterSound, SoundCategory.BLOCKS, 0.6f, 1.0f);
            }

        }
    }

    // -------------------------------------------------------------------------
    // Entities load — restore TextDisplay nametags
    // -------------------------------------------------------------------------

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        if (DonjonManager.keyTotemDisplay == null) return;
        for (Entity entity : event.getEntities()) {
            if (!(entity instanceof TextDisplay td)) continue;
            if (!td.getPersistentDataContainer().has(DonjonManager.keyTotemDisplay)) continue;
            DonjonManager.restoreNametag(td);
        }
    }

    // -------------------------------------------------------------------------
    // Chunk populate — auto-create donjon from vanilla structure (fires once on first gen)
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkPopulate(ChunkPopulateEvent event) {
        Chunk chunk = event.getChunk();
        World world = chunk.getWorld();

        for (DonjonType type : DonjonType.values()) {
            for (GeneratedStructure gs : chunk.getStructures(type.getStructure())) {
                BoundingBox bb = gs.getBoundingBox();
                DonjonManager.createDonjonFromStructure(type, bb, world);
            }
        }
    }
}
