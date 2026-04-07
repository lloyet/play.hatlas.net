package org.minecraft.atlas.faction;

import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Faction {

    private String name;
    private UUID owner;
    private final List<UUID> members = new ArrayList<>();
    private NamedTextColor color = NamedTextColor.WHITE;
    private String description = "";
    private final Map<UUID, FactionRole> roles = new HashMap<>();
    private int level = 0;
    private int exp = 0;

    public Faction(String name, UUID owner) {
        this.name = name;
        this.owner = owner;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public UUID getOwner() { return owner; }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }
    public List<UUID> getMembers() { return members; }
    public void addMember(UUID uuid) { members.add(uuid); }
    public void removeMember(UUID uuid) { members.remove(uuid); }
    public NamedTextColor getColor() { return color; }
    public void setColor(NamedTextColor color) { this.color = color; }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getExp() {
        return exp;
    }

    public void setExp(int exp) {
        this.exp = exp;
    }

    public void addExp(int exp) {
        this.exp += exp;
    }

    public FactionRole getRole(UUID uuid) {
        return roles.getOrDefault(uuid, FactionRole.MEMBER);
    }

    public void setRole(UUID uuid, FactionRole role) {
        roles.put(uuid, role);
    }

    public void removeRole(UUID uuid) {
        roles.remove(uuid);
    }

    public Map<UUID, FactionRole> getRoles() {
        return roles;
    }
}
