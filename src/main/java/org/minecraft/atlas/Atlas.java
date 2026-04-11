package org.minecraft.atlas;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.handler.LifecycleEventHandler;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.jspecify.annotations.NonNull;
import org.minecraft.atlas.command.CrystalCommand;
import org.minecraft.atlas.command.DonjonCommand;
import org.minecraft.atlas.command.FactionCommand;
import org.minecraft.atlas.donjon.DonjonManager;
import org.minecraft.atlas.faction.AtlasCrystalManager;
import org.minecraft.atlas.command.TradeCommand;
import org.minecraft.atlas.faction.FactionClaimManager;
import org.minecraft.atlas.faction.FactionLevelManager;
import org.minecraft.atlas.faction.FactionManager;
import org.minecraft.atlas.listener.DonjonListener;
import org.minecraft.atlas.listener.FactionListener;
import org.minecraft.atlas.command.HelpCommand;
import org.minecraft.atlas.command.JobCommand;
import org.minecraft.atlas.job.JobGui;
import org.minecraft.atlas.job.JobManager;
import org.minecraft.atlas.listener.JobListener;

import org.bukkit.plugin.java.JavaPlugin;
import org.minecraft.atlas.listener.GolemListener;
import org.minecraft.atlas.listener.TradeListener;

public final class Atlas extends JavaPlugin {

    public static Atlas instance;

    /** HP regenerated per second by Atlas Crystals (loaded from config). */
    public static double crystalRegenPerSecond = 1.5;
    /** Immunity duration in ms after a checkpoint level drop (loaded from config). */
    public static long crystalImmunityDurationMs = 3_600_000L;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        crystalRegenPerSecond = getConfig().getDouble("crystal.regen_per_second", 1.5);
        crystalImmunityDurationMs = getConfig().getLong("crystal.immunity_duration_seconds", 3600L) * 1000L;
        AtlasCrystalManager.regenTimeoutMs = getConfig().getLong("crystal.regen_timeout_seconds", 60L) * 1000L;
        FactionLevelManager.loadCheckpoints(getConfig());
        FactionManager.loadFactions(getConfig());
        FactionClaimManager.loadClaims(getConfig());
        JobManager.loadJobs(getConfig());
        DonjonManager.loadConfig(getConfig());
        DonjonManager.loadDonjons(getConfig());

        // Register all listeners
        getServer().getPluginManager().registerEvents(new FactionListener(), this);
        getServer().getPluginManager().registerEvents(new TradeListener(), this);
        getServer().getPluginManager().registerEvents(new GolemListener(), this);
        getServer().getPluginManager().registerEvents(new JobListener(), this);
        getServer().getPluginManager().registerEvents(new JobGui(), this);
        getServer().getPluginManager().registerEvents(new DonjonListener(), this);
        // Scheduler with ticks
        GolemListener.schedule(this);
        AtlasCrystalManager.schedule(this);
        DonjonManager.schedule(this);
        // Register all commands
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                new LifecycleEventHandler<ReloadableRegistrarEvent<Commands>>() {
                    @Override
                    public void run(@NonNull ReloadableRegistrarEvent<Commands> event) {
                        event.registrar().register(FactionCommand.build());
                        event.registrar().register(TradeCommand.build());
                        event.registrar().register(JobCommand.build());
                        event.registrar().register(HelpCommand.build());
                        event.registrar().register(CrystalCommand.build());
                        event.registrar().register(DonjonCommand.build());
                    }
                }
        );

        getLogger().info("Atlas enabled.");

    }

    @Override
    public void onDisable() {
        FactionManager.saveFactions(getConfig());
        FactionClaimManager.saveClaims(getConfig());
        JobManager.saveJobs(getConfig());
        DonjonManager.saveDonjonConfig(getConfig());
        saveConfig();

        getLogger().info("Atlas disabled.");    }
}
