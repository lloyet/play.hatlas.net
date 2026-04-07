package org.minecraft.atlas.faction;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.*;

public class FactionManager {

    // factionName -> Faction
    private static final Map<String, Faction> factions = new HashMap<>();
    // playerUUID -> factionName (quick reverse lookup)
    private static final Map<UUID, String> playerFaction = new HashMap<>();
    // invitedUUID -> factionName (pending invitations)
    private static final Map<UUID, String> pendingInvitations = new HashMap<>();

    /** Creates a new faction. Returns false if the name is taken or the owner is already in a faction. */
    public static boolean createFaction(String factionName, UUID ownerUUID) {
        if (factions.containsKey(factionName)) return false;
        if (playerFaction.containsKey(ownerUUID)) return false;

        factions.put(factionName, new Faction(factionName, ownerUUID));
        playerFaction.put(ownerUUID, factionName);

        return true;
    }

    /** Deletes a faction. Only the owner can do this. */
    public static boolean deleteFaction(UUID requesterUUID) {
        String factionName = playerFaction.get(requesterUUID);

        if (factionName == null) return false;

        Faction faction = factions.get(factionName);

        if (!faction.getOwner().equals(requesterUUID)) return false;

        AtlasCrystalManager.removeAllForFaction(factionName);
        faction.getMembers().forEach(playerFaction::remove);
        playerFaction.remove(requesterUUID);
        factions.remove(factionName);

        return true;
    }

    /**
     * Renames a faction. Owner or Leaders can do this. Returns false if the new name is already taken.
     */
    public static boolean renameFaction(String newName, UUID requesterUUID) {
        String oldName = playerFaction.get(requesterUUID);

        if (oldName == null) return false;

        Faction faction = factions.get(oldName);

        boolean canRename = faction.getOwner().equals(requesterUUID)
                || faction.getRole(requesterUUID).isAtLeast(FactionRole.LEADER);
        if (!canRename) return false;
        if (factions.containsKey(newName)) return false;

        faction.setName(newName);
        factions.remove(oldName);
        factions.put(newName, faction);
        playerFaction.put(faction.getOwner(), newName);
        faction.getMembers().forEach(uuid -> playerFaction.put(uuid, newName));
        pendingInvitations.replaceAll((uuid, name) -> name.equals(oldName) ? newName : name);

        return true;
    }

    /** Sends an invitation. Owner, Leaders, and Moderators can invite. Returns false if target is already in a faction. */
    public static boolean invitePlayer(UUID inviterUUID, UUID targetUUID) {
        String factionName = playerFaction.get(inviterUUID);

        if (factionName == null) return false;

        Faction faction = factions.get(factionName);

        boolean canInvite = faction.getOwner().equals(inviterUUID)
                || faction.getRole(inviterUUID).isAtLeast(FactionRole.MODERATOR);
        if (!canInvite) return false;
        if (playerFaction.containsKey(targetUUID)) return false;

        pendingInvitations.put(targetUUID, factionName);

        return true;
    }

    /** Accepts a pending invitation. Returns the faction name joined, or null if no invitation exists. */
    public static String acceptInvitation(UUID playerUUID) {
        String factionName = pendingInvitations.remove(playerUUID);

        if (factionName == null) return null;

        Faction faction = factions.get(factionName);

        if (faction == null) return null;

        faction.addMember(playerUUID);
        playerFaction.put(playerUUID, factionName);

        return factionName;
    }

    /** Declines a pending invitation. Returns false if no invitation exists. */
    public static boolean declineInvitation(UUID playerUUID) {
        return pendingInvitations.remove(playerUUID) != null;
    }

    /** Lets a non-owner member leave their faction voluntarily. Returns false if not in a faction or is the owner. */
    public static boolean leaveFaction(UUID playerUUID) {
        String factionName = playerFaction.get(playerUUID);
        if (factionName == null) return false;

        Faction faction = factions.get(factionName);
        if (faction.getOwner().equals(playerUUID)) return false; // owner must delete, not leave

        faction.removeMember(playerUUID);
        faction.removeRole(playerUUID);
        playerFaction.remove(playerUUID);

        return true;
    }

    /** Kicks a member from the faction. Only the owner can do this. */
    public static boolean kickPlayer(UUID ownerUUID, UUID targetUUID) {
        String factionName = playerFaction.get(ownerUUID);

        if (factionName == null) return false;

        Faction faction = factions.get(factionName);

        if (!faction.getOwner().equals(ownerUUID)) return false;
        if (!faction.getMembers().contains(targetUUID)) return false;

        faction.removeMember(targetUUID);
        faction.removeRole(targetUUID);
        playerFaction.remove(targetUUID);

        return true;
    }

