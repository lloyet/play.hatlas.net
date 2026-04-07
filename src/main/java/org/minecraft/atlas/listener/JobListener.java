package org.minecraft.atlas.listener;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.type.Beehive;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.MushroomCow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.minecraft.atlas.job.Job;
import org.minecraft.atlas.job.JobManager;
import org.minecraft.atlas.job.JobRegistry;
import org.minecraft.atlas.job.JobSource;
import org.minecraft.atlas.job.JobSources;
import org.minecraft.atlas.job.JobSources.SourceRef;
import org.minecraft.atlas.job.PlayerJobData;

public class JobListener implements Listener {

    // -------------------------------------------------------------------------
    // Shared helper
    // -------------------------------------------------------------------------

    private void award(Player player, Job job, PlayerJobData data, SourceRef ref) {
        award(player, job, data, ref, 1);
    }

    private void award(Player player, Job job, PlayerJobData data, SourceRef ref, int multiplier) {
        JobSource source = JobRegistry.getSource(job, ref.category(), ref.key());
        if (source == null) return;
        // Mastered players bypass cutoff — any source they've already unlocked stays earnable
        boolean canEarn = data.isMastered()
                ? data.getLevel() >= source.getUnlockLevel()
                : source.isActive(data.getLevel());
        if (!canEarn) return;
        JobManager.addXp(player.getUniqueId(), job, source.getXp() * multiplier, player);
    }

    // -------------------------------------------------------------------------
    // MINER + LUMBERJACK + FARMER — block breaks
    // -------------------------------------------------------------------------

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material mat = block.getType();

        PlayerJobData minerData = JobManager.getJobData(player.getUniqueId(), Job.MINER);
        if (minerData != null) {
            SourceRef ref = JobSources.MINER_BREAK.get(mat);
            if (ref != null) award(player, Job.MINER, minerData, ref);
        }

        PlayerJobData lumberjackData = JobManager.getJobData(player.getUniqueId(), Job.LUMBERJACK);
        if (lumberjackData != null) {
            SourceRef ref = JobSources.LUMBERJACK_BREAK.get(mat);
            if (ref != null) award(player, Job.LUMBERJACK, lumberjackData, ref);
        }

        PlayerJobData farmerData = JobManager.getJobData(player.getUniqueId(), Job.FARMER);
        if (farmerData != null) {
            SourceRef ref = getFarmerCropRef(block);
            if (ref != null) award(player, Job.FARMER, farmerData, ref);
        }
    }

    /** Returns the crop SourceRef only if the crop is fully grown (or has no growth stage). */
    private SourceRef getFarmerCropRef(Block block) {
        SourceRef ref = JobSources.FARMER_BREAK.get(block.getType());
        if (ref == null) return null;
        if (block.getBlockData() instanceof Ageable ageable
                && ageable.getAge() < ageable.getMaximumAge()) return null;
        return ref;
    }

    // -------------------------------------------------------------------------
    // MINER — smelting (XP per item extracted, not per smelt tick)
    // -------------------------------------------------------------------------

    @EventHandler
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        Player player = event.getPlayer();
        PlayerJobData data = JobManager.getJobData(player.getUniqueId(), Job.MINER);
        if (data == null) return;

        SourceRef ref = JobSources.MINER_SMELT.get(event.getItemType());
        if (ref != null) award(player, Job.MINER, data, ref, event.getItemAmount());
    }

    // -------------------------------------------------------------------------
    // LUMBERJACK — sapling planting
    // -------------------------------------------------------------------------

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        PlayerJobData data = JobManager.getJobData(player.getUniqueId(), Job.LUMBERJACK);
        if (data == null) return;

        if (JobSources.LUMBERJACK_SAPLINGS.contains(event.getBlockPlaced().getType())) {
            award(player, Job.LUMBERJACK, data, new SourceRef("extras", "saplings"));
        }
    }

    // -------------------------------------------------------------------------
    // HUNTER — entity kills
    // -------------------------------------------------------------------------

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        PlayerJobData data = JobManager.getJobData(killer.getUniqueId(), Job.HUNTER);
        if (data == null) return;

        SourceRef ref = JobSources.HUNTER_KILLS.get(event.getEntity().getType());
        if (ref != null) award(killer, Job.HUNTER, data, ref);
    }

    // -------------------------------------------------------------------------
    // FARMER — breeding
    // -------------------------------------------------------------------------

    @EventHandler
    public void onEntityBreed(EntityBreedEvent event) {
        Entity breeder = event.getBreeder();
        if (!(breeder instanceof Player player)) return;

        PlayerJobData data = JobManager.getJobData(player.getUniqueId(), Job.FARMER);
        if (data == null) return;

        if (JobSources.FARMER_BREEDABLE.contains(event.getEntity().getType())) {
            award(player, Job.FARMER, data, new SourceRef("animals", "breeding"));
        }
    }

    // -------------------------------------------------------------------------
    // FARMER — shearing sheep
    // -------------------------------------------------------------------------

    @EventHandler
    public void onShear(PlayerShearEntityEvent event) {
        Player player = event.getPlayer();
        PlayerJobData data = JobManager.getJobData(player.getUniqueId(), Job.FARMER);
        if (data == null) return;

        if (JobSources.FARMER_SHEARABLE.contains(event.getEntity().getType())) {
            award(player, Job.FARMER, data, new SourceRef("animals", "shearing"));
        }
    }

    // -------------------------------------------------------------------------
    // FARMER — milking cows (right-click cow with bucket)
    // -------------------------------------------------------------------------

    @EventHandler
    public void onMilk(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Entity entity = event.getRightClicked();
        if (!(entity instanceof Cow) && !(entity instanceof MushroomCow)) return;

        Player player = event.getPlayer();
        if (player.getInventory().getItemInMainHand().getType() != Material.BUCKET) return;

        PlayerJobData data = JobManager.getJobData(player.getUniqueId(), Job.FARMER);
        if (data == null) return;

        award(player, Job.FARMER, data, new SourceRef("animals", "milking"));
    }

    // -------------------------------------------------------------------------
    // FARMER — harvesting honey from beehives/bee nests
    // -------------------------------------------------------------------------

    @EventHandler
    public void onHarvestHoney(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null) return;
        if (block.getType() != Material.BEEHIVE && block.getType() != Material.BEE_NEST) return;

        if (!(block.getBlockData() instanceof Beehive beehive)) return;
        if (beehive.getHoneyLevel() < 5) return;

        Material held = event.getPlayer().getInventory().getItemInMainHand().getType();
        if (held != Material.GLASS_BOTTLE && held != Material.SHEARS) return;

        Player player = event.getPlayer();
        PlayerJobData data = JobManager.getJobData(player.getUniqueId(), Job.FARMER);
        if (data == null) return;

        award(player, Job.FARMER, data, new SourceRef("animals", "honey"));
    }
}
