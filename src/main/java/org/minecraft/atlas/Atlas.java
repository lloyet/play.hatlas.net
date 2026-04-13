package org.minecraft.atlas;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.handler.LifecycleEventHandler;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.configuration.file.FileConfiguration;
import org.jspecify.annotations.NonNull;
import org.minecraft.atlas.command.CrystalCommand;
import org.minecraft.atlas.command.DonjonCommand;
import org.minecraft.atlas.command.FactionCommand;
import org.minecraft.atlas.command.TagCommand;
import org.minecraft.atlas.faction.CrystalGui;
import org.minecraft.atlas.donjon.DonjonManager;
import org.minecraft.atlas.faction.AtlasCrystalManager;
import org.minecraft.atlas.command.TradeCommand;
import org.minecraft.atlas.faction.FactionClaimManager;
import org.minecraft.atlas.faction.FactionLevelManager;
import org.minecraft.atlas.faction.FactionManager;
import org.minecraft.atlas.listener.ChatListener;
import org.minecraft.atlas.listener.DonjonListener;
import org.minecraft.atlas.listener.FactionListener;
import org.minecraft.atlas.listener.TagListener;
import org.minecraft.atlas.command.HelpCommand;
import org.minecraft.atlas.command.JobCommand;
import org.minecraft.atlas.tag.TagManager;
import org.minecraft.atlas.job.JobGui;
import org.minecraft.atlas.job.JobManager;
import org.minecraft.atlas.listener.JobListener;

import org.bukkit.plugin.java.JavaPlugin;
import org.minecraft.atlas.listener.GolemListener;
import org.minecraft.atlas.listener.TradeListener;

public final class Atlas extends JavaPlugin {

    public static Atlas instance;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        FileConfiguration configFile = getConfig();
        AtlasCrystalManager.loadConfig(configFile);
        FactionLevelManager.loadCheckpoints(configFile);
        FactionManager.loadFactions(configFile);
        FactionClaimManager.loadClaims(configFile);
        JobManager.loadJobs(configFile);
        DonjonManager.loadConfig(configFile);
        DonjonManager.loadDonjons(configFile);
        TagManager.init();
        TagManager.loadTags(configFile);

        // Register all listeners
        getServer().getPluginManager().registerEvents(new ChatListener(), this);
        getServer().getPluginManager().registerEvents(new FactionListener(), this);
        getServer().getPluginManager().registerEvents(new TradeListener(), this);
        getServer().getPluginManager().registerEvents(new GolemListener(), this);
        getServer().getPluginManager().registerEvents(new JobListener(), this);
        getServer().getPluginManager().registerEvents(new JobGui(), this);
        getServer().getPluginManager().registerEvents(new DonjonListener(), this);
        getServer().getPluginManager().registerEvents(new TagListener(), this);
        getServer().getPluginManager().registerEvents(new CrystalGui(), this);

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
                        event.registrar().register(TagCommand.build());
                    }
                }
        );

        getLogger().info("Atlas enabled.");

    }

    @Override
    public void onDisable() {
        FileConfiguration configFile = getConfig();

        FactionManager.saveFactions(configFile);
        FactionClaimManager.saveClaims(configFile);
        JobManager.saveJobs(configFile);
        DonjonManager.saveDonjonConfig(configFile);
        TagManager.saveTags(configFile);
        saveConfig();

        getLogger().info("Atlas disabled.");    }
}
