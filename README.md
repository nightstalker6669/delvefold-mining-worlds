# Delvefold: Mining Worlds

Delvefold is a NeoForge 1.21.1 mod that creates a renewable, configurable mining dimension. Each save can be initialized as a **Flat**, **Cavern**, or **Wild** mining world, with ore generation controlled through an in-game GUI, commands, or canonical JSON.

> **1.x compatibility:** Delvefold keeps configuration schema 2 and public API version 1. Unified Ores in 1.4 is a server-authoritative discovery and editing workflow layered over the existing schema-2 target model; it does not rewrite or consolidate existing rules. Existing 0.2–1.3 schema-2 saves, exact-block targets, and block-tag targets remain compatible. Schema-1 saves remain in non-destructive read-only compatibility mode. Delvefold 1.4.1 uses network protocol 14 and requires the identical 1.4.1 JAR on each client and the server.

The same JAR supports singleplayer, LAN, and dedicated servers. Configuration remains server-authoritative even in singleplayer, and the integrated-world owner may administer Delvefold with cheats disabled.

## Core features

- Explicit initialization through `/delvefold gui` or `/delvefold initialize`; portal activation never chooses settings.
- Flat, roofed stone cavern, and overworld-shaped mining terrain, each with Classic and Expansive scale variants. Both Cavern scales begin as continuous stone hosts and use a compact, vertically bounded cave carver, avoiding giant terrain-density voids and detached floating shelves. Caverns have no global water table or grass surface, use sparse shallow water pockets, and retain natural lava only near the bottom; Expansive stays taller and deeper without using amplified terrain.
- Five recreation-locked geology themes—Classic, Volcanic, Dripstone, Lush, and Crystal—with bounded vanilla-block strata, decorations, fluids, particles, and ambience.
- A reloadable structure-system landmark catalog with six bundled discoveries: survey camp, collapsed mine entrance, lift station, geode vault, motherlode chamber, and fault-line grotto.
- Pure Mining, Balanced, and Abundant landmark density presets, plus individual category toggles and one-time landmark loot.
- A configurable world name, stable or rotating recreation layouts, and opt-in scheduled renewal with player warnings and mandatory backups.
- An onboarding advancement path for building, activating, and entering the mining world.
- A craftable **Seam Ledger** and `/delvefold guide` screen that publish the active terrain, scale, geology theme, ore outputs, best mining heights, relative frequency, terrain applicability, portal state, and renewal status without exposing administrative data.
- Vanilla-balanced, Rich, and Empty starting ore profiles.
- Named per-save ore profiles with safe duplication, selection, and JSON import/export.
- Visual height-distribution and generation-workload previews in the ore editor.
- A whole-profile forecast with per-terrain attempts/work, an active-height graph, and missing, shadowed, or ineffective-rule diagnostics.
- A Unified Ores library that groups equivalent copper, tin, silver, and other ores across installed providers. Conventional `c:ores/<material>` tags are authoritative; clear conventional ore names remain usable when a mod omits them, while only ambiguous identities or hosts require review.
- Server-side material search and paging, configured families hidden by default, persistent multi-selection, and atomic batch addition of up to 128 families. Minecraft is the default provider when present; otherwise the lexically first provider is selected.
- Inventory-style editing with real item icons. Open a family to enable or disable provider-specific stone, deepslate, netherrack, and end-stone variants, or use exact registry-ID entry when a block cannot be grouped safely.
- A guided profile importer that uses the same material families, previews the diff and workload, and creates a new inactive profile without overwriting anything.
- Three-page ore-rule wizard for recognized and custom host variants, weighted output selection, replacement hosts, block-state properties, biome include/exclude selectors, vein or regional-province placement, height distribution, terrain filters, and air-exposure discard.
- Modded ores selected by icon or registry ID without hard dependencies on their mods.
- Read-only ore profiles supplied by datapacks or startup scripts, including tag-driven outputs such as `c:ores/tin`.
- Native NeoForge permission nodes, public lifecycle events, and a stable versioned integration API.
- Optional JEI and EMI integration with a visual portal-construction guide, Flint and Steel catalyst, and no required recipe-viewer dependency.
- Safe, Hostile, and Normal gameplay presets with individual spawn-category toggles.
- Dedicated Delvefold creative tab containing the Portal Frame and Seam Ledger.
- Iron-tier framed portal ignited with vanilla Flint and Steel.
- Safe world deletion/recreation on restart, with a timestamped backup by default.
- In-game backup browser with pinning, background SHA-256 verification, confirmed deletion, and restart-safe restoration gated by verified manifests.
- Optional automatic backup retention by count, age, and total size, with pinned, pending, and newest-two safeguards.
- A redacted Doctor report, exportable diagnostics, and a rotating JSON-lines audit log for server operations.
- Coordinate-linked or central-hub portal routing, including a protected vanilla-block hub and guaranteed return portal.
- Atomic per-save JSON, validation, stale-edit protection, and last-known-good runtime snapshots.

