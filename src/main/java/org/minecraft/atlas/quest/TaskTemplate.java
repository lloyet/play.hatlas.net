package org.minecraft.atlas.quest;

import org.bukkit.inventory.ItemStack;
import org.minecraft.atlas.job.Job;

import java.util.List;

/**
 * Static task definition loaded from config.
 * Difficulty, amount, and time limits are generated dynamically per quest — not stored here.
 */
public class TaskTemplate {

    private final String          id;
    private final String          name;
    private final String          description;
    private final Job job;
    private final String          actionType;       // e.g. "break_block", "kill_entity"
    private final List<String>    targets;          // block/entity names that count
    private final List<ItemStack> baseItemRewards;  // base amounts, scaled at generation time

    public TaskTemplate(String id, String name, String description, Job job,
                        String actionType, List<String> targets, List<ItemStack> baseItemRewards) {
        this.id              = id;
        this.name            = name;
        this.description     = description;
        this.job             = job;
        this.actionType      = actionType;
        this.targets         = List.copyOf(targets);
        this.baseItemRewards = List.copyOf(baseItemRewards);
    }

    public String          getId()              { return id; }
    public String          getName()            { return name; }
    public String          getDescription()     { return description; }
    public Job             getJob()             { return job; }
    public String          getActionType()      { return actionType; }
    public List<String>    getTargets()         { return targets; }
    public List<ItemStack> getBaseItemRewards() { return baseItemRewards; }
}
