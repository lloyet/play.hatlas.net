package org.minecraft.atlas.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.minecraft.atlas.faction.TpaManager;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public class TpaCommand {

    public static LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("tpa")
                .requires(src -> src.getSender().hasPermission("atlas.tpa"))

                // /tpa <player> — send request
                .then(Commands.argument("player", ArgumentTypes.player())
                        .executes(ctx -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player requester)) {
                                ctx.getSource().getSender().sendMessage(
                                        Component.text("Only players can use this command.", NamedTextColor.RED));
                                return Command.SINGLE_SUCCESS;
                            }
                            List<Player> resolved = ctx.getArgument("player", PlayerSelectorArgumentResolver.class)
                                    .resolve(ctx.getSource());
                            if (resolved.isEmpty()) {
                                requester.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                                return Command.SINGLE_SUCCESS;
                            }
                            TpaManager.sendRequest(requester, resolved.getFirst());
                            return Command.SINGLE_SUCCESS;
                        }))

                // /tpa accept — accept the pending request directed at you
                .then(Commands.literal("accept")
                        .executes(ctx -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player target)) {
                                ctx.getSource().getSender().sendMessage(
                                        Component.text("Only players can use this command.", NamedTextColor.RED));
                                return Command.SINGLE_SUCCESS;
                            }
                            TpaManager.acceptRequest(target);
                            return Command.SINGLE_SUCCESS;
                        }))

                // /tpa deny — deny the pending request directed at you
                .then(Commands.literal("deny")
                        .executes(ctx -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player target)) {
                                ctx.getSource().getSender().sendMessage(
                                        Component.text("Only players can use this command.", NamedTextColor.RED));
                                return Command.SINGLE_SUCCESS;
                            }
                            TpaManager.denyRequest(target);
                            return Command.SINGLE_SUCCESS;
                        }))

                .build();
    }
}