## Requirements

- Minecraft Java Edition 1.21.1
- NeoForge 21.1.244 or newer for Minecraft 1.21.1
- Java 21
- Delvefold installed on both the client and server

## First use

1. Start or open a world with Delvefold installed.
2. Run `/delvefold gui`.
3. Choose terrain shape, scale, geology theme, ore, gameplay, and landmark presets.
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
/delvefold doctor
/delvefold doctor export
/delvefold identity
/delvefold identity name <name>
/delvefold identity landmarks <pure_mining|balanced|abundant>
/delvefold identity variant <classic|expansive>
/delvefold identity geology-theme <classic|volcanic|dripstone|lush|crystal>
/delvefold renewal
/delvefold renewal configure <interval_days> <warning_minutes>
/delvefold renewal disable
/delvefold renewal seed-mode <stable|rotate_on_recreate>
/delvefold portal
/delvefold portal routing <coordinate_linked|central_hub>
/delvefold portal hub <x> <z> <protection_radius>
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
/delvefold ore band placement <rule> <band_id> <vein|province>
/delvefold ore band province <rule> <band_id> <region_size|radius|vertical_thickness|density|work_cap> <value>
```

Fields supported by `ore band set` are `vein_size`, `attempts`, `min_y`, `max_y`, `peak_y`, `plateau_min_y`, `plateau_max_y`, and `discard`. The GUI, dedicated placement/province commands, and canonical JSON can switch a band between `vein` and `province` and configure its bounded regional controls.

Ore targets may carry an optional relative `weight` from 1 through 1000 in the GUI or canonical schema-2 JSON. Omitted weights default to `1`; existing and all-1 profiles keep the earlier member-uniform deterministic per-vein selection sequence. Exact targets use their configured weight directly, while each output tag divides its total weight equally among installed members when a host group contains a non-default weight. Identical resolved states are deduplicated first-wins, and later overlaps are ignored with a warning.

## Surveying and modded-ore import

Open the **Ores** dashboard tab and choose **Forecast** to inspect the complete active or named profile before generating new chunks. The server calculates configured and effective attempts and work units for Flat, Cavern, and Wild terrain, plots the active terrain's height overlay, and marks disabled, biome-filtered, terrain-mismatched, missing, invalid, and shadowed rules. Pages and diagnostic details are bounded; forecasts never contain the world seed, coordinates, filesystem paths, or lifecycle confirmation data.

Choose **Add ores** to open the Unified Ores library. It groups installed blocks by logical material across provider mods—every tagged Copper variant appears under Copper—using `c:ores/<material>` as authoritative metadata. Clear conventional ore names remain a safe fallback when a mod omits that tag; conflicting material tags, aggregate blocks, and unknown replacement hosts are marked for review. Search and paging happen on the server. Configured families are hidden by default, selections persist across pages, and up to 128 families can be added in one atomic profile mutation.

A new family enables safe host variants from Minecraft when available, otherwise from the lexically first provider. Tagged `nether_<material>_ore` and `end_<material>_ore` variants are mapped automatically to `#c:netherracks` and `#c:end_stones`; every detected replacement tag remains editable. Other providers remain visible but off by default so duplicate copper, tin, or similar output is intentional rather than automatic. Open the resulting rule to toggle provider variants, up to the existing 16-target limit. Exact registry-ID entry and `/delvefold ore add` remain available for unusual blocks that should not share a family. Existing exact/tag rules are never auto-consolidated.

Open the **Profiles** tab and choose **Detect ores…** to use the same family discovery for a new profile. Select groups by their real block icons, preview the exact rule diff and added workload for every terrain, then provide a new profile ID. Suggested rules use the Uncommon template. Creation is strict: it neither overwrites an existing profile nor activates the result, so an administrator must explicitly select it afterward.

