package org.minecraft.atlas.job;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Immutable task definition loaded from config. Tasks are combined into dynamic quests. */
public class TaskTemplate {

    private final String          id;
    private final String          name;
    private final String          description;
    private final Job             job;
    private final String          actionType;    // e.g. "break_block", "kill_entity", "harvest_crop"
    private final int             minLevel;      // minimum faction level to be eligible
    private final int             maxLevel;      // maximum faction level to be eligible
    private final int             difficulty;    // 1=easy, 2=normal, 3=hard
    private final List<String>    targets;       // block/entity names tied to the action
    private final List<ItemStack> itemRewards;
    private final long            timeLimitMs;

    public TaskTemplate(String id, String name, String description, Job job,
                        String actionType, int minLevel, int maxLevel,
                        int difficulty, List<String> targets,
                        List<ItemStack> itemRewards, long timeLimitMs) {
        this.id          = id;
        this.name        = name;
        this.description = description;
        this.job         = job;
        this.actionType  = actionType;
        this.minLevel    = minLevel;
        this.maxLevel    = maxLevel;
        this.difficulty  = difficulty;
        this.targets     = List.copyOf(targets);
        this.itemRewards = List.copyOf(itemRewards);
        this.timeLimitMs = timeLimitMs;
    }

    public String          getId()          { return id; }
    public String          getName()        { return name; }
    public String          getDescription() { return description; }
    public Job             getJob()         { return job; }
    public String          getActionType()  { return actionType; }
    public int             getMinLevel()    { return minLevel; }
    public int             getMaxLevel()    { return maxLevel; }
    public int             getDifficulty()  { return difficulty; }
    public List<String>    getTargets()     { return targets; }
    public List<ItemStack> getItemRewards() { return itemRewards; }
    public long            getTimeLimitMs() { return timeLimitMs; }
}
