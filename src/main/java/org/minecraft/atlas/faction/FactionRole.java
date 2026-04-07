package org.minecraft.atlas.faction;

public enum FactionRole {
    MEMBER,
    MODERATOR,
    LEADER;

    public boolean isAtLeast(FactionRole required) {
        return this.ordinal() >= required.ordinal();
    }

    public String displayName() {
        return switch (this) {
            case MEMBER -> "Member";
            case MODERATOR -> "Moderator";
            case LEADER -> "Leader";
        };
    }
}
