package org.minecraft.atlas.job;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A dynamically generated quest composed of one or more {@link TaskTemplate}s.
 * Exp reward is not fixed — it is calculated at completion time using the formula:
 *   exp = baseExpReward * sumOfDifficulties * sqrt(factionLevel + 1)
 */
public class QuestTemplate {

    private final String            id;
    private final List<TaskTemplate> tasks;
    /** Duration of the quest = max time limit across all tasks. */
    private final long              timeLimitMs;

    public QuestTemplate(String id, List<TaskTemplate> tasks) {
        this.id    = id;
        this.tasks = List.copyOf(tasks);
        this.timeLimitMs = tasks.stream()
                .mapToLong(TaskTemplate::getTimeLimitMs)
                .max()
                .orElse(3_600_000L);
    }

    public String             getId()          { return id; }
    public List<TaskTemplate> getTasks()       { return tasks; }
    public long               getTimeLimitMs() { return timeLimitMs; }

    /** Aggregated item rewards from all tasks. */
    public List<ItemStack> getItemRewards() {
        List<ItemStack> all = new ArrayList<>();
        for (TaskTemplate t : tasks) all.addAll(t.getItemRewards());
        return all;
    }

    /** Sum of difficulty levels across all tasks (used in the exp formula). */
    public int getSumDifficulty() {
        return tasks.stream().mapToInt(TaskTemplate::getDifficulty).sum();
    }

    /**
     * Calculates the faction XP awarded on completion.
     * Formula: baseExpReward * sumOfDifficulties * sqrt(factionLevel + 1)
     * — Low faction levels earn proportionally less; higher levels earn more.
     */
    public int calculateExpReward(int factionLevel, int baseExpReward) {
        return (int) (baseExpReward * getSumDifficulty() * Math.sqrt(factionLevel + 1));
    }
}
