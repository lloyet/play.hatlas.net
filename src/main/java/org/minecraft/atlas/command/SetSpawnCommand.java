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
import org.minecraft.atlas.faction.SpawnManager;

public class SetSpawnCommand {

    public static LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("setspawn")
                .requires(src -> src.getSender().hasPermission("atlas.spawn.admin"))
                .executes(ctx -> {
                    Entity executor = ctx.getSource().getExecutor();
                    if (!(executor instanceof Player player)) {
                        ctx.getSource().getSender().sendMessage(
                                Component.text("Only players can use this command.", NamedTextColor.RED));
                        return Command.SINGLE_SUCCESS;
                    }
                    SpawnManager.setSpawn(player.getLocation());
                    SpawnManager.saveSpawn(Atlas.instance.getConfig());
                    Atlas.instance.saveConfig();
                    player.sendMessage(Component.text(
                            "Server spawn set to your location ("
                                    + player.getLocation().getBlockX() + ", "
                                    + player.getLocation().getBlockY() + ", "
                                    + player.getLocation().getBlockZ() + ").",
                            NamedTextColor.GREEN));
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
