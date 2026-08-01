# Delvefold: Mining Worlds

Delvefold is a NeoForge 1.21.1 mod that creates a renewable, configurable mining dimension. Each save can be initialized as a **Flat**, **Cavern**, or **Wild** mining world, with ore generation controlled through an in-game GUI, commands, or canonical JSON.

> **1.0 compatibility:** Delvefold 1.0 stabilizes configuration schema 2 and public API version 1. Existing 0.2–0.4 schema-2 saves and exact-block ore targets remain compatible. Schema-1 saves remain in non-destructive read-only compatibility mode.

The same JAR supports singleplayer, LAN, and dedicated servers. Configuration remains server-authoritative even in singleplayer, and the integrated-world owner may administer Delvefold with cheats disabled.

## Core features

- Explicit initialization through `/delvefold gui` or `/delvefold initialize`; portal activation never chooses settings.
- Flat, roofed cavern, and overworld-shaped mining terrain, each with Classic and Expansive scale variants.
- Optional survey stations, ore motherlodes, and fault-line landmarks with Pure Mining, Balanced, and Abundant presets.
- A configurable world name, stable or rotating recreation layouts, and opt-in scheduled renewal with player warnings and mandatory backups.
- An onboarding advancement path for building, activating, and entering the mining world.
- A craftable **Seam Ledger** and `/delvefold guide` screen that publish server-authoritative ore outputs, best mining heights, relative frequency, terrain applicability, portal state, and renewal status without exposing administrative data.
- Vanilla-balanced, Rich, and Empty starting ore profiles.
- Named per-save ore profiles with safe duplication, selection, and JSON import/export.
- Visual height-distribution and generation-workload previews in the ore editor.
- Inventory-style block picker with real item icons, `c:ores` candidates, search, namespace filtering, and a Show All fallback.
- Three-page ore-rule wizard for stone/deepslate or other variants, weighted output selection, replacement hosts, block-state properties, biome include/exclude selectors, vein size, attempts per chunk, height distribution, terrain filters, and air-exposure discard.
- Modded ores selected by icon or registry ID without hard dependencies on their mods.
- Read-only ore profiles supplied by datapacks or startup scripts, including tag-driven outputs such as `c:ores/tin`.
- Native NeoForge permission nodes, public lifecycle events, and a stable versioned integration API.
- Optional JEI and EMI integration with a visual portal-construction guide, Flint and Steel catalyst, and no required recipe-viewer dependency.
- Safe, Hostile, and Normal gameplay presets with individual spawn-category toggles.
- Dedicated Delvefold creative tab containing the Portal Frame and Seam Ledger.
- Iron-tier framed portal ignited with vanilla Flint and Steel.
- Safe world deletion/recreation on restart, with a timestamped backup by default.
- In-game backup browser with pinning, confirmed deletion, and restart-safe restoration.
- Atomic per-save JSON, validation, stale-edit protection, and last-known-good runtime snapshots.

## Requirements

- Minecraft Java Edition 1.21.1
- NeoForge 21.1.x (built against 21.1.244)
- Java 21
- Delvefold installed on both the client and server

## First use

1. Start or open a world with Delvefold installed.
2. Run `/delvefold gui`.
3. Choose terrain shape and scale, ore, gameplay, and landmark presets.
4. Check the lock confirmation and click **Initialize**.
5. Build and activate a Delvefold portal.

Until step 4 is complete, portal activation changes no blocks and does not damage Flint and Steel. It displays an instruction to use the GUI or initialization command.

The console equivalent is:

```text
/delvefold initialize <flat|cavern|wild> <balanced|rich|empty> <safe|hostile|normal>
```

## Commands

All commands use the `/delvefold` root. The player guide is public by default. The integrated singleplayer owner can administer Delvefold even with cheats disabled. Dedicated-server configuration requires operator level 2; world deletion and recreation require level 4.

Setup, status, and JSON:

```text
/delvefold gui
/delvefold config
/delvefold initialize <flat|cavern|wild> <balanced|rich|empty> <safe|hostile|normal>
/delvefold status
/delvefold guide
/delvefold guide visibility
/delvefold guide visibility <public|operators|disabled>
/delvefold config validate
/delvefold config reload
/delvefold identity
/delvefold identity name <name>
/delvefold identity landmarks <pure_mining|balanced|abundant>
/delvefold identity variant <classic|expansive>
/delvefold renewal
/delvefold renewal configure <interval_days> <warning_minutes>
/delvefold renewal disable
/delvefold renewal seed-mode <stable|rotate_on_recreate>
```

For players, `/delvefold guide` opens the same read-only Seam Ledger screen as right-clicking the item. From a dedicated-server console it prints a bounded text summary instead. The visibility command requires configuration access; `public` allows all sources, `operators` allows the integrated owner, configure-authorized players, and the trusted server console, while `disabled` blocks every command, item, command-block, and console opening source.

Named profiles:

