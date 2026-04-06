package org.minecraft.atlas.job;

public class PlayerJobData {

    private final Job job;
    private int level;
    private int progress;

    public PlayerJobData(Job job) {
        this.job = job;
        this.level = 1;
        this.progress = 0;
    }

    public PlayerJobData(Job job, int level, int progress) {
        this.job = job;
        this.level = level;
        this.progress = progress;
    }

    public Job getJob() { return job; }
    public int getLevel() { return level; }
    public int getProgress() { return progress; }

    /** Progress required to advance from the current level to the next. */
    public int getProgressRequired() {
        return level * 64;
    }

    /**
     * Adds progress toward the next level.
     * @return true if the player leveled up as a result.
     */
    public boolean addProgress(int amount) {
        progress += amount;
        if (progress >= getProgressRequired()) {
            progress -= getProgressRequired();
            level++;
            return true;
        }
        return false;
    }
}
