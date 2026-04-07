package org.minecraft.atlas.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.minecraft.atlas.faction.HomeTeleportManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.minecraft.atlas.faction.AtlasCrystal;
import org.minecraft.atlas.faction.AtlasCrystalManager;
import org.minecraft.atlas.faction.Faction;
import org.minecraft.atlas.faction.FactionManager;
import org.minecraft.atlas.faction.FactionRole;

import java.util.ArrayList;
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

    /**
     * Returns the display name for a UUID, checking online players first then offline player cache.
     */
    private static String getPlayerName(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        String name = offline.getName();
        return name != null ? name : uuid.toString();
    }

    /**
     * Builds the faction info Component. The detailed member list is only shown when isInsideFaction is true.
     */
    private static Component buildFactionInfo(Faction faction, boolean isInsideFaction) {
        String colorName = NamedTextColor.NAMES.key(faction.getColor());
        if (colorName == null) colorName = "white";
        String desc = (faction.getDescription() == null || faction.getDescription().isBlank())
                ? "No description set." : faction.getDescription();
        String ownerName = getPlayerName(faction.getOwner());
        int totalMembers = 1 + faction.getMembers().size();

        Component msg = Component.text("--- " + faction.getName() + " ---", faction.getColor())
                .append(Component.newline())
                .append(Component.text("Color: ", NamedTextColor.GRAY))
                .append(Component.text(colorName, faction.getColor()))
                .append(Component.newline())
                .append(Component.text("Description: ", NamedTextColor.GRAY))
                .append(Component.text(desc, NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("Owner: ", NamedTextColor.GRAY))
                .append(Component.text(ownerName, NamedTextColor.YELLOW));

        if (isInsideFaction) {
            List<String> leaders = new ArrayList<>();
            List<String> moderators = new ArrayList<>();
            List<String> plainMembers = new ArrayList<>();

            for (UUID uuid : faction.getMembers()) {
                String name = getPlayerName(uuid);
                switch (faction.getRole(uuid)) {
                    case LEADER -> leaders.add(name);
                    case MODERATOR -> moderators.add(name);
                    case MEMBER -> plainMembers.add(name);
                }
            }

            msg = msg.append(Component.newline())
                    .append(Component.text("Leaders: ", NamedTextColor.GRAY))
                    .append(Component.text(leaders.isEmpty() ? "None" : String.join(", ", leaders), NamedTextColor.GOLD))
                    .append(Component.newline())
                    .append(Component.text("Moderators: ", NamedTextColor.GRAY))
                    .append(Component.text(moderators.isEmpty() ? "None" : String.join(", ", moderators), NamedTextColor.AQUA))
                    .append(Component.newline())
                    .append(Component.text("Members (" + totalMembers + "):", NamedTextColor.GRAY))
                    .append(Component.newline())
                    .append(Component.text("  " + ownerName, NamedTextColor.WHITE))
                    .append(Component.text(" [Owner]", NamedTextColor.YELLOW));

            for (String name : leaders) {
                msg = msg.append(Component.newline())
                        .append(Component.text("  " + name, NamedTextColor.WHITE))
                        .append(Component.text(" [Leader]", NamedTextColor.GOLD));
            }
            for (String name : moderators) {
                msg = msg.append(Component.newline())
                        .append(Component.text("  " + name, NamedTextColor.WHITE))
                        .append(Component.text(" [Moderator]", NamedTextColor.AQUA));
            }
            for (String name : plainMembers) {
                msg = msg.append(Component.newline())
                        .append(Component.text("  " + name, NamedTextColor.WHITE));
            }
        } else {
            msg = msg.append(Component.newline())
                    .append(Component.text("Members: ", NamedTextColor.GRAY))
                    .append(Component.text(String.valueOf(totalMembers), NamedTextColor.WHITE));
        }

        return msg;
    }

    /**
     * Builds one help entry: gray "/faction " + gold subcommand + dark-aqua args + yellow " - description".
     */
    private static Component helpEntry(String sub, String args, String desc) {
        Component line = Component.text("/faction ", NamedTextColor.GRAY)
                .append(Component.text(sub, NamedTextColor.GOLD));
        if (!args.isEmpty()) {
            line = line.append(Component.text(" " + args, NamedTextColor.DARK_AQUA));
        }
        return line.append(Component.text(" - " + desc, NamedTextColor.YELLOW));
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
                                    .append(Component.newline()).append(helpEntry("create", "<name>", "Create a new faction"))
                                    .append(Component.newline()).append(helpEntry("invite", "<player>", "Invite a player (moderator+)"))
                                    .append(Component.newline()).append(helpEntry("accept", "", "Accept a pending invitation"))
                                    .append(Component.newline()).append(helpEntry("decline", "", "Decline a pending invitation"))
                                    .append(Component.newline()).append(helpEntry("leave", "", "Leave your current faction"))
                                    .append(Component.newline()).append(helpEntry("rename", "<name>", "Rename your faction (leader+)"))
                                    .append(Component.newline()).append(helpEntry("description", "[text]", "View or set description (leader+)"))
                                    .append(Component.newline()).append(helpEntry("color", "<color>", "Change faction color (leader+)"))
                                    .append(Component.newline()).append(helpEntry("promote", "<player>", "Promote a member (owner only)"))
                                    .append(Component.newline()).append(helpEntry("demote", "<player>", "Demote a member (owner only)"))
                                    .append(Component.newline()).append(helpEntry("transfer", "<player>", "Transfer ownership (owner only)"))
                                    .append(Component.newline()).append(helpEntry("kick", "<player>", "Kick a member (owner only)"))
                                    .append(Component.newline()).append(helpEntry("disband", "", "Delete your faction (owner only)"))
                                    .append(Component.newline()).append(helpEntry("info", "[faction]", "Show faction information"))
                                    .append(Component.newline()).append(helpEntry("list", "", "List all factions"))
                                    .append(Component.newline()).append(helpEntry("members", "", "Show members of your faction"))
                                    .append(Component.newline()).append(helpEntry("home", "", "Teleport to faction home"))
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
                                        player.sendMessage(info("Don't forget to pick a job using the /job command!"));
                                        ItemStack crystal = new ItemStack(Material.END_CRYSTAL);
                                        ItemMeta meta = crystal.getItemMeta();
                                        meta.displayName(Component.text("Crystal of the End", NamedTextColor.LIGHT_PURPLE)
                                                .decoration(TextDecoration.ITALIC, false));
                                        meta.getPersistentDataContainer().set(
                                                AtlasCrystalManager.getKeyFaction(),
                                                PersistentDataType.STRING, name);
                                        crystal.setItemMeta(meta);

                                        ItemStack itemMainHand = player.getInventory().getItemInMainHand();
                                        if (!(itemMainHand.equals(new ItemStack(Material.AIR)))) {
                                            player.getWorld().dropItemNaturally(player.getLocation(), itemMainHand);
                                        }

                                        player.getInventory().setItemInMainHand(crystal);
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
                                        player.sendMessage(error("Could not invite " + target.getName() + ". You must be at least a Moderator, and the player must not already be in a faction."));
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
                                player.sendMessage(info("Don't forget to pick a job using the /job command!"));
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
                                        player.sendMessage(error("Could not rename. You must be a Leader or Owner, and the new name must not already be taken."));
                                    }

                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("description")
                        .requires(src -> src.getSender().hasPermission("atlas.faction.description"))
                        .executes(ctx -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player player)) {
                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                return Command.SINGLE_SUCCESS;
                            }

                            String factionName = FactionManager.getPlayerFaction(player.getUniqueId());
                            if (factionName == null) {
                                player.sendMessage(error("You are not in any faction."));
                                return Command.SINGLE_SUCCESS;
                            }

                            Faction faction = FactionManager.getFaction(factionName);
                            String desc = faction.getDescription();
                            if (desc == null || desc.isBlank()) {
                                player.sendMessage(info("Your faction has no description set."));
                            } else {
                                player.sendMessage(Component.text("--- " + factionName + " ---", faction.getColor())
                                        .append(Component.newline())
                                        .append(Component.text(desc, NamedTextColor.WHITE)));
                            }

                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    Entity executor = ctx.getSource().getExecutor();
                                    if (!(executor instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    String text = StringArgumentType.getString(ctx, "text");

                                    if (FactionManager.setFactionDescription(player.getUniqueId(), text)) {
                                        player.sendMessage(success("Faction description updated."));
                                        String factionName = FactionManager.getPlayerFaction(player.getUniqueId());
                                        FactionManager.broadcastToFaction(factionName,
                                                info(player.getName() + " updated the faction description."),
                                                player.getUniqueId());
                                    } else {
                                        player.sendMessage(error("Could not update description. You must be a Leader or Owner."));
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
                                        player.sendMessage(error("Could not change color. You must be a Leader or Owner."));
                                    }

                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("promote")
                        .requires(src -> src.getSender().hasPermission("atlas.faction.promote"))
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
                                        player.sendMessage(error("You cannot promote yourself."));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    if (FactionManager.promotePlayer(player.getUniqueId(), target.getUniqueId())) {
                                        FactionRole newRole = FactionManager.getPlayerRole(target.getUniqueId());
                                        String roleName = newRole != null ? newRole.displayName() : "Leader";
                                        String factionName = FactionManager.getPlayerFaction(player.getUniqueId());
                                        player.sendMessage(success(target.getName() + " has been promoted to " + roleName + "."));
                                        target.sendMessage(info("You have been promoted to " + roleName + " in your faction."));
                                        FactionManager.broadcastToFaction(factionName,
                                                info(target.getName() + " has been promoted to " + roleName + "."),
                                                player.getUniqueId());
                                    } else {
                                        player.sendMessage(error("Could not promote " + target.getName() + ". Only the Owner can promote, and the player must be in your faction and not already at the highest role."));
                                    }

                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("demote")
                        .requires(src -> src.getSender().hasPermission("atlas.faction.demote"))
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
                                        player.sendMessage(error("You cannot demote yourself."));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    if (FactionManager.demotePlayer(player.getUniqueId(), target.getUniqueId())) {
                                        FactionRole newRole = FactionManager.getPlayerRole(target.getUniqueId());
                                        String roleName = newRole != null ? newRole.displayName() : "Member";
                                        String factionName = FactionManager.getPlayerFaction(player.getUniqueId());
                                        player.sendMessage(success(target.getName() + " has been demoted to " + roleName + "."));
                                        target.sendMessage(info("You have been demoted to " + roleName + " in your faction."));
                                        FactionManager.broadcastToFaction(factionName,
                                                info(target.getName() + " has been demoted to " + roleName + "."),
                                                player.getUniqueId());
                                    } else {
                                        player.sendMessage(error("Could not demote " + target.getName() + ". Only the Owner can demote, and the player must be in your faction and not already at the lowest role."));
                                    }

                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("transfer")
                        .requires(src -> src.getSender().hasPermission("atlas.faction.transfer"))
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
                                        player.sendMessage(error("You cannot transfer ownership to yourself."));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    if (FactionManager.transferOwnership(player.getUniqueId(), target.getUniqueId())) {
                                        String factionName = FactionManager.getPlayerFaction(player.getUniqueId());
                                        player.sendMessage(success("Ownership of the faction has been transferred to " + target.getName() + "."));
                                        target.sendMessage(info("You are now the Owner of faction '" + factionName + "'."));
                                        FactionManager.broadcastToFaction(factionName,
                                                info(target.getName() + " is now the Owner of the faction."),
                                                player.getUniqueId());
                                    } else {
                                        player.sendMessage(error("Could not transfer ownership. You must be the Owner and the target must be a member of your faction."));
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
                                        player.sendMessage(error("Could not kick " + target.getName() + ". Make sure you are the Owner and that player is in your faction."));
                                    }

                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("disband")
                        .requires(src -> src.getSender().hasPermission("atlas.faction.disband"))
                        .executes(ctx -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player player)) {
                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                return Command.SINGLE_SUCCESS;
                            }

                            String factionNameForDisband = FactionManager.getPlayerFaction(player.getUniqueId());
                            if (factionNameForDisband == null) {
                                player.sendMessage(error("You are not in any faction."));
                                return Command.SINGLE_SUCCESS;
                            }
                            Faction grpForDisband = FactionManager.getFaction(factionNameForDisband);
                            if (!grpForDisband.getOwner().equals(player.getUniqueId())) {
                                player.sendMessage(error("Only the Owner can disband the faction."));
                                return Command.SINGLE_SUCCESS;
                            }

                            ClickCallback.Options singleUse = ClickCallback.Options.builder().uses(1).build();

                            Component confirmMsg = Component.text("Disband '", NamedTextColor.YELLOW)
                                    .append(Component.text(factionNameForDisband, grpForDisband.getColor()))
                                    .append(Component.text("'? This cannot be undone!  ", NamedTextColor.YELLOW))
                                    .append(Component.text("[Confirm]", NamedTextColor.GREEN)
                                            .decorate(TextDecoration.BOLD)
                                            .clickEvent(ClickEvent.callback(audience -> {
                                                if (!(audience instanceof Player p)) return;
                                                String fn = FactionManager.getPlayerFaction(p.getUniqueId());
                                                if (fn == null) {
                                                    p.sendMessage(error("You are no longer in a faction."));
                                                    return;
                                                }
                                                Faction f = FactionManager.getFaction(fn);
                                                if (!f.getOwner().equals(p.getUniqueId())) {
                                                    p.sendMessage(error("You are no longer the Owner."));
                                                    return;
                                                }
                                                FactionManager.broadcastToFaction(fn,
                                                        error("The faction has been disbanded by " + p.getName() + "."),
                                                        p.getUniqueId());
                                                FactionManager.deleteFaction(p.getUniqueId());
                                                p.sendMessage(success("Your faction has been disbanded."));
                                            }, singleUse)))
                                    .append(Component.text("  [Cancel]", NamedTextColor.RED)
                                            .decorate(TextDecoration.BOLD)
                                            .clickEvent(ClickEvent.callback(audience -> {
                                                if (!(audience instanceof Player p)) return;
                                                p.sendMessage(info("Disband cancelled."));
                                            }, singleUse)));

                            player.sendMessage(confirmMsg);
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
                                player.sendMessage(error("You are the Owner — use /faction disband to disband the faction instead."));
                            }

                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("info")
                        .requires(src -> src.getSender().hasPermission("atlas.faction.info"))
                        .executes(ctx -> {
                            // No argument: show the player's own faction
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player player)) {
                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                return Command.SINGLE_SUCCESS;
                            }
                            String ownFaction = FactionManager.getPlayerFaction(player.getUniqueId());
                            if (ownFaction == null) {
                                player.sendMessage(error("You are not in any faction. Use /faction info <name> to look up a faction."));
                                return Command.SINGLE_SUCCESS;
                            }
                            player.sendMessage(buildFactionInfo(FactionManager.getFaction(ownFaction), true));
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("faction", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    FactionManager.getFactions().keySet().forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    Entity executor = ctx.getSource().getExecutor();
                                    if (!(executor instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    String targetFactionName = StringArgumentType.getString(ctx, "faction");
                                    Faction faction = FactionManager.getFaction(targetFactionName);

                                    if (faction == null) {
                                        player.sendMessage(error("Faction '" + targetFactionName + "' does not exist."));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    boolean isInsideFaction = targetFactionName.equals(
                                            FactionManager.getPlayerFaction(player.getUniqueId()));

                                    player.sendMessage(buildFactionInfo(faction, isInsideFaction));
                                    return Command.SINGLE_SUCCESS;
                                })))
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
                                int total = 1 + g.getMembers().size();
                                String desc = (g.getDescription() == null || g.getDescription().isBlank())
                                        ? "No description set." : g.getDescription();
                                list = list.append(Component.newline())
                                        .append(Component.text(g.getName(), g.getColor()))
                                        .append(Component.text(" (" + total + " member" + (total == 1 ? "" : "s") + ")", NamedTextColor.GRAY))
                                        .append(Component.text(" - " + desc, NamedTextColor.DARK_GRAY));
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

                                Component roleTag;
                                if (isOwner) {
                                    roleTag = Component.text(" [Owner]", NamedTextColor.YELLOW);
                                } else {
                                    FactionRole role = grp.getRole(uuid);
                                    roleTag = switch (role) {
                                        case LEADER -> Component.text(" [Leader]", NamedTextColor.GOLD);
                                        case MODERATOR -> Component.text(" [Moderator]", NamedTextColor.AQUA);
                                        case MEMBER -> Component.empty();
                                    };
                                }

                                list = list.append(Component.newline())
                                        .append(Component.text(name, online ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY))
                                        .append(roleTag)
                                        .append(Component.text(online ? "" : " (offline)", NamedTextColor.DARK_GRAY));
                            }
                            player.sendMessage(list);
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("home")
                        .requires(src -> src.getSender().hasPermission("atlas.faction.home"))
                        .executes(ctx -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player player)) {
                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                return Command.SINGLE_SUCCESS;
                            }
                            String factionName = FactionManager.getPlayerFaction(player.getUniqueId());
                            if (factionName == null) {
                                player.sendMessage(error("You are not in any faction."));
                                return Command.SINGLE_SUCCESS;
                            }
                            Location home = AtlasCrystalManager.getFirstHome(factionName);
                            if (home == null) {
                                player.sendMessage(error("Your faction has no home. Place the Atlas Crystal to set one."));
                                return Command.SINGLE_SUCCESS;
                            }
                            HomeTeleportManager.startTeleport(player, home, "faction home");
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("crystal", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    Entity exec = ctx.getSource().getExecutor();
                                    if (exec instanceof Player p) {
                                        String fn = FactionManager.getPlayerFaction(p.getUniqueId());
                                        if (fn != null) {
                                            AtlasCrystalManager.getFactionCrystals(fn)
                                                    .forEach(c -> builder.suggest(c.getName()));
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    Entity executor = ctx.getSource().getExecutor();
                                    if (!(executor instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    String factionName = FactionManager.getPlayerFaction(player.getUniqueId());
                                    if (factionName == null) {
                                        player.sendMessage(error("You are not in any faction."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    String crystalName = StringArgumentType.getString(ctx, "crystal");
                                    AtlasCrystal crystal = AtlasCrystalManager.getCrystalByName(factionName, crystalName);
                                    if (crystal == null) {
                                        player.sendMessage(error("No crystal named '" + crystalName + "' found in your faction."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    Location home = crystal.getHome();
                                    if (home == null) {
                                        player.sendMessage(error("Crystal '" + crystalName + "' has no home set."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    HomeTeleportManager.startTeleport(player, home, crystalName);
                                    return Command.SINGLE_SUCCESS;
                                })))
                .build();
    }
}
