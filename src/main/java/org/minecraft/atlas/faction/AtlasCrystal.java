package org.minecraft.atlas.faction;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.EnderCrystal;

/**
 * Represents the atlas ender crystal entity that guards a faction.
 * HP is managed independently of vanilla ender crystal mechanics.
 */
public class AtlasCrystal {

    private final EnderCrystal entity;
    private final String factionName;
    private double hp;
    private final double maxHp;
    /**
     * System.currentTimeMillis() of the last hit by an outside player. 0 = never attacked.
     */
    private long lastAttackMillis;

    public AtlasCrystal(EnderCrystal entity, String factionName, double hp, double maxHp) {
        this.entity = entity;
        this.factionName = factionName;
        this.hp = hp;
        this.maxHp = maxHp;
        this.lastAttackMillis = 0;
    }

    public EnderCrystal getEntity() {
        return entity;
    }

    public String getFactionName() {
        return factionName;
    }

    public double getHp() {
        return hp;
    }

    public double getMaxHp() {
        return maxHp;
    }

    /**
     * Reduces HP by the given amount. Returns true if HP reached 0 (crystal dies).
     */
    public boolean damage(double amount) {
        hp = Math.max(0, hp - amount);
        lastAttackMillis = System.currentTimeMillis();
        updateNametag();
        return hp <= 0;
    }

    /**
     * Regenerates HP by the given amount, capped at maxHp.
     */
    public void regen(double amount) {
        hp = Math.min(maxHp, hp + amount);
        updateNametag();
    }

    /**
     * Returns true if 60 seconds have elapsed since the last outside-player attack (regen may start).
     */
    public boolean canRegen() {
        if (lastAttackMillis == 0) return true;
        return System.currentTimeMillis() - lastAttackMillis >= 60_000;
    }

    /**
     * Refreshes the entity's nametag with current faction name, level, and HP.
     */
    public void updateNametag() {
        if (entity.isDead()) return;
        Faction faction = FactionManager.getFaction(factionName);
        NamedTextColor color = faction != null ? faction.getColor() : NamedTextColor.WHITE;
        int level = faction != null ? faction.getLevel() : 0;

        entity.customName(
                Component.text(factionName, color)
                        .append(Component.text(" [Lv.", NamedTextColor.GRAY))
                        .append(Component.text(String.valueOf(level), NamedTextColor.YELLOW))
                        .append(Component.text("] ", NamedTextColor.GRAY))
                        .append(Component.text((int) hp + "/" + (int) maxHp, NamedTextColor.RED))
                        .append(Component.text("♥", NamedTextColor.DARK_RED))
        );
        entity.setCustomNameVisible(true);
    }
}
