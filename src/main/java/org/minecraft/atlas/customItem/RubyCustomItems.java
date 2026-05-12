package org.minecraft.atlas.customItem;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.minecraft.atlas.Atlas;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Raw ruby materials: gem, block, and ores. Gear (sword/pickaxe/armor/…) lives in
 * {@link AmethystCustomItems} since the gear set was migrated from ruby to amethyst.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RubyCustomItems {

    public static final String NAMESPACE = "hatlas";

    private static final Map<String, ItemStack> REGISTRY = new LinkedHashMap<>();

    private RubyCustomItems() {
    }

    // ── Entry points ───────────────────────────────────────────────────────────

    public static void init() {
        REGISTRY.clear();
        register("ruby", buildRuby());
        register("ruby_block", buildRubyBlock());
        register("ruby_ore", buildRubyOre(false));
        register("deepslate_ruby_ore", buildRubyOre(true));
    }

    public static void registerRecipes() {
        // Block ↔ gem compaction
        shaped("ruby_block_from_rubies", get("ruby_block"), new String[]{"RRR","RRR","RRR"},
                Map.of('R', ingredient("ruby")));
        shaped("rubies_from_ruby_block", with(get("ruby"), 9), new String[]{"B"},
                Map.of('B', ingredient("ruby_block")));
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
