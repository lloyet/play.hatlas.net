package org.minecraft.atlas;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.handler.LifecycleEventHandler;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.jspecify.annotations.NonNull;
import org.minecraft.atlas.command.FactionCommand;
import org.minecraft.atlas.command.TradeCommand;
import org.minecraft.atlas.faction.FactionManager;
import org.minecraft.atlas.listener.FactionListener;
import org.bukkit.plugin.java.JavaPlugin;
import org.minecraft.atlas.listener.GolemListener;
import org.minecraft.atlas.listener.TradeListener;

public final class Atlas extends JavaPlugin {

    public static Atlas instance;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        FactionManager.loadFactions(getConfig());

        // Register all listeners
        getServer().getPluginManager().registerEvents(new FactionListener(), this);
        getServer().getPluginManager().registerEvents(new TradeListener(), this);
        getServer().getPluginManager().registerEvents(new GolemListener(), this);

        // Scheduler with ticks
        GolemListener.schedule(this);

        // Register all commands
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                new LifecycleEventHandler<ReloadableRegistrarEvent<Commands>>() {
                    @Override
                    public void run(@NonNull ReloadableRegistrarEvent<Commands> event) {
                        event.registrar().register(FactionCommand.build());
                        event.registrar().register(TradeCommand.build());
                    }
                }
        );

        getLogger().info("Atlas enabled.");
    }

    @Override
    public void onDisable() {
        FactionManager.saveFactions(getConfig());
        saveConfig();

        getLogger().info("Atlas disabled.");    }
}
