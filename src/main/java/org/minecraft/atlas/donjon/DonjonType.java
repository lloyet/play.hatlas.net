package org.minecraft.atlas.donjon;

import org.bukkit.generator.structure.Structure;

public enum DonjonType {
    TRIAL("trial", "Trial", Structure.TRIAL_CHAMBERS);

    private final String configKey;
    private final String displayName;
    /** The vanilla Minecraft structure this donjon type maps to. */
    private final Structure structure;

    DonjonType(String configKey, String displayName, Structure structure) {
        this.configKey   = configKey;
        this.displayName = displayName;
        this.structure   = structure;
    }

    public String    getConfigKey()   { return configKey; }
    public String    getDisplayName() { return displayName; }
    public Structure getStructure()   { return structure; }

    public static DonjonType fromConfigKey(String key) {
        if (key == null) return null;

        for (DonjonType t : values()) {
            if (t.configKey.equalsIgnoreCase(key)) return t;
        }

        return null;
    }
}
