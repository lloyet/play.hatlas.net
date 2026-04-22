package org.minecraft.atlas.donjon;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class DonjonKey {

    public static ItemStack create(int amount) {
        ItemStack item = new ItemStack(Material.TRIAL_KEY, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("✦ Donjon Key", NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        meta.lore(List.of(
            Component.empty(),
            Component.text("  Use to activate a dormant dungeon.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
            Component.empty(),
            Component.text("  Obtained from Jokeyrini.", NamedTextColor.DARK_PURPLE)
                    .decoration(TextDecoration.ITALIC, false)
        ));
        meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }
}
