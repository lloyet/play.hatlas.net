package org.minecraft.atlas.job;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.Map;
import java.util.Set;

/**
 * Static mappings from Bukkit types to job source references.
 * Keys must match the source keys defined in jobs.yml.
 */
public final class JobSources {

    private JobSources() {}

    /** Points to a specific source within a job's YAML definition. */
    public record SourceRef(String category, String key) {}

    // -------------------------------------------------------------------------
    // MINER — block breaks
    // -------------------------------------------------------------------------

    public static final Map<Material, SourceRef> MINER_BREAK = Map.ofEntries(
            Map.entry(Material.STONE,                     new SourceRef("mining", "stone")),
            Map.entry(Material.COBBLESTONE,               new SourceRef("mining", "stone")),
            Map.entry(Material.DEEPSLATE,                 new SourceRef("mining", "stone")),
            Map.entry(Material.COBBLED_DEEPSLATE,         new SourceRef("mining", "stone")),
            Map.entry(Material.GRANITE,                   new SourceRef("mining", "granite_diorite_andesite")),
            Map.entry(Material.DIORITE,                   new SourceRef("mining", "granite_diorite_andesite")),
            Map.entry(Material.ANDESITE,                  new SourceRef("mining", "granite_diorite_andesite")),
            Map.entry(Material.TUFF,                      new SourceRef("mining", "granite_diorite_andesite")),
            Map.entry(Material.CALCITE,                   new SourceRef("mining", "granite_diorite_andesite")),
            Map.entry(Material.COAL_ORE,                  new SourceRef("mining", "coal")),
            Map.entry(Material.DEEPSLATE_COAL_ORE,        new SourceRef("mining", "coal")),
            Map.entry(Material.COPPER_ORE,                new SourceRef("mining", "copper")),
            Map.entry(Material.DEEPSLATE_COPPER_ORE,      new SourceRef("mining", "copper")),
            Map.entry(Material.IRON_ORE,                  new SourceRef("mining", "iron")),
            Map.entry(Material.DEEPSLATE_IRON_ORE,        new SourceRef("mining", "iron")),
            Map.entry(Material.REDSTONE_ORE,              new SourceRef("mining", "redstone")),
            Map.entry(Material.DEEPSLATE_REDSTONE_ORE,    new SourceRef("mining", "redstone")),
            Map.entry(Material.LAPIS_ORE,                 new SourceRef("mining", "lapis")),
            Map.entry(Material.DEEPSLATE_LAPIS_ORE,       new SourceRef("mining", "lapis")),
            Map.entry(Material.GOLD_ORE,                  new SourceRef("mining", "gold")),
            Map.entry(Material.DEEPSLATE_GOLD_ORE,        new SourceRef("mining", "gold")),
            Map.entry(Material.NETHER_GOLD_ORE,           new SourceRef("mining", "gold")),
            Map.entry(Material.NETHER_QUARTZ_ORE,         new SourceRef("mining", "quartz")),
            Map.entry(Material.OBSIDIAN,                  new SourceRef("mining", "obsidian")),
            Map.entry(Material.CRYING_OBSIDIAN,           new SourceRef("mining", "obsidian")),
            Map.entry(Material.DIAMOND_ORE,               new SourceRef("mining", "diamond")),
            Map.entry(Material.DEEPSLATE_DIAMOND_ORE,     new SourceRef("mining", "diamond")),
            Map.entry(Material.EMERALD_ORE,               new SourceRef("mining", "emerald")),
            Map.entry(Material.DEEPSLATE_EMERALD_ORE,     new SourceRef("mining", "emerald"))
    );

    // MINER — furnace output materials that award smelting XP
    public static final Map<Material, SourceRef> MINER_SMELT = Map.of(
            Material.CHARCOAL,       new SourceRef("smelting", "charcoal"),
            Material.NETHER_BRICK,   new SourceRef("smelting", "nether_brick"),
            Material.COPPER_INGOT,   new SourceRef("smelting", "copper_ingot"),
            Material.IRON_INGOT,     new SourceRef("smelting", "iron_ingot"),
            Material.GOLD_INGOT,     new SourceRef("smelting", "gold_ingot"),
            Material.NETHERITE_SCRAP, new SourceRef("smelting", "netherite_scrap")
    );

