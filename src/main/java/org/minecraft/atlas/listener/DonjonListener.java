package org.minecraft.atlas.listener;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.minecraft.atlas.donjon.Donjon;
import org.minecraft.atlas.donjon.DonjonManager;
import org.minecraft.atlas.donjon.DonjonStatus;
import org.minecraft.atlas.donjon.ElectricalCreeperManager;
import org.minecraft.atlas.donjon.SmugglerManager;
import org.minecraft.atlas.faction.FactionManager;
import org.minecraft.atlas.gui.DonjonListGui;
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
    // Smuggler NPC
    // -------------------------------------------------------------------------

    @EventHandler
    public void onSmugglerInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!SmugglerManager.isSmugglerNpc(event.getRightClicked())) return;
        event.setCancelled(true);
        new DonjonListGui(event.getPlayer()).open(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onSmugglerDamage(EntityDamageByEntityEvent event) {
        if (SmugglerManager.isSmugglerNpc(event.getEntity())) {
            event.setCancelled(true);
        }
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

        org.bukkit.block.Block clicked = event.getClickedBlock();
        long chunkKey = Chunk.getChunkKey(clicked.getX() >> 4, clicked.getZ() >> 4);
        Donjon donjon = DonjonManager.getDonjonAtChunk(clicked.getWorld(), chunkKey);
        if (donjon == null) return;

        // Always cancel to prevent vanilla vault behavior
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

        ItemStack heldItem = player.getInventory().getItemInMainHand();
        Material itemType = heldItem.getType();
        boolean isEnchanted = !heldItem.getEnchantments().isEmpty();

        if (donjon.getStatus() != DonjonStatus.ACTIVE) {
            // IDLE donjon — only an enchanted Ominous Trial Key can force-activate it
            if (!itemType.equals(Material.OMINOUS_TRIAL_KEY) || !isEnchanted) {
                TitleUtil.notify(player,
                        "This donjon is not active. Use an enchanted Ominous Trial Key to force-activate it.",
                        NamedTextColor.RED);
                return;
            }
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            DonjonManager.forceActivateDonjon(donjon);
            DonjonManager.startDonjon(donjon, player, factionName);
        } else {
            // ACTIVE donjon — enchanted Trial Key or enchanted Ominous Trial Key
            boolean isTrial  = itemType.equals(Material.TRIAL_KEY);
            boolean isOminous = itemType.equals(Material.OMINOUS_TRIAL_KEY);
            if ((!isTrial && !isOminous) || !isEnchanted) {
                TitleUtil.notify(player,
                        "You need an enchanted Trial Key or enchanted Ominous Trial Key to start this donjon.",
                        NamedTextColor.RED);
                return;
            }
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            DonjonManager.startDonjon(donjon, player, factionName);
        }
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
    // Friendly-fire protection inside donjon chunks
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDamagePlayer(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player target)) return;

        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            attacker = p;
        }
        if (attacker == null) return;
        if (attacker.equals(target)) return;

        long chunkKey = Chunk.getChunkKey(target.getChunk().getX(), target.getChunk().getZ());
        Donjon donjon = DonjonManager.getDonjonAtChunk(target.getWorld(), chunkKey);
        if (donjon == null || !donjon.isInProgress()) return;

        String attackerFaction = FactionManager.getPlayerFaction(attacker.getUniqueId());
        String targetFaction   = FactionManager.getPlayerFaction(target.getUniqueId());
        if (!FactionManager.areAllied(attackerFaction, targetFaction)) return;

        event.setCancelled(true);
        attacker.sendActionBar(net.kyori.adventure.text.Component.text(
                target.getName() + " is your ally.", NamedTextColor.GREEN));
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
                DonjonManager.recordPlayerVisit(player.getUniqueId(), donjon.getId());
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
    // Electrical Creeper Egg — use to spawn a charged creeper
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onElectricalEggUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) return;

        ItemStack item = event.getItem();
        if (!ElectricalCreeperManager.isElectricalCreeperEgg(item)) return;

        event.setCancelled(true);

        Location spawnLoc = event.getClickedBlock() != null
                ? event.getClickedBlock().getLocation().add(0.5, 1, 0.5)
                : event.getPlayer().getLocation();

        World world = spawnLoc.getWorld();
        if (world == null) return;

        world.spawn(spawnLoc, Creeper.class, creeper -> {
            creeper.setPowered(true);
            creeper.getPersistentDataContainer().set(
                    ElectricalCreeperManager.getElectricalCreeperKey(),
                    PersistentDataType.BYTE, (byte) 1);
        });

        // Consume one egg from the stack
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            event.getPlayer().getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        }
    }

    // -------------------------------------------------------------------------
    // Electrical Creeper — track hits on obsidian (4 hits = destroyed)
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.NORMAL)
    public void onElectricalCreeperExplode(EntityExplodeEvent event) {
        if (!ElectricalCreeperManager.isElectricalCreeper(event.getEntity())) return;

        Location loc = event.getEntity().getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        int radius = 6;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radius * radius) continue;
                    Block block = world.getBlockAt(
                            loc.getBlockX() + dx,
                            loc.getBlockY() + dy,
                            loc.getBlockZ() + dz);
                    if (block.getType() != Material.OBSIDIAN) continue;

                    int hits = ElectricalCreeperManager.addObsidianHit(block.getLocation());
                    if (hits >= ElectricalCreeperManager.OBSIDIAN_HITS_REQUIRED) {
                        ElectricalCreeperManager.clearObsidianHit(block.getLocation());
                        block.setType(Material.AIR);
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Obsidian hit-map cleanup when a block is broken by other means
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onObsidianBreakCleanup(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.OBSIDIAN) {
            ElectricalCreeperManager.clearObsidianHit(event.getBlock().getLocation());
        }
    }

}
