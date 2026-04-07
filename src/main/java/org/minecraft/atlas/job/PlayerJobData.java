package org.minecraft.atlas.job;

public class PlayerJobData {

    private final Job job;
    private int level;    // 1 to max-level (from jobs.yml)
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

    /** XP required to advance from the current level to the next. */
    public int getXpRequired() {
        if (level >= JobRegistry.getMaxLevel()) return Integer.MAX_VALUE;
        return (int) Math.round(JobRegistry.getXpBase() * Math.pow(JobRegistry.getXpMultiplier(), level - 1));
    }

    /** Direct setters for admin use. */
    public void setLevel(int level) {
        this.level = Math.max(1, Math.min(JobRegistry.getMaxLevel(), level));
    }

    public void setXp(double xp) {
        this.xp = Math.max(0, xp);
    }

    /**
     * Adds XP, handling carry-over across multiple level-ups and capping at max level.
     * @return true if the player leveled up at least once.
     */
    public boolean addXp(double amount) {
        if (level >= JobRegistry.getMaxLevel()) return false;

        xp += amount;
        boolean leveledUp = false;

        while (level < JobRegistry.getMaxLevel() && xp >= getXpRequired()) {
            xp -= getXpRequired();
            level++;
            leveledUp = true;
        }

        if (level >= JobRegistry.getMaxLevel()) xp = 0;
        return leveledUp;
    }
}
