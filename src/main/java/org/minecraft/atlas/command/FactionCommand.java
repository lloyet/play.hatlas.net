package org.minecraft.atlas.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.minecraft.atlas.faction.Faction;
import org.minecraft.atlas.faction.FactionManager;

import java.util.List;
import java.util.UUID;

public class FactionCommand {

    // -------------------------------------------------------------------------
    // Message helpers
    // -------------------------------------------------------------------------

    private static Component error(String msg) {
        return Component.text(msg, NamedTextColor.RED);
    }

    private static Component success(String msg) {
        return Component.text(msg, NamedTextColor.GREEN);
    }

    private static Component info(String msg) {
        return Component.text(msg, NamedTextColor.GOLD);
    }

    // -------------------------------------------------------------------------
    // Command tree
    // -------------------------------------------------------------------------

    public static LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("faction")
                .requires(src -> src.getSender().hasPermission("atlas.faction"))
                .executes(ctx -> {
                    ctx.getSource().getSender().sendMessage(
                            Component.text("--- Faction Commands ---", NamedTextColor.GOLD)
                                    .append(Component.newline())
                                    .append(info("/faction create <name>   ")).append(Component.text("- Create a new faction", NamedTextColor.YELLOW))
                                    .append(Component.newline())
                                    .append(info("/faction invite <player> ")).append(Component.text("- Invite a player (owner only)", NamedTextColor.YELLOW))
                                    .append(Component.newline())
                                    .append(info("/faction accept          ")).append(Component.text("- Accept a pending invitation", NamedTextColor.YELLOW))
                                    .append(Component.newline())
                                    .append(info("/faction decline         ")).append(Component.text("- Decline a pending invitation", NamedTextColor.YELLOW))
                                    .append(Component.newline())
                                    .append(info("/faction leave           ")).append(Component.text("- Leave your current faction", NamedTextColor.YELLOW))
                                    .append(Component.newline())
                                    .append(info("/faction rename <name>   ")).append(Component.text("- Rename your faction (owner only)", NamedTextColor.YELLOW))
                                    .append(Component.newline())
                                    .append(info("/faction color <color>   ")).append(Component.text("- Change faction color (owner only)", NamedTextColor.YELLOW))
                                    .append(Component.newline())
                                    .append(info("/faction kick <player>   ")).append(Component.text("- Kick a member (owner only)", NamedTextColor.YELLOW))
                                    .append(Component.newline())
                                    .append(info("/faction delete          ")).append(Component.text("- Delete your faction (owner only)", NamedTextColor.YELLOW))
                                    .append(Component.newline())
                                    .append(info("/faction list            ")).append(Component.text("- List all factions", NamedTextColor.YELLOW))
                                    .append(Component.newline())
                                    .append(info("/faction members         ")).append(Component.text("- Show members of your faction", NamedTextColor.YELLOW))
                    );
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("create")
                        .requires(src -> src.getSender().hasPermission("atlas.faction.create"))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> {
                                    Entity executor = ctx.getSource().getExecutor();
                                    if (!(executor instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    String name = StringArgumentType.getString(ctx, "name");

                                    if (FactionManager.createFaction(name, player.getUniqueId())) {
                                        player.sendMessage(success("Faction '" + name + "' created successfully."));
                                        ItemStack crystal = new ItemStack(Material.END_CRYSTAL);
                                        ItemMeta meta = crystal.getItemMeta();
                                        meta.displayName(Component.text("Crystal of the End", NamedTextColor.LIGHT_PURPLE)
                                                .decoration(TextDecoration.ITALIC, false));
                                        crystal.setItemMeta(meta);
                                        player.getInventory().setItemInOffHand(crystal);
                                    } else {
                                        player.sendMessage(error("Could not create faction. You may already be in one, or that name is taken."));
                                    }

                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("invite")
                        .requires(src -> src.getSender().hasPermission("atlas.faction.invite"))
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .executes(ctx -> {
                                    Entity executor = ctx.getSource().getExecutor();
                                    if (!(executor instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
                                    Player target = resolver.resolve(ctx.getSource()).getFirst();

                                    if (target.equals(player)) {
                                        player.sendMessage(error("You cannot invite yourself."));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    if (FactionManager.invitePlayer(player.getUniqueId(), target.getUniqueId())) {
                                        String factionName = FactionManager.getPlayerFaction(player.getUniqueId());
                                        player.sendMessage(info("Invitation sent to " + target.getName() + "."));
                                        target.sendMessage(
                                                info(player.getName() + " invited you to join faction '")
                                                        .append(Component.text(factionName, NamedTextColor.GOLD))
                                                        .append(info("'.  "))
                                                        .append(Component.text("[Accept]", NamedTextColor.GREEN)
                                                                .clickEvent(ClickEvent.runCommand("/faction accept")))
                                                        .append(Component.text("  "))
                                                        .append(Component.text("[Decline]", NamedTextColor.RED)
                                                                .clickEvent(ClickEvent.runCommand("/faction decline")))
                                        );
                                    } else {
                                        player.sendMessage(error("Could not invite " + target.getName() + ". Make sure you are the faction owner and the player is not already in a faction."));
                                    }

                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("accept")
                        .requires(src -> src.getSender().hasPermission("atlas.faction.accept"))
                        .executes(ctx -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player player)) {
                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                return Command.SINGLE_SUCCESS;
                            }

                            String factionName = FactionManager.acceptInvitation(player.getUniqueId());

                            if (factionName != null) {
                                player.sendMessage(success("You joined faction '" + factionName + "'."));
                                FactionManager.broadcastToFaction(factionName,
                                        info(player.getName() + " joined the faction."),
                                        player.getUniqueId());
                            } else {
                                player.sendMessage(error("You have no pending invitation."));
                            }

                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("decline")
                        .requires(src -> src.getSender().hasPermission("atlas.faction.decline"))
                        .executes(ctx -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player player)) {
                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                return Command.SINGLE_SUCCESS;
                            }

                            if (FactionManager.declineInvitation(player.getUniqueId())) {
                                player.sendMessage(Component.text("Invitation declined.", NamedTextColor.RED));
                            } else {
                                player.sendMessage(error("You have no pending invitation."));
                            }

                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("rename")
                        .requires(src -> src.getSender().hasPermission("atlas.faction.rename"))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> {
                                    Entity executor = ctx.getSource().getExecutor();
                                    if (!(executor instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    String newName = StringArgumentType.getString(ctx, "name");

                                    if (FactionManager.renameFaction(newName, player.getUniqueId())) {
                                        player.sendMessage(success("Faction renamed to '" + newName + "'."));
                                        FactionManager.broadcastToFaction(newName,
                                                info(player.getName() + " renamed the faction to '" + newName + "'."),
                                                player.getUniqueId());
                                    } else {
                                        player.sendMessage(error("Could not rename. Make sure you are the owner and the new name is not already taken."));
                                    }

                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("color")
                        .requires(src -> src.getSender().hasPermission("atlas.faction.color"))
                        .then(Commands.argument("color", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    NamedTextColor.NAMES.keys().forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    Entity executor = ctx.getSource().getExecutor();
                                    if (!(executor instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    String colorName = StringArgumentType.getString(ctx, "color");
                                    NamedTextColor color = NamedTextColor.NAMES.value(colorName);

                                    if (color == null) {
                                        player.sendMessage(error("Unknown color '" + colorName + "'. Use tab-complete to see valid colors."));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    String factionNameForColor = FactionManager.getPlayerFaction(player.getUniqueId());
                                    if (FactionManager.setFactionColor(player.getUniqueId(), color)) {
                                        Component colorMsg = info("Faction color changed to ")
                                                .append(Component.text(colorName, color))
                                                .append(info("."));
                                        player.sendMessage(colorMsg);
                                        FactionManager.broadcastToFaction(factionNameForColor,
                                                info(player.getName() + " changed the faction color to ")
                                                        .append(Component.text(colorName, color))
                                                        .append(info(".")),
                                                player.getUniqueId());
                                    } else {
                                        player.sendMessage(error("Could not change color. Make sure you are the faction owner."));
                                    }

                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("kick")
                        .requires(src -> src.getSender().hasPermission("atlas.faction.kick"))
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .executes(ctx -> {
                                    Entity executor = ctx.getSource().getExecutor();
                                    if (!(executor instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
                                    Player target = resolver.resolve(ctx.getSource()).getFirst();

                                    if (FactionManager.kickPlayer(player.getUniqueId(), target.getUniqueId())) {
                                        String factionNameForKick = FactionManager.getPlayerFaction(player.getUniqueId());
                                        player.sendMessage(info(target.getName() + " has been kicked from the faction."));
                                        target.sendMessage(error("You have been kicked from the faction."));
                                        FactionManager.broadcastToFaction(factionNameForKick,
                                                info(target.getName() + " has been kicked from the faction."),
                                                player.getUniqueId());
                                    } else {
                                        player.sendMessage(error("Could not kick " + target.getName() + ". Make sure you are the owner and that player is in your faction."));
                                    }

                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("delete")
                        .requires(src -> src.getSender().hasPermission("atlas.faction.delete"))
                        .executes(ctx -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player player)) {
                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                return Command.SINGLE_SUCCESS;
                            }

                            String factionNameForDelete = FactionManager.getPlayerFaction(player.getUniqueId());
                            Faction grpForDelete = factionNameForDelete != null
                                    ? FactionManager.getFaction(factionNameForDelete) : null;
                            if (grpForDelete != null && grpForDelete.getOwner().equals(player.getUniqueId())) {
                                FactionManager.broadcastToFaction(factionNameForDelete,
                                        error("The faction has been disbanded by " + player.getName() + "."),
                                        player.getUniqueId());
                            }

                            if (FactionManager.deleteFaction(player.getUniqueId())) {
                                player.sendMessage(success("Your faction has been deleted."));
                            } else {
                                player.sendMessage(error("Could not delete faction. Make sure you are the owner."));
                            }

                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("leave")
                        .requires(src -> src.getSender().hasPermission("atlas.faction.leave"))
                        .executes(ctx -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player player)) {
                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                return Command.SINGLE_SUCCESS;
                            }

                            String factionNameForLeave = FactionManager.getPlayerFaction(player.getUniqueId());
                            if (factionNameForLeave == null) {
                                player.sendMessage(error("You are not in any faction."));
                                return Command.SINGLE_SUCCESS;
                            }

                            if (FactionManager.leaveFaction(player.getUniqueId())) {
                                player.sendMessage(success("You left the faction '" + factionNameForLeave + "'."));
                                FactionManager.broadcastToFaction(factionNameForLeave,
                                        info(player.getName() + " left the faction."),
                                        player.getUniqueId());
                            } else {
                                player.sendMessage(error("You are the owner — use /faction delete to disband the faction instead."));
                            }

                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("list")
                        .requires(src -> src.getSender().hasPermission("atlas.faction.list"))
                        .executes(ctx -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player player)) {
                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                return Command.SINGLE_SUCCESS;
                            }

                            var factions = FactionManager.getFactions();
                            if (factions.isEmpty()) {
                                player.sendMessage(info("No factions exist yet."));
                                return Command.SINGLE_SUCCESS;
                            }

                            Component list = Component.text("--- Factions (" + factions.size() + ") ---", NamedTextColor.GOLD);
                            for (Faction g : factions.values()) {
                                int total = 1 + g.getMembers().size(); // owner + members
                                list = list.append(Component.newline())
                                        .append(Component.text(g.getName(), g.getColor()))
                                        .append(Component.text(" (" + total + " member" + (total == 1 ? "" : "s") + ")", NamedTextColor.GRAY));
                            }
                            player.sendMessage(list);
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("members")
                        .requires(src -> src.getSender().hasPermission("atlas.faction.members"))
                        .executes(ctx -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player player)) {
                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                return Command.SINGLE_SUCCESS;
                            }

                            String factionNameForMembers = FactionManager.getPlayerFaction(player.getUniqueId());
                            if (factionNameForMembers == null) {
                                player.sendMessage(error("You are not in any faction."));
                                return Command.SINGLE_SUCCESS;
                            }

                            Faction grp = FactionManager.getFaction(factionNameForMembers);
                            List<UUID> memberUUIDs = FactionManager.getFactionPlayers(factionNameForMembers);

                            Component list = Component.text("--- " + factionNameForMembers + " members ---", grp.getColor());
                            for (UUID uuid : memberUUIDs) {
                                boolean isOwner = uuid.equals(grp.getOwner());
                                Player member = Bukkit.getPlayer(uuid);
                                String name = member != null ? member.getName() : uuid.toString();
                                boolean online = member != null;
                                list = list.append(Component.newline())
                                        .append(Component.text(name, online ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY))
                                        .append(Component.text(isOwner ? " [Owner]" : "", NamedTextColor.YELLOW))
                                        .append(Component.text(online ? "" : " (offline)", NamedTextColor.DARK_GRAY));
                            }
                            player.sendMessage(list);
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
