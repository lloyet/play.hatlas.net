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
import org.minecraft.atlas.spawn.Protection;
import org.minecraft.atlas.spawn.ProtectionManager;
import org.minecraft.atlas.spawn.SpawnTeleportManager;

public class SpawnCommand {

    private static Component error(String msg)   { return Component.text(msg, NamedTextColor.RED); }
    private static Component success(String msg) { return Component.text(msg, NamedTextColor.GREEN); }

    public static LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("spawn")
                .requires(src -> src.getSender().hasPermission("atlas.spawn"))

                // /spawn — teleport to "spawn" protection, or world spawn if none configured
                .executes(ctx -> {
                    Entity executor = ctx.getSource().getExecutor();
                    if (!(executor instanceof Player player)) {
                        ctx.getSource().getSender().sendMessage(error("Only players can use this command."));
                        return Command.SINGLE_SUCCESS;
                    }
                    Protection spawnProt = ProtectionManager.get("spawn");
                    if (spawnProt != null && spawnProt.getSpawnPoint() != null) {
                        player.teleport(spawnProt.getSpawnPoint());
                        player.sendMessage(success("Teleported to spawn."));
                        ProtectionManager.recordVisit(player.getUniqueId(), "spawn");
                    } else {
                        SpawnTeleportManager.startTeleport(player);
                    }
                    return Command.SINGLE_SUCCESS;
                })

                // /spawn <protection-name> — op: any; player: only if visited
                .then(Commands.argument("protection", StringArgumentType.word())
                        .suggests((ctx, b) -> {
                            ProtectionManager.getAll().stream()
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
                            String name = StringArgumentType.getString(ctx, "protection").toLowerCase();
                            Protection prot = ProtectionManager.get(name);
                            if (prot == null) {
                                player.sendMessage(error("No protection area named '" + name + "' exists."));
                                return Command.SINGLE_SUCCESS;
                            }
                            Location dest = prot.getSpawnPoint();
                            if (dest == null) {
                                player.sendMessage(error("No teleport location is set for '" + name + "'."));
                                return Command.SINGLE_SUCCESS;
                            }
                            boolean isAdmin = player.hasPermission("atlas.spawn.admin");
                            if (!isAdmin && !ProtectionManager.hasVisited(player.getUniqueId(), name)) {
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
