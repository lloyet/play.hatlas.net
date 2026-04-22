# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Atlas is a **Minecraft Paper API plugin** written in **Java 21** using Gradle (Kotlin DSL). It adds faction, job, trade, and golem systems to a Paper 1.21.11 server.

## Commands

```bash
./gradlew build        # Compile and build the plugin JAR
./gradlew runServer    # Start a local Paper test server with the plugin loaded
```

There is no automated test suite — features are tested manually in-game via `./gradlew runServer`.

## Architecture

### Main Plugin Class
`Atlas.java` is the entry point. It handles `onEnable`/`onDisable`, loads/saves `config.yml`, registers all event listeners, registers all commands via Paper's lifecycle API, and starts scheduled tasks (e.g., golem targeting every 1 tick).

### Static Manager Pattern
All four subsystems expose a static manager API. Any class can call `FactionManager.getFaction(...)`, `JobManager.getJob(...)`, etc. without dependency injection.

### Subsystems

**Faction** (`faction/`) — Create/manage player factions with colors. Prevents friendly fire between members. Persisted to `config.yml`.

**Job** (`job/`) — Players hold one of 4 jobs (Miner, Lumberjack, Hunter, Farmer). `JobListener` grants XP on relevant events (block breaks, entity kills). XP required per level: `(level + 1) * 64`. State tracked in `PlayerJobData` and persisted to `config.yml`.

**Trade** (`trade/`) — Inventory-based peer-to-peer trading UI. Two 54-slot inventories share a mirrored layout; both players must accept before a 3-second XP-bar countdown commits the swap atomically.

**Golem** (`golem/`) — 6 custom Iron Golem variants (`GolemType` enum) with unique stats and combat effects. Golems are tagged via Bukkit's **Persistent Data Container (PDC)** to survive server restarts, including their Adventure component display names. They scan for targets every 1 second and only attack players outside their owning faction.

### Commands
Located in `command/`. Use Paper's Brigadier API with hierarchical subcommands and permission nodes defined in `plugin.yml`.

### Persistence
All data (factions, job assignments, golem state) is saved to `src/main/resources/config.yml` on `onDisable` and loaded on `onEnable`. There is no database.

### Key APIs in Use
- **Paper/Bukkit event API** for all gameplay hooks
- **Kyori Adventure** for all player-facing text (MiniMessage / Component)
- **PDC (PersistentDataContainer)** for tagging golem entities across restarts
- **Bukkit Scheduler** for repeating tasks (golem targeting loop)
