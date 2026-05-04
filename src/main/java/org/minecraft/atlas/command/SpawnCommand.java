package org.minecraft.atlas.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.minecraft.atlas.Atlas;
import org.minecraft.atlas.safezone.SafeZone;
import org.minecraft.atlas.safezone.SafeZoneManager;
import org.minecraft.atlas.spawn.SpawnManager;
import org.minecraft.atlas.spawn.SpawnTeleportManager;

public class SpawnCommand {

    private static Component error(String msg)   { return Component.text(msg, NamedTextColor.RED); }
    private static Component success(String msg) { return Component.text(msg, NamedTextColor.GREEN); }

    public static LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("spawn")
                .requires(src -> src.getSender().hasPermission("atlas.spawn"))

                // /spawn — teleport to "spawn" safe zone, or world spawn if none configured
                .executes(ctx -> {
                    Entity executor = ctx.getSource().getExecutor();
                    if (!(executor instanceof Player player)) {
                        ctx.getSource().getSender().sendMessage(error("Only players can use this command."));
                        return Command.SINGLE_SUCCESS;
                    }
                    SafeZone spawnZone = SafeZoneManager.get("spawn");
                    if (spawnZone != null && spawnZone.getSpawnPoint() != null) {
                        player.teleport(spawnZone.getSpawnPoint());
                        player.sendMessage(success("Teleported to spawn."));
                        SafeZoneManager.recordVisit(player.getUniqueId(), "spawn");
                    } else {
                        SpawnTeleportManager.startTeleport(player);
                    }
                    return Command.SINGLE_SUCCESS;
                })

                // /spawn set — set the world spawn to the player's location (admin)
                .then(Commands.literal("set")
                        .requires(src -> src.getSender().hasPermission("atlas.spawn.admin"))
                        .executes(ctx -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player player)) {
                                ctx.getSource().getSender().sendMessage(error("Only players can use this command."));
                                return Command.SINGLE_SUCCESS;
                            }
                            SpawnManager.setSpawn(player.getLocation());
                            SpawnManager.saveSpawn(Atlas.instance.getConfig());
                            Atlas.instance.saveConfig();
                            player.sendMessage(success(
                                    "Server spawn set to your location ("
                                            + player.getLocation().getBlockX() + ", "
                                            + player.getLocation().getBlockY() + ", "
                                            + player.getLocation().getBlockZ() + ")."));
                            return Command.SINGLE_SUCCESS;
                        }))

                // /spawn <safezone-name> — op: any; player: only if visited
                .then(Commands.argument("safezone", StringArgumentType.word())
                        .suggests((ctx, b) -> {
                            SafeZoneManager.getAll().stream()
                                    .filter(p -> p.getSpawnPoint() != null)
                                    .forEach(p -> b.suggest(p.getName()));
                            return b.buildFuture();
                        })
                        .executes(ctx -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player player)) {
                                ctx.getSource().getSender().sendMessage(error("Only players can use this command."));
                                return Command.SINGLE_SUCCESS;
                            }
                            String name = StringArgumentType.getString(ctx, "safezone").toLowerCase();
                            SafeZone zone = SafeZoneManager.get(name);
                            if (zone == null) {
                                player.sendMessage(error("No safe zone named '" + name + "' exists."));
                                return Command.SINGLE_SUCCESS;
                            }
                            Location dest = zone.getSpawnPoint();
                            if (dest == null) {
                                player.sendMessage(error("No teleport location is set for '" + name + "'."));
                                return Command.SINGLE_SUCCESS;
                            }
                            boolean isAdmin = player.hasPermission("atlas.spawn.admin");
                            if (!isAdmin && !SafeZoneManager.hasVisited(player.getUniqueId(), name)) {
                                player.sendMessage(error("You have not visited '" + name + "' yet."));
                                return Command.SINGLE_SUCCESS;
                            }
                            player.teleport(dest);
                            player.sendMessage(success("Teleported to '" + name + "'."));
                            return Command.SINGLE_SUCCESS;
                        }))

                .build();
    }
}
