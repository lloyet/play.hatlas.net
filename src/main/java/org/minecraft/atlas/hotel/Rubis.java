package org.minecraft.atlas.hotel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.minecraft.atlas.Atlas;

import java.util.List;
import java.util.Map;

/**
 * Custom Rubis currency item.
 *
 * Base material: EMERALD (the natural in-game currency analog).
 * Custom model data 1001 is reserved so a resource pack can overlay an
 * old-ruby / historical emerald texture without touching the default emerald.
 * Identified by a PDC byte key so it survives rename or lore edits.
 */
public class Rubis {

    private static NamespacedKey key;

    public static void init() {
        key = new NamespacedKey(Atlas.instance, "rubis");
    }

    public static NamespacedKey getKey() { return key; }

    // ── Factory ────────────────────────────────────────────────────────────────

    /** Creates a single stack of at most 64 Rubis. */
    public static ItemStack create(int amount) {
        ItemStack item = new ItemStack(Material.EMERALD, Math.clamp(amount, 1, 64));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("✦ Rubis", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD,   false));
        meta.lore(List.of(
                Component.text("  Monnaie officielle.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.setEnchantmentGlintOverride(true);
        meta.setCustomModelData(1001);
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    // ── Detection ──────────────────────────────────────────────────────────────

    public static boolean isRubis(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    // ── Inventory helpers ──────────────────────────────────────────────────────

    /** Counts total Rubis across all inventory slots. */
    public static int count(Player player) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (isRubis(stack)) total += stack.getAmount();
        }
        return total;
    }

    /**
     * Removes {@code amount} Rubis from the player's inventory.
     * Returns false (no change) if the player does not have enough.
     */
    public static boolean take(Player player, int amount) {
        if (count(player) < amount) return false;
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            if (!isRubis(contents[i])) continue;
            int stackAmt = contents[i].getAmount();
            if (stackAmt <= remaining) {
                remaining -= stackAmt;
                contents[i] = null;
            } else {
                contents[i].setAmount(stackAmt - remaining);
                remaining = 0;
            }
        }
        player.getInventory().setContents(contents);
        player.updateInventory();
        return true;
    }

    /** Adds {@code amount} Rubis to the player's inventory; drops overflow at their feet. */
    public static void give(Player player, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            int batch = Math.min(64, remaining);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(create(batch));
            for (ItemStack overflow : leftover.values())
                player.getWorld().dropItemNaturally(player.getLocation(), overflow);
            remaining -= batch;
        }
        player.updateInventory();
    }
}
