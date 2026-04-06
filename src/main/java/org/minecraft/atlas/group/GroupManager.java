package org.minecraft.atlas.group;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.*;

public class GroupManager {

    // groupName -> Group
    private static final Map<String, Group> groups = new HashMap<>();
    // playerUUID -> groupName (quick reverse lookup)
    private static final Map<UUID, String> playerGroup = new HashMap<>();
    // invitedUUID -> groupName (pending invitations)
    private static final Map<UUID, String> pendingInvitations = new HashMap<>();

    /** Creates a new group. Returns false if the name is taken or the owner is already in a group. */
    public static boolean createGroup(String groupName, UUID ownerUUID) {
        if (groups.containsKey(groupName)) return false;
        if (playerGroup.containsKey(ownerUUID)) return false;

        groups.put(groupName, new Group(groupName, ownerUUID));
        playerGroup.put(ownerUUID, groupName);

        return true;
    }

    /** Deletes a group. Only the owner can do this. */
    public static boolean deleteGroup(UUID requesterUUID) {
        String groupName = playerGroup.get(requesterUUID);

        if (groupName == null) return false;

        Group group = groups.get(groupName);

        if (!group.getOwner().equals(requesterUUID)) return false;

        group.getMembers().forEach(playerGroup::remove);
        playerGroup.remove(requesterUUID);
        groups.remove(groupName);

        return true;
    }

    /** Renames a group. Only the owner can do this. Returns false if the new name is already taken. */
    public static boolean renameGroup(String newName, UUID requesterUUID) {
        String oldName = playerGroup.get(requesterUUID);

        if (oldName == null) return false;

        Group group = groups.get(oldName);

        if (!group.getOwner().equals(requesterUUID)) return false;
        if (groups.containsKey(newName)) return false;

        group.setName(newName);
        groups.remove(oldName);
        groups.put(newName, group);
        playerGroup.put(group.getOwner(), newName);
        group.getMembers().forEach(uuid -> playerGroup.put(uuid, newName));
        pendingInvitations.replaceAll((uuid, name) -> name.equals(oldName) ? newName : name);

        return true;
    }

    /** Sends an invitation from the owner to a target. Returns false if the inviter is not an owner or target is already grouped. */
    public static boolean invitePlayer(UUID inviterUUID, UUID targetUUID) {
        String groupName = playerGroup.get(inviterUUID);

        if (groupName == null) return false;

        Group group = groups.get(groupName);

        if (!group.getOwner().equals(inviterUUID)) return false;
        if (playerGroup.containsKey(targetUUID)) return false;

        pendingInvitations.put(targetUUID, groupName);

        return true;
    }

    /** Accepts a pending invitation. Returns the group name joined, or null if no invitation exists. */
    public static String acceptInvitation(UUID playerUUID) {
        String groupName = pendingInvitations.remove(playerUUID);

        if (groupName == null) return null;

        Group group = groups.get(groupName);

        if (group == null) return null;

        group.addMember(playerUUID);
        playerGroup.put(playerUUID, groupName);

        return groupName;
    }

    /** Declines a pending invitation. Returns false if no invitation exists. */
    public static boolean declineInvitation(UUID playerUUID) {
        return pendingInvitations.remove(playerUUID) != null;
    }

    /** Lets a non-owner member leave their group voluntarily. Returns false if not in a group or is the owner. */
    public static boolean leaveGroup(UUID playerUUID) {
        String groupName = playerGroup.get(playerUUID);
        if (groupName == null) return false;

        Group group = groups.get(groupName);
        if (group.getOwner().equals(playerUUID)) return false; // owner must delete, not leave

        group.removeMember(playerUUID);
        playerGroup.remove(playerUUID);

        return true;
    }

