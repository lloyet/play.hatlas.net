package org.minecraft.atlas.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.minecraft.atlas.Atlas;
import org.minecraft.atlas.spawn.SpawnManager;

public class SetSpawnCommand {

    private static Component error(String msg)   { return Component.text(msg, NamedTextColor.RED); }
    private static Component success(String msg) { return Component.text(msg, NamedTextColor.GREEN); }

    public static LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("setspawn")
                .requires(src -> src.getSender().hasPermission("atlas.spawn.admin"))
                .executes(ctx -> {
                    Entity executor = ctx.getSource().getExecutor();
                    if (!(executor instanceof Player player)) {
                        ctx.getSource().getSender().sendMessage(
                                error("Only players can use this command."));
                        return Command.SINGLE_SUCCESS;
                    }
                    SpawnManager.setSpawn(player.getLocation());
                    SpawnManager.saveSpawn(Atlas.instance.getConfig());
                    Atlas.instance.saveConfig();
                    player.sendMessage(success(
                            "Server spawn set to your location ("
                                    + player.getLocation().getBlockX() + ", "
                                    + player.getLocation().getBlockY() + ", "
                                    + player.getLocation().getBlockZ() + ")."));
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
