package org.minecraft.atlas.faction;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.EnderCrystal;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Represents an atlas ender crystal entity that guards a faction.
 * HP is managed independently of vanilla ender crystal mechanics.
 */
public class AtlasCrystal {

    static final double BASE_MAX_HP = 50.0;

    private final EnderCrystal entity;
    private String factionName;
    private String name;
    private Location home;
    private double hp;
    private double maxHp;
    /** Checkpoint levels whose HP upgrade has been permanently applied to this crystal. */
    private final Set<Integer> appliedUpgrades = new HashSet<>();
    /** System.currentTimeMillis() after which immunity ends. 0 = not immune. */
    private long immuneUntilMillis = 0;
    /** System.currentTimeMillis() of the last hit by an outside player. 0 = never attacked. */
    private long lastAttackMillis = 0;

    public AtlasCrystal(EnderCrystal entity, String factionName, double hp, double maxHp) {
        this.entity = entity;
        this.factionName = factionName;
        this.name = "";
        this.home = null;
        this.hp = hp;
        this.maxHp = maxHp;
    }

    public EnderCrystal getEntity() { return entity; }
    public String getFactionName() { return factionName; }
    public void setFactionName(String factionName) { this.factionName = factionName; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Location getHome() { return home; }
    public void setHome(Location home) { this.home = home; }
    public double getHp() { return hp; }
    public double getMaxHp() { return maxHp; }

    public Set<Integer> getAppliedUpgrades() { return new HashSet<>(appliedUpgrades); }
    public long getImmuneUntilMillis() { return immuneUntilMillis; }
    public void setImmuneUntilMillis(long ts) { this.immuneUntilMillis = ts; }

    /** Directly sets HP (clamped to [0, maxHp]). Does NOT record an attack timestamp. */
    public void setHp(double hp) {
        this.hp = Math.clamp(hp, 0, maxHp);
        updateNametag();
    }

    /**
     * Used during PDC restore: records that an upgrade was previously applied without
     * re-adding the HP (maxHp is already the correct accumulated value restored from PDC).
     */
    void restoreUpgrade(int checkpointLevel) {
        appliedUpgrades.add(checkpointLevel);
    }

    /**
     * Applies a permanent HP upgrade from a checkpoint.
     * Does nothing if this checkpoint level was already applied.
     */
    public void addUpgrade(int checkpointLevel, double upgradeHp) {
        if (appliedUpgrades.add(checkpointLevel)) {
            maxHp += upgradeHp;
        }
    }

    /**
     * Strips HP upgrades from checkpoint levels above the given threshold.
     * Reduces maxHp accordingly and clamps hp if needed.
     */
    public void stripUpgradesAbove(int level, Map<Integer, Double> upgradeBonusMap) {
        java.util.Iterator<Integer> iter = appliedUpgrades.iterator();
        while (iter.hasNext()) {
            int cp = iter.next();
            if (cp > level) {
                maxHp -= upgradeBonusMap.getOrDefault(cp, 0.0);
                iter.remove();
            }
        }
        maxHp = Math.max(BASE_MAX_HP, maxHp);
        hp = Math.min(hp, maxHp);
    }

    /** Returns true if this crystal cannot be damaged right now. */
    public boolean isImmune() {
        return immuneUntilMillis > 0 && System.currentTimeMillis() < immuneUntilMillis;
    }

    /** Grants immunity for the specified number of milliseconds from now. */
    public void setImmuneFor(long durationMs) {
        this.immuneUntilMillis = System.currentTimeMillis() + durationMs;
    }

    /**
     * Reduces HP by the given amount.
     * Returns true if HP reached 0 (level-drop or disband should trigger).
     * Returns false without applying damage if the crystal is currently immune.
     */
    public boolean damage(double amount) {
        if (isImmune()) return false;
        hp = Math.max(0, hp - amount);
        lastAttackMillis = System.currentTimeMillis();
        updateNametag();
        return hp <= 0;
    }

    /** Regenerates HP by the given amount, capped at maxHp. */
    public void regen(double amount) {
        hp = Math.min(maxHp, hp + amount);
        updateNametag();
    }

    /** Returns true if enough time (configured via {@code crystal.regen_timeout_seconds}) has elapsed since the last outside-player attack. */
    public boolean canRegen() {
        if (lastAttackMillis == 0) return true;
        return System.currentTimeMillis() - lastAttackMillis >= AtlasCrystalManager.regenTimeoutMs;
    }

    /** Refreshes the entity's overhead nametag. */
    public void updateNametag() {
        if (entity.isDead()) return;
        Faction faction = FactionManager.getFaction(factionName);
        NamedTextColor color = faction != null ? faction.getColor() : NamedTextColor.WHITE;
        int level = faction != null ? faction.getLevel() : 0;

        Component immuneTag = isImmune()
                ? Component.text(" [IMMUNE]", NamedTextColor.AQUA)
                : Component.empty();

        Component tag;
        if (name != null && !name.isEmpty()) {
            tag = Component.text(name, NamedTextColor.WHITE)
                    .append(Component.text(" [", NamedTextColor.GRAY))
                    .append(Component.text(factionName, color))
                    .append(Component.text(" Lv.", NamedTextColor.GRAY))
                    .append(Component.text(String.valueOf(level), NamedTextColor.YELLOW))
                    .append(Component.text("] ", NamedTextColor.GRAY))
                    .append(Component.text((int) hp + "/" + (int) maxHp, NamedTextColor.RED))
                    .append(Component.text("♥", NamedTextColor.DARK_RED))
                    .append(immuneTag);
        } else {
            tag = Component.text(factionName, color)
                    .append(Component.text(" [Lv.", NamedTextColor.GRAY))
                    .append(Component.text(String.valueOf(level), NamedTextColor.YELLOW))
                    .append(Component.text("] ", NamedTextColor.GRAY))
                    .append(Component.text((int) hp + "/" + (int) maxHp, NamedTextColor.RED))
                    .append(Component.text("♥", NamedTextColor.DARK_RED))
                    .append(immuneTag);
        }

        entity.customName(tag);
        entity.setCustomNameVisible(true);
    }
}
