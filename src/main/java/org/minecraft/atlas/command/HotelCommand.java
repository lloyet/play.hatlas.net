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
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.minecraft.atlas.Atlas;
import org.minecraft.atlas.hotel.HotelShop;
import org.minecraft.atlas.hotel.HotelShopManager;
import org.minecraft.atlas.hotel.Rubis;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public class HotelCommand {

    private static Component error(String msg)   { return Component.text(msg, NamedTextColor.RED); }
    private static Component success(String msg) { return Component.text(msg, NamedTextColor.GREEN); }
    private static Component info(String msg)    { return Component.text(msg, NamedTextColor.GOLD); }

    private static final String PERM = "atlas.hotel.admin";

    public static LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("hotel")
                .requires(src -> src.getSender().hasPermission(PERM))

                // /hotel — help
                .executes(ctx -> {
                    var s = ctx.getSource().getSender();
                    s.sendMessage(Component.text("--- Hotel Commands ---", NamedTextColor.GOLD)
                            .append(Component.newline()).append(entry("shop create sell <mat> <qty> <price> [stock]", "Crée une boutique qui vend"))
                            .append(Component.newline()).append(entry("shop create buy  <mat> <qty> <price> [stock]", "Crée une boutique qui rachète"))
                            .append(Component.newline()).append(entry("shop delete",               "Supprime la boutique ciblée"))
                            .append(Component.newline()).append(entry("shop stock <amount>",        "Ajoute du stock à la boutique ciblée"))
                            .append(Component.newline()).append(entry("shop list",                  "Liste toutes les boutiques"))
                            .append(Component.newline()).append(entry("give <player> <amount>",     "Donne des Rubis à un joueur")));
                    return Command.SINGLE_SUCCESS;
                })

                // /hotel shop ...
                .then(Commands.literal("shop")

                        // /hotel shop create sell|buy <material> <amount> <price> [stock]
                        .then(Commands.literal("create")
                                .then(buildCreateBranch("sell", HotelShop.Type.SELL))
                                .then(buildCreateBranch("buy",  HotelShop.Type.BUY)))

                        // /hotel shop delete
                        .then(Commands.literal("delete")
                                .executes(ctx -> {
                                    Entity exec = ctx.getSource().getExecutor();
                                    if (!(exec instanceof Player player)) {
                                        ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    Block target = player.getTargetBlockExact(8);
                                    if (target == null) {
                                        player.sendMessage(error("Aucun bloc ciblé (max 8 blocs)."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    boolean deleted = HotelShopManager.deleteAt(target.getLocation());
                                    if (deleted) {
                                        save();
                                        player.sendMessage(success("Boutique supprimée."));
                                    } else {
                                        player.sendMessage(error("Aucune boutique à cet emplacement."));
                                    }
                                    return Command.SINGLE_SUCCESS;
                                }))

                        // /hotel shop stock <amount>
                        .then(Commands.literal("stock")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            Entity exec = ctx.getSource().getExecutor();
                                            if (!(exec instanceof Player player)) {
                                                ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            Block target = player.getTargetBlockExact(8);
                                            if (target == null) {
                                                player.sendMessage(error("Aucun bloc ciblé."));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            HotelShop shop = HotelShopManager.getAt(target.getLocation());
                                            if (shop == null) {
                                                player.sendMessage(error("Aucune boutique à cet emplacement."));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            if (shop.isUnlimited()) {
                                                player.sendMessage(error("Cette boutique a un stock illimité."));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                            shop.addStock(amount);
                                            save();
                                            player.sendMessage(success("+" + amount + " unités ajoutées. Stock total : " + shop.getStock()));
                                            return Command.SINGLE_SUCCESS;
                                        })))

                        // /hotel shop list
                        .then(Commands.literal("list")
                                .executes(ctx -> {
                                    var sender = ctx.getSource().getSender();
                                    var all = HotelShopManager.getAll();
                                    if (all.isEmpty()) {
                                        sender.sendMessage(info("Aucune boutique enregistrée."));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    sender.sendMessage(Component.text("=== Boutiques Hotel des Vents ===", NamedTextColor.GOLD));
                                    for (HotelShop s : all) {
                                        var loc = s.getLocation();
                                        String stockStr = s.isUnlimited() ? "∞" : String.valueOf(s.getStock());
                                        String typeLabel = s.getType() == HotelShop.Type.SELL ? "VENTE" : "RACHAT";
                                        sender.sendMessage(Component.text("  [" + s.getId() + "] ", NamedTextColor.GRAY)
                                                .append(Component.text(typeLabel, s.getType() == HotelShop.Type.SELL
                                                        ? NamedTextColor.GREEN : NamedTextColor.YELLOW))
                                                .append(Component.text(" " + s.getMaterial().name()
                                                        + " ×" + s.getAmountPerDeal()
                                                        + " — " + s.getPricePerDeal() + " ✦"
                                                        + " | stock:" + stockStr
                                                        + " @ " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ(),
                                                        NamedTextColor.GRAY)));
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })))

                // /hotel give <player> <amount>
                .then(Commands.literal("give")
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            var sender = ctx.getSource().getSender();
                                            List<Player> resolved = ctx.getArgument("player", PlayerSelectorArgumentResolver.class)
                                                    .resolve(ctx.getSource());
                                            if (resolved.isEmpty()) {
                                                sender.sendMessage(error("Joueur introuvable."));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            Player target = resolved.getFirst();
                                            int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                            Rubis.give(target, amount);
                                            sender.sendMessage(success(amount + " ✦ Rubis donnés à " + target.getName() + "."));
                                            target.sendMessage(info("Vous avez reçu " + amount + " ✦ Rubis."));
                                            return Command.SINGLE_SUCCESS;
                                        }))))

                .build();
    }

    // ── Branch builder for /hotel shop create sell|buy ────────────────────────

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildCreateBranch(
            String literal, HotelShop.Type type) {

        return Commands.literal(literal)
                .then(Commands.argument("material", StringArgumentType.word())
                        .then(Commands.argument("qty",   IntegerArgumentType.integer(1))
                                .then(Commands.argument("price", IntegerArgumentType.integer(1))
                                        // unlimited stock
                                        .executes(ctx -> createShop(ctx, type, -1))
                                        // limited stock
                                        .then(Commands.argument("stock", IntegerArgumentType.integer(1))
                                                .executes(ctx -> createShop(ctx, type,
                                                        IntegerArgumentType.getInteger(ctx, "stock")))))));
    }

    private static int createShop(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
                                   HotelShop.Type type, int stock) {
        Entity exec = ctx.getSource().getExecutor();
        if (!(exec instanceof Player player)) {
            ctx.getSource().getSender().sendMessage(error("Only players can run this command."));
            return Command.SINGLE_SUCCESS;
        }
        Block target = player.getTargetBlockExact(8);
        if (target == null) {
            player.sendMessage(error("Aucun bloc ciblé (max 8 blocs)."));
            return Command.SINGLE_SUCCESS;
        }
        if (HotelShopManager.getAt(target.getLocation()) != null) {
            player.sendMessage(error("Une boutique existe déjà à cet emplacement."));
            return Command.SINGLE_SUCCESS;
        }
        String matStr = StringArgumentType.getString(ctx, "material").toUpperCase();
        Material mat;
        try { mat = Material.valueOf(matStr); }
        catch (IllegalArgumentException e) {
            player.sendMessage(error("Matériau inconnu : " + matStr));
            return Command.SINGLE_SUCCESS;
        }
        if (!mat.isItem()) {
            player.sendMessage(error(matStr + " n'est pas un item valide."));
            return Command.SINGLE_SUCCESS;
        }
        int qty   = IntegerArgumentType.getInteger(ctx, "qty");
        int price = IntegerArgumentType.getInteger(ctx, "price");
        HotelShop shop = HotelShopManager.create(type, mat, qty, price, stock,
                null, "Serveur", target.getLocation());
        save();
        String typeLabel = type == HotelShop.Type.SELL ? "vente" : "rachat";
        String stockLabel = stock < 0 ? "illimité" : String.valueOf(stock);
        player.sendMessage(success("Boutique de " + typeLabel + " créée [" + shop.getId() + "] : "
                + qty + "× " + mat.name() + " → " + price + " ✦ Rubis (stock: " + stockLabel + ")"));
        return Command.SINGLE_SUCCESS;
    }

    private static void save() {
        HotelShopManager.save(Atlas.hotelDataConfig);
        Atlas.saveHotelDataConfig();
    }

    private static Component entry(String syntax, String desc) {
        return Component.text("  /hotel ", NamedTextColor.GRAY)
                .append(Component.text(syntax, NamedTextColor.GOLD))
                .append(Component.text(" — " + desc, NamedTextColor.YELLOW));
    }
}
