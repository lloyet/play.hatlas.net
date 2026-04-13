package org.minecraft.atlas.tag;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.minecraft.atlas.Atlas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TagManager {

    /** All registered tags, keyed by name. */
    private static final Map<String, Tag> tags = new LinkedHashMap<>();

    /** PDC key stored on each TextDisplay to link it back to its tag name. */
    public static NamespacedKey keyTagName;

    // -------------------------------------------------------------------------
    // Initialisation
    // -------------------------------------------------------------------------

    public static void init() {
        keyTagName = new NamespacedKey(Atlas.instance, "tag_name");
    }

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    public static void loadTags(FileConfiguration config) {
        tags.clear();
        ConfigurationSection sec = config.getConfigurationSection("tags");
        if (sec == null) return;

        for (String name : sec.getKeys(false)) {
            ConfigurationSection ts = sec.getConfigurationSection(name);
            if (ts == null) continue;

            String worldName = ts.getString("world");
            if (worldName == null) continue;
            World world = Bukkit.getWorld(worldName);
            if (world == null) continue;

            double x = ts.getDouble("x");
            double y = ts.getDouble("y");
            double z = ts.getDouble("z");
            List<String> lines = new ArrayList<>(ts.getStringList("lines"));
            Location loc = new Location(world, x, y, z);

            Tag tag = new Tag(name, loc, lines);

            String uuidStr = ts.getString("text_display_uuid");
            if (uuidStr != null) {
                try { tag.setTextDisplayUUID(UUID.fromString(uuidStr)); }
                catch (IllegalArgumentException ignored) {}
            }

            tags.put(name, tag);
        }
    }

    public static void saveTags(FileConfiguration config) {
        config.set("tags", null);
        if (tags.isEmpty()) return;

        ConfigurationSection sec = config.createSection("tags");
        for (Tag tag : tags.values()) {
            ConfigurationSection ts = sec.createSection(tag.getName());
            Location loc = tag.getLocation();
            ts.set("world", loc.getWorld().getName());
            ts.set("x",     loc.getX());
            ts.set("y",     loc.getY());
            ts.set("z",     loc.getZ());
            ts.set("lines", tag.getLines());
            if (tag.getTextDisplayUUID() != null) {
                ts.set("text_display_uuid", tag.getTextDisplayUUID().toString());
            }
        }
    }

    // -------------------------------------------------------------------------
    // CRUD
    // -------------------------------------------------------------------------

    public static boolean tagExists(String name) {
        return tags.containsKey(name);
    }

    public static Tag getTag(String name) {
        return tags.get(name);
    }

    public static Map<String, Tag> getTags() {
        return Collections.unmodifiableMap(tags);
    }

    /**
     * Creates a new tag at {@code loc} with a single first line.
     * Returns {@code null} if a tag with that name already exists.
     */
    public static Tag createTag(String name, Location loc, String firstLine) {
        if (tags.containsKey(name)) return null;

        List<String> lines = new ArrayList<>();
        lines.add(firstLine);
        Tag tag = new Tag(name, loc, lines);
        tags.put(name, tag);
        spawnOrUpdateDisplay(tag);
        return tag;
    }

    /** Removes the tag and its in-world TextDisplay. Returns false if not found. */
    public static boolean deleteTag(String name) {
        Tag tag = tags.remove(name);
        if (tag == null) return false;
        if (tag.getTextDisplayUUID() != null) {
            Entity e = Bukkit.getEntity(tag.getTextDisplayUUID());
            if (e != null) e.remove();
        }
        return true;
    }

    /** Appends a line to an existing tag. Returns false if the tag does not exist. */
    public static boolean addLine(String name, String text) {
        Tag tag = tags.get(name);
        if (tag == null) return false;
        tag.getLines().add(text);
        spawnOrUpdateDisplay(tag);
        return true;
    }

    /**
     * Replaces the line at {@code lineNumber} (1-based) with {@code text}.
     * Returns false if the tag does not exist or the line number is out of range.
     */
    public static boolean editLine(String name, int lineNumber, String text) {
        Tag tag = tags.get(name);
        if (tag == null) return false;
        List<String> lines = tag.getLines();
        if (lineNumber < 1 || lineNumber > lines.size()) return false;
        lines.set(lineNumber - 1, text);
        spawnOrUpdateDisplay(tag);
        return true;
    }

    // -------------------------------------------------------------------------
    // Display helpers
    // -------------------------------------------------------------------------

    /** Spawns a new TextDisplay or updates the text on an existing one. */
    public static void spawnOrUpdateDisplay(Tag tag) {
        TextDisplay display = null;
        UUID existingUUID = tag.getTextDisplayUUID();
        if (existingUUID != null) {
            Entity e = Bukkit.getEntity(existingUUID);
            if (e instanceof TextDisplay td) display = td;
        }

        if (display == null) {
            Location loc = tag.getLocation();
            if (!loc.isChunkLoaded()) return;
            String tagName = tag.getName();
            display = loc.getWorld().spawn(loc, TextDisplay.class, td -> {
                td.setBillboard(Display.Billboard.CENTER);
                td.setPersistent(true);
                td.setInvulnerable(true);
                td.setGravity(false);
                td.getPersistentDataContainer().set(keyTagName, PersistentDataType.STRING, tagName);
            });
            tag.setTextDisplayUUID(display.getUniqueId());
        }

        display.text(buildComponent(tag.getLines()));
    }

    /**
     * Called from {@link org.minecraft.atlas.listener.TagListener} when a TextDisplay
     * with the tag PDC key loads from disk.
     */
    public static void restoreDisplay(TextDisplay display) {
        String name = display.getPersistentDataContainer().get(keyTagName, PersistentDataType.STRING);
        if (name == null) return;

        Tag tag = tags.get(name);
        if (tag == null) {
            display.remove(); // orphaned entity from a deleted tag
            return;
        }

        tag.setTextDisplayUUID(display.getUniqueId());
        display.text(buildComponent(tag.getLines()));
    }

    private static Component buildComponent(List<String> lines) {
        if (lines.isEmpty()) return Component.empty();
        Component result = Component.text(lines.get(0), NamedTextColor.WHITE);
        for (int i = 1; i < lines.size(); i++) {
            result = result.append(Component.newline())
                           .append(Component.text(lines.get(i), NamedTextColor.WHITE));
        }
        return result;
    }
}
