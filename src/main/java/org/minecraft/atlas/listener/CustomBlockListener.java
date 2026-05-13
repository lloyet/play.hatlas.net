package org.minecraft.atlas.listener;

import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.block.NotePlayEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.minecraft.atlas.customBlock.CustomBlockManager;

import java.util.Set;

/**
 * Routes vanilla note-block side effects around custom ruby blocks: places the
 * note-block state on placement, drops the custom item (iron+ pickaxe required)
 * on break, and suppresses note play / redstone / physics / piston interactions
 * for tracked locations.
 */
public final class CustomBlockListener implements Listener {

    private static final Set<Material> ACCEPTED_PICKAXES = Set.of(
            Material.IRON_PICKAXE,
            Material.DIAMOND_PICKAXE,
            Material.NETHERITE_PICKAXE
    );

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack inHand = event.getItemInHand();
        String customId = CustomBlockManager.customBlockIdForItem(inHand);
        if (customId == null) return;
        CustomBlockManager.placeAt(event.getBlockPlaced(), customId);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        String id = CustomBlockManager.getCustomIdAt(block);
        if (id == null) return;

        // Take full control of the break so we can play stone effects instead of the
        // note-block ones — vanilla captures the destroy state before our handler can
        // mutate it, so swapping the type mid-event doesn't change the sound.
        event.setCancelled(true);
        CustomBlockManager.removeAt(block);

        Location loc = block.getLocation();
        block.setType(Material.AIR, false);
        block.getWorld().playEffect(loc, Effect.STEP_SOUND, Material.STONE);

        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        if (!ACCEPTED_PICKAXES.contains(tool.getType())) return;

        block.getWorld().dropItemNaturally(loc.toCenterLocation(),
                CustomBlockManager.dropFor(id));
    }

    /**
     * Right-clicking a tracked note block normally triggers vanilla's pitch cycle.
     * Cancel that, and if the player is holding another ruby custom block, treat the
     * click as a placement against the clicked face.
     */
    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;
        if (!CustomBlockManager.isTrackedNoteBlock(clicked)) return;

        // Always cancel the vanilla pitch-cycle interaction.
        event.setCancelled(true);

        ItemStack hand = event.getItem();
        if (hand == null) return;
        String heldId = CustomBlockManager.customBlockIdForItem(hand);
        if (heldId == null) return;

        Block target = clicked.getRelative(event.getBlockFace());
        if (!target.getType().isAir()) return;

        Player player = event.getPlayer();
        if (target.getBoundingBox().overlaps(player.getBoundingBox())) return;

        CustomBlockManager.placeAt(target, heldId);
        target.getWorld().playSound(target.getLocation().toCenterLocation(),
                Sound.BLOCK_STONE_PLACE, 1.0f, 1.0f);

        if (player.getGameMode() != GameMode.CREATIVE) {
            hand.setAmount(hand.getAmount() - 1);
        }
    }

    /** Suppress note playback (and the implicit redstone-triggered animation). */
    @EventHandler(ignoreCancelled = true)
    public void onNotePlay(NotePlayEvent event) {
        if (CustomBlockManager.isTrackedNoteBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /**
     * Cancel physics on tracked note blocks so vanilla doesn't recompute the
     * instrument when the block below them changes.
     */
    @EventHandler(ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.NOTE_BLOCK) return;
        if (CustomBlockManager.isTrackedNoteBlock(block)) {
            event.setCancelled(true);
        }
    }

    /** Suppress redstone power changes — note blocks fire on any signal. */
    @EventHandler
    public void onRedstone(BlockRedstoneEvent event) {
        if (CustomBlockManager.isTrackedNoteBlock(event.getBlock())) {
            event.setNewCurrent(event.getOldCurrent());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block b : event.getBlocks()) {
            if (CustomBlockManager.isTrackedNoteBlock(b)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks()) {
            if (CustomBlockManager.isTrackedNoteBlock(b)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        handleExplosion(event.blockList());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        handleExplosion(event.blockList());
    }

    private void handleExplosion(java.util.List<Block> blocks) {
        java.util.Iterator<Block> it = blocks.iterator();
        while (it.hasNext()) {
            Block b = it.next();
            String id = CustomBlockManager.getCustomIdAt(b);
            if (id == null) continue;
            CustomBlockManager.removeAt(b);
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5),
                    CustomBlockManager.dropFor(id));
            b.setType(Material.AIR, false);
            it.remove();
        }
    }
}