    // -------------------------------------------------------------------------
    // LUMBERJACK — block breaks (logs + leaves)
    // -------------------------------------------------------------------------

    public static final Map<Material, SourceRef> LUMBERJACK_BREAK = Map.ofEntries(
            Map.entry(Material.OAK_LOG,                   new SourceRef("logging", "oak_spruce_birch")),
            Map.entry(Material.SPRUCE_LOG,                new SourceRef("logging", "oak_spruce_birch")),
            Map.entry(Material.BIRCH_LOG,                 new SourceRef("logging", "oak_spruce_birch")),
            Map.entry(Material.JUNGLE_LOG,                new SourceRef("logging", "jungle_acacia")),
            Map.entry(Material.ACACIA_LOG,                new SourceRef("logging", "jungle_acacia")),
            Map.entry(Material.DARK_OAK_LOG,              new SourceRef("logging", "dark_oak")),
            Map.entry(Material.MANGROVE_LOG,              new SourceRef("logging", "mangrove")),
            Map.entry(Material.CHERRY_LOG,                new SourceRef("logging", "cherry")),
            Map.entry(Material.CRIMSON_STEM,              new SourceRef("logging", "crimson_warped")),
            Map.entry(Material.WARPED_STEM,               new SourceRef("logging", "crimson_warped")),
            Map.entry(Material.MUSHROOM_STEM,             new SourceRef("logging", "mushroom_stem")),
            Map.entry(Material.BROWN_MUSHROOM_BLOCK,      new SourceRef("logging", "mushroom_stem")),
            Map.entry(Material.RED_MUSHROOM_BLOCK,        new SourceRef("logging", "mushroom_stem")),
            Map.entry(Material.OAK_LEAVES,               new SourceRef("extras", "leaves")),
            Map.entry(Material.SPRUCE_LEAVES,             new SourceRef("extras", "leaves")),
            Map.entry(Material.BIRCH_LEAVES,              new SourceRef("extras", "leaves")),
            Map.entry(Material.JUNGLE_LEAVES,             new SourceRef("extras", "leaves")),
            Map.entry(Material.ACACIA_LEAVES,             new SourceRef("extras", "leaves")),
            Map.entry(Material.DARK_OAK_LEAVES,           new SourceRef("extras", "leaves")),
            Map.entry(Material.MANGROVE_LEAVES,           new SourceRef("extras", "leaves")),
            Map.entry(Material.CHERRY_LEAVES,             new SourceRef("extras", "leaves")),
            Map.entry(Material.AZALEA_LEAVES,             new SourceRef("extras", "leaves")),
            Map.entry(Material.FLOWERING_AZALEA_LEAVES,   new SourceRef("extras", "leaves"))
    );

    /** Sapling materials that award XP when placed by a Lumberjack. */
    public static final Set<Material> LUMBERJACK_SAPLINGS = Set.of(
            Material.OAK_SAPLING, Material.SPRUCE_SAPLING, Material.BIRCH_SAPLING,
            Material.JUNGLE_SAPLING, Material.ACACIA_SAPLING, Material.DARK_OAK_SAPLING,
            Material.MANGROVE_PROPAGULE, Material.CHERRY_SAPLING
    );

    // -------------------------------------------------------------------------
    // FARMER — block breaks (crops require maturity check in the listener)
    // -------------------------------------------------------------------------

    public static final Map<Material, SourceRef> FARMER_BREAK = Map.ofEntries(
            Map.entry(Material.WHEAT,       new SourceRef("crops", "wheat_carrot_potato")),
            Map.entry(Material.CARROTS,     new SourceRef("crops", "wheat_carrot_potato")),
            Map.entry(Material.POTATOES,    new SourceRef("crops", "wheat_carrot_potato")),
            Map.entry(Material.BEETROOTS,   new SourceRef("crops", "beetroot")),
            Map.entry(Material.MELON,       new SourceRef("crops", "melon_pumpkin")),
            Map.entry(Material.PUMPKIN,     new SourceRef("crops", "melon_pumpkin")),
            Map.entry(Material.SUGAR_CANE,  new SourceRef("crops", "sugar_cane")),
            Map.entry(Material.BAMBOO,      new SourceRef("crops", "bamboo")),
            Map.entry(Material.CACTUS,      new SourceRef("crops", "cactus")),
            Map.entry(Material.COCOA,       new SourceRef("crops", "cocoa_beans")),
            Map.entry(Material.NETHER_WART, new SourceRef("crops", "nether_wart"))
    );