Discovery and planning are server-authoritative in singleplayer and multiplayer. Scan/preview capabilities expire, are bound to the requesting player and current profile/registry state, and cannot be replayed to overwrite a profile. If installed mods or the active profile change, start a fresh scan.

## Living geology

Geology themes are chosen during initialization or a confirmed recreation. `classic` preserves the established terrain composition; `volcanic`, `dripstone`, `lush`, and `crystal` add deterministic, per-chunk-bounded vanilla-block strata, decorations, sealed fluid pockets, particles, sounds, and ambience without changing Delvefold's registered dimension IDs or replacing its terrain generators. Existing schema-2 worlds that omit `identity.geology_theme` load as `classic` and are not rewritten merely by loading.

Ore bands may use the compatibility-default `vein` placement or the optional `province` placement. Provinces derive regional centers deterministically across chunk borders, but generate only inside the chunk currently being built. Region size, radius, vertical thickness, density, and a hard per-chunk work cap are editable in the ore-rule GUI, dedicated commands, or canonical JSON and participate in the existing workload safety budget.

Landmarks are now structure-system templates selected from `data/<namespace>/delvefold/landmarks/*.json`. Bundled definitions cover the six landmarks listed above. Pure Mining accepts no candidates, Balanced deterministically accepts 25%, and Abundant accepts 75%; the existing survey-station, motherlode, and fault-line toggles narrow those candidates further. Invalid datapack reloads retain the complete last-known-good catalog and report the rejected definitions in diagnostics. Entering a landmark awards **Signs in the Stone** and posts the additive API-v1 discovery event.

Safe world deletion and recreation:

```text
/delvefold world recreate request
/delvefold world recreate request <flat|cavern|wild>
/delvefold world recreate request <flat|cavern|wild> <keep_backup|permanent>
/delvefold world recreate request <flat|cavern|wild> <classic|expansive> [keep_backup|permanent]
/delvefold world recreate request <flat|cavern|wild> <classic|expansive> <classic|volcanic|dripstone|lush|crystal> [keep_backup|permanent]
/delvefold world delete request
/delvefold world delete request <keep_backup|permanent>
/delvefold world confirm <token>
/delvefold world cancel
```

Backup management:

```text
/delvefold backup list
/delvefold backup verify <backup>
/delvefold backup pin <backup>
/delvefold backup unpin <backup>
/delvefold backup delete <backup> confirm
/delvefold backup retention
/delvefold backup retention configure <max_count> <max_age_days> <max_total_bytes>
/delvefold backup retention disable
/delvefold backup restore request <backup>
/delvefold backup restore confirm <token>
/delvefold backup restore cancel
```

World operations use a short-lived confirmation token and retain a timestamped backup unless `permanent` is explicitly selected. Every new backup receives a normalized SHA-256 manifest. Legacy backups remain listed but cannot be restored until an administrator explicitly runs `backup verify`, which validates the legacy snapshot and creates its manifest. Restore verifies the selected backup again during startup before active files are changed. Pre-restore moves are transactional, and Delvefold stops startup if it cannot prove a safe complete state, preventing partial mining folders from being regenerated.

Retention is disabled by default. A zero count, age, or byte limit means unbounded. When enabled, Delvefold previews the deterministic prune set and never automatically removes pinned backups, the newest two backups, backups referenced by pending lifecycle operations, or any legacy, invalid, unverified, non-restorable, or incompletely measured backup.

`/delvefold doctor` prints a bounded operational report; administrators can use `/delvefold doctor export` to write the same redacted data to `serverconfig/delvefold/exports/`. Reports omit seeds, filesystem paths, confirmation tokens, server addresses, complete profile JSON, and unrelated player data. See [Commands](docs/COMMANDS.md) for behavior and permission details.

Recreation layout defaults to `stable`, which reproduces the established ore, province, themed geology, and landmark layout. Administrators can select `rotate_on_recreate` through the GUI or renewal command; the selection applies only when the world is next initialized or recreated, and ordinary restarts never change an existing layout. Omitting the geology argument preserves the current theme.

## Seam Ledger

