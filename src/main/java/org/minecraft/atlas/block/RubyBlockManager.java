package org.minecraft.atlas.block;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Instrument;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.minecraft.atlas.Item.RubyItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Backs three custom "blocks" (ruby_block, ruby_ore, deepslate_ruby_ore) with the
 * note-block blockstate trick: each placed block is a {@code NOTE_BLOCK} carrying
 * a unique {@code (instrument, note)} state that the resource pack overrides to
 * render a custom model. Identification is location-based via a persisted map,
 * not by blockstate — so collisions with player-placed note blocks are tolerated.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RubyBlockManager {

    public record BlockSpec(String id, Instrument instrument, int note) {}

    private static final Map<String, BlockSpec> SPECS = new LinkedHashMap<>();

    /** All placed custom blocks, keyed by their world location. */
    private static final Map<Location, String> PLACED = new ConcurrentHashMap<>();

    private RubyBlockManager() {
    }

    public static void init() {
        SPECS.clear();
        register(new BlockSpec("ruby_block",          Instrument.PLING, 0));
        register(new BlockSpec("ruby_ore",            Instrument.PLING, 1));
        register(new BlockSpec("deepslate_ruby_ore",  Instrument.PLING, 2));
    }

    private static void register(BlockSpec spec) {
        SPECS.put(spec.id(), spec);
    }

    // ── Item ↔ block lookup ─────────────────────────────────────────────────────

    /**
     * Returns the custom-block id for an item whose {@code ITEM_MODEL} component
     * matches one of the registered specs, or null if it isn't a placeable
     * custom block.
     */
    public static String customBlockIdForItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        Key model = stack.getData(DataComponentTypes.ITEM_MODEL);
        if (model == null) return null;
        if (!RubyItem.NAMESPACE.equals(model.namespace())) return null;
        String id = model.value();
        return SPECS.containsKey(id) ? id : null;
    }

    public static BlockSpec spec(String id) {
        return SPECS.get(id);
    }

    // ── Placement / breakage ────────────────────────────────────────────────────

    /**
     * Replaces the given block with a NOTE_BLOCK carrying the custom blockstate
     * for {@code id} and records the placement. Does not validate that the block
     * is empty/replaceable — caller (the BlockPlaceEvent handler) has already
     * done that via the original event.
     */
    public static void placeAt(Block block, String id) {
        BlockSpec spec = SPECS.get(id);
        if (spec == null) throw new IllegalArgumentException("Unknown custom block: " + id);

        // Track the location before mutating the block so the BlockPhysicsEvent handler
        // sees this as a tracked note block during the type change and suppresses any
        // vanilla instrument recompute.
        PLACED.put(block.getLocation(), id);

        block.setType(Material.NOTE_BLOCK, false);
        NoteBlock data = (NoteBlock) block.getBlockData();
        data.setInstrument(spec.instrument());
        data.setNote(new org.bukkit.Note(spec.note()));
        data.setPowered(false);
        block.setBlockData(data, false);
    }

    /** Returns the custom-block id at {@code block}, or null. */
    public static String getCustomIdAt(Block block) {
        if (block.getType() != Material.NOTE_BLOCK) return null;
        return PLACED.get(block.getLocation());
    }

    /**
     * Returns the drop for a broken custom block. Ores drop the refined gem
     * (matching vanilla emerald-ore behavior); the block drops itself.
     */
    public static ItemStack dropFor(String id) {
        if ("ruby_ore".equals(id) || "deepslate_ruby_ore".equals(id)) {
            return RubyItem.get("ruby");
        }
        return RubyItem.get(id);
    }

    public static void removeAt(Block block) {
        PLACED.remove(block.getLocation());
    }

    public static boolean isTrackedNoteBlock(Block block) {
        return block.getType() == Material.NOTE_BLOCK && PLACED.containsKey(block.getLocation());
    }

    /**
     * Returns the tracked custom-block locations within the given chunk.
     */
    public static List<Map.Entry<Location, String>> trackedInChunk(World world, int chunkX, int chunkZ) {
        List<Map.Entry<Location, String>> out = new ArrayList<>();
        for (Map.Entry<Location, String> e : PLACED.entrySet()) {
            Location loc = e.getKey();
            if (loc.getWorld() != world) continue;
            if (loc.getBlockX() >> 4 != chunkX) continue;
            if (loc.getBlockZ() >> 4 != chunkZ) continue;
            out.add(e);
        }
        return out;
    }

    /**
     * Re-asserts the expected (instrument, note, powered) state for a tracked block.
     * Used after chunk load: vanilla can mutate the note block's instrument behind our
     * BlockPhysicsEvent suppression (the property is dynamically re-derived from the
     * block above/below in some paths), which flips the variant key off our custom
     * model. This forces it back if it has drifted.
     */
    public static void ensureState(Block block, String id) {
        BlockSpec spec = SPECS.get(id);
        if (spec == null) return;
        if (block.getType() != Material.NOTE_BLOCK) return;
        NoteBlock data = (NoteBlock) block.getBlockData();
        if (data.getInstrument() == spec.instrument()
                && data.getNote().getId() == spec.note()
                && !data.isPowered()) {
            return;
        }
        data.setInstrument(spec.instrument());
        data.setNote(new org.bukkit.Note(spec.note()));
        data.setPowered(false);
        block.setBlockData(data, false);
    }

    // ── Persistence ─────────────────────────────────────────────────────────────

    public static void loadConfig(FileConfiguration config) {
        PLACED.clear();
        ConfigurationSection sec = config.getConfigurationSection("custom-blocks");
        if (sec == null) return;

        for (String key : sec.getKeys(false)) {
            ConfigurationSection bs = sec.getConfigurationSection(key);
            if (bs == null) continue;

            String worldName = bs.getString("world");
            if (worldName == null) continue;
            World world = Bukkit.getWorld(worldName);
            if (world == null) continue;

            String id = bs.getString("id");
            if (id == null || !SPECS.containsKey(id)) continue;

            Location loc = new Location(world,
                    bs.getInt("x"), bs.getInt("y"), bs.getInt("z"));
            PLACED.put(loc, id);
        }
    }

    public static void saveConfig(FileConfiguration config) {
        config.set("custom-blocks", null);
        int i = 0;
        for (Map.Entry<Location, String> e : PLACED.entrySet()) {
            Location loc = e.getKey();
            if (loc.getWorld() == null) continue;
            String path = "custom-blocks." + (i++);
            config.set(path + ".id", e.getValue());
            config.set(path + ".world", loc.getWorld().getName());
            config.set(path + ".x", loc.getBlockX());
            config.set(path + ".y", loc.getBlockY());
            config.set(path + ".z", loc.getBlockZ());
        }
    }
}
