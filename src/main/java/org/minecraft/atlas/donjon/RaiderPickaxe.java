package org.minecraft.atlas.donjon;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.minecraft.atlas.Atlas;

import java.util.List;

public class RaiderPickaxe {

    private static NamespacedKey keyRaiderPickaxe;

    public static void init() {
        keyRaiderPickaxe = new NamespacedKey(Atlas.instance, "raider_pickaxe");
    }

    /**
     * Creates the raider's pickaxe with ~5% of wooden pickaxe durability remaining.
     */
    public static ItemStack create() {
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("⚔ Raider's Pickaxe", NamedTextColor.DARK_RED)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Allows breaking blocks in enemy claims.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Very fragile — use wisely!", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(keyRaiderPickaxe, PersistentDataType.BYTE, (byte) 1);

        // 5% of wooden pickaxe durability (59 × 0.05 ≈ 3 uses remaining)
        int remainingUses = Math.max(1, (int) Math.ceil(Material.WOODEN_PICKAXE.getMaxDurability() * 0.05));
        if (meta instanceof Damageable damageable) {
            damageable.setDamage(Material.DIAMOND_PICKAXE.getMaxDurability() - remainingUses);
        }

        item.setItemMeta(meta);
        return item;
    }

    public static boolean isRaiderPickaxe(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(keyRaiderPickaxe, PersistentDataType.BYTE);
    }
}
