package org.minecraft.atlas.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
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

    /** Suggests all currently registered donjon IDs. */
    private static final SuggestionProvider<CommandSourceStack> DONJON_IDS =
            (ctx, builder) -> {
                DonjonManager.getDonjons().keySet().forEach(builder::suggest);
                return builder.buildFuture();
            };

    /** Suggests all valid donjon type config keys (e.g. "trial"). */
    private static final SuggestionProvider<CommandSourceStack> DONJON_TYPES =
            (ctx, builder) -> {
                for (DonjonType t : DonjonType.values()) builder.suggest(t.getConfigKey());
                return builder.buildFuture();
            };

    public static LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("donjon")
                .requires(src -> src.getSender().hasPermission("atlas.donjon.admin"))

                // /donjon — show help
                .executes(ctx -> {
                    ctx.getSource().getSender().sendMessage(
                            Component.text("--- Donjon Commands ---", NamedTextColor.GOLD)
                                    .append(Component.newline()).append(helpEntry("create", "<type>", "Create a donjon at your location"))
                                    .append(Component.newline()).append(helpEntry("list", "", "List all registered donjons"))
                                    .append(Component.newline()).append(helpEntry("info", "<id>", "Show detailed donjon info"))
                                    .append(Component.newline()).append(helpEntry("activate", "<id>", "Activate a donjon"))
                                    .append(Component.newline()).append(helpEntry("reset", "<id>", "Reset a donjon to idle"))
                                    .append(Component.newline()).append(helpEntry("tp", "<id>", "Teleport to a donjon"))
                                    .append(Component.newline()).append(helpEntry("delete", "<id>", "Delete a donjon"))
                    );
                    return Command.SINGLE_SUCCESS;
                })

                // /donjon create <type>
                .then(Commands.literal("create")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests(DONJON_TYPES)
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
                                .suggests(DONJON_IDS)
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
                                    ctx.getSource().getSender().sendMessage(
                                            Component.text("ID: " + d.getId()
                                                    + " | Type: " + d.getType().getDisplayName()
                                                    + " | Level: " + d.getLevel()
                                                    + " | Rarity: ", NamedTextColor.AQUA)
                                            .append(Component.text(
                                                    "[" + d.getRarity().getDisplayName() + "] Lv." + d.getLevel(),
                                                    d.getRarity().getColor())));
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
                                .suggests(DONJON_IDS)
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
                                .suggests(DONJON_IDS)
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

                // /donjon tp <id>
                .then(Commands.literal("tp")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(DONJON_IDS)
                                .executes(ctx -> {
                                    Entity executor = ctx.getSource().getExecutor();

                                    if (!(executor instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(
                                                Component.text("Only players can use this.", NamedTextColor.RED));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    String id = StringArgumentType.getString(ctx, "id");
                                    Donjon d = DonjonManager.getDonjon(id);

                                    if (d == null) {
                                        player.sendMessage(Component.text(
                                                "Donjon not found: " + id, NamedTextColor.RED));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    player.teleport(d.getCenter());
                                    player.sendMessage(Component.text(
                                            "Teleported to donjon: " + d.getName()
                                                    + " (" + d.getCenter().getBlockX()
                                                    + "," + d.getCenter().getBlockY()
                                                    + "," + d.getCenter().getBlockZ() + ")",
                                            NamedTextColor.GREEN));

                                    return Command.SINGLE_SUCCESS;
                                })))

                // /donjon delete <id>
                .then(Commands.literal("delete")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests(DONJON_IDS)
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

    private static Component helpEntry(String sub, String args, String desc) {
        Component line = Component.text("/donjon ", NamedTextColor.GRAY)
                .append(Component.text(sub, NamedTextColor.GOLD));
        if (!args.isEmpty()) {
            line = line.append(Component.text(" " + args, NamedTextColor.DARK_AQUA));
        }
        return line.append(Component.text(" - " + desc, NamedTextColor.YELLOW));
    }
}
