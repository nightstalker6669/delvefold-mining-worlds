# Delvefold: Mining Worlds

Delvefold is a NeoForge 1.21.1 mod that creates a renewable, configurable mining dimension. Each save can be initialized as a **Flat**, **Cavern**, or **Wild** mining world, with ore generation controlled through an in-game GUI, commands, or canonical JSON.

The same JAR supports singleplayer, LAN, and dedicated servers. Configuration remains server-authoritative even in singleplayer, and the integrated-world owner may administer Delvefold with cheats disabled.

## Core features

- Explicit initialization through `/delvefold gui` or `/delvefold initialize`; portal activation never chooses settings.
- Flat, roofed cavern, and overworld-shaped mining terrain.
- Vanilla-balanced, Rich, and Empty starting ore profiles.
- Inventory-style block picker with real item icons, `c:ores` candidates, search, namespace filtering, and a Show All fallback.
- Wizard for stone/deepslate or other variants, replacement hosts, vein size, attempts per chunk, height distribution, terrain filters, and air-exposure discard.
- Modded ores selected by icon or registry ID without hard dependencies on their mods.
- Safe, Hostile, and Normal gameplay presets with individual spawn-category toggles.
- Dedicated Delvefold creative tab with the Portal Frame and room for future content.
- Iron-tier framed portal ignited with vanilla Flint and Steel.
- Safe world deletion/recreation on restart, with a timestamped backup by default.
- Atomic per-save JSON, validation, stale-edit protection, and last-known-good runtime snapshots.

## Requirements

- Minecraft Java Edition 1.21.1
- NeoForge 21.1.x (built against 21.1.244)
- Java 21
- Delvefold installed on both the client and server

## First use

1. Start or open a world with Delvefold installed.
2. Run `/delvefold gui`.
3. Choose terrain, ore, and gameplay presets.
4. Check the lock confirmation and click **Initialize**.
5. Build and activate a Delvefold portal.

Until step 4 is complete, portal activation changes no blocks and does not damage Flint and Steel. It displays an instruction to use the GUI or initialization command.

The console equivalent is:

```text
/delvefold initialize <flat|cavern|wild> <balanced|rich|empty> <safe|hostile|normal>
```

## Commands

All commands use the `/delvefold` root. The integrated singleplayer owner can administer Delvefold even with cheats disabled. Dedicated-server configuration requires operator level 2; world deletion and recreation require level 4.

Setup, status, and JSON:

```text
/delvefold gui
/delvefold config
/delvefold initialize <flat|cavern|wild> <balanced|rich|empty> <safe|hostile|normal>
/delvefold status
/delvefold config validate
/delvefold config reload
```

Ore discovery and rules:

```text
/delvefold ore list
/delvefold ore show <rule>
/delvefold ore scan [namespace]
/delvefold ore add <block_id> <exact|detected> <common|uncommon|rare|very_rare>
/delvefold ore enable <rule>
/delvefold ore disable <rule>
/delvefold ore remove <rule>
```

Ore targets and spawn bands:

```text
/delvefold ore target add <rule> <block_id> <replace_tag>
/delvefold ore target remove <rule> <block_id>
/delvefold ore band add <rule> <band_id> <common|uncommon|rare|very_rare>
/delvefold ore band remove <rule> <band_id>
/delvefold ore band set <rule> <band_id> <field> <value>
```

Band fields are `vein_size`, `attempts`, `min_y`, `max_y`, `peak_y`, `plateau_min_y`, `plateau_max_y`, and `discard`.

Safe world deletion and recreation:

```text
/delvefold world recreate request
/delvefold world recreate request <flat|cavern|wild>
/delvefold world recreate request <flat|cavern|wild> <keep_backup|permanent>
/delvefold world delete request
/delvefold world delete request <keep_backup|permanent>
/delvefold world confirm <token>
/delvefold world cancel
```

World operations use a short-lived confirmation token and retain a timestamped backup unless `permanent` is explicitly selected. See [Commands](docs/COMMANDS.md) for behavior and permission details.

## Portal recipe and activation

Portal frame (outputs four blocks):

```text
Iron Ingot          Polished Deepslate   Iron Ingot
Polished Deepslate  Obsidian             Polished Deepslate
Iron Ingot          Polished Deepslate   Iron Ingot
```

Build a complete rectangular frame, then right-click any Portal Frame block with vanilla Flint and Steel. A failed activation does not consume durability. The portal interior may be 2–21 blocks wide and 3–21 blocks tall.

## JSON locations

Each save owns its configuration:

```text
<save>/serverconfig/delvefold/ores.json
<save>/serverconfig/delvefold/settings.json
```

Editing JSON affects only chunks generated after a successful `/delvefold config reload`. Existing chunks are never silently retrogened. See [Configuration](docs/CONFIGURATION.md), [Commands](docs/COMMANDS.md), and the [JSON Schema](schemas/ores.schema.json).

## Recreating or deleting the mining world

Use the **World Management** GUI tab or commands documented in [Commands](docs/COMMANDS.md). Delvefold never deletes a loaded dimension. A confirmed operation:

1. blocks new portal entry;
2. evacuates players to the Overworld;
3. waits for the server restart, or for the singleplayer owner to exit to title and reopen the save;
4. moves only validated Delvefold dimension folders;
5. commits the new generation epoch and settings;
6. retains a timestamped backup unless permanent deletion was explicitly selected.

Deleting the world returns Delvefold to the uninitialized state while retaining ore/settings JSON unless configuration reset was separately requested.

## Development

```bash
./gradlew build
./gradlew runClient
./gradlew runServer
```

The release JAR is written to `build/libs/delvefold-1.21.1-0.1.0.jar`.

License: MIT.