    /**
     * Sets the faction description. Owner or Leaders can do this.
     */
    public static boolean setFactionDescription(UUID requesterUUID, String description) {
        String factionName = playerFaction.get(requesterUUID);

        if (factionName == null) return false;

        Faction faction = factions.get(factionName);

        boolean canEdit = faction.getOwner().equals(requesterUUID)
                || faction.getRole(requesterUUID).isAtLeast(FactionRole.LEADER);
        if (!canEdit) return false;

        faction.setDescription(description);
        return true;
    }

    /**
     * Promotes a member to the next role (MEMBER → MODERATOR → LEADER). Only the owner can promote.
     */
    public static boolean promotePlayer(UUID ownerUUID, UUID targetUUID) {
        String factionName = playerFaction.get(ownerUUID);

        if (factionName == null) return false;

        Faction faction = factions.get(factionName);

        if (!faction.getOwner().equals(ownerUUID)) return false;
        if (!factionName.equals(playerFaction.get(targetUUID))) return false;
        if (faction.getOwner().equals(targetUUID)) return false;

        FactionRole current = faction.getRole(targetUUID);
        if (current == FactionRole.LEADER) return false; // already at highest role

        FactionRole[] values = FactionRole.values();
        faction.setRole(targetUUID, values[current.ordinal() + 1]);
        return true;
    }

    /**
     * Transfers ownership to a current member. The old owner becomes a Leader.
     * Only the current owner can do this, and the target must be in the members list.
     */
    public static boolean transferOwnership(UUID ownerUUID, UUID targetUUID) {
        String factionName = playerFaction.get(ownerUUID);

        if (factionName == null) return false;

        Faction faction = factions.get(factionName);

        if (!faction.getOwner().equals(ownerUUID)) return false;
        if (!faction.getMembers().contains(targetUUID)) return false;

        // Target leaves the members list and becomes the new owner (no role)
        faction.removeMember(targetUUID);
        faction.removeRole(targetUUID);

        // Old owner joins the members list as Leader
        faction.addMember(ownerUUID);
        faction.setRole(ownerUUID, FactionRole.LEADER);

        faction.setOwner(targetUUID);

        return true;
    }

    /**
     * Demotes a member to the previous role (LEADER → MODERATOR → MEMBER). Only the owner can demote.
     */
    public static boolean demotePlayer(UUID ownerUUID, UUID targetUUID) {
        String factionName = playerFaction.get(ownerUUID);

        if (factionName == null) return false;

        Faction faction = factions.get(factionName);

        if (!faction.getOwner().equals(ownerUUID)) return false;
        if (!factionName.equals(playerFaction.get(targetUUID))) return false;
        if (faction.getOwner().equals(targetUUID)) return false;

        FactionRole current = faction.getRole(targetUUID);
        if (current == FactionRole.MEMBER) return false; // already at lowest role

        FactionRole[] values = FactionRole.values();
        faction.setRole(targetUUID, values[current.ordinal() - 1]);
        return true;
    }

    /**
     * Returns the role of a player in their faction, or null if they are the owner or not in any faction.
     */
    public static FactionRole getPlayerRole(UUID playerUUID) {
        String factionName = playerFaction.get(playerUUID);
        if (factionName == null) return null;
        Faction faction = factions.get(factionName);
        if (faction.getOwner().equals(playerUUID)) return null;
        return faction.getRole(playerUUID);
    }

    /** Returns the faction name of a player, or null if not in any faction. */
    public static String getPlayerFaction(UUID playerUUID) {
        return playerFaction.get(playerUUID);
    }

    /** Returns all members of a faction (owner + members), or an empty list if the faction doesn't exist. */
    public static List<UUID> getFactionPlayers(String factionName) {
        Faction faction = factions.get(factionName);

        if (faction == null) return List.of();

        List<UUID> all = new ArrayList<>();
        all.add(faction.getOwner());
        all.addAll(faction.getMembers());

        return all;
    }

    public static boolean hasPendingInvitation(UUID playerUUID) {
        return pendingInvitations.containsKey(playerUUID);
    }

    public static String getPendingInvitationFaction(UUID playerUUID) {
        return pendingInvitations.get(playerUUID);
    }

