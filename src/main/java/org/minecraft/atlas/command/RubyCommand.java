package org.minecraft.atlas.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.minecraft.atlas.customItem.RubyCustomItems;

import java.util.List;

public final class RubyCommand {

    private static final List<String> ITEMS = List.of("ruby", "ruby_block", "ruby_ore", "deepslate_ruby_ore");

    private RubyCommand() {
    }

    private static Component error(String msg) {
        return Component.text(msg, NamedTextColor.RED);
    }

    private static Component success(String msg) {
        return Component.text(msg, NamedTextColor.GREEN);
    }

    public static LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("ruby")
                .requires(src -> src.getSender().hasPermission("atlas.ruby.admin"))
                .executes(ctx -> {
                    ctx.getSource().getSender().sendMessage(
                            Component.text("Usage: /ruby give <player> <"
                                    + String.join("|", ITEMS) + "> [amount]",
                                    NamedTextColor.GOLD));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("give")
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .then(Commands.argument("item", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            ITEMS.forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> runGive(ctx, 1))
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 6400))
                                                .executes(ctx -> runGive(ctx,
                                                        IntegerArgumentType.getInteger(ctx, "amount"))))))
                )
                .build();
    }

    private static int runGive(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, int amount)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        PlayerSelectorArgumentResolver resolver =
                ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
        Player target = resolver.resolve(ctx.getSource()).getFirst();
        String item = StringArgumentType.getString(ctx, "item").toLowerCase();

        if (!ITEMS.contains(item)) {
            sender.sendMessage(error("Unknown ruby item '" + item + "'. Allowed: "
                    + String.join(", ", ITEMS) + "."));
            return Command.SINGLE_SUCCESS;
        }

        giveRuby(sender, target, item, amount);
        return Command.SINGLE_SUCCESS;
    }

    private static void giveRuby(CommandSender sender, Player target, String item, int amount) {
        ItemStack template = RubyCustomItems.get(item);
        int maxStack = template.getMaxStackSize();
        int remaining = amount;
        while (remaining > 0) {
            int n = Math.min(remaining, maxStack);
            ItemStack stack = template.clone();
            stack.setAmount(n);
            var overflow = target.getInventory().addItem(stack);
            for (ItemStack drop : overflow.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), drop);
            }
            remaining -= n;
        }
        sender.sendMessage(success("Gave " + amount + "× " + item + " to " + target.getName() + "."));
        target.sendMessage(success("You received " + amount + "× " + item + "."));
    }
}
