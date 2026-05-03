package org.minecraft.atlas.crystal;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.inventory.ItemStack;
import org.minecraft.atlas.faction.Faction;
import org.minecraft.atlas.faction.FactionManager;

import java.util.*;

/**
 * Represents an atlas ender crystal entity that guards a faction.
 * HP is managed independently of vanilla ender crystal mechanics.
 * Skill-point upgrades (hp, claims, chests, protection) are stored per-crystal.
 */
public class AtlasCrystal {

    static final double BASE_MAX_HP = 50.0;

    private final EnderCrystal entity;
    private String factionName;
    private String name;
    private Location home;
    private double hp;
    private double maxHp;
    /** System.currentTimeMillis() after which immunity ends. 0 = not immune. */
    private long immuneUntilMillis = 0;
    /** System.currentTimeMillis() of the last hit by an outside player. 0 = never attacked. */
    private long lastAttackMillis = 0;
    /** UUID of the TextDisplay entity used as this crystal's overhead nametag. */
    private UUID textDisplayUUID = null;

    // ── Skill-point-driven upgrades ───────────────────────────────────────────
    private double hpBonus            = 0;
    private int    claimCapacity      = 1;
    private final LinkedHashSet<String>  claimedChunks       = new LinkedHashSet<>();
    private final List<Integer>          purchasedChestSizes = new ArrayList<>();
    private final Map<Integer, ItemStack[]> chestContents    = new HashMap<>();
    private int  spentSkillPoints      = 0;
    private long purchasedProtectionMs = 0;
    /** Escalation multiplier for immunity duration — starts at 1, doubles on each defeat within the window. */
    private int  defeatMul             = 1;
    /** Epoch-ms timestamp when the current defeat window ends. 0 = no active window. */
    private long defeatWindowEndMs     = 0L;
    /** True when this crystal was placed as an outpost (not the faction's founding crystal). */
    private boolean outpost = false;

    public AtlasCrystal(EnderCrystal entity, String factionName, double hp, double maxHp) {
        this.entity      = entity;
        this.factionName = factionName;
        this.name        = "";
        this.home        = null;
        this.hp          = hp;
        this.maxHp       = maxHp;
    }

    // ── Basic getters/setters ─────────────────────────────────────────────────
    public boolean isOutpost()              { return outpost; }
    public void    setOutpost(boolean b)    { this.outpost = b; }
    public EnderCrystal getEntity()           { return entity; }
    public String getFactionName()            { return factionName; }
    public void setFactionName(String n)      { this.factionName = n; }
    public String getName()                   { return name; }
    public void setName(String n)             { this.name = n; }
    public Location getHome()                 { return home; }
    public void setHome(Location home)        { this.home = home; }
    public double getHp()                     { return hp; }
    public double getMaxHp()                  { return maxHp; }
    public long getImmuneUntilMillis()        { return immuneUntilMillis; }
    public void setImmuneUntilMillis(long ts) { this.immuneUntilMillis = ts; }
    public UUID getTextDisplayUUID()          { return textDisplayUUID; }
    public void setTextDisplayUUID(UUID id)   { this.textDisplayUUID = id; }

    // ── Skill upgrade fields ──────────────────────────────────────────────────
    public double getHpBonus()                            { return hpBonus; }
    public int    getClaimCapacity()                      { return claimCapacity; }
    public LinkedHashSet<String> getClaimedChunks()       { return claimedChunks; }
    public List<Integer> getPurchasedChestSizes()         { return purchasedChestSizes; }
    public Map<Integer, ItemStack[]> getChestContentsMap(){ return chestContents; }
    public int  getSpentSkillPoints()                     { return spentSkillPoints; }
    public long getPurchasedProtectionMs()                { return purchasedProtectionMs; }
    public int  getDefeatMul()                            { return defeatMul; }
    public void setDefeatMul(int mul)                     { this.defeatMul = Math.max(1, mul); }
    public long getDefeatWindowEndMs()                    { return defeatWindowEndMs; }
    public void setDefeatWindowEndMs(long ts)             { this.defeatWindowEndMs = ts; }

