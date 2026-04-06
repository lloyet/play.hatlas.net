package org.minecraft.atlas.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.minecraft.atlas.group.GroupManager;
import org.minecraft.atlas.job.JobGui;
import org.minecraft.atlas.job.JobManager;
import org.minecraft.atlas.job.PlayerJobData;

public class JobCommand {

    public static LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("job")
                .requires(src -> src.getSender().hasPermission("atlas.job"))
                .executes(ctx -> {
                    Entity executor = ctx.getSource().getExecutor();
                    if (!(executor instanceof Player player)) {
                        ctx.getSource().getSender().sendMessage(Component.text("Only players can run this command.", NamedTextColor.RED));
                        return Command.SINGLE_SUCCESS;
                    }

                    if (GroupManager.getPlayerGroup(player.getUniqueId()) == null) {
                        player.sendMessage(Component.text("You must join or create a group before choosing a job.", NamedTextColor.RED));
                        return Command.SINGLE_SUCCESS;
                    }

                    PlayerJobData data = JobManager.getJobData(player.getUniqueId());
                    if (data != null) {
                        player.sendMessage(
                            Component.text("Your job: ", NamedTextColor.GOLD)
                                .append(Component.text(data.getJob().getDisplayName(), data.getJob().getColor()))
                                .append(Component.text("  (Level " + data.getLevel() + ")", NamedTextColor.YELLOW))
                        );
                    } else {
                        JobGui.open(player);
                    }

                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
