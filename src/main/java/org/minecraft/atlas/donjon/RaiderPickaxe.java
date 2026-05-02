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

import java.util.ArrayList;
import java.util.List;

public class RaiderPickaxe {

    private static NamespacedKey keyRaiderPickaxe;

    public static void init() {
        keyRaiderPickaxe = new NamespacedKey(Atlas.instance, "raider_pickaxe");
    }

    /** Creates the raider's pickaxe with a default of 10 block uses (for admin /give). */
    public static ItemStack create() {
        return create(10);
    }

    /**
     * Creates the raider's pickaxe that can break exactly {@code blockUses} blocks.
     * The durability is set so precisely that many blocks can be mined before the tool breaks.
     */
    public static ItemStack create(int blockUses) {
        int uses = Math.max(1, blockUses);
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("⚔ Raider's Pickaxe", NamedTextColor.DARK_RED)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Raider I", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("  Allows breaking blocks in enemy claims.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  ⚠ " + uses + " block" + (uses == 1 ? "" : "s") + " remaining.", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(keyRaiderPickaxe, PersistentDataType.BYTE, (byte) 1);

        int maxDurability = Material.DIAMOND_PICKAXE.getMaxDurability();
        if (meta instanceof Damageable damageable) {
            damageable.setDamage(Math.max(0, maxDurability - uses));
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