    public void addHpBonus(double bonus) {
        this.hpBonus += bonus;
        this.maxHp   += bonus;
    }
    public void addClaimCapacity(int amount) { this.claimCapacity = Math.max(1, claimCapacity + amount); }
    public void addPurchasedChest(int size)  { purchasedChestSizes.add(size); }
    public void addProtectionMs(long durationMs) { this.purchasedProtectionMs += durationMs; }
    public void addSpentSkillPoints(int cost)    { this.spentSkillPoints += cost; }

    // Restore-only setters — do NOT modify maxHp (it was already persisted correctly)
    void restoreHpBonus(double bonus)          { this.hpBonus = bonus; }
    void restoreClaimCapacity(int cap)         { this.claimCapacity = cap; }
    void restoreSpentSkillPoints(int sp)       { this.spentSkillPoints = sp; }
    void restoreProtectionMs(long ms)          { this.purchasedProtectionMs = ms; }
    void restoreDefeatMul(int mul)             { this.defeatMul = Math.max(1, mul); }
    void restoreDefeatWindowEndMs(long ts)     { this.defeatWindowEndMs = ts; }

    /** Returns the contents of crystal chest at {@code index}, or an empty array of the right size. */
    public ItemStack[] getChestContents(int index) {
        int size = index < purchasedChestSizes.size() ? purchasedChestSizes.get(index) : 27;
        return chestContents.getOrDefault(index, new ItemStack[size]);
    }
    public void setChestContents(int index, ItemStack[] contents) { chestContents.put(index, contents); }

    // ── HP mechanics ──────────────────────────────────────────────────────────

    /** Directly sets HP (clamped to [0, maxHp]). Does NOT record an attack timestamp. */
    public void setHp(double hp) {
        this.hp = Math.clamp(hp, 0, maxHp);
        updateNametag();
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
     * Returns true if HP reached 0.
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

    /** Returns true if enough time has elapsed since the last outside-player attack. */
    public boolean canRegen() {
        if (lastAttackMillis == 0) return true;
        return System.currentTimeMillis() - lastAttackMillis >= AtlasCrystalManager.regenTimeoutMs;
    }

    // ── Nametag ───────────────────────────────────────────────────────────────

    /** Builds the 3-line nametag Component without touching any entity. */
    Component buildNametagComponent() {
        Faction faction = FactionManager.getFaction(factionName);
        NamedTextColor color = faction != null ? faction.getColor() : NamedTextColor.WHITE;
        int level = faction != null ? faction.getLevel() : 0;

        Component line1;
        if (name != null && !name.isEmpty()) {
            line1 = Component.text(name + " ", NamedTextColor.WHITE)
                    .append(Component.text("[", NamedTextColor.GRAY))
                    .append(Component.text(factionName, color))
                    .append(Component.text("]", NamedTextColor.GRAY));
        } else {
            line1 = Component.text("[", NamedTextColor.GRAY)
                    .append(Component.text(factionName, color))
                    .append(Component.text("]", NamedTextColor.GRAY));
        }

        Component line2 = Component.text("LvL.", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(level), NamedTextColor.YELLOW));

        Component immuneTag = isImmune()
                ? Component.text(" [IMMUNE " + formatImmunityRemaining() + "]", NamedTextColor.AQUA)
                : Component.empty();
        Component line3 = Component.text((int) hp + "/" + (int) maxHp + " ♥", NamedTextColor.RED)
                .append(immuneTag);

        return line1.append(Component.newline())
                .append(line2).append(Component.newline())
                .append(line3);
    }

    private String formatImmunityRemaining() {
        long secs = Math.max(0, immuneUntilMillis - System.currentTimeMillis()) / 1000L;
        return secs >= 60 ? (secs / 60) + "m " + (secs % 60) + "s" : secs + "s";
    }

    /** Refreshes the overhead TextDisplay nametag. Creates it if it doesn't exist yet. */
    public void updateNametag() {
        if (entity.isDead()) return;
        AtlasCrystalManager.spawnOrUpdateNametagDisplay(this);
    }
}
