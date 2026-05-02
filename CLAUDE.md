# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

`play.mc.atlas` is a PaperMC 1.21.11 plugin (Java 21) implementing a complete multiplayer progression system: factions, jobs, dungeons, trading, teleportation, and in-world GUIs. Group ID: `org.minecraft`, artifact: `atlas`.

## Build & Run Commands

```bash
# Compile and package the plugin JAR
./gradlew build

# Run a local Paper test server (auto-reloads plugin)
./gradlew runServer

# Build without running tests
./gradlew assemble
```

No formal test suite exists — verification is done manually via `runServer`.

## Architecture

### Entry Point & Initialization

`Atlas.java` (singleton via `Atlas.instance`) orchestrates startup in `onEnable()`:
1. Loads 4 YAML configs from `getDataFolder()`: `factions.yml`, `jobs.yml`, `donjons.yml`, `tags.yml`
2. Instantiates and initializes all managers
3. Registers 9 event listeners and 13 Brigadier commands (via `LifecycleEvents.COMMANDS`)
4. Schedules recurring background tasks (AFK, item clearing, crystal upkeep, dungeon ticks)

All configs are saved back in `onDisable()`. There is **no database** — persistence is YAML-only.

### Manager Pattern

All domain logic lives in static manager classes with in-memory caches:

| Manager | Key state | Persistence file |
|---|---|---|
| `FactionManager` | `factions`, `playerFaction`, `pendingInvitations`, `pendingAllyRequests` | `factions.yml` |
| `JobManager` | `playerJobs`, `taskTemplates`, `jobCooldowns` | `jobs.yml` |
| `DonjonManager` | `donjons`, `entityToDonjonId`, `playerVisitedDonjons` | `donjons.yml` |
| `AtlasCrystalManager` | Crystal block tracking | `factions.yml` |

Managers expose static methods: `FactionManager.getFaction(name)`, `JobManager.getJobData(uuid)`, etc. They are not dependency-injected — call them directly.

### Commands (Brigadier)

Commands use the modern Paper Brigadier API. Each command class has a static `build()` method returning `LiteralCommandNode<CommandSourceStack>`:

```java
public static LiteralCommandNode<CommandSourceStack> build() {
    return Commands.literal("faction")
        .requires(src -> src.getSender().hasPermission("atlas.faction.use"))
        .then(Commands.literal("create")
            .then(Commands.argument("name", StringArgumentType.word())
                .executes(ctx -> { /* handler */ })))
        .build();
}
```

All 13 commands are registered in `Atlas.onEnable()` via the lifecycle event registrar. Permission nodes are declared in `plugin.yml`.

### Events

9 listener classes implement Bukkit's `Listener`. They use `@EventHandler` annotations and are registered via `getServer().getPluginManager().registerEvents(...)`. Use `ConcurrentHashMap` / `ConcurrentHashSet` for any state shared across event threads.

### GUI System

`AtlasGui` is the base class for all inventory-based menus. GUIs are opened from commands or other GUIs. `GuiListener` routes `InventoryClickEvent` to the correct GUI handler. Paper's `Dialog` API is used for text-input dialogs (e.g., crystal naming, quest selection).

### Data Models

Key enums and records:
- `Job` — MINER, LUMBERJACK, HUNTER, FARMER
- `FactionRole` — MEMBER, MODERATOR, LEADER, OWNER
- `DonjonType` — TRIAL (only type currently active)
- `DonjonRarity` — COMMON, RARE, EPIC, LEGENDARY, MYSTIC, GODDESS (with configured weights)
- `DonjonStatus` — IDLE, ACTIVE, COOLDOWN

Entity metadata is stored via **Persistent Data Containers (PDC)** using `NamespacedKey` (e.g., `keyDonjonId`, `keyIsBoss`, `KEY_NPC_JOB`).

### Rich Text

All player-facing messages use the **Adventure Component API** (never legacy color codes). Helper methods `error()`, `success()`, `info()` in command classes return pre-styled `Component` objects.

### Dungeon Structures

7 pre-built NBT structure files live in `src/main/resources/structures/`. `DonjonManager` reads these at runtime to determine spawn points and totem anchor positions via an offset cache.

## Configuration Files

| File | Purpose |
|---|---|
| `config.yml` | Runtime tuning: cooldowns, AFK timeout, spawn protection radius |
| `factions.yml` | Persisted faction data (members, XP, claims, upgrades, chest contents) |
| `jobs.yml` | Persisted job data + 100+ task templates + Jokeyrini NPC location |
| `donjons.yml` | Persisted dungeon state + activation config |
| `tags.yml` | Persisted in-world TextDisplay tags |

## Key Conventions

- Static manager methods are the canonical way to access domain data — no passing manager instances around.
- Brigadier command trees are built in `build()` factory methods, not constructors.
- Use `NamedTextColor` + Adventure API for all text; avoid Bukkit's legacy `ChatColor`.
- PDC keys are declared as static fields on the relevant Manager or Listener class.
- YAML config sections are passed to manager `load()`/`save()` methods; managers own the serialization logic.
