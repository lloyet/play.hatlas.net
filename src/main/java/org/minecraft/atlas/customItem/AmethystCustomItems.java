package org.minecraft.atlas.customItem;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.minecraft.atlas.Atlas;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Custom amethyst gear: tools, weapons, armor, horse barding. Migrated 1:1 from
 * the legacy ruby gear set — ruby_sword → amethyst_sword, etc. Crafted from
 * vanilla amethyst shards. Item models live under the hatlas resource pack
 * (hatlas:item/amethyst_*); armor pieces reference equipment asset hatlas:amethyst.
 */
@SuppressWarnings("UnstableApiUsage")
public final class AmethystCustomItems {

    public static final String NAMESPACE = "hatlas";
    public static final String EQUIPMENT_ASSET = "amethyst";

    private static final Map<String, ItemStack> REGISTRY = new LinkedHashMap<>();

    private AmethystCustomItems() {
    }

    // ── Entry points ───────────────────────────────────────────────────────────

    public static void init() {
        REGISTRY.clear();
        register("amethyst_sword", buildSword());
        register("amethyst_pickaxe", buildTool(Material.NETHERITE_PICKAXE, "amethyst_pickaxe", "Amethyst Pickaxe"));
        register("amethyst_axe", buildTool(Material.NETHERITE_AXE, "amethyst_axe", "Amethyst Axe"));
        register("amethyst_shovel", buildTool(Material.NETHERITE_SHOVEL, "amethyst_shovel", "Amethyst Shovel"));
        register("amethyst_hoe", buildTool(Material.NETHERITE_HOE, "amethyst_hoe", "Amethyst Hoe"));
        register("amethyst_spear", buildSpear());
        register("amethyst_helmet", buildArmor(Material.NETHERITE_HELMET, EquipmentSlot.HEAD, "amethyst_helmet", "Amethyst Helmet"));
        register("amethyst_chestplate", buildArmor(Material.NETHERITE_CHESTPLATE, EquipmentSlot.CHEST, "amethyst_chestplate", "Amethyst Chestplate"));
        register("amethyst_leggings", buildArmor(Material.NETHERITE_LEGGINGS, EquipmentSlot.LEGS, "amethyst_leggings", "Amethyst Leggings"));
        register("amethyst_boots", buildArmor(Material.NETHERITE_BOOTS, EquipmentSlot.FEET, "amethyst_boots", "Amethyst Boots"));
        register("amethyst_horse_armor", buildHorseArmor());
    }

    public static void registerRecipes() {
        RecipeChoice shard = new RecipeChoice.MaterialChoice(Material.AMETHYST_SHARD);
        RecipeChoice stick = new RecipeChoice.MaterialChoice(Material.STICK);

        // Tools / weapons
        shaped("amethyst_sword", get("amethyst_sword"), new String[]{"A", "A", "S"},
                Map.of('A', shard, 'S', stick));
        shaped("amethyst_pickaxe", get("amethyst_pickaxe"), new String[]{"AAA", " S ", " S "},
                Map.of('A', shard, 'S', stick));
        shaped("amethyst_axe", get("amethyst_axe"), new String[]{"AA", "AS", " S"},
                Map.of('A', shard, 'S', stick));
        shaped("amethyst_shovel", get("amethyst_shovel"), new String[]{"A", "S", "S"},
                Map.of('A', shard, 'S', stick));
        shaped("amethyst_hoe", get("amethyst_hoe"), new String[]{"AA", " S", " S"},
                Map.of('A', shard, 'S', stick));
        shaped("amethyst_spear", get("amethyst_spear"), new String[]{"A", "A", "A"},
                Map.of('A', shard));

        // Armor
        shaped("amethyst_helmet", get("amethyst_helmet"), new String[]{"AAA", "A A"}, Map.of('A', shard));
        shaped("amethyst_chestplate", get("amethyst_chestplate"), new String[]{"A A", "AAA", "AAA"}, Map.of('A', shard));
        shaped("amethyst_leggings", get("amethyst_leggings"), new String[]{"AAA", "A A", "A A"}, Map.of('A', shard));
        shaped("amethyst_boots", get("amethyst_boots"), new String[]{"A A", "A A"}, Map.of('A', shard));
        shaped("amethyst_horse_armor", get("amethyst_horse_armor"), new String[]{"  A", "AAA", "A A"}, Map.of('A', shard));
    }

    public static ItemStack get(String id) {
        ItemStack template = REGISTRY.get(id);
        if (template == null) throw new IllegalArgumentException("Unknown amethyst item: " + id);
        return template.clone();
    }

    public static List<String> ids() {
        return List.copyOf(REGISTRY.keySet());
    }

    // ── Factories ──────────────────────────────────────────────────────────────

    private static ItemStack buildSword() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        applyCommon(item, "amethyst_sword", "Amethyst Sword", "Cuts cleanly through the toughest foes.");
        return item;
    }

    private static ItemStack buildTool(Material base, String modelId, String displayName) {
        ItemStack item = new ItemStack(base);
        applyCommon(item, modelId, displayName, null);
        return item;
    }

    private static ItemStack buildSpear() {
        ItemStack item = new ItemStack(Material.TRIDENT);
        applyCommon(item, "amethyst_spear", "Amethyst Spear", "A balanced thrusting weapon.");
        return item;
    }

    private static ItemStack buildArmor(Material base, EquipmentSlot slot, String modelId, String displayName) {
        ItemStack item = new ItemStack(base);
        applyCommon(item, modelId, displayName, null);
        item.setData(DataComponentTypes.EQUIPPABLE,
                Equippable.equippable(slot)
                        .assetId(Key.key(NAMESPACE, EQUIPMENT_ASSET))
                        .build());
        return item;
    }

    private static ItemStack buildHorseArmor() {
        ItemStack item = new ItemStack(Material.NETHERITE_HORSE_ARMOR);
        applyCommon(item, "amethyst_horse_armor", "Amethyst Horse Armor", "Plated barding for your steed.");
        item.setData(DataComponentTypes.EQUIPPABLE,
                Equippable.equippable(EquipmentSlot.BODY)
                        .assetId(Key.key(NAMESPACE, EQUIPMENT_ASSET + "_horse"))
                        .build());
        return item;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static void applyCommon(ItemStack item, String modelId, String displayName, String loreLine) {
        item.setData(DataComponentTypes.ITEM_NAME,
                Component.text(displayName, NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        item.setData(DataComponentTypes.ITEM_MODEL, Key.key(NAMESPACE, modelId));
        if (loreLine != null) {
            item.setData(DataComponentTypes.LORE,
                    ItemLore.lore()
                            .addLine(Component.text(loreLine, NamedTextColor.GRAY)
                                    .decoration(TextDecoration.ITALIC, false))
                            .build());
        }
    }

    private static void register(String id, ItemStack item) {
        REGISTRY.put(id, item);
    }

    private static void shaped(String key, ItemStack result, String[] shape, Map<Character, RecipeChoice> map) {
        NamespacedKey nk = new NamespacedKey(Atlas.instance, key);
        if (Bukkit.getRecipe(nk) != null) Bukkit.removeRecipe(nk);
        ShapedRecipe recipe = new ShapedRecipe(nk, result);
        recipe.shape(shape);
        map.forEach(recipe::setIngredient);
        Bukkit.addRecipe(recipe);
    }
}
