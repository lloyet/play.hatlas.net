package org.minecraft.atlas.job;

/** Immutable data for a single XP source (one row in the leveling tables). */
public class JobSource {

    private final String key;
    private final String displayName;
    private final int unlockLevel;
    private final double xp;
    private final int cutoffLevel;

    public JobSource(String key, String displayName, int unlockLevel, double xp, int cutoffLevel) {
        this.key = key;
        this.displayName = displayName;
        this.unlockLevel = unlockLevel;
        this.xp = xp;
        this.cutoffLevel = cutoffLevel;
    }

    public String getKey() { return key; }
    public String getDisplayName() { return displayName; }
    public int getUnlockLevel() { return unlockLevel; }
    public double getXp() { return xp; }
    public int getCutoffLevel() { return cutoffLevel; }

    public boolean isActive(int level) { return level >= unlockLevel && level <= cutoffLevel; }
    public boolean isLocked(int level) { return level < unlockLevel; }
    public boolean isExpired(int level) { return level > cutoffLevel; }
}
