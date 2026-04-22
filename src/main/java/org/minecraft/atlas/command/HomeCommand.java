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
                            boolean hadHome = HomeManager.hasHome(player.getUniqueId());
                            HomeManager.setHome(player.getUniqueId(), name, player.getLocation());

                            HomeManager.saveHomes(Atlas.instance.getConfig());
                            Atlas.instance.saveConfig();

                            player.sendMessage(Component.text(
                                    (hadHome ? "Home updated" : "Home set") + ": '" + name + "'.",
                                    NamedTextColor.GREEN));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    public static LiteralCommandNode<CommandSourceStack> buildHome() {
        return Commands.literal("home")
                .requires(src -> src.getSender().hasPermission("atlas.home"))
                .executes(ctx -> {
                    Entity executor = ctx.getSource().getExecutor();
                    if (!(executor instanceof Player player)) {
                        ctx.getSource().getSender().sendMessage(
                                Component.text("Only players can use this command.", NamedTextColor.RED));
                        return Command.SINGLE_SUCCESS;
                    }
                    HomeManager.startTeleport(player);
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
