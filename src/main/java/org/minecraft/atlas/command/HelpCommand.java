package org.minecraft.atlas.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class HelpCommand {

    public static LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("help")
                .executes(ctx -> {
                    ctx.getSource().getSender().sendMessage(
                            Component.text("Use ", NamedTextColor.GRAY)
                                    .append(Component.text("/help <topic>", NamedTextColor.GOLD))
                                    .append(Component.text(" for help on a specific topic. Available: ", NamedTextColor.GRAY))
                                    .append(Component.text("job, donjon", NamedTextColor.YELLOW))
                    );
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("job")
                        .executes(ctx -> {
                            ctx.getSource().getSender().sendMessage(buildJobHelp());
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("donjon")
                        .executes(ctx -> {
                            ctx.getSource().getSender().sendMessage(buildDonjonHelp());
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    private static Component buildJobHelp() {
        return Component.text("--- Job Commands ---", NamedTextColor.GOLD)
                .append(entry("/job", "Open the job selection menu (requires faction, no job yet)"))
                .append(entry("/job list", "List all available jobs"))
                .append(entry("/job level", "View your current level and XP progress"))
                .append(entry("/job remove [player]", "Remove a player's job (admin only)"))
                .append(entry("/job set <job> [player]", "Set a player's job directly (admin only)"))
                .append(entry("/job level add <player> <amount> xp", "Add XP to a player, capped at remaining XP for their current level (admin only)"))
                .append(entry("/job level add <player> <amount> level", "Add levels to a player (admin only)"))
                .append(entry("/job level set <player> <amount> xp", "Set a player's XP to an exact value (admin only)"))
                .append(entry("/job level set <player> <amount> level", "Set a player's level to an exact value (admin only)"))
                .append(entry("/job level remove <player> <amount> xp", "Remove XP from a player (admin only)"))
                .append(entry("/job level remove <player> <amount> level", "Remove levels from a player (admin only)"));
    }

    private static Component buildDonjonHelp() {
        return Component.text("--- Donjon Commands ---", NamedTextColor.GOLD)
                .append(entry("/donjon create <type>", "Create a donjon at your location (admin only)"))
                .append(entry("/donjon list", "List all registered donjons (admin only)"))
                .append(entry("/donjon info <id>", "Show detailed info about a donjon (admin only)"))
                .append(entry("/donjon activate <id>", "Activate a donjon so players can start it (admin only)"))
                .append(entry("/donjon reset <id>", "Reset a donjon to idle state (admin only)"))
                .append(entry("/donjon tp <id>", "Teleport to a donjon (admin only)"))
                .append(entry("/donjon delete <id>", "Permanently delete a donjon (admin only)"));
    }

    private static Component entry(String command, String description) {
        return Component.newline()
                .append(Component.text(command, NamedTextColor.GOLD))
                .append(Component.text(" — " + description, NamedTextColor.YELLOW));
    }
}
