package org.minecraft.atlas.item;

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
 * Custom ruby items wired through Paper's DataComponentTypes API (1.21.4+).
 * Item models reference the hatlas resource pack via the ITEM_MODEL component.
 * Armor pieces additionally set the EQUIPPABLE component (1.21.2+) to override
 * the worn-body texture (asset id: hatlas:ruby).
 */
@SuppressWarnings("UnstableApiUsage")
public final class RubyItems {

    public static final String NAMESPACE = "hatlas";
    public static final String EQUIPMENT_ASSET = "ruby";

    private static final Map<String, ItemStack> REGISTRY = new LinkedHashMap<>();

    private RubyItems() {}

    // ── Entry points ───────────────────────────────────────────────────────────

    public static void init() {
        REGISTRY.clear();
        register("ruby",                buildRuby());
        register("ruby_block",          buildRubyBlock());
        register("ruby_ore",            buildRubyOre(false));
        register("deepslate_ruby_ore",  buildRubyOre(true));
        register("ruby_sword",          buildSword());
        register("ruby_pickaxe",        buildTool(Material.NETHERITE_PICKAXE, "ruby_pickaxe", "Ruby Pickaxe"));
        register("ruby_axe",            buildTool(Material.NETHERITE_AXE,     "ruby_axe",     "Ruby Axe"));
        register("ruby_shovel",         buildTool(Material.NETHERITE_SHOVEL,  "ruby_shovel",  "Ruby Shovel"));
        register("ruby_hoe",            buildTool(Material.NETHERITE_HOE,     "ruby_hoe",     "Ruby Hoe"));
        register("ruby_spear",          buildSpear());
        register("ruby_helmet",         buildArmor(Material.NETHERITE_HELMET,     EquipmentSlot.HEAD,  "ruby_helmet",     "Ruby Helmet"));
        register("ruby_chestplate",     buildArmor(Material.NETHERITE_CHESTPLATE, EquipmentSlot.CHEST, "ruby_chestplate", "Ruby Chestplate"));
        register("ruby_leggings",       buildArmor(Material.NETHERITE_LEGGINGS,   EquipmentSlot.LEGS,  "ruby_leggings",   "Ruby Leggings"));
        register("ruby_boots",          buildArmor(Material.NETHERITE_BOOTS,      EquipmentSlot.FEET,  "ruby_boots",      "Ruby Boots"));
        register("ruby_horse_armor",    buildHorseArmor());
    }

    public static void registerRecipes() {
        // Block ↔ gem compaction
        shaped("ruby_block_from_rubies", get("ruby_block"), new String[]{"RRR","RRR","RRR"},
                Map.of('R', ingredient("ruby")));
        shaped("rubies_from_ruby_block", with(get("ruby"), 9), new String[]{"B"},
                Map.of('B', ingredient("ruby_block")));

        // Tools
        shaped("ruby_sword",   get("ruby_sword"),   new String[]{"R","R","S"},
                Map.of('R', ingredient("ruby"), 'S', new RecipeChoice.MaterialChoice(Material.STICK)));
        shaped("ruby_pickaxe", get("ruby_pickaxe"), new String[]{"RRR"," S "," S "},
                Map.of('R', ingredient("ruby"), 'S', new RecipeChoice.MaterialChoice(Material.STICK)));
        shaped("ruby_axe",     get("ruby_axe"),     new String[]{"RR","RS"," S"},
                Map.of('R', ingredient("ruby"), 'S', new RecipeChoice.MaterialChoice(Material.STICK)));
        shaped("ruby_shovel",  get("ruby_shovel"),  new String[]{"R","S","S"},
                Map.of('R', ingredient("ruby"), 'S', new RecipeChoice.MaterialChoice(Material.STICK)));
        shaped("ruby_hoe",     get("ruby_hoe"),     new String[]{"RR"," S"," S"},
                Map.of('R', ingredient("ruby"), 'S', new RecipeChoice.MaterialChoice(Material.STICK)));
        shaped("ruby_spear",   get("ruby_spear"),   new String[]{"R","R","R"},
                Map.of('R', ingredient("ruby")));

        // Armor
        shaped("ruby_helmet",     get("ruby_helmet"),     new String[]{"RRR","R R"},        Map.of('R', ingredient("ruby")));
        shaped("ruby_chestplate", get("ruby_chestplate"), new String[]{"R R","RRR","RRR"},  Map.of('R', ingredient("ruby")));
        shaped("ruby_leggings",   get("ruby_leggings"),   new String[]{"RRR","R R","R R"},  Map.of('R', ingredient("ruby")));
        shaped("ruby_boots",      get("ruby_boots"),      new String[]{"R R","R R"},        Map.of('R', ingredient("ruby")));
        shaped("ruby_horse_armor",get("ruby_horse_armor"),new String[]{"  R","RRR","R R"},  Map.of('R', ingredient("ruby")));
    }

