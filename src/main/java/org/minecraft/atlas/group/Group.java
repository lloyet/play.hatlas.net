package org.minecraft.atlas.group;

import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Group {

    private String name;
    private final UUID owner;
    private final List<UUID> members = new ArrayList<>();
    private NamedTextColor color = NamedTextColor.WHITE;

    public Group(String name, UUID owner) {
        this.name = name;
        this.owner = owner;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public UUID getOwner() { return owner; }
    public List<UUID> getMembers() { return members; }
    public void addMember(UUID uuid) { members.add(uuid); }
    public void removeMember(UUID uuid) { members.remove(uuid); }
    public NamedTextColor getColor() { return color; }
    public void setColor(NamedTextColor color) { this.color = color; }
}
