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
import org.minecraft.atlas.group.Group;
import org.minecraft.atlas.group.GroupManager;

import java.util.List;
import java.util.UUID;

public class GroupCommand {

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
        return Commands.literal("group")
                .requires(src -> src.getSender().hasPermission("territory.group"))
                .executes(ctx -> {
                    ctx.getSource().getSender().sendMessage(
                            Component.text("--- Group Commands ---", NamedTextColor.GOLD)
                                    .append(Component.newline())
                                    .append(info("/group create <name>   ")).append(Component.text("- Create a new group", NamedTextColor.YELLOW))
                                    .append(Component.newline())
                                    .append(info("/group invite <player> ")).append(Component.text("- Invite a player (owner only)", NamedTextColor.YELLOW))
                                    .append(Component.newline())
                                    .append(info("/group accept          ")).append(Component.text("- Accept a pending invitation", NamedTextColor.YELLOW))
                                    .append(Component.newline())
                                    .append(info("/group decline         ")).append(Component.text("- Decline a pending invitation", NamedTextColor.YELLOW))
                                    .append(Component.newline())
                                    .append(info("/group leave           ")).append(Component.text("- Leave your current group", NamedTextColor.YELLOW))
                                    .append(Component.newline())
                                    .append(info("/group rename <name>   ")).append(Component.text("- Rename your group (owner only)", NamedTextColor.YELLOW))
                                    .append(Component.newline())
                                    .append(info("/group color <color>   ")).append(Component.text("- Change group color (owner only)", NamedTextColor.YELLOW))
                                    .append(Component.newline())
                                    .append(info("/group kick <player>   ")).append(Component.text("- Kick a member (owner only)", NamedTextColor.YELLOW))
                                    .append(Component.newline())
                                    .append(info("/group delete          ")).append(Component.text("- Delete your group (owner only)", NamedTextColor.YELLOW))
                                    .append(Component.newline())
                                    .append(info("/group list            ")).append(Component.text("- List all groups", NamedTextColor.YELLOW))
                                    .append(Component.newline())
                                    .append(info("/group members         ")).append(Component.text("- Show members of your group", NamedTextColor.YELLOW))
                    );
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("create")
                        .requires(src -> src.getSender().hasPermission("territory.group.create"))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> {
                                    Entity executor = ctx.getSource().getExecutor();
                                    if (!(executor instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    String name = StringArgumentType.getString(ctx, "name");

                                    if (GroupManager.createGroup(name, player.getUniqueId())) {
                                        player.sendMessage(success("Group '" + name + "' created successfully."));
                                        ItemStack crystal = new ItemStack(Material.END_CRYSTAL);
                                        ItemMeta meta = crystal.getItemMeta();
                                        meta.displayName(Component.text("Crystal of the End", NamedTextColor.LIGHT_PURPLE)
                                                .decoration(TextDecoration.ITALIC, false));
                                        crystal.setItemMeta(meta);
                                        player.getInventory().setItemInOffHand(crystal);
                                    } else {
                                        player.sendMessage(error("Could not create group. You may already be in one, or that name is taken."));
                                    }

                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("invite")
                        .requires(src -> src.getSender().hasPermission("territory.group.invite"))
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

                                    if (GroupManager.invitePlayer(player.getUniqueId(), target.getUniqueId())) {
                                        String groupName = GroupManager.getPlayerGroup(player.getUniqueId());
                                        player.sendMessage(info("Invitation sent to " + target.getName() + "."));
                                        target.sendMessage(
                                                info(player.getName() + " invited you to join group '")
                                                        .append(Component.text(groupName, NamedTextColor.GOLD))
                                                        .append(info("'.  "))
                                                        .append(Component.text("[Accept]", NamedTextColor.GREEN)
                                                                .clickEvent(ClickEvent.runCommand("/group accept")))
                                                        .append(Component.text("  "))
                                                        .append(Component.text("[Decline]", NamedTextColor.RED)
                                                                .clickEvent(ClickEvent.runCommand("/group decline")))
                                        );
                                    } else {
                                        player.sendMessage(error("Could not invite " + target.getName() + ". Make sure you are the group owner and the player is not already in a group."));
                                    }

                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("accept")
                        .requires(src -> src.getSender().hasPermission("territory.group.accept"))
                        .executes(ctx -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player player)) {
                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                return Command.SINGLE_SUCCESS;
                            }

                            String groupName = GroupManager.acceptInvitation(player.getUniqueId());

                            if (groupName != null) {
                                player.sendMessage(success("You joined group '" + groupName + "'."));
                                GroupManager.broadcastToGroup(groupName,
                                        info(player.getName() + " joined the group."),
                                        player.getUniqueId());
                            } else {
                                player.sendMessage(error("You have no pending invitation."));
                            }

                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("decline")
                        .requires(src -> src.getSender().hasPermission("territory.group.decline"))
                        .executes(ctx -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player player)) {
                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                return Command.SINGLE_SUCCESS;
                            }

                            if (GroupManager.declineInvitation(player.getUniqueId())) {
                                player.sendMessage(Component.text("Invitation declined.", NamedTextColor.RED));
                            } else {
                                player.sendMessage(error("You have no pending invitation."));
                            }

                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("rename")
                        .requires(src -> src.getSender().hasPermission("territory.group.rename"))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> {
                                    Entity executor = ctx.getSource().getExecutor();
                                    if (!(executor instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    String newName = StringArgumentType.getString(ctx, "name");

                                    if (GroupManager.renameGroup(newName, player.getUniqueId())) {
                                        player.sendMessage(success("Group renamed to '" + newName + "'."));
                                        GroupManager.broadcastToGroup(newName,
                                                info(player.getName() + " renamed the group to '" + newName + "'."),
                                                player.getUniqueId());
                                    } else {
                                        player.sendMessage(error("Could not rename. Make sure you are the owner and the new name is not already taken."));
                                    }

                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("color")
                        .requires(src -> src.getSender().hasPermission("territory.group.color"))
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

                                    String groupNameForColor = GroupManager.getPlayerGroup(player.getUniqueId());
                                    if (GroupManager.setGroupColor(player.getUniqueId(), color)) {
                                        Component colorMsg = info("Group color changed to ")
                                                .append(Component.text(colorName, color))
                                                .append(info("."));
                                        player.sendMessage(colorMsg);
                                        GroupManager.broadcastToGroup(groupNameForColor,
                                                info(player.getName() + " changed the group color to ")
                                                        .append(Component.text(colorName, color))
                                                        .append(info(".")),
                                                player.getUniqueId());
                                    } else {
                                        player.sendMessage(error("Could not change color. Make sure you are the group owner."));
                                    }

                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("kick")
                        .requires(src -> src.getSender().hasPermission("territory.group.kick"))
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .executes(ctx -> {
                                    Entity executor = ctx.getSource().getExecutor();
                                    if (!(executor instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
                                    Player target = resolver.resolve(ctx.getSource()).getFirst();

                                    if (GroupManager.kickPlayer(player.getUniqueId(), target.getUniqueId())) {
                                        String groupNameForKick = GroupManager.getPlayerGroup(player.getUniqueId());
                                        player.sendMessage(info(target.getName() + " has been kicked from the group."));
                                        target.sendMessage(error("You have been kicked from the group."));
                                        GroupManager.broadcastToGroup(groupNameForKick,
                                                info(target.getName() + " has been kicked from the group."),
                                                player.getUniqueId());
                                    } else {
                                        player.sendMessage(error("Could not kick " + target.getName() + ". Make sure you are the owner and that player is in your group."));
                                    }

                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("delete")
                        .requires(src -> src.getSender().hasPermission("territory.group.delete"))
                        .executes(ctx -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player player)) {
                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                return Command.SINGLE_SUCCESS;
                            }

                            String groupNameForDelete = GroupManager.getPlayerGroup(player.getUniqueId());
                            Group grpForDelete = groupNameForDelete != null
                                    ? GroupManager.getGroup(groupNameForDelete) : null;
                            if (grpForDelete != null && grpForDelete.getOwner().equals(player.getUniqueId())) {
                                GroupManager.broadcastToGroup(groupNameForDelete,
                                        error("The group has been disbanded by " + player.getName() + "."),
                                        player.getUniqueId());
                            }

                            if (GroupManager.deleteGroup(player.getUniqueId())) {
                                player.sendMessage(success("Your group has been deleted."));
                            } else {
                                player.sendMessage(error("Could not delete group. Make sure you are the owner."));
                            }

                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("leave")
                        .requires(src -> src.getSender().hasPermission("territory.group.leave"))
                        .executes(ctx -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player player)) {
                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                return Command.SINGLE_SUCCESS;
                            }

                            String groupNameForLeave = GroupManager.getPlayerGroup(player.getUniqueId());
                            if (groupNameForLeave == null) {
                                player.sendMessage(error("You are not in any group."));
                                return Command.SINGLE_SUCCESS;
                            }

                            if (GroupManager.leaveGroup(player.getUniqueId())) {
                                player.sendMessage(success("You left the group '" + groupNameForLeave + "'."));
                                GroupManager.broadcastToGroup(groupNameForLeave,
                                        info(player.getName() + " left the group."),
                                        player.getUniqueId());
                            } else {
                                player.sendMessage(error("You are the owner — use /group delete to disband the group instead."));
                            }

                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("list")
                        .requires(src -> src.getSender().hasPermission("territory.group.list"))
                        .executes(ctx -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player player)) {
                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                return Command.SINGLE_SUCCESS;
                            }

                            var groups = GroupManager.getGroups();
                            if (groups.isEmpty()) {
                                player.sendMessage(info("No groups exist yet."));
                                return Command.SINGLE_SUCCESS;
                            }

                            Component list = Component.text("--- Groups (" + groups.size() + ") ---", NamedTextColor.GOLD);
                            for (Group g : groups.values()) {
                                int total = 1 + g.getMembers().size(); // owner + members
                                list = list.append(Component.newline())
                                        .append(Component.text(g.getName(), g.getColor()))
                                        .append(Component.text(" (" + total + " member" + (total == 1 ? "" : "s") + ")", NamedTextColor.GRAY));
                            }
                            player.sendMessage(list);
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("members")
                        .requires(src -> src.getSender().hasPermission("territory.group.members"))
                        .executes(ctx -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player player)) {
                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                return Command.SINGLE_SUCCESS;
                            }

                            String groupNameForMembers = GroupManager.getPlayerGroup(player.getUniqueId());
                            if (groupNameForMembers == null) {
                                player.sendMessage(error("You are not in any group."));
                                return Command.SINGLE_SUCCESS;
                            }

                            Group grp = GroupManager.getGroup(groupNameForMembers);
                            List<UUID> memberUUIDs = GroupManager.getGroupPlayers(groupNameForMembers);

                            Component list = Component.text("--- " + groupNameForMembers + " members ---", grp.getColor());
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