    public static ItemStack get(String id) {
        ItemStack template = REGISTRY.get(id);
        if (template == null) throw new IllegalArgumentException("Unknown ruby item: " + id);
        return template.clone();
    }

    public static List<String> ids() {
        return List.copyOf(REGISTRY.keySet());
    }

    // ── Factories ──────────────────────────────────────────────────────────────

    private static ItemStack buildRuby() {
        ItemStack item = new ItemStack(Material.EMERALD);
        applyCommon(item, "ruby", "Ruby", NamedTextColor.RED, "A glittering shard of refined ruby.");
        return item;
    }

    private static ItemStack buildRubyBlock() {
        ItemStack item = new ItemStack(Material.EMERALD_BLOCK);
        applyCommon(item, "ruby_block", "Block of Ruby", NamedTextColor.RED, "Nine rubies, compacted.");
        return item;
    }

    private static ItemStack buildRubyOre(boolean deepslate) {
        ItemStack item = new ItemStack(deepslate ? Material.DEEPSLATE_EMERALD_ORE : Material.EMERALD_ORE);
        String id = deepslate ? "deepslate_ruby_ore" : "ruby_ore";
        String name = deepslate ? "Deepslate Ruby Ore" : "Ruby Ore";
        applyCommon(item, id, name, NamedTextColor.RED, "A vein of raw ruby crystals.");
        return item;
    }

    private static ItemStack buildSword() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        applyCommon(item, "ruby_sword", "Ruby Sword", NamedTextColor.RED, "Cuts cleanly through the toughest foes.");
        return item;
    }

    private static ItemStack buildTool(Material base, String modelId, String displayName) {
        ItemStack item = new ItemStack(base);
        applyCommon(item, modelId, displayName, NamedTextColor.RED, null);
        return item;
    }

    private static ItemStack buildSpear() {
        ItemStack item = new ItemStack(Material.TRIDENT);
        applyCommon(item, "ruby_spear", "Ruby Spear", NamedTextColor.RED, "A balanced thrusting weapon.");
        return item;
    }

    private static ItemStack buildArmor(Material base, EquipmentSlot slot, String modelId, String displayName) {
        ItemStack item = new ItemStack(base);
        applyCommon(item, modelId, displayName, NamedTextColor.RED, null);
        item.setData(DataComponentTypes.EQUIPPABLE,
                Equippable.equippable(slot)
                        .assetId(Key.key(NAMESPACE, EQUIPMENT_ASSET))
                        .build());
        return item;
    }

    private static ItemStack buildHorseArmor() {
        ItemStack item = new ItemStack(Material.NETHERITE_HORSE_ARMOR);
        applyCommon(item, "ruby_horse_armor", "Ruby Horse Armor", NamedTextColor.RED, "Plated barding for your steed.");
        item.setData(DataComponentTypes.EQUIPPABLE,
                Equippable.equippable(EquipmentSlot.BODY)
                        .assetId(Key.key(NAMESPACE, EQUIPMENT_ASSET + "_horse"))
                        .build());
        return item;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static void applyCommon(ItemStack item, String modelId, String displayName,
                                    NamedTextColor color, String loreLine) {
        item.setData(DataComponentTypes.ITEM_NAME,
                Component.text(displayName, color).decoration(TextDecoration.ITALIC, false));
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

    private static RecipeChoice ingredient(String id) {
        return new RecipeChoice.ExactChoice(get(id));
    }

    private static ItemStack with(ItemStack stack, int amount) {
        stack.setAmount(amount);
        return stack;
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
