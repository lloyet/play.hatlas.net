package org.minecraft.atlas.job;

import java.util.List;

/** A quest currently in progress for a player. */
public class ActiveQuest {

    private final String       questId;
    private final List<String> taskIds;   // IDs of tasks that make up this quest
    private final long         expiresAt; // System.currentTimeMillis()

    public ActiveQuest(String questId, List<String> taskIds, long expiresAt) {
        this.questId   = questId;
        this.taskIds   = List.copyOf(taskIds);
        this.expiresAt = expiresAt;
    }

    public String       getQuestId()   { return questId; }
    public List<String> getTaskIds()   { return taskIds; }
    public long         getExpiresAt() { return expiresAt; }
    public boolean      isExpired()    { return System.currentTimeMillis() > expiresAt; }
}
