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
import org.minecraft.atlas.spawn.Protection;
import org.minecraft.atlas.spawn.ProtectionManager;

public class ProtectionCommand {

    private static Component error(String msg)   { return Component.text(msg, NamedTextColor.RED); }
    private static Component success(String msg) { return Component.text(msg, NamedTextColor.GREEN); }
    private static Component info(String msg)    { return Component.text(msg, NamedTextColor.GOLD); }

    private static final String PERM = "atlas.protection.admin";

    public static LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("protection")
                .requires(src -> src.getSender().hasPermission(PERM))

                // /protection (no args) — show help
                .executes(ctx -> {
                    ctx.getSource().getSender().sendMessage(
                            Component.text("--- Protection Commands ---", NamedTextColor.GOLD)
                                    .append(Component.newline()).append(entry("create", "<name>", "Create a new protection area"))
                                    .append(Component.newline()).append(entry("claim",    "<name>", "Add current chunk to a protection area"))
                                    .append(Component.newline()).append(entry("unclaim",  "<name>", "Remove current chunk from a protection area"))
                                    .append(Component.newline()).append(entry("list",     "",       "List all protection areas with chunk counts"))
                                    .append(Component.newline()).append(entry("setspawn", "<name>", "Set the teleport location for a protection area"))
                    );
                    return Command.SINGLE_SUCCESS;
                })

                // /protection create <name>
                .then(Commands.literal("create")
                        .executes(ctx -> usage(ctx.getSource().getSender(), "create <name>"))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> {
                                    Entity exec = ctx.getSource().getExecutor();
                                    if (!(exec instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    String name = StringArgumentType.getString(ctx, "name").toLowerCase();
                                    if (ProtectionManager.create(name)) {
                                        save();
                                        player.sendMessage(success("Protection area '" + name + "' created."));
                                    } else {
                                        player.sendMessage(error("A protection area named '" + name + "' already exists."));
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })))

                // /protection claim <name>
                .then(Commands.literal("claim")
                        .executes(ctx -> usage(ctx.getSource().getSender(), "claim <name>"))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, b) -> { ProtectionManager.getAll().forEach(p -> b.suggest(p.getName())); return b.buildFuture(); })
                                .executes(ctx -> {
                                    Entity exec = ctx.getSource().getExecutor();
                                    if (!(exec instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    String name = StringArgumentType.getString(ctx, "name").toLowerCase();
                                    Protection prot = ProtectionManager.get(name);
                                    if (prot == null) {
                                        player.sendMessage(error("No protection area named '" + name + "'."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    org.bukkit.Chunk chunk = player.getLocation().getChunk();
                                    boolean added = prot.claim(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
                                    save();
                                    if (added) {
                                        player.sendMessage(success("Chunk [" + chunk.getX() + ", " + chunk.getZ()
                                                + "] added to '" + name + "'. (" + prot.getChunkCount() + " chunks total)"));
                                    } else {
                                        player.sendMessage(info("Chunk [" + chunk.getX() + ", " + chunk.getZ()
                                                + "] is already in '" + name + "'."));
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })))

                // /protection unclaim <name>
                .then(Commands.literal("unclaim")
                        .executes(ctx -> usage(ctx.getSource().getSender(), "unclaim <name>"))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, b) -> { ProtectionManager.getAll().forEach(p -> b.suggest(p.getName())); return b.buildFuture(); })
                                .executes(ctx -> {
                                    Entity exec = ctx.getSource().getExecutor();
                                    if (!(exec instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    String name = StringArgumentType.getString(ctx, "name").toLowerCase();
                                    Protection prot = ProtectionManager.get(name);
                                    if (prot == null) {
                                        player.sendMessage(error("No protection area named '" + name + "'."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    org.bukkit.Chunk chunk = player.getLocation().getChunk();
                                    boolean removed = prot.unclaim(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
                                    save();
                                    if (removed) {
                                        player.sendMessage(success("Chunk [" + chunk.getX() + ", " + chunk.getZ()
                                                + "] removed from '" + name + "'. (" + prot.getChunkCount() + " chunks remaining)"));
                                    } else {
                                        player.sendMessage(error("Chunk [" + chunk.getX() + ", " + chunk.getZ()
                                                + "] is not part of '" + name + "'."));
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })))

                // /protection list
                .then(Commands.literal("list")
                        .executes(ctx -> {
                            var sender = ctx.getSource().getSender();
                            var all = ProtectionManager.getAll();
                            if (all.isEmpty()) {
                                sender.sendMessage(info("No protection areas defined."));
                                return Command.SINGLE_SUCCESS;
                            }
                            sender.sendMessage(Component.text("=== Protection Areas ===", NamedTextColor.GOLD));
                            for (Protection p : all) {
                                String spawnInfo = p.getSpawnPoint() != null ? " [spawn set]" : "";
                                sender.sendMessage(
                                        Component.text("  " + p.getName(), NamedTextColor.AQUA)
                                                .append(Component.text(" — " + p.getChunkCount() + " chunks" + spawnInfo,
                                                        NamedTextColor.GRAY)));
                            }
                            return Command.SINGLE_SUCCESS;
                        }))

                // /protection setspawn <name>
                .then(Commands.literal("setspawn")
                        .executes(ctx -> usage(ctx.getSource().getSender(), "setspawn <name>"))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, b) -> { ProtectionManager.getAll().forEach(p -> b.suggest(p.getName())); return b.buildFuture(); })
                                .executes(ctx -> {
                                    Entity exec = ctx.getSource().getExecutor();
                                    if (!(exec instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    String name = StringArgumentType.getString(ctx, "name").toLowerCase();
                                    Protection prot = ProtectionManager.get(name);
                                    if (prot == null) {
                                        player.sendMessage(error("No protection area named '" + name + "'."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    prot.setSpawnPoint(player.getLocation());
                                    save();
                                    player.sendMessage(success("Teleport location set for '" + name + "' at "
                                            + player.getLocation().getBlockX() + ", "
                                            + player.getLocation().getBlockY() + ", "
                                            + player.getLocation().getBlockZ() + "."));
                                    return Command.SINGLE_SUCCESS;
                                })))

                .build();
    }

    private static void save() {
        ProtectionManager.saveConfig(Atlas.instance.getConfig());
        Atlas.instance.saveConfig();
    }

    private static int usage(net.kyori.adventure.audience.Audience audience, String syntax) {
        audience.sendMessage(error("Usage: /protection " + syntax));
        return Command.SINGLE_SUCCESS;
    }

    private static Component entry(String sub, String args, String desc) {
        Component line = Component.text("/protection ", NamedTextColor.GRAY)
                .append(Component.text(sub, NamedTextColor.GOLD));
        if (!args.isEmpty()) line = line.append(Component.text(" " + args, NamedTextColor.DARK_AQUA));
        return line.append(Component.text(" - " + desc, NamedTextColor.YELLOW));
    }
}
