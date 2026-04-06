package org.minecraft.atlas.job;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

public enum Job {

    MINER("Miner", Material.IRON_PICKAXE, NamedTextColor.GRAY),
    LUMBERJACK("Lumberjack", Material.IRON_AXE, NamedTextColor.GREEN),
    HUNTER("Hunter", Material.BOW, NamedTextColor.RED),
    FARMER("Farmer", Material.WHEAT_SEEDS, NamedTextColor.YELLOW);

    private final String displayName;
    private final Material icon;
    private final NamedTextColor color;

    Job(String displayName, Material icon, NamedTextColor color) {
        this.displayName = displayName;
        this.icon = icon;
        this.color = color;
    }

    public String getDisplayName() { return displayName; }
    public Material getIcon() { return icon; }
    public NamedTextColor getColor() { return color; }
}