```text
/delvefold profile list
/delvefold profile create <id> <balanced|rich|empty> [overwrite]
/delvefold profile duplicate <source> <id> [overwrite]
/delvefold profile save-current <id> [overwrite]
/delvefold profile select <id>
/delvefold profile delete <id>
/delvefold profile import <file.json> <id> [overwrite]
/delvefold profile export <id> <file.json>
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
/delvefold ore target add <rule> <block_id> <replace_tag> [weight]
/delvefold ore target remove <rule> <block_id>
/delvefold ore target add-tag <rule> <block_tag> <replace_tag> [weight]
/delvefold ore target set-weight <rule> <block_id> <weight>
/delvefold ore target set-tag-weight <rule> <block_tag> <weight>
/delvefold ore target remove-tag <rule> <block_tag>
/delvefold ore band add <rule> <band_id> <common|uncommon|rare|very_rare>
/delvefold ore band remove <rule> <band_id>
/delvefold ore band set <rule> <band_id> <field> <value>
```

Band fields are `vein_size`, `attempts`, `min_y`, `max_y`, `peak_y`, `plateau_min_y`, `plateau_max_y`, and `discard`.

Ore targets may carry an optional relative `weight` from 1 through 1000 in the GUI or canonical schema-2 JSON. Omitted weights default to `1`; existing and all-1 profiles keep the earlier member-uniform deterministic per-vein selection sequence. Exact targets use their configured weight directly, while each output tag divides its total weight equally among installed members when a host group contains a non-default weight. Identical resolved states are deduplicated first-wins, and later overlaps are ignored with a warning.

Safe world deletion and recreation:

```text
/delvefold world recreate request
/delvefold world recreate request <flat|cavern|wild>
/delvefold world recreate request <flat|cavern|wild> <keep_backup|permanent>
/delvefold world recreate request <flat|cavern|wild> <classic|expansive> [keep_backup|permanent]
/delvefold world delete request
/delvefold world delete request <keep_backup|permanent>
/delvefold world confirm <token>
/delvefold world cancel
```

Backup management:

```text
/delvefold backup list
/delvefold backup pin <backup>
/delvefold backup unpin <backup>
/delvefold backup delete <backup> confirm
/delvefold backup restore request <backup>
/delvefold backup restore confirm <token>
/delvefold backup restore cancel
```

World operations use a short-lived confirmation token and retain a timestamped backup unless `permanent` is explicitly selected. See [Commands](docs/COMMANDS.md) for behavior and permission details.

Recreation layout defaults to `stable`, which reproduces the established ore and landmark layout. Administrators can select `rotate_on_recreate` through the GUI or renewal command; the selection applies only when the world is next initialized or recreated, and ordinary restarts never change an existing layout.

## Seam Ledger

Craft the Seam Ledger shapelessly from one Book, one Compass, and one Copper Ingot, or find it in the Delvefold creative tab. Right-clicking it opens a scrollable, read-only view of the active world and enabled ore profile. It shows representative ore icons, output IDs or tags, best height bands, vein sizes, relative frequency, terrain applicability, bounded biome include/exclude selectors, portal availability, and renewal timing.

The server constructs and authorizes every snapshot. Oversized profiles are safely truncated, and the snapshot never publishes world seeds, horizontal coordinates, filesystem paths, world-operation confirmation tokens, permissions, or administration diagnostics. Successfully obtaining the ledger has an advancement. Consultation is awarded only after the client installs a server-authorized screen, returns its short-lived single-use acknowledgement, and the server rechecks visibility.

## Portal recipe and activation

Portal frame (outputs four blocks):

```text
Iron Ingot          Polished Deepslate   Iron Ingot
Polished Deepslate  Obsidian             Polished Deepslate
Iron Ingot          Polished Deepslate   Iron Ingot
```

Build a complete rectangular frame, then right-click any Portal Frame block with vanilla Flint and Steel. A failed activation does not consume durability. The portal interior may be 2–21 blocks wide and 3–21 blocks tall. Delvefold portals have distinct reverse-fold particles, crystalline resonance, and activation effects.

## JSON locations

Each save owns its configuration:

```text
<save>/serverconfig/delvefold/ores.json
<save>/serverconfig/delvefold/settings.json
<save>/serverconfig/delvefold/profiles/*.json
<save>/serverconfig/delvefold/imports/*.json
<save>/serverconfig/delvefold/exports/*.json
```

Editing ore-generation JSON affects only chunks generated after a successful `/delvefold config reload`. Existing chunks are never silently retrogened. See [Configuration](docs/CONFIGURATION.md), [Commands](docs/COMMANDS.md), the [ore schema](schemas/ores.schema.json), and the [settings schema](schemas/settings.schema.json).

Modpack authors can provide namespaced, read-only profiles under `data/<namespace>/delvefold/ore_profiles/`, register profiles from startup scripts, and use NeoForge events and permission nodes. See [Integration](docs/INTEGRATION.md) and the [example datapack](examples/datapack).

For upgrades, supported combinations, and recovery behavior, see [Compatibility](docs/COMPATIBILITY.md). For common startup, portal, JSON, and reset problems, see [Troubleshooting](docs/TROUBLESHOOTING.md).

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

The release JAR is written to `build/libs/delvefold-1.21.1-1.0.1.jar`. Pull requests run a clean Java 21 build, unit tests, NeoForge GameTests, JSON validation, and translation-key validation. Version tags publish the JAR and SHA-256 checksum automatically.

Contributions are welcome; see [CONTRIBUTING.md](CONTRIBUTING.md). Security reports should follow [SECURITY.md](SECURITY.md).

License: MIT.