    public static Map<String, Faction> getFactions() {
        return factions;
    }

    public static Faction getFaction(String factionName) {
        return factions.get(factionName);
    }

    /**
     * Sends a message to every online member of a faction, optionally excluding one player (pass null to include all).
     */
    public static void broadcastToFaction(String factionName, net.kyori.adventure.text.Component message, UUID exclude) {
        Faction faction = factions.get(factionName);
        if (faction == null) return;

        if (!faction.getOwner().equals(exclude)) {
            Player owner = Bukkit.getPlayer(faction.getOwner());
            if (owner != null) owner.sendMessage(message);
        }

        for (UUID memberUUID : faction.getMembers()) {
            if (memberUUID.equals(exclude)) continue;
            Player member = Bukkit.getPlayer(memberUUID);
            if (member != null) member.sendMessage(message);
        }
    }

    /**
     * Disbands a faction by name without requiring the owner UUID. Used when an atlas crystal is destroyed.
     */
    public static void disbandFaction(String factionName) {
        Faction faction = factions.get(factionName);
        if (faction == null) return;

        AtlasCrystalManager.removeAllForFaction(factionName);
        faction.getMembers().forEach(playerFaction::remove);
        playerFaction.remove(faction.getOwner());
        factions.remove(factionName);
    }

    /**
     * Changes the color of the faction. Owner or Leaders can do this.
     */
    public static boolean setFactionColor(UUID requesterUUID, NamedTextColor color) {
        String factionName = playerFaction.get(requesterUUID);

        if (factionName == null) return false;

        Faction faction = factions.get(factionName);

        boolean canChange = faction.getOwner().equals(requesterUUID)
                || faction.getRole(requesterUUID).isAtLeast(FactionRole.LEADER);
        if (!canChange) return false;

        faction.setColor(color);

        return true;
    }

    /** Serializes all factions into config.yml under the "factions" key. */
    public static void saveFactions(FileConfiguration config) {
        config.set("factions", null);
        ConfigurationSection factionsSection = config.createSection("factions");

        for (Faction faction : factions.values()) {
            ConfigurationSection s = factionsSection.createSection(faction.getName());
            s.set("owner", faction.getOwner().toString());
            s.set("members", faction.getMembers().stream().map(UUID::toString).toList());
            String colorName = NamedTextColor.NAMES.key(faction.getColor());
            s.set("color", colorName != null ? colorName : "white");
            s.set("description", faction.getDescription());

            s.set("level", faction.getLevel());

            Map<UUID, FactionRole> roles = faction.getRoles();
            if (!roles.isEmpty()) {
                ConfigurationSection rolesSection = s.createSection("roles");
                roles.forEach((uuid, role) -> rolesSection.set(uuid.toString(), role.name()));
            }
        }
    }

    /** Deserializes factions from config.yml and recreates all faction/player mappings. */
    public static void loadFactions(FileConfiguration config) {
        factions.clear();
        playerFaction.clear();

        ConfigurationSection factionsSection = config.getConfigurationSection("factions");
        if (factionsSection == null) return;

        for (String factionName : factionsSection.getKeys(false)) {
            ConfigurationSection s = factionsSection.getConfigurationSection(factionName);
            if (s == null) continue;

            String ownerStr = s.getString("owner");
            if (ownerStr == null) continue;

            UUID ownerUUID = UUID.fromString(ownerStr);
            Faction faction = new Faction(factionName, ownerUUID);
            playerFaction.put(ownerUUID, factionName);

            String colorName = s.getString("color", "white");
            NamedTextColor color = NamedTextColor.NAMES.value(colorName);
            faction.setColor(color != null ? color : NamedTextColor.WHITE);

            faction.setDescription(s.getString("description", ""));

            for (String memberStr : s.getStringList("members")) {
                UUID memberUUID = UUID.fromString(memberStr);
                faction.addMember(memberUUID);
                playerFaction.put(memberUUID, factionName);
            }

            faction.setLevel(s.getInt("level", 0));

            ConfigurationSection rolesSection = s.getConfigurationSection("roles");
            if (rolesSection != null) {
                for (String uuidStr : rolesSection.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        String roleName = rolesSection.getString(uuidStr, "MEMBER");
                        faction.setRole(uuid, FactionRole.valueOf(roleName));
                    } catch (IllegalArgumentException ignored) {}
                }
            }

            factions.put(factionName, faction);
        }
    }
}