    /** Entity types that award shearing XP to a Farmer. */
    public static final Set<EntityType> FARMER_SHEARABLE = Set.of(EntityType.SHEEP);

    /** Entity types that award breeding XP to a Farmer. */
    public static final Set<EntityType> FARMER_BREEDABLE = Set.of(
            EntityType.COW, EntityType.SHEEP, EntityType.PIG, EntityType.CHICKEN,
            EntityType.HORSE, EntityType.DONKEY, EntityType.RABBIT, EntityType.LLAMA,
            EntityType.TURTLE, EntityType.FOX, EntityType.BEE, EntityType.STRIDER,
            EntityType.HOGLIN, EntityType.AXOLOTL, EntityType.GOAT, EntityType.FROG,
            EntityType.CAMEL, EntityType.SNIFFER
    );

    // -------------------------------------------------------------------------
    // HUNTER — entity kills
    // -------------------------------------------------------------------------

    public static final Map<EntityType, SourceRef> HUNTER_KILLS = Map.ofEntries(
            Map.entry(EntityType.CHICKEN,          new SourceRef("kills", "chicken")),
            Map.entry(EntityType.COW,              new SourceRef("kills", "cow")),
            Map.entry(EntityType.SHEEP,            new SourceRef("kills", "sheep")),
            Map.entry(EntityType.PIG,              new SourceRef("kills", "pig")),
            Map.entry(EntityType.COD,              new SourceRef("kills", "fish")),
            Map.entry(EntityType.SALMON,           new SourceRef("kills", "fish")),
            Map.entry(EntityType.TROPICAL_FISH,    new SourceRef("kills", "fish")),
            Map.entry(EntityType.PUFFERFISH,       new SourceRef("kills", "fish")),
            Map.entry(EntityType.ZOMBIE,           new SourceRef("kills", "zombie")),
            Map.entry(EntityType.ZOMBIE_VILLAGER,  new SourceRef("kills", "zombie")),
            Map.entry(EntityType.HUSK,             new SourceRef("kills", "zombie")),
            Map.entry(EntityType.DROWNED,          new SourceRef("kills", "zombie")),
            Map.entry(EntityType.SKELETON,         new SourceRef("kills", "skeleton")),
            Map.entry(EntityType.STRAY,            new SourceRef("kills", "skeleton")),
            Map.entry(EntityType.SPIDER,           new SourceRef("kills", "spider")),
            Map.entry(EntityType.CAVE_SPIDER,      new SourceRef("kills", "spider")),
            Map.entry(EntityType.CREEPER,          new SourceRef("kills", "creeper")),
            Map.entry(EntityType.ENDERMAN,         new SourceRef("kills", "enderman")),
            Map.entry(EntityType.BLAZE,            new SourceRef("kills", "blaze")),
            Map.entry(EntityType.WITCH,            new SourceRef("kills", "witch")),
            Map.entry(EntityType.PIGLIN,           new SourceRef("kills", "piglin_brute")),
            Map.entry(EntityType.PIGLIN_BRUTE,     new SourceRef("kills", "piglin_brute")),
            Map.entry(EntityType.ZOMBIFIED_PIGLIN, new SourceRef("kills", "piglin_brute")),
            Map.entry(EntityType.WITHER_SKELETON,  new SourceRef("kills", "wither_skeleton")),
            Map.entry(EntityType.WITHER,           new SourceRef("kills", "wither")),
            Map.entry(EntityType.ENDER_DRAGON,     new SourceRef("kills", "ender_dragon"))
    );
}
