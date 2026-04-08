package org.minecraft.atlas.job;

public class PlayerJobData {

    private final Job job;
    private int level;    // 1 to max-level and beyond (post-100 is cosmetic)
    private double xp;    // accumulated XP within the current level

    public PlayerJobData(Job job) {
        this.job = job;
        this.level = 1;
        this.xp = 0;
    }

    public PlayerJobData(Job job, int level, double xp) {
        this.job = job;
        this.level = level;
        this.xp = xp;
    }

    public Job getJob() { return job; }
    public int getLevel() { return level; }
    public double getXp() { return xp; }

    /** True when the job has reached the mastery threshold (max level). */
    public boolean isMastered() {
        return level >= JobRegistry.getMaxLevel();
    }

    /** XP required to advance from the current level to the next. Scales exponentially forever. */
    public long getXpRequired() {
        return Math.round(JobRegistry.getXpBase() * Math.pow(JobRegistry.getXpMultiplier(), level - 1));
    }

    /** Direct setters for admin use. Level is clamped to a minimum of 1. */
    public void setLevel(int level) {
        this.level = Math.max(1, level);
    }

    public void setXp(double xp) {
        this.xp = Math.max(0, xp);
    }

    /**
     * Adds XP with carry-over across level-ups. Levels continue indefinitely past max level
     * (post-mastery levels are cosmetic only — reward logic is handled by the caller).
     *
     * @return true if the player leveled up at least once.
     */
    public boolean addXp(double amount) {
        xp += amount;
        boolean leveledUp = false;

        while (xp >= getXpRequired()) {
            xp -= getXpRequired();
            level++;
            leveledUp = true;
        }

        return leveledUp;
    }
}
