package org.minecraft.atlas;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.handler.LifecycleEventHandler;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.jspecify.annotations.NonNull;
import org.minecraft.atlas.command.GroupCommand;
import org.minecraft.atlas.group.GroupManager;
import org.minecraft.atlas.listener.GroupListener;

import org.bukkit.plugin.java.JavaPlugin;

public final class Atlas extends JavaPlugin {

    public static Atlas instance;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        GroupManager.loadGroups(getConfig());

        // Register all listeners
        getServer().getPluginManager().registerEvents(new GroupListener(), this);

        // Register all commands
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                new LifecycleEventHandler<ReloadableRegistrarEvent<Commands>>() {
                    @Override
                    public void run(@NonNull ReloadableRegistrarEvent<Commands> event) {
                        event.registrar().register(GroupCommand.build());
                    }
                }
        );

        getLogger().info("Atlas enabled.");

    }

    @Override
    public void onDisable() {
        GroupManager.saveGroups(getConfig());
        saveConfig();

        getLogger().info("Atlas disabled.");    }
}
