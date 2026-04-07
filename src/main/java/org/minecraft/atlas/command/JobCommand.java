package org.minecraft.atlas.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.minecraft.atlas.faction.FactionManager;
import org.minecraft.atlas.job.Job;
import org.minecraft.atlas.job.JobGui;
import org.minecraft.atlas.job.JobManager;
import org.minecraft.atlas.job.JobRegistry;
import org.minecraft.atlas.job.JobSource;
import org.minecraft.atlas.job.PlayerJobData;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public class JobCommand {

    private static Component error(String msg) { return Component.text(msg, NamedTextColor.RED); }
    private static Component success(String msg) { return Component.text(msg, NamedTextColor.GREEN); }
    private static Component info(String msg) { return Component.text(msg, NamedTextColor.GOLD); }

    // -------------------------------------------------------------------------
    // Command tree
    // -------------------------------------------------------------------------

    public static LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("job")
                .requires(src -> src.getSender().hasPermission("atlas.job"))
                // /job — open selection GUI
                .executes(ctx -> {
                    Entity executor = ctx.getSource().getExecutor();
                    if (!(executor instanceof Player player)) {
                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                        return Command.SINGLE_SUCCESS;
                    }
                    if (FactionManager.getPlayerFaction(player.getUniqueId()) == null) {
                        player.sendMessage(error("You must join or create a faction before choosing a job."));
                        return Command.SINGLE_SUCCESS;
                    }
                    if (JobManager.hasJob(player.getUniqueId())) {
                        player.sendMessage(error("You already have a job. Use /job level to view your progress."));
                        return Command.SINGLE_SUCCESS;
                    }
                    JobGui.open(player);
                    return Command.SINGLE_SUCCESS;
                })
                // /job remove [player]
                .then(Commands.literal("remove")
                        .requires(src -> src.getSender().hasPermission("atlas.job.admin"))
                        .executes(ctx -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player player)) {
                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                return Command.SINGLE_SUCCESS;
                            }
                            doRemove(player, player);
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .executes(ctx -> {
                                    Entity executor = ctx.getSource().getExecutor();
                                    if (!(executor instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
                                    Player target = resolver.resolve(ctx.getSource()).getFirst();
                                    doRemove(player, target);
                                    return Command.SINGLE_SUCCESS;
                                })))
                // /job set <job> [player]
                .then(Commands.literal("set")
                        .requires(src -> src.getSender().hasPermission("atlas.job.admin"))
                        .then(Commands.argument("job", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (Job j : Job.values()) builder.suggest(j.name().toLowerCase());
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    Entity executor = ctx.getSource().getExecutor();
                                    if (!(executor instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    Job job = parseJob(player, StringArgumentType.getString(ctx, "job"));
                                    if (job == null) return Command.SINGLE_SUCCESS;
                                    doAdminSet(player, player, job);
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Commands.argument("player", ArgumentTypes.player())
                                        .executes(ctx -> {
                                            Entity executor = ctx.getSource().getExecutor();
                                            if (!(executor instanceof Player player)) {
                                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            Job job = parseJob(player, StringArgumentType.getString(ctx, "job"));
                                            if (job == null) return Command.SINGLE_SUCCESS;
                                            PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
                                            Player target = resolver.resolve(ctx.getSource()).getFirst();
                                            doAdminSet(player, target, job);
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                // /job list
                .then(Commands.literal("list")
                        .executes(ctx -> {
                            Component msg = Component.text("--- Available Jobs ---", NamedTextColor.GOLD);
                            for (Job j : Job.values()) {
                                msg = msg.append(Component.newline())
                                        .append(Component.text("• ", NamedTextColor.GRAY))
                                        .append(Component.text(j.getDisplayName(), j.getColor()));
                            }
                            ctx.getSource().getSender().sendMessage(msg);
                            return Command.SINGLE_SUCCESS;
                        }))
                // /job info
                .then(Commands.literal("info")
                        .executes(ctx -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player player)) {
                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                return Command.SINGLE_SUCCESS;
                            }
                            showInfo(player);
                            return Command.SINGLE_SUCCESS;
                        }))
                // /job level [add|set|remove <player> <amount> xp|level]
                .then(Commands.literal("level")
                        .executes(ctx -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player player)) {
                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                return Command.SINGLE_SUCCESS;
                            }
                            showLevel(player);
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(levelAdminBranch("add"))
                        .then(levelAdminBranch("set"))
                        .then(levelAdminBranch("remove")))
                .build();
    }

    /**
     * Builds the add/set/remove branch under /job level.
     * Structure: /job level <operation> <player> <amount> (xp|level)
     */
    private static LiteralArgumentBuilder<CommandSourceStack> levelAdminBranch(String operation) {
        return Commands.literal(operation)
                .requires(src -> src.getSender().hasPermission("atlas.job.admin"))
                .then(Commands.argument("player", ArgumentTypes.player())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .then(Commands.literal("xp")
                                        .executes(ctx -> {
                                            Entity executor = ctx.getSource().getExecutor();
                                            if (!(executor instanceof Player player)) {
                                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
                                            Player target = resolver.resolve(ctx.getSource()).getFirst();
                                            doLevelOp(player, target, operation, "xp", IntegerArgumentType.getInteger(ctx, "amount"));
                                            return Command.SINGLE_SUCCESS;
                                        }))
                                .then(Commands.literal("level")
                                        .executes(ctx -> {
                                            Entity executor = ctx.getSource().getExecutor();
                                            if (!(executor instanceof Player player)) {
                                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
                                            Player target = resolver.resolve(ctx.getSource()).getFirst();
                                            doLevelOp(player, target, operation, "level", IntegerArgumentType.getInteger(ctx, "amount"));
                                            return Command.SINGLE_SUCCESS;
                                        }))));
    }

    // -------------------------------------------------------------------------
    // Action handlers
    // -------------------------------------------------------------------------

    private static Job parseJob(Player executor, String input) {
        try {
            return Job.valueOf(input.toUpperCase());
        } catch (IllegalArgumentException e) {
            String valid = Arrays.stream(Job.values())
                    .map(j -> j.name().toLowerCase())
                    .collect(Collectors.joining(", "));
            executor.sendMessage(error("Unknown job '" + input + "'. Valid jobs: " + valid + "."));
            return null;
        }
    }

    private static void doRemove(Player executor, Player target) {
        if (JobManager.removeJob(target.getUniqueId())) {
            executor.sendMessage(success("Removed " + target.getName() + "'s job."));
            if (!executor.equals(target)) {
                target.sendMessage(info("An admin removed your job. Use /job to select a new one."));
            }
        } else {
            executor.sendMessage(error(target.getName() + " does not have a job."));
        }
    }

    private static void doAdminSet(Player executor, Player target, Job job) {
        JobManager.forceSetJob(target.getUniqueId(), job);
        executor.sendMessage(success("Set " + target.getName() + "'s job to " + job.getDisplayName() + "."));
        if (!executor.equals(target)) {
            target.sendMessage(info("An admin set your job to " + job.getDisplayName() + "."));
        }
    }

    private static void showLevel(Player player) {
        PlayerJobData data = JobManager.getJobData(player.getUniqueId());
        if (data == null) {
            player.sendMessage(error("You don't have a job yet. Use /job to select one."));
            return;
        }

        boolean maxed = data.getLevel() >= JobRegistry.getMaxLevel();
        int xp = (int) data.getXp();
        int required = data.getXpRequired();
        int barLength = 20;
        int filled = maxed ? barLength : Math.min(barLength, (int) (data.getXp() / required * barLength));

        Component bar = Component.text("[", NamedTextColor.DARK_GRAY)
                .append(Component.text("█".repeat(filled), NamedTextColor.GREEN))
                .append(Component.text("█".repeat(barLength - filled), NamedTextColor.DARK_GRAY))
                .append(Component.text("]", NamedTextColor.DARK_GRAY));

        Component xpText = maxed
                ? Component.text(" MAX", NamedTextColor.GOLD)
                : Component.text(" " + xp + "/" + required + " XP", NamedTextColor.GRAY);

        player.sendMessage(
                Component.text("[", NamedTextColor.DARK_GRAY)
                        .append(Component.text(data.getJob().getDisplayName(), data.getJob().getColor()))
                        .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                        .append(Component.text("Level " + data.getLevel(), NamedTextColor.YELLOW))
                        .append(Component.newline())
                        .append(Component.text("Progress: ", NamedTextColor.GRAY))
                        .append(bar)
                        .append(xpText)
        );
    }

    private static void showInfo(Player player) {
        PlayerJobData data = JobManager.getJobData(player.getUniqueId());
        if (data == null) {
            player.sendMessage(error("You don't have a job yet. Use /job to select one."));
            return;
        }

        int level = data.getLevel();
        boolean maxed = level >= JobRegistry.getMaxLevel();
        int xp = (int) data.getXp();
        int required = data.getXpRequired();

        Component msg = Component.text("[Job: " + data.getJob().getDisplayName() + "]", data.getJob().getColor())
                .append(Component.newline())
                .append(Component.text("Level: " + level, NamedTextColor.YELLOW))
                .append(maxed
                        ? Component.text(" (MAX)", NamedTextColor.GOLD)
                        : Component.text("  (XP: " + xp + " / " + required + ")", NamedTextColor.GRAY));

        for (Map.Entry<String, Map<String, JobSource>> catEntry : JobRegistry.getCategories(data.getJob()).entrySet()) {
            String categoryTitle = catEntry.getKey().substring(0, 1).toUpperCase() + catEntry.getKey().substring(1);
            msg = msg.append(Component.newline())
                    .append(Component.newline())
                    .append(Component.text(categoryTitle + ":", NamedTextColor.GOLD));

            for (JobSource source : catEntry.getValue().values()) {
                msg = msg.append(Component.newline()).append(sourceEntry(source, level));
            }
        }

        player.sendMessage(msg);
    }

    private static Component sourceEntry(JobSource source, int level) {
        if (source.isLocked(level)) {
            return Component.text("  • " + source.getDisplayName(), NamedTextColor.RED)
                    .append(Component.text(" [LOCKED – unlocks at lvl " + source.getUnlockLevel() + "]", NamedTextColor.DARK_RED));
        }
        if (source.isExpired(level)) {
            return Component.text("  • " + source.getDisplayName() + " → " + fmtXp(source.getXp()) + " XP", NamedTextColor.DARK_GRAY)
                    .append(Component.text(" [EXPIRED]", NamedTextColor.DARK_GRAY));
        }
        // ACTIVE
        return Component.text("  • " + source.getDisplayName() + " → " + fmtXp(source.getXp()) + " XP", NamedTextColor.GREEN)
                .append(Component.text(" [ACTIVE]", NamedTextColor.GREEN));
    }

    private static String fmtXp(double xp) {
        return xp == (int) xp ? String.valueOf((int) xp) : String.valueOf(xp);
    }

    private static void doLevelOp(Player executor, Player target, String operation, String type, int amount) {
        boolean ok;
        String desc;

        switch (operation) {
            case "add" -> {
                if (type.equals("xp")) {
                    PlayerJobData data = JobManager.getJobData(target.getUniqueId());
                    if (data == null) {
                        executor.sendMessage(error(target.getName() + " does not have a job."));
                        return;
                    }
                    int maxAddable = (int) (data.getXpRequired() - data.getXp());
                    if (amount > maxAddable) {
                        executor.sendMessage(error("Cannot add " + amount + " XP — " + target.getName()
                                + " can receive at most " + maxAddable + " XP before leveling up."));
                        return;
                    }
                    ok = JobManager.adminAddXp(target.getUniqueId(), amount, target);
                    desc = "Added " + amount + " XP to " + target.getName();
                } else {
                    ok = JobManager.adminAddLevel(target.getUniqueId(), amount);
                    desc = "Added " + amount + " level(s) to " + target.getName();
                }
            }
            case "set" -> {
                if (type.equals("xp")) {
                    ok = JobManager.adminSetXp(target.getUniqueId(), amount);
                    desc = "Set " + target.getName() + "'s XP to " + amount;
                } else {
                    ok = JobManager.adminSetLevel(target.getUniqueId(), amount);
                    desc = "Set " + target.getName() + "'s level to " + amount;
                }
            }
            case "remove" -> {
                if (type.equals("xp")) {
                    ok = JobManager.adminRemoveXp(target.getUniqueId(), amount);
                    desc = "Removed " + amount + " XP from " + target.getName();
                } else {
                    ok = JobManager.adminRemoveLevel(target.getUniqueId(), amount);
                    desc = "Removed " + amount + " level(s) from " + target.getName();
                }
            }
            default -> {
                executor.sendMessage(error("Unknown operation."));
                return;
            }
        }

        if (ok) {
            executor.sendMessage(success(desc + "."));
        } else {
            executor.sendMessage(error(target.getName() + " does not have a job."));
        }
    }
}
