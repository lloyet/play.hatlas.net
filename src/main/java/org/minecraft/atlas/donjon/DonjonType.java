package org.minecraft.atlas.donjon;

public enum DonjonType {
    TRIAL("trial", "Trial"),
    DESERT("desert", "Desert"),
    NETHER_CASTLE("nether_castle", "Nether Castle"),
    PLAIN("plain", "Plains"),
    SKY("sky", "Sky");

    private final String configKey;
    private final String displayName;

    DonjonType(String configKey, String displayName) {
        this.configKey   = configKey;
        this.displayName = displayName;
    }

    public String getConfigKey()   { return configKey; }
    public String getDisplayName() { return displayName; }

    public static DonjonType fromConfigKey(String key) {
        if (key == null) return null;
        for (DonjonType t : values()) {
            if (t.configKey.equalsIgnoreCase(key)) return t;
        }
        return null;
    }
}
