# Atlas — Player Wiki

## Summary

| Section | Description |
|---|---|
| [Faction](#faction) | Create and manage a faction: levels, XP, upgrades, chests, claims, allies |
| [Job](#job) | Choose a profession, complete daily quests, earn faction XP and item rewards |
| [Dungeon](#dungeon) | Raid wave-based dungeons, fight a boss, and share loot between factions |
| [Basic Commands](#basic-commands) | Home, trade, teleport, and utility commands |

---

## Faction

### What is a Faction?

A faction is a persistent group of players that fight, progress, and earn rewards together. Every member's contributions (job quests, dungeon clears) flow into the faction's shared XP pool, which raises the faction's level and unlocks new perks.

### Levels and XP

Factions start at **level 0**. XP required to reach the next level follows a compounding formula:

```
XP to reach level 1  = 100
XP to reach level N  = ceil(XP(N-1) × 1.09)
```

The maximum faction level is **100**.

XP is earned by completing job quests (via the daily quest system) or clearing dungeons. It is added to the faction pool automatically — no player action required.

### Upgrades

At certain faction levels, an **upgrade** becomes available. Each upgrade grants two bonuses:

- **HP bonus** — increases the maximum HP of your faction's Atlas Crystals (Iron Golems)
- **Faction chest** — unlocks one or more additional virtual double-chests accessible by all members

When an upgrade threshold is reached, it appears as a **pending upgrade** in `/faction upgrade list`. An officer or owner must apply it with `/faction upgrade apply` to receive the HP bonus on the crystals.

> Chests unlocked by an upgrade are accessible immediately without applying; only the HP bonus requires the explicit apply step.

### Faction Chest

The faction chest is a virtual double-chest storage shared between all members. The number of available chests depends on how many upgrade thresholds have been reached. Use `/faction upgrade list` to see the current count.

Access the faction chest through the Atlas Crystal GUI (right-click on a faction crystal).

### Level Gap Protection and Downgrade

If a faction's level ever drops below an upgrade threshold (due to an admin action), chests beyond the new maximum are **automatically destroyed** — their contents are dropped on the ground at the crystal location.

### Claims and Outpost

Factions can claim territory using **outposts**. A claimed chunk prevents non-faction/non-ally players from building or breaking blocks inside it. Use `/faction outpost` to view and manage claims.

### Ally System

Two factions can become allies. Allied factions:
- Do **not** deal damage to each other (friendly fire is disabled)
- Do **not** attack each other's dungeon entities
- Can optionally share faction messaging

To form an alliance, both faction owners or leaders must agree:

```
/faction ally <faction>       — send an ally request
/faction allyaccept <faction> — accept a pending request
/faction allydeny  <faction>  — deny a pending request
/faction unally <faction>     — remove an existing alliance
```

### Faction Commands

| Command | Description | Permission |
|---|---|---|
| `/faction` | Show the help menu | `atlas.faction` |
| `/faction create <name>` | Create a new faction | `atlas.faction.create` |
| `/faction disband` | Disband your faction (owner only) | `atlas.faction.disband` |
| `/faction rename <name>` | Rename your faction | `atlas.faction.rename` |
| `/faction description <text>` | Set the faction description | `atlas.faction.description` |
| `/faction color <color>` | Change the faction chat color | `atlas.faction.color` |
| `/faction invite <player>` | Invite a player to your faction | `atlas.faction.invite` |
| `/faction accept` | Accept a faction invitation | `atlas.faction.accept` |
| `/faction decline` | Decline a faction invitation | `atlas.faction.decline` |
| `/faction leave` | Leave your current faction | `atlas.faction.leave` |
| `/faction kick <player>` | Kick a member from the faction | `atlas.faction.kick` |
| `/faction promote <player>` | Promote a member to the next role | `atlas.faction.promote` |
| `/faction demote <player>` | Demote a member to the previous role | `atlas.faction.demote` |
| `/faction transfer <player>` | Transfer faction ownership | `atlas.faction.transfer` |
| `/faction info [faction]` | View faction information | `atlas.faction.info` |
| `/faction list` | List all factions | `atlas.faction.list` |
| `/faction members` | List your faction's members and roles | `atlas.faction.members` |
| `/faction home` | Teleport to the faction home (set via atlas crystal) | `atlas.faction.home` |
| `/faction upgrade list` | Show available and pending upgrades | `atlas.faction.upgrade` |
| `/faction upgrade apply` | Apply a pending upgrade to your crystals | `atlas.faction.upgrade` |
| `/faction ally <faction>` | Send an ally request | — |
| `/faction allyaccept <faction>` | Accept an ally request | — |
| `/faction allydeny <faction>` | Deny an ally request | — |
| `/faction unally <faction>` | Remove an alliance | — |
| `/faction msg <text>` | Send a message to online faction members only | `atlas.faction.msg` |

### Member Roles

| Role | Permissions |
|---|---|
| **Member** | Basic access, no management |
| **Moderator** | Invite, kick members |
| **Leader** | All Moderator actions + rename, color, description |
| **Owner** | Full control, including disband and ownership transfer |

---

## Job

### What is a Job?

Each player can hold one of four jobs. Your job determines which daily quests you receive and how you contribute to your faction's XP. Job progress (level + XP) is personal; faction XP earned from quest rewards is shared.

### Available Jobs

| Job | Tool | Focus |
|---|---|---|
| **Miner** | Iron Pickaxe | Mining ores and stone |
| **Lumberjack** | Iron Axe | Chopping trees |
| **Hunter** | Bow | Killing mobs |
| **Farmer** | Wheat Seeds | Harvesting crops |

Assign or change your job by interacting with the corresponding job NPC in the world.

> Changing your job resets your current job XP and level and starts a **24-hour cooldown** before you can change again.

### Daily Quests

Each day, three quests are generated for you based on your job. You may select and activate **up to 2** of the three offered quests per day. Once you have selected 2, the daily slot is locked until the next day.

Each quest contains **1 or 2 tasks** drawn randomly from your job's task pool, scaled by a random difficulty tier:

| Difficulty | Name | Description |
|---|---|---|
| 1 | Easy | Small amounts, short time |
| 2 | Normal | Moderate amounts |
| 3 | Hard | Larger amounts |
| 4 | Hardcore | High amounts, tight time |

Progress is tracked automatically as you play. An action bar message shows your current progress per task in real time. When all tasks in a quest are complete, rewards are granted automatically.

### Faction XP Reward Formula

Completing a quest grants XP directly to your faction's pool:

```
Faction XP = baseReward × sumDifficulty × √(factionLevel + 1)
```

Where:
- `baseReward` = 10 (server default, configurable)
- `sumDifficulty` = sum of difficulty values across all tasks in the quest
- `factionLevel` = your faction's current level at the time of completion

Higher faction levels and harder quests multiply the reward significantly.

### Item Rewards

Each task can provide item rewards that scale with difficulty. Items are placed directly in your inventory; any overflow is dropped at your feet.

### Jokeyrini — The Special Quest NPC

**Jokeyrini** is a special NPC available separately from the job system. Once per day, Jokeyrini offers a unique quest with 2–3 tasks at **Hard** (difficulty 3) or **Hardcore** (difficulty 4) level. There is a **15% chance** the daily offer is a **Legendary Special Quest** — 3 tasks all at difficulty 5.

Upon completion, Jokeyrini rewards **Donjon Keys**, which are used to start a dungeon:

| Quest type | Keys rewarded |
|---|---|
| Hard (max difficulty 3) | 1 key |
| Hardcore (max difficulty 4) | 2 keys |
| Legendary Special | 3 keys |

Progress on Jokeyrini quests is tracked via the action bar with the `[Jokeyrini]` prefix.

### Job Commands

Interact directly with the job NPCs to access the job GUI. There are no chat commands for daily quest management — everything is done through the in-game interface.

---

## Dungeon

### What is a Dungeon?

Dungeons are pre-built structures placed in the world by an admin. Each dungeon cycles between **idle** and **active** states. When active, a player holding a **Donjon Key** can enter and start the dungeon. The dungeon then runs through a series of **waves** of enemies, ending with a **boss wave**.

### Dungeon Types

| Type | Theme |
|---|---|
| **Trial** | Classic dungeon |
| **Desert** | Desert ruins |
| **Nether Castle** | Nether fortress |
| **Plains** | Open plains |
| **Sky** | Sky island |
| **Ocean** | Underwater ruins |

Each type has its own set of mobs, boss types, and loot tables.

### Level

When a dungeon becomes active, it is assigned a random level from **0 to 99**. Level determines:
- Number of waves (scales from the type's min to max wave count)
- Number of mobs per wave
- Enemy HP and attack damage multipliers
- Faction XP reward amount

Higher levels = more mobs, tougher enemies, and more XP.

### Rarity

Each dungeon activation is also assigned a **rarity**, drawn from a weighted pool:

| Rarity | Color | EXP Multiplier | Weight |
|---|---|---|---|
| Common | White | ×1.0 | 66% |
| Rare | Aqua | ×1.5 | 14% |
| Epic | Light Purple | ×2.5 | 10% |
| Legendary | Gold | ×5.0 | 5% |
| Mystic | Red | ×10.0 | 3% |
| Goddess | Yellow | ×20.0 | 2% |

Rarity multiplies the total EXP distributed at the end of the dungeon.

### Waves

A dungeon runs through a series of waves. Each wave must be fully cleared before the next one begins. After clearing a wave you have **5 seconds** before the next wave starts.

The **final wave** is always a **Boss Wave**: fewer, far stronger enemies. A special sound and title announce the boss wave when it begins.

If all players leave the dungeon (no one is detected inside), the dungeon resets automatically — progress and enemy kills are lost.

### Starting a Dungeon

1. Obtain a **Donjon Key** (from Jokeyrini quests)
2. Find an **active** dungeon (announced server-wide when one activates)
3. Enter the dungeon structure with the key in hand — right-clicking the trial spawner will start the dungeon
4. Survive all waves and defeat the boss

### EXP Distribution

Total EXP is calculated as:

```
Total EXP = (baseExp + level/99 × (maxExp - baseExp)) × rarityMultiplier
```

At the end of the dungeon, EXP is distributed between all factions that dealt damage to the **boss**, proportional to how much damage each faction dealt:

```
Faction share = totalEXP × (faction's boss damage / total boss damage)
```

This means multiple factions can participate in the same dungeon and each receive a proportional share of the rewards. The faction that dealt the most boss damage is announced as the clearing faction.

### Ominous Trial Key Drop

When a dungeon of **Epic rarity or higher** is completed, there is a chance that an enchanted **Ominous Trial Key** drops at the dungeon center. This key can be used to **force-activate** a dungeon, guaranteeing a Legendary, Mystic, or Goddess rarity activation.

The drop chance scales with rarity: rarer dungeons have a higher chance.

### Ally System in Dungeons

Allied factions fighting in the same dungeon do **not** deal damage to each other. This allows allied factions to cooperate inside a dungeon without risking friendly fire. Alliance status is checked at the time of each attack.

---

## Basic Commands

### /home and /sethome

Set a named personal home and teleport back to it at any time.

```
/sethome <name>     — save your current location as a home
/home               — teleport to your first (oldest) home
/home <name>        — teleport to a specific named home
```

- Teleportation has a **5-second countdown**. Moving cancels the teleport.
- There is a **30-second cooldown** between teleports.
- Multiple named homes are supported.

### /trade

Initiate a peer-to-peer item trade with another player.

```
/trade <player>     — send a trade request to a player
```

Both players are shown a GUI where they can place items in their respective slots. Both players must click **Accept** before a 3-second countdown commits the swap. Either player can cancel at any time before the countdown completes.

### /tpa

Request to teleport to another player.

```
/tpa <player>       — send a teleport request
/tpa accept         — accept an incoming request (or click [Accept] in chat)
/tpa deny           — deny an incoming request (or click [Deny] in chat)
```

- Requests expire after **30 seconds** if not answered.
- Accepted teleports have a **10-second countdown**. Taking damage cancels the teleport.
- There is a **30-second cooldown** between requests.

### /spawn

Teleport to the world spawn point.

```
/spawn
```

- Starts a **10-second countdown**. Moving or taking damage cancels it.
- Has a **30-second cooldown**.
- Players within **64 blocks** of the spawn are protected from PvP.

### /rtp

Randomly teleport to a safe location within 1024 blocks of the world spawn.

```
/rtp
```

- Starts a **5-second countdown**. Moving cancels it.
- Has a **30-second cooldown**.
- The destination is always a safe solid-ground location with air above.