    /** Kicks a member from the owner's group. */
    public static boolean kickPlayer(UUID ownerUUID, UUID targetUUID) {
        String groupName = playerGroup.get(ownerUUID);

        if (groupName == null) return false;

        Group group = groups.get(groupName);

        if (!group.getOwner().equals(ownerUUID)) return false;
        if (!group.getMembers().contains(targetUUID)) return false;

        group.removeMember(targetUUID);
        playerGroup.remove(targetUUID);

        return true;
    }

    /** Returns the group name of a player, or null if not in any group. */
    public static String getPlayerGroup(UUID playerUUID) {
        return playerGroup.get(playerUUID);
    }

    /** Returns all members of a group (owner + members), or an empty list if the group doesn't exist. */
    public static List<UUID> getGroupPlayers(String groupName) {
        Group group = groups.get(groupName);

        if (group == null) return List.of();

        List<UUID> all = new ArrayList<>();
        all.add(group.getOwner());
        all.addAll(group.getMembers());

        return all;
    }

    public static boolean hasPendingInvitation(UUID playerUUID) {
        return pendingInvitations.containsKey(playerUUID);
    }

    public static String getPendingInvitationGroup(UUID playerUUID) {
        return pendingInvitations.get(playerUUID);
    }

    public static Map<String, Group> getGroups() {
        return groups;
    }

    public static Group getGroup(String groupName) {
        return groups.get(groupName);
    }

    /** Sends a message to every online member of a group, optionally excluding one player. */
    public static void broadcastToGroup(String groupName, net.kyori.adventure.text.Component message, UUID exclude) {
        Group group = groups.get(groupName);
        if (group == null) return;

        if (!group.getOwner().equals(exclude)) {
            Player owner = Bukkit.getPlayer(group.getOwner());
            if (owner != null) owner.sendMessage(message);
        }

        for (UUID memberUUID : group.getMembers()) {
            if (memberUUID.equals(exclude)) continue;
            Player member = Bukkit.getPlayer(memberUUID);
            if (member != null) member.sendMessage(message);
        }
    }

    /** Changes the color of the caller's group. Only the owner can do this. */
    public static boolean setGroupColor(UUID ownerUUID, NamedTextColor color) {
        String groupName = playerGroup.get(ownerUUID);

        if (groupName == null) return false;

        Group group = groups.get(groupName);

        if (!group.getOwner().equals(ownerUUID)) return false;

        group.setColor(color);

        return true;
    }

    /** Serializes all groups into config.yml under the "groups" key. */
    public static void saveGroups(FileConfiguration config) {
        config.set("groups", null);
        ConfigurationSection groupsSection = config.createSection("groups");

        for (Group group : groups.values()) {
            ConfigurationSection s = groupsSection.createSection(group.getName());
            s.set("owner", group.getOwner().toString());
            s.set("members", group.getMembers().stream().map(UUID::toString).toList());
            String colorName = NamedTextColor.NAMES.key(group.getColor());
            s.set("color", colorName != null ? colorName : "white");
        }
    }

    /** Deserializes groups from config.yml and recreates all group/player mappings. */
    public static void loadGroups(FileConfiguration config) {
        groups.clear();
        playerGroup.clear();

        ConfigurationSection groupsSection = config.getConfigurationSection("groups");
        if (groupsSection == null) return;

        for (String groupName : groupsSection.getKeys(false)) {
            ConfigurationSection s = groupsSection.getConfigurationSection(groupName);
            if (s == null) continue;

            String ownerStr = s.getString("owner");
            if (ownerStr == null) continue;

            UUID ownerUUID = UUID.fromString(ownerStr);
            Group group = new Group(groupName, ownerUUID);
            playerGroup.put(ownerUUID, groupName);

            String colorName = s.getString("color", "white");
            NamedTextColor color = NamedTextColor.NAMES.value(colorName);
            group.setColor(color != null ? color : NamedTextColor.WHITE);

            for (String memberStr : s.getStringList("members")) {
                UUID memberUUID = UUID.fromString(memberStr);
                group.addMember(memberUUID);
                playerGroup.put(memberUUID, groupName);
            }

            groups.put(groupName, group);
        }
    }
}
