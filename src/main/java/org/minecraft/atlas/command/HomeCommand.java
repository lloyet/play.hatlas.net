package org.minecraft.atlas.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.minecraft.atlas.Atlas;
import org.minecraft.atlas.faction.FactionClaimManager;
import org.minecraft.atlas.faction.FactionManager;
import org.minecraft.atlas.faction.HomeManager;

public class HomeCommand {

    public static LiteralCommandNode<CommandSourceStack> buildSetHome() {
        return Commands.literal("sethome")
                .requires(src -> src.getSender().hasPermission("atlas.home.set"))
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player player)) {
                                ctx.getSource().getSender().sendMessage(
                                        Component.text("Only players can use this command.", NamedTextColor.RED));
                                return Command.SINGLE_SUCCESS;
                            }
                            String name = StringArgumentType.getString(ctx, "name");
                            boolean alreadyExists = HomeManager.hasHome(player.getUniqueId(), name);

                            if (!alreadyExists && HomeManager.hasAnyHome(player.getUniqueId())) {
                                player.sendMessage(Component.text(
                                        "You already have a home set. Delete it first or use the same name to update it.",
                                        NamedTextColor.RED));
                                return Command.SINGLE_SUCCESS;
                            }

                            // Block /sethome inside another faction's territory
                            Location loc = player.getLocation();
                            String claimOwner = FactionClaimManager.getClaimingFaction(
                                    loc.getWorld().getName(), loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
                            if (claimOwner != null && !claimOwner.equals(FactionManager.getPlayerFaction(player.getUniqueId()))) {
                                player.sendMessage(Component.text(
                                        "You cannot set a home in another faction's territory!", NamedTextColor.RED));
                                return Command.SINGLE_SUCCESS;
                            }

                            HomeManager.setHome(player.getUniqueId(), name, player.getLocation());
                            HomeManager.saveHomes(Atlas.instance.getConfig());
                            Atlas.instance.saveConfig();

                            player.sendMessage(Component.text(
                                    (alreadyExists ? "Home updated" : "Home set") + ": '" + name + "'.",
                                    NamedTextColor.GREEN));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    public static LiteralCommandNode<CommandSourceStack> buildHome() {
        return Commands.literal("home")
                .requires(src -> src.getSender().hasPermission("atlas.home"))

                // /home — teleport to first home
                .executes(ctx -> {
                    Entity executor = ctx.getSource().getExecutor();
                    if (!(executor instanceof Player player)) {
                        ctx.getSource().getSender().sendMessage(
                                Component.text("Only players can use this command.", NamedTextColor.RED));
                        return Command.SINGLE_SUCCESS;
                    }
                    HomeManager.startTeleport(player, null);
                    return Command.SINGLE_SUCCESS;
                })

                // /home <name> — teleport to named home
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (executor instanceof Player player) {
                                HomeManager.getHomeNames(player.getUniqueId()).forEach(builder::suggest);
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player player)) {
                                ctx.getSource().getSender().sendMessage(
                                        Component.text("Only players can use this command.", NamedTextColor.RED));
                                return Command.SINGLE_SUCCESS;
                            }
                            String name = StringArgumentType.getString(ctx, "name");
                            HomeManager.startTeleport(player, name);
                            return Command.SINGLE_SUCCESS;
                        }))

                .build();
    }

    public static LiteralCommandNode<CommandSourceStack> buildDelHome() {
        return Commands.literal("delhome")
                .requires(src -> src.getSender().hasPermission("atlas.home.delete"))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (executor instanceof Player player) {
                                HomeManager.getHomeNames(player.getUniqueId()).forEach(builder::suggest);
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player player)) {
                                ctx.getSource().getSender().sendMessage(
                                        Component.text("Only players can use this command.", NamedTextColor.RED));
                                return Command.SINGLE_SUCCESS;
                            }
                            String name = StringArgumentType.getString(ctx, "name");
                            boolean deleted = HomeManager.deleteHome(player.getUniqueId(), name);
                            if (deleted) {
                                HomeManager.saveHomes(Atlas.instance.getConfig());
                                Atlas.instance.saveConfig();
                                player.sendMessage(Component.text(
                                        "Home '" + name + "' deleted.", NamedTextColor.GREEN));
                            } else {
                                player.sendMessage(Component.text(
                                        "Home '" + name + "' not found.", NamedTextColor.RED));
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
