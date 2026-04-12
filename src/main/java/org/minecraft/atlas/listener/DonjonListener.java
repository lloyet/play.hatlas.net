package org.minecraft.atlas.listener;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
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

import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.minecraft.atlas.donjon.Donjon;
import org.minecraft.atlas.donjon.DonjonManager;
import org.minecraft.atlas.donjon.DonjonStatus;
import org.minecraft.atlas.faction.FactionManager;
import org.minecraft.atlas.util.TitleUtil;

public class DonjonListener implements Listener {

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
        if (event.getClickedBlock().getType() != Material.VAULT) return;

        // Check whether the right-clicked anchor sits inside any donjon's protected chunks
        org.bukkit.block.Block clicked = event.getClickedBlock();
        long chunkKey = Chunk.getChunkKey(clicked.getX() >> 4, clicked.getZ() >> 4);
        Donjon donjon = DonjonManager.getDonjonAtChunk(clicked.getWorld(), chunkKey);
        if (donjon == null) return;

        // Always cancel to prevent vanilla charge/explode behavior
        event.setCancelled(true);

        Player player = event.getPlayer();

        String factionName = FactionManager.getPlayerFaction(player.getUniqueId());
        if (factionName == null) {
            TitleUtil.notify(player, "Join a faction to start a donjon!", NamedTextColor.RED);
            return;
        }

        if (donjon.isInProgress()) {
            TitleUtil.notify(player, "Donjon run already in progress!", NamedTextColor.YELLOW);
            return;
        }

        Material playerItemType = player.getInventory().getItemInMainHand().getType();

        if (donjon.getStatus() != DonjonStatus.ACTIVE) {
            if (!playerItemType.equals(Material.OMINOUS_TRIAL_KEY)) {
                TitleUtil.notify(player, "This donjon is not active yet. Wait or use OminousTrialKey to activate this donjon.", NamedTextColor.RED);

                return;
            }
        } else {
            if (!playerItemType.equals(Material.TRIAL_KEY) && !playerItemType.equals(Material.OMINOUS_TRIAL_KEY)) {
                TitleUtil.notify(player, "You need a TrialKey or OminousTrialKey to start this donjon.", NamedTextColor.RED);

                return;
            }
        }

        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
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
        int toCX = to.getBlockX() >> 4, toCZ = to.getBlockZ() >> 4;
        if (fromCX == toCX && fromCZ == toCZ) return; // same chunk

        long fromKey = Chunk.getChunkKey(fromCX, fromCZ);
        long toKey = Chunk.getChunkKey(toCX, toCZ);

        Player player = event.getPlayer();

        for (Donjon donjon : DonjonManager.getDonjons().values()) {
            if (!player.getWorld().equals(donjon.getCenter().getWorld())) continue;

            boolean wasIn = donjon.getProtectedChunkKeys().contains(fromKey);
            boolean isIn = donjon.getProtectedChunkKeys().contains(toKey);

            if (!wasIn && isIn) {
                TitleUtil.alert(player,
                        donjon.getName() + " LvL." + donjon.getLevel()
                                + " [" + donjon.getRarity().getDisplayName() + "]",
                        donjon.getRarity().getColor());
                player.playSound(player.getLocation(), Sound.BLOCK_TRIAL_SPAWNER_AMBIENT_OMINOUS, SoundCategory.BLOCKS, 0.6f, 1.0f);
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
    // Chunk populate — structure-based donjon generation (fires once per new chunk)
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkPopulate(ChunkPopulateEvent event) {
        DonjonManager.generateNaturallyDonjonInChunk(event.getChunk());
    }
}
