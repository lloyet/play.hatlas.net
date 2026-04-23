package org.minecraft.atlas.donjon;

import org.bukkit.Location;

import java.util.*;

public class Donjon {

    private final String id;
    private String name;
    private final DonjonType type;
    private final Location center;
    private int level;
    private DonjonRarity rarity;
    private DonjonStatus status;

    // Wave progression
    private List<DonjonWave> waves = new ArrayList<>();
    private int currentWaveIndex;
    private boolean inProgress;

    // Timing
    private long activationTime;
    private boolean timeoutWarned;

    // Faction tracking
    private String startingFaction;
    private final Map<String, Double> bossDamageMap = new HashMap<>();

    // Player presence (recalculated each tick, not persisted)
    private final Set<UUID> playersInside = new HashSet<>();

    // Auxiliary entities (e.g. chickens in chicken-jockeys) — tracked for cleanup only
    private final Set<UUID> auxiliaryEntities = new HashSet<>();

    // Protected chunk keys (computed from center + radius)
    private final Set<Long> protectedChunkKeys = new HashSet<>();

    // Wave spawn points — absolute locations of all RESPAWN_ANCHOR blocks in the placed structure
    private List<Location> spawnPoints = new ArrayList<>();

    // Nametag display — positioned above the VAULT block in the NBT structure
    private Location nametagLocation;
    private UUID textDisplayUUID;

    // Player teleport destination set via /donjon setspawn
    private Location teleportSpawn;

    public Donjon(String id, String name, DonjonType type, Location center, int level, DonjonRarity rarity) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.center = center.clone();
        this.level = level;
        this.rarity = rarity;
        this.status = DonjonStatus.IDLE;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public DonjonType getType() { return type; }
    public Location getCenter() { return center.clone(); }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public DonjonRarity getRarity() { return rarity; }
    public void setRarity(DonjonRarity rarity) { this.rarity = rarity; }
    public DonjonStatus getStatus() { return status; }
    public void setStatus(DonjonStatus status) { this.status = status; }

    public List<DonjonWave> getWaves() { return waves; }
    public void setWaves(List<DonjonWave> waves) { this.waves = waves; }
    public int getCurrentWaveIndex() { return currentWaveIndex; }
    public void setCurrentWaveIndex(int idx) { this.currentWaveIndex = idx; }
    public boolean isInProgress() { return inProgress; }
    public void setInProgress(boolean inProgress) { this.inProgress = inProgress; }

    public long getActivationTime() { return activationTime; }
    public void setActivationTime(long time) { this.activationTime = time; }
    public boolean isTimeoutWarned() { return timeoutWarned; }
    public void setTimeoutWarned(boolean warned) { this.timeoutWarned = warned; }

    public String getStartingFaction() { return startingFaction; }
    public void setStartingFaction(String faction) { this.startingFaction = faction; }
    public Map<String, Double> getBossDamageMap() { return bossDamageMap; }

    public Set<UUID> getPlayersInside() { return playersInside; }
    public Set<UUID> getAuxiliaryEntities() { return auxiliaryEntities; }
    public Set<Long> getProtectedChunkKeys() { return protectedChunkKeys; }

    public List<Location> getSpawnPoints() { return spawnPoints; }
    public void setSpawnPoints(List<Location> points) { this.spawnPoints = new ArrayList<>(points); }

    public Location getNametagLocation() { return nametagLocation != null ? nametagLocation.clone() : null; }
    public void setNametagLocation(Location loc) { this.nametagLocation = loc != null ? loc.clone() : null; }
    public UUID getTextDisplayUUID() { return textDisplayUUID; }
    public void setTextDisplayUUID(UUID uuid) { this.textDisplayUUID = uuid; }

    public Location getTeleportSpawn() { return teleportSpawn != null ? teleportSpawn.clone() : null; }
    public void setTeleportSpawn(Location loc) { this.teleportSpawn = loc != null ? loc.clone() : null; }

    public DonjonWave getCurrentWave() {
        if (waves.isEmpty() || currentWaveIndex >= waves.size()) return null;

        return waves.get(currentWaveIndex);
    }
}
