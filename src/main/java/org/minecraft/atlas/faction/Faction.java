package org.minecraft.atlas.faction;

import net.kyori.adventure.text.format.NamedTextColor;

import java.util.*;

public class Faction {

    private String name;
    private UUID owner;
    private final List<UUID> members = new ArrayList<>();
    private NamedTextColor color = NamedTextColor.WHITE;
    private String description = "";
    private final Map<UUID, FactionRole> roles = new HashMap<>();
    private int level = 0;
    private int exp = 0;
    private int skillPoints = 0;

    private int downgradeMul = 1;
    private long downgradeWindowEndMs = 0L;

    private boolean outpostUnlocked = false;

    private final Set<String> allies = new HashSet<>();

    public Faction(String name, UUID owner) {
        this.name = name;
        this.owner = owner;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public UUID getOwner() { return owner; }
    public void setOwner(UUID owner) { this.owner = owner; }
    public List<UUID> getMembers() { return members; }
    public void addMember(UUID uuid) { members.add(uuid); }
    public void removeMember(UUID uuid) { members.remove(uuid); }
    public NamedTextColor getColor() { return color; }
    public void setColor(NamedTextColor color) { this.color = color; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = Math.clamp(level, 0, FactionLevelManager.MAX_LEVEL); }

    public int getExp() { return exp; }
    public void setExp(int exp) { this.exp = Math.max(0, exp); }
    public void addExp(int amount) { this.exp += amount; }

    public int getSkillPoints() { return skillPoints; }
    public void setSkillPoints(int v) { this.skillPoints = Math.max(0, v); }
    public void addSkillPoints(int n) { this.skillPoints += n; }

    /** Returns true if skill points were deducted; false if not enough. */
    public boolean spendSkillPoints(int cost) {
        if (skillPoints < cost) return false;
        skillPoints -= cost;
        return true;
    }

    public FactionRole getRole(UUID uuid) { return roles.getOrDefault(uuid, FactionRole.MEMBER); }
    public void setRole(UUID uuid, FactionRole role) { roles.put(uuid, role); }
    public void removeRole(UUID uuid) { roles.remove(uuid); }
    public Map<UUID, FactionRole> getRoles() { return roles; }

    public int getDowngradeMul() { return downgradeMul; }
    public void setDowngradeMul(int mul) { this.downgradeMul = Math.max(1, mul); }
    public long getDowngradeWindowEndMs() { return downgradeWindowEndMs; }
    public void setDowngradeWindowEndMs(long ts) { this.downgradeWindowEndMs = ts; }

    public boolean isOutpostUnlocked()        { return outpostUnlocked; }
    public void setOutpostUnlocked(boolean b) { this.outpostUnlocked = b; }

    public Set<String> getAllies() { return allies; }
    public boolean hasAlly(String factionName) { return allies.contains(factionName); }
    public void addAlly(String factionName) { allies.add(factionName); }
    public void removeAlly(String factionName) { allies.remove(factionName); }
}
