package org.minecraft.atlas.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.minecraft.atlas.faction.RandomTeleportManager;

public class RandomTeleportCommand {

    public static LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("rtp")
                .requires(src -> src.getSender().hasPermission("atlas.rtp"))
                .executes(ctx -> {
                    Entity executor = ctx.getSource().getExecutor();
                    if (!(executor instanceof Player player)) {
                        ctx.getSource().getSender().sendMessage(
                                Component.text("Only players can use this command.", NamedTextColor.RED));
                        return Command.SINGLE_SUCCESS;
                    }
                    RandomTeleportManager.startTeleport(player);
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
