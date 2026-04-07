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
import org.minecraft.atlas.job.JobSettings;
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
                // /job — show help menu
                .executes(ctx -> {
                    ctx.getSource().getSender().sendMessage(buildHelp());
                    return Command.SINGLE_SUCCESS;
                })
                // /job select — open job selection GUI
                .then(Commands.literal("select")
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
                            if (!JobManager.canAddJob(player.getUniqueId())) {
                                player.sendMessage(error("Master your current job(s) first to unlock an additional job slot."));
                                return Command.SINGLE_SUCCESS;
                            }
                            JobGui.open(player);
                            return Command.SINGLE_SUCCESS;
                        }))
                // /job remove all [player]
                // /job remove <job> [player]
                .then(Commands.literal("remove")
                        .requires(src -> src.getSender().hasPermission("atlas.job.admin"))
                        .then(Commands.literal("all")
                                .executes(ctx -> {
                                    Entity executor = ctx.getSource().getExecutor();
                                    if (!(executor instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    doRemoveAll(player, player);
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
                                            doRemoveAll(player, target);
                                            return Command.SINGLE_SUCCESS;
                                        })))
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
                                    doRemoveJob(player, player, job);
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
                                            doRemoveJob(player, target, job);
                                            return Command.SINGLE_SUCCESS;
                                        }))))
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
                // /job info [job]
                .then(Commands.literal("info")
                        .executes(ctx -> {
                            Entity executor = ctx.getSource().getExecutor();
                            if (!(executor instanceof Player player)) {
                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                return Command.SINGLE_SUCCESS;
                            }
                            showInfo(player, null);
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("job", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    Entity executor = ctx.getSource().getExecutor();
                                    if (executor instanceof Player player) {
                                        for (Job j : JobManager.getAllJobData(player.getUniqueId()).keySet()) {
                                            builder.suggest(j.name().toLowerCase());
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
                                    Job job = parseJob(player, StringArgumentType.getString(ctx, "job"));
                                    if (job == null) return Command.SINGLE_SUCCESS;
                                    showInfo(player, job);
                                    return Command.SINGLE_SUCCESS;
                                })))
                // /job level [add|set|remove <player> <job> <amount> level|xp]
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
                // /job settings <setting> <value>
                .then(Commands.literal("settings")
                        .then(Commands.literal("extra_levels")
                                .then(Commands.literal("true")
                                        .executes(ctx -> {
                                            Entity executor = ctx.getSource().getExecutor();
                                            if (!(executor instanceof Player player)) {
                                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            JobManager.getSettings(player.getUniqueId()).setExtraLevels(true);
                                            player.sendMessage(success("Extra levels enabled. You will now see XP and level progress beyond mastery."));
                                            return Command.SINGLE_SUCCESS;
                                        }))
                                .then(Commands.literal("false")
                                        .executes(ctx -> {
                                            Entity executor = ctx.getSource().getExecutor();
                                            if (!(executor instanceof Player player)) {
                                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            JobManager.getSettings(player.getUniqueId()).setExtraLevels(false);
                                            player.sendMessage(success("Extra levels disabled. Mastered jobs will display as MAX level."));
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                .build();
    }

    /**
     * Builds the add/set/remove branch under /job level.
     * Structure: /job level <operation> <player> <job> <amount> (level|xp)
     */
    private static LiteralArgumentBuilder<CommandSourceStack> levelAdminBranch(String operation) {
        return Commands.literal(operation)
                .requires(src -> src.getSender().hasPermission("atlas.job.admin"))
                .then(Commands.argument("player", ArgumentTypes.player())
                        .then(Commands.argument("job", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    try {
                                        PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
                                        Player target = resolver.resolve(ctx.getSource()).getFirst();
                                        for (Job j : JobManager.getAllJobData(target.getUniqueId()).keySet()) {
                                            builder.suggest(j.name().toLowerCase());
                                        }
                                    } catch (Exception ignored) {
                                        for (Job j : Job.values()) builder.suggest(j.name().toLowerCase());
                                    }
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .then(Commands.literal("level")
                                                .executes(ctx -> {
                                                    Entity executor = ctx.getSource().getExecutor();
                                                    if (!(executor instanceof Player player)) {
                                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                                        return Command.SINGLE_SUCCESS;
                                                    }
                                                    PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
                                                    Player target = resolver.resolve(ctx.getSource()).getFirst();
                                                    Job job = parseJob(player, StringArgumentType.getString(ctx, "job"));
                                                    if (job == null) return Command.SINGLE_SUCCESS;
                                                    doLevelOp(player, target, job, operation, "level", IntegerArgumentType.getInteger(ctx, "amount"));
                                                    return Command.SINGLE_SUCCESS;
                                                }))
                                        .then(Commands.literal("xp")
                                                .executes(ctx -> {
                                                    Entity executor = ctx.getSource().getExecutor();
                                                    if (!(executor instanceof Player player)) {
                                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                                        return Command.SINGLE_SUCCESS;
                                                    }
                                                    PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
                                                    Player target = resolver.resolve(ctx.getSource()).getFirst();
                                                    Job job = parseJob(player, StringArgumentType.getString(ctx, "job"));
                                                    if (job == null) return Command.SINGLE_SUCCESS;
                                                    doLevelOp(player, target, job, operation, "xp", IntegerArgumentType.getInteger(ctx, "amount"));
                                                    return Command.SINGLE_SUCCESS;
                                                })))));
    }

    // -------------------------------------------------------------------------
    // Help
    // -------------------------------------------------------------------------

    private static Component buildHelp() {
        return Component.text("--- Job Commands ---", NamedTextColor.GOLD)
                .append(entry("/job select", "Open the job selection menu (requires faction; mastery unlocks additional slots)"))
                .append(entry("/job list", "List all available jobs"))
                .append(entry("/job info [job]", "View your job(s), level, XP, and source ACTIVE/LOCKED/EXPIRED status"))
                .append(entry("/job level", "Quick view of your current level(s) and XP bar(s)"))
                .append(entry("/job settings extra_levels <true|false>", "Toggle XP/level display and notifications beyond mastery (level 100)"))
                .append(entry("/job remove all [player]", "Remove all jobs from a player (admin only)"))
                .append(entry("/job remove <job> [player]", "Remove a specific job from a player (admin only)"))
                .append(entry("/job set <job> [player]", "Set a player's job directly (admin only)"))
                .append(entry("/job level add <player> <job> <amount> level|xp", "Add levels or XP to a player's job (admin only)"))
                .append(entry("/job level set <player> <job> <amount> level|xp", "Set a player's level or XP (admin only)"))
                .append(entry("/job level remove <player> <job> <amount> level|xp", "Remove levels or XP from a player (admin only)"));
    }

    private static Component entry(String command, String description) {
        return Component.newline()
                .append(Component.text(command, NamedTextColor.GOLD))
                .append(Component.text(" — " + description, NamedTextColor.YELLOW));
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

    private static void doRemoveAll(Player executor, Player target) {
        if (JobManager.removeJob(target.getUniqueId())) {
            executor.sendMessage(success("Removed all of " + target.getName() + "'s jobs."));
            if (!executor.equals(target)) {
                target.sendMessage(info("An admin removed all your jobs. Use /job select to choose a new one."));
            }
        } else {
            executor.sendMessage(error(target.getName() + " does not have any jobs."));
        }
    }

    private static void doRemoveJob(Player executor, Player target, Job job) {
        if (JobManager.removeJob(target.getUniqueId(), job)) {
            executor.sendMessage(success("Removed " + target.getName() + "'s " + job.getDisplayName() + " job."));
            if (!executor.equals(target)) {
                target.sendMessage(info("An admin removed your " + job.getDisplayName() + " job."));
            }
        } else {
            executor.sendMessage(error(target.getName() + " does not have the " + job.getDisplayName() + " job."));
        }
    }

    private static void doAdminSet(Player executor, Player target, Job job) {
        JobManager.forceSetJob(target.getUniqueId(), job);
        executor.sendMessage(success("Set " + target.getName() + "'s " + job.getDisplayName() + " job."));
        if (!executor.equals(target)) {
            target.sendMessage(info("An admin set your job to " + job.getDisplayName() + "."));
        }
    }

    private static void showLevel(Player player) {
        Map<Job, PlayerJobData> allJobs = JobManager.getAllJobData(player.getUniqueId());
        if (allJobs.isEmpty()) {
            player.sendMessage(error("You don't have a job yet. Use /job select to choose one."));
            return;
        }

        JobSettings settings = JobManager.getSettings(player.getUniqueId());
        boolean extraLevels = settings.isExtraLevels();
        int maxLevel = JobRegistry.getMaxLevel();
        int barLength = 20;

        Component msg = null;
        for (PlayerJobData data : allJobs.values()) {
            boolean mastered = data.isMastered();
            int displayLevel = (!extraLevels && mastered) ? maxLevel : data.getLevel();
            int filled;
            Component xpText;

            if (!extraLevels && mastered) {
                filled = barLength;
                xpText = Component.text(" MAX level", NamedTextColor.GOLD);
            } else {
                int xp = (int) data.getXp();
                int required = data.getXpRequired();
                filled = Math.min(barLength, (int) (data.getXp() / required * barLength));
                xpText = Component.text(" " + fmt(xp) + "/" + fmt(required) + " XP", NamedTextColor.GRAY);
            }

            Component bar = Component.text("[", NamedTextColor.DARK_GRAY)
                    .append(Component.text("█".repeat(filled), NamedTextColor.GREEN))
                    .append(Component.text("█".repeat(barLength - filled), NamedTextColor.DARK_GRAY))
                    .append(Component.text("]", NamedTextColor.DARK_GRAY));

            String levelLabel = "Level " + displayLevel + (mastered ? " ✦" : "");
            Component line = Component.text("[", NamedTextColor.DARK_GRAY)
                    .append(Component.text(data.getJob().getDisplayName(), data.getJob().getColor()))
                    .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(levelLabel, NamedTextColor.YELLOW))
                    .append(Component.newline())
                    .append(Component.text("Progress: ", NamedTextColor.GRAY))
                    .append(bar)
                    .append(xpText);

            msg = (msg == null) ? line : msg.append(Component.newline()).append(line);
        }

        player.sendMessage(msg);
    }

    /**
     * Shows job info. If {@code filter} is non-null, shows only that job;
     * otherwise shows all of the player's jobs.
     */
    private static void showInfo(Player player, Job filter) {
        Map<Job, PlayerJobData> allJobs = JobManager.getAllJobData(player.getUniqueId());
        if (allJobs.isEmpty()) {
            player.sendMessage(error("You don't have a job yet. Use /job select to choose one."));
            return;
        }

        if (filter != null && !allJobs.containsKey(filter)) {
            player.sendMessage(error("You don't have the " + filter.getDisplayName() + " job."));
            return;
        }

        JobSettings settings = JobManager.getSettings(player.getUniqueId());
        boolean extraLevels = settings.isExtraLevels();
        int maxLevel = JobRegistry.getMaxLevel();

        Component msg = null;
        for (PlayerJobData data : allJobs.values()) {
            if (filter != null && data.getJob() != filter) continue;
            Component section = buildInfoSection(data, extraLevels, maxLevel);
            msg = (msg == null) ? section : msg.append(Component.newline()).append(Component.newline()).append(section);
        }

        player.sendMessage(msg);
    }

    private static Component buildInfoSection(PlayerJobData data, boolean extraLevels, int maxLevel) {
        int level = data.getLevel();
        boolean mastered = data.isMastered();
        int displayLevel = (!extraLevels && mastered) ? maxLevel : level;

        Component section = Component.text("[Job: " + data.getJob().getDisplayName()
                + (mastered ? " ✦" : "") + "]", data.getJob().getColor())
                .append(Component.newline())
                .append(Component.text("Level: " + displayLevel, NamedTextColor.YELLOW));

        if (!extraLevels && mastered) {
            section = section.append(Component.text(" (MAX level)", NamedTextColor.GOLD));
        } else {
            int xp = (int) data.getXp();
            int required = data.getXpRequired();
            section = section.append(Component.text("  (XP: " + fmt(xp) + " / " + fmt(required) + ")", NamedTextColor.GRAY));
        }

        for (Map.Entry<String, Map<String, JobSource>> catEntry : JobRegistry.getCategories(data.getJob()).entrySet()) {
            String categoryTitle = catEntry.getKey().substring(0, 1).toUpperCase() + catEntry.getKey().substring(1);
            section = section.append(Component.newline())
                    .append(Component.newline())
                    .append(Component.text(categoryTitle + ":", NamedTextColor.GOLD));

            for (JobSource source : catEntry.getValue().values()) {
                section = section.append(Component.newline()).append(sourceEntry(source, level, mastered));
            }
        }

        return section;
    }

    private static Component sourceEntry(JobSource source, int level, boolean mastered) {
        if (source.isLocked(level)) {
            return Component.text("  • " + source.getDisplayName(), NamedTextColor.RED)
                    .append(Component.text(" [LOCKED – unlocks at lvl " + source.getUnlockLevel() + "]", NamedTextColor.DARK_RED));
        }
        // Mastered players bypass cutoff, so expired sources show as active
        if (!mastered && source.isExpired(level)) {
            return Component.text("  • " + source.getDisplayName() + " → " + fmtXp(source.getXp()) + " XP", NamedTextColor.DARK_GRAY)
                    .append(Component.text(" [EXPIRED]", NamedTextColor.DARK_GRAY));
        }
        return Component.text("  • " + source.getDisplayName() + " → " + fmtXp(source.getXp()) + " XP", NamedTextColor.GREEN)
                .append(Component.text(" [ACTIVE]", NamedTextColor.GREEN));
    }

    private static String fmtXp(double xp) {
        return xp == (int) xp ? String.valueOf((int) xp) : String.valueOf(xp);
    }

    private static String fmt(int n) {
        return String.format("%,d", n);
    }

    private static void doLevelOp(Player executor, Player target, Job job, String operation, String type, int amount) {
        boolean ok;
        String desc;
        String jobName = job.getDisplayName();

        switch (operation) {
            case "add" -> {
                if (type.equals("xp")) {
                    PlayerJobData data = JobManager.getJobData(target.getUniqueId(), job);
                    if (data == null) {
                        executor.sendMessage(error(target.getName() + " does not have the " + jobName + " job."));
                        return;
                    }
                    int maxAddable = (int) (data.getXpRequired() - data.getXp());
                    if (amount > maxAddable) {
                        executor.sendMessage(error("Cannot add " + amount + " XP — " + target.getName()
                                + " can receive at most " + maxAddable + " XP before leveling up."));
                        return;
                    }
                    ok = JobManager.adminAddXp(target.getUniqueId(), job, amount, target);
                    desc = "Added " + amount + " XP to " + target.getName() + "'s " + jobName;
                } else {
                    ok = JobManager.adminAddLevel(target.getUniqueId(), job, amount);
                    desc = "Added " + amount + " level(s) to " + target.getName() + "'s " + jobName;
                }
            }
            case "set" -> {
                if (type.equals("xp")) {
                    ok = JobManager.adminSetXp(target.getUniqueId(), job, amount);
                    desc = "Set " + target.getName() + "'s " + jobName + " XP to " + amount;
                } else {
                    ok = JobManager.adminSetLevel(target.getUniqueId(), job, amount);
                    desc = "Set " + target.getName() + "'s " + jobName + " level to " + amount;
                }
            }
            case "remove" -> {
                if (type.equals("xp")) {
                    ok = JobManager.adminRemoveXp(target.getUniqueId(), job, amount);
                    desc = "Removed " + amount + " XP from " + target.getName() + "'s " + jobName;
                } else {
                    ok = JobManager.adminRemoveLevel(target.getUniqueId(), job, amount);
                    desc = "Removed " + amount + " level(s) from " + target.getName() + "'s " + jobName;
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
            executor.sendMessage(error(target.getName() + " does not have the " + jobName + " job."));
        }
    }
}
