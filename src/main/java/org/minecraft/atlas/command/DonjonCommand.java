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
import org.minecraft.atlas.Atlas;
import org.minecraft.atlas.donjon.Donjon;
import org.minecraft.atlas.donjon.DonjonManager;
import org.minecraft.atlas.donjon.DonjonStatus;
import org.minecraft.atlas.donjon.DonjonType;

import java.util.Map;

@SuppressWarnings("UnstableApiUsage")
public class DonjonCommand {

    public static LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("donjon")
                .requires(src -> src.getSender().hasPermission("atlas.donjon.admin"))

                // /donjon create <type>
                .then(Commands.literal("create")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .executes(ctx -> {
                                    Entity executor = ctx.getSource().getExecutor();
                                    if (!(executor instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(
                                                Component.text("Only players can use this.", NamedTextColor.RED));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    String typeArg = StringArgumentType.getString(ctx, "type");
                                    DonjonType type = DonjonType.fromConfigKey(typeArg);
                                    if (type == null) {
                                        player.sendMessage(Component.text(
                                                "Unknown donjon type: " + typeArg + ". Valid types: trial",
                                                NamedTextColor.RED));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    Donjon donjon = DonjonManager.createDonjon(
                                            type, player.getLocation(), player.getLocation());
                                    if (donjon == null) {
                                        player.sendMessage(Component.text(
                                                "Failed to create donjon (no config for type?).", NamedTextColor.RED));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    Atlas.instance.getConfig();
                                    DonjonManager.saveDonjonConfig(Atlas.instance.getConfig());
                                    Atlas.instance.saveConfig();
                                    player.sendMessage(Component.text(
                                            "Donjon created: [" + donjon.getId() + "] " + donjon.getName()
                                                    + " Lv." + donjon.getLevel()
                                                    + " [" + donjon.getRarity().getDisplayName() + "]",
                                            NamedTextColor.GREEN));
                                    return Command.SINGLE_SUCCESS;
                                })))

                // /donjon list
                .then(Commands.literal("list")
                        .executes(ctx -> {
                            Map<String, Donjon> all = DonjonManager.getDonjons();
                            if (all.isEmpty()) {
                                ctx.getSource().getSender().sendMessage(
                                        Component.text("No donjons registered.", NamedTextColor.YELLOW));
                                return Command.SINGLE_SUCCESS;
                            }
                            ctx.getSource().getSender().sendMessage(
                                    Component.text("=== Donjons ===", NamedTextColor.GOLD));
                            for (Donjon d : all.values()) {
                                ctx.getSource().getSender().sendMessage(
                                        Component.text("[" + d.getId() + "] ", NamedTextColor.GRAY)
                                                .append(Component.text(d.getName(), d.getRarity().getColor()))
                                                .append(Component.text(
                                                        " Lv." + d.getLevel()
                                                                + " | " + d.getType().getDisplayName()
                                                                + " | " + d.getStatus().name()
                                                                + (d.isInProgress() ? " (IN PROGRESS)" : ""),
                                                        NamedTextColor.YELLOW)));
                            }
                            return Command.SINGLE_SUCCESS;
                        }))

                // /donjon info <id>
                .then(Commands.literal("info")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> {
                                    String id = StringArgumentType.getString(ctx, "id");
                                    Donjon d = DonjonManager.getDonjon(id);
                                    if (d == null) {
                                        ctx.getSource().getSender().sendMessage(
                                                Component.text("Donjon not found: " + id, NamedTextColor.RED));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    ctx.getSource().getSender().sendMessage(
                                            Component.text("=== " + d.getName() + " ===", d.getRarity().getColor()));
                                    ctx.getSource().getSender().sendMessage(Component.text(
                                            "ID: " + d.getId()
                                                    + " | Type: " + d.getType().getDisplayName()
                                                    + " | Level: " + d.getLevel()
                                                    + " | Rarity: " + d.getRarity().getDisplayName(),
                                            NamedTextColor.AQUA));
                                    ctx.getSource().getSender().sendMessage(Component.text(
                                            "Status: " + d.getStatus().name()
                                                    + " | InProgress: " + d.isInProgress()
                                                    + " | Wave: " + (d.getCurrentWaveIndex() + 1) + "/" + d.getWaves().size(),
                                            NamedTextColor.YELLOW));
                                    if (d.getCenter() != null) {
                                        ctx.getSource().getSender().sendMessage(Component.text(
                                                "Center: " + d.getCenter().getWorld().getName()
                                                        + " " + d.getCenter().getBlockX()
                                                        + "," + d.getCenter().getBlockY()
                                                        + "," + d.getCenter().getBlockZ(),
                                                NamedTextColor.GRAY));
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })))

                // /donjon activate <id>
                .then(Commands.literal("activate")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> {
                                    String id = StringArgumentType.getString(ctx, "id");
                                    Donjon d = DonjonManager.getDonjon(id);
                                    if (d == null) {
                                        ctx.getSource().getSender().sendMessage(
                                                Component.text("Donjon not found: " + id, NamedTextColor.RED));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    if (d.getStatus() == DonjonStatus.ACTIVE) {
                                        ctx.getSource().getSender().sendMessage(
                                                Component.text("Donjon is already active.", NamedTextColor.YELLOW));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    DonjonManager.activateDonjon(d);
                                    DonjonManager.saveDonjonConfig(Atlas.instance.getConfig());
                                    Atlas.instance.saveConfig();
                                    ctx.getSource().getSender().sendMessage(
                                            Component.text("Donjon activated: " + d.getName(), NamedTextColor.GREEN));
                                    return Command.SINGLE_SUCCESS;
                                })))

                // /donjon reset <id>
                .then(Commands.literal("reset")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> {
                                    String id = StringArgumentType.getString(ctx, "id");
                                    Donjon d = DonjonManager.getDonjon(id);
                                    if (d == null) {
                                        ctx.getSource().getSender().sendMessage(
                                                Component.text("Donjon not found: " + id, NamedTextColor.RED));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    DonjonManager.setIdle(d, null);
                                    DonjonManager.saveDonjonConfig(Atlas.instance.getConfig());
                                    Atlas.instance.saveConfig();
                                    ctx.getSource().getSender().sendMessage(
                                            Component.text("Donjon reset to idle: " + d.getName(), NamedTextColor.GREEN));
                                    return Command.SINGLE_SUCCESS;
                                })))

                // /donjon delete <id>
                .then(Commands.literal("delete")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> {
                                    String id = StringArgumentType.getString(ctx, "id");
                                    Donjon d = DonjonManager.getDonjon(id);
                                    if (d == null) {
                                        ctx.getSource().getSender().sendMessage(
                                                Component.text("Donjon not found: " + id, NamedTextColor.RED));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    String name = d.getName();
                                    DonjonManager.deleteDonjon(id);
                                    DonjonManager.saveDonjonConfig(Atlas.instance.getConfig());
                                    Atlas.instance.saveConfig();
                                    ctx.getSource().getSender().sendMessage(
                                            Component.text("Donjon deleted: " + name, NamedTextColor.GREEN));
                                    return Command.SINGLE_SUCCESS;
                                })))

                .build();
    }
}