Craft the Seam Ledger shapelessly from one Book, one Compass, and one Copper Ingot, or find it in the Delvefold creative tab. Right-clicking it opens a scrollable, read-only view of the active world and enabled ore profile. It shows terrain, scale, the active geology theme, representative ore icons, output IDs or tags, best height bands, vein sizes or province distributions, relative frequency, terrain applicability, bounded biome include/exclude selectors, portal availability, and renewal timing.

The server constructs and authorizes every snapshot. Oversized profiles are safely truncated, and the snapshot never publishes world seeds, horizontal coordinates, filesystem paths, world-operation confirmation tokens, permissions, or administration diagnostics. Successfully obtaining the ledger has an advancement. Consultation is awarded only after the client installs a server-authorized screen, returns its short-lived single-use acknowledgement, and the server rechecks visibility.

## Portal recipe and activation

Portal frame (outputs four blocks):

```text
Iron Ingot          Polished Deepslate   Iron Ingot
Polished Deepslate  Obsidian             Polished Deepslate
Iron Ingot          Polished Deepslate   Iron Ingot
```

Build a complete rectangular frame, then right-click any Portal Frame block with vanilla Flint and Steel. A failed activation does not consume durability. The portal interior may be 2–21 blocks wide and 3–21 blocks tall. Delvefold portals have distinct reverse-fold particles, crystalline resonance, and activation effects.

Portal routing defaults to `coordinate_linked`, preserving the established coordinate-scale behavior. Administrators may opt into `central_hub`, which sends incoming players to a configured mining-world hub, creates a safe vanilla-block platform and return portal, and protects the configured horizontal radius (16 blocks by default). Modification inside that radius requires `delvefold.manage_world`. Portal travel remains player-only throughout 1.x; mobs, items, boats, and minecarts do not pass through Delvefold portals.

## JSON locations

Each save owns its configuration:

```text
<save>/serverconfig/delvefold/ores.json
<save>/serverconfig/delvefold/settings.json
<save>/serverconfig/delvefold/profiles/*.json
<save>/serverconfig/delvefold/imports/*.json
<save>/serverconfig/delvefold/exports/*.json
<save>/serverconfig/delvefold/audit/delvefold-audit.jsonl
<save>/delvefold_backups/<backup>/manifest.json
```

The audit log records accepted mutations as redacted JSON lines and rotates at 10 MiB, retaining the active file plus four archives. It never records confirmation tokens, complete profiles, server addresses, or unrelated player data. Backup manifests inventory normalized relative paths, byte sizes, and SHA-256 hashes; pin and verification-control files are not part of the immutable backup contents.

Editing ore-generation JSON affects only chunks generated after a successful `/delvefold config reload`. Existing chunks are never silently retrogened. See [Configuration](docs/CONFIGURATION.md), [Commands](docs/COMMANDS.md), the [ore schema](schemas/ores.schema.json), and the [settings schema](schemas/settings.schema.json).

Modpack authors can provide namespaced, read-only profiles under `data/<namespace>/delvefold/ore_profiles/`, reloadable landmarks under `data/<namespace>/delvefold/landmarks/`, register profiles from startup scripts, and use NeoForge events and permission nodes. See [Integration](docs/INTEGRATION.md) and the [example datapack](examples/datapack).

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
./gradlew runClientRecipeViewers
./gradlew runServer
```

`runClientRecipeViewers` reproducibly resolves JEI, EMI, Mekanism plus Generators, Tools, and Additions, Ender IO, and Athena. Local full-pack acceptance testing additionally covered Silent's Gems, Applied Energistics 2, GuideME, WorldEdit, and hundreds of other mods from an external pack installation. All such fixtures are test-only: they are neither bundled in Delvefold nor declared as player/server dependencies.

The release JAR is written to `build/libs/delvefold-1.21.1-1.4.1.jar`. Pull requests run a clean Java 21 build, unit tests, NeoForge GameTests, JSON validation, translation-key validation, dedicated-server startup, static analysis, documentation checks, and optional recipe-viewer client smoke tests. Version tags publish the GitHub JAR and SHA-256 checksum automatically. CurseForge upload remains a manual project-owner step; no workflow publishes there.

Contributions are welcome; see [CONTRIBUTING.md](CONTRIBUTING.md). Security reports should follow [SECURITY.md](SECURITY.md).

License: MIT.
