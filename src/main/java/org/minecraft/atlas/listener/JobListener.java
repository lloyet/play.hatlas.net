package org.minecraft.atlas.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.minecraft.atlas.job.Job;
import org.minecraft.atlas.job.JobGui;
import org.minecraft.atlas.job.JobManager;
import org.minecraft.atlas.job.PlayerJobData;

import java.util.Set;

public class JobListener implements Listener {

    private static final Set<Material> MINER_BLOCKS = Set.of(
        Material.STONE, Material.COBBLESTONE, Material.DEEPSLATE, Material.COBBLED_DEEPSLATE,
        Material.COAL_ORE, Material.IRON_ORE, Material.COPPER_ORE, Material.GOLD_ORE,
        Material.REDSTONE_ORE, Material.LAPIS_ORE, Material.DIAMOND_ORE, Material.EMERALD_ORE,
        Material.NETHER_QUARTZ_ORE, Material.NETHER_GOLD_ORE, Material.ANCIENT_DEBRIS,
        Material.DEEPSLATE_COAL_ORE, Material.DEEPSLATE_IRON_ORE, Material.DEEPSLATE_COPPER_ORE,
        Material.DEEPSLATE_GOLD_ORE, Material.DEEPSLATE_REDSTONE_ORE, Material.DEEPSLATE_LAPIS_ORE,
        Material.DEEPSLATE_DIAMOND_ORE, Material.DEEPSLATE_EMERALD_ORE,
        Material.GRANITE, Material.DIORITE, Material.ANDESITE, Material.TUFF, Material.CALCITE
    );

    private static final Set<Material> LUMBERJACK_LOGS = Set.of(
        Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.JUNGLE_LOG,
        Material.ACACIA_LOG, Material.DARK_OAK_LOG, Material.MANGROVE_LOG, Material.CHERRY_LOG,
        Material.CRIMSON_STEM, Material.WARPED_STEM
    );

    private static final Set<Material> FARMER_CROPS = Set.of(
        Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
        Material.NETHER_WART, Material.COCOA, Material.SUGAR_CANE, Material.MELON, Material.PUMPKIN
    );

    @EventHandler
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager villager)) return;
        String jobName = villager.getPersistentDataContainer()
                .get(JobManager.getKeyNpcJob(), PersistentDataType.STRING);
        if (jobName == null) return;

        event.setCancelled(true);

        Job npcJob;
        try { npcJob = Job.valueOf(jobName); }
        catch (IllegalArgumentException e) { return; }

        Player player = event.getPlayer();
        PlayerJobData data = JobManager.getJobData(player.getUniqueId());
        if (data == null || data.getJob() != npcJob) {
            player.sendMessage(Component.text("This NPC is for " + npcJob.getDisplayName() + "s only.", NamedTextColor.RED));
            return;
        }

        JobGui.openJobMain(player);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        PlayerJobData data = JobManager.getJobData(player.getUniqueId());
        if (data == null) return;

        Material type = event.getBlock().getType();
        int progress = 0;

        if (data.getJob() == Job.MINER && MINER_BLOCKS.contains(type)) {
            progress = 1;
        } else if (data.getJob() == Job.LUMBERJACK && LUMBERJACK_LOGS.contains(type)) {
            progress = 1;
        } else if (data.getJob() == Job.FARMER && FARMER_CROPS.contains(type)) {
            progress = 1;
        }

        if (progress > 0) {
            JobManager.addProgress(player.getUniqueId(), progress, player);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) return;

        PlayerJobData data = JobManager.getJobData(killer.getUniqueId());
        if (data == null || data.getJob() != Job.HUNTER) return;

        JobManager.addProgress(killer.getUniqueId(), 1, killer);
    }
}
