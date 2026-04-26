package org.minecraft.atlas.faction;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    /**
     * Upgrade levels reached but whose crystal HP bonus has not yet been applied
     * via /faction upgrade apply. Persisted across restarts.
     */
    private final List<Integer> pendingUpgrades = new ArrayList<>();

    /**
     * Virtual double-chest storage — index → 54-slot ItemStack array.
     * Persisted across restarts. Chests beyond getAvailableChests(level) are stored
     * but not accessible via the GUI until the faction re-levels.
     */
    private final Map<Integer, ItemStack[]> chestContents = new HashMap<>();

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

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getLevel() { return level; }

    public void setLevel(int level) {
        this.level = Math.clamp(level, 0, FactionLevelManager.MAX_LEVEL);
    }

    public int getExp() { return exp; }

    public void setExp(int exp) {
        this.exp = Math.max(0, exp);
    }

    public void addExp(int amount) {
        this.exp += amount;
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

    public List<Integer> getPendingUpgrades() {
        return pendingUpgrades;
    }

    public void addPendingUpgrade(int upgradeLevel) {
        pendingUpgrades.add(upgradeLevel);
    }

    /**
     * Removes the specific upgrade level from the pending list.
     * Returns true if it was present and removed.
     */
    public boolean removePendingUpgrade(int upgradeLevel) {
        return pendingUpgrades.remove(Integer.valueOf(upgradeLevel));
    }

    public boolean hasPendingUpgrade() {
        return !pendingUpgrades.isEmpty();
    }

    /** Returns the contents of the virtual chest at {@code index}, or an empty array if never written. */
    public ItemStack[] getChestContents(int index) {
        return chestContents.getOrDefault(index, new ItemStack[FactionLevelManager.getChestSize(index)]);
    }

    public void setChestContents(int index, ItemStack[] contents) {
        chestContents.put(index, contents);
    }

    /** Direct access to the raw map — used only for persistence. */
    public Map<Integer, ItemStack[]> getChestContentsMap() {
        return chestContents;
    }

    // ── Downgrade escalation ──────────────────────────────────────────────────

    /** Current immunity multiplier. Starts at 1 (first ever defeat), escalates on repeated defeats. */
    private int downgradeMul = 1;
    /** Epoch-ms timestamp of when the current escalation window expires. 0 = no window set. */
    private long downgradeWindowEndMs = 0L;

    public int getDowngradeMul() { return downgradeMul; }
    public void setDowngradeMul(int mul) { this.downgradeMul = Math.max(1, mul); }
    public long getDowngradeWindowEndMs() { return downgradeWindowEndMs; }
    public void setDowngradeWindowEndMs(long ts) { this.downgradeWindowEndMs = ts; }

    // ── Allies ────────────────────────────────────────────────────────────────

    private final Set<String> allies = new HashSet<>();

    public Set<String> getAllies() { return allies; }
    public boolean hasAlly(String factionName) { return allies.contains(factionName); }
    public void addAlly(String factionName) { allies.add(factionName); }
    public void removeAlly(String factionName) { allies.remove(factionName); }
}
