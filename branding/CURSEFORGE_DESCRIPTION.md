# Delvefold: Mining Worlds

![Delvefold: Mining Worlds feature banner](https://media.forgecdn.net/attachments/1833/961/delvefold-feature-banner-under-2mb-jpg.jpg)

**Build a renewable mining world around your pack—not the other way around.**

Delvefold adds a configurable mining dimension for **Minecraft 1.21.1 on NeoForge**. Move large-scale mining out of the Overworld, choose how the new world generates, decide exactly which ores appear, and safely recreate it whenever your players need fresh terrain.

The same JAR supports **singleplayer, LAN, and dedicated servers**. Configuration is server-authoritative, while integrated singleplayer owners can manage Delvefold even with cheats disabled.

## Choose your mining world

Create the world explicitly through `/delvefold gui` or `/delvefold initialize`—lighting a portal never silently chooses settings for you.

- **Flat:** layered, predictable geology for branch mining and automation.
- **Cavern:** a roofed underground dimension with caves and a subterranean atmosphere.
- **Wild:** hills, valleys, caves, and Overworld-like exploration.
- **Classic or Expansive:** choose the scale that fits your pack.
- **Classic, Volcanic, Dripstone, Lush, or Crystal geology:** choose the material theme installed by initialization or recreation.
- **Pure Mining, Balanced, or Abundant:** control survey stations, motherlodes, and fault-line landmarks.
- Discover six structure-system landmarks, from survey camps and lift stations to geode vaults and fault-line grottos.

## Configure ores visually

Delvefold includes an in-game ore picker and rule editor built for both vanilla and modded blocks.

- Browse ores as material families, so copper, tin, silver, and other variants from different mods appear together instead of as hundreds of unrelated blocks.
- Search and page through the server's installed ore catalog, hide families already configured, select across pages, and add up to 128 families atomically.
- Use conventional `c:ores/<material>` tags for authoritative grouping. Clear conventional registry names keep untagged ores safely discoverable; only ambiguous identities or replacement hosts require review.
- Start with Minecraft's provider when available, otherwise the first installed provider, then open the rule to enable or disable other provider-specific stone, deepslate, netherrack, and end-stone variants.
- Add unusual ores by exact registry ID when they should not be grouped.
- Configure automatically detected replacement tags and output block states, including `#c:netherracks` and `#c:end_stones` for recognized Nether and End variants.
- Select replacement blocks or block tags.
- Control vein size, attempts per chunk, height ranges, and air-exposure discard.
- Choose classic veins or deterministic regional ore provinces with bounded per-chunk work.
- Weight multiple outputs that share the same replacement host.
- Use uniform, triangle, or trapezoid distributions with visual height and workload previews.
- Restrict rules by biome or terrain type and give one ore multiple spawn bands.
- Forecast a complete profile and detect missing, shadowed, biome-filtered, or ineffective rules before generating chunks.
- Use the guided **Detect ores…** wizard to build a new inactive profile from the same material families without overwriting or activating it automatically.

Ore rules can be managed through the GUI, `/delvefold` commands, or canonical per-save JSON. Changes apply to newly generated chunks; Delvefold never silently retrogenerates existing terrain.

## Profiles for players and modpacks

Start with **Vanilla-Balanced**, **Rich**, or **Empty**, then build named profiles for different progression stages or pack styles. Profiles can be created, duplicated, selected, imported, exported, and safely deleted.

Modpack authors can also provide read-only profiles through datapacks or startup scripts. Delvefold supports tag-driven outputs, native NeoForge permission nodes, optional JEI/EMI portal guidance, and a versioned integration API with lifecycle and portal events.

Players can craft the **Seam Ledger** or run `/delvefold guide` for a bounded, server-authorized view of active geology, ore targets, best heights, distributions, terrain/biome applicability, portal state, and renewal timing.

## A portal with clear rules

Craft the iron-tier **Delvefold Portal Frame**, build a complete rectangular frame, and ignite it with vanilla Flint and Steel. If the mining world has not been initialized—or the frame is incomplete—activation is rejected without consuming durability.

Portal access, cooldown, coordinate scaling, and Overworld-only entry can be configured by the server. Coordinate-linked routing remains the default, while optional central-hub routing creates a protected vanilla-block arrival platform and guaranteed return portal. Activation has its own reverse-fold particles and crystalline sound effects. Portal transport is player-only throughout the 1.x series.

## Safe recreation and backups

Delete, recreate, renew, or restore the mining world through the GUI or commands. Confirmed operations can:

- Block new portal entry while work is pending.
- Evacuate players safely to the Overworld.
- Recreate the world with a different terrain shape or scale.
- Keep a timestamped backup by default.
- Restore backups through the in-game backup browser.
- Verify normalized-path SHA-256 manifests asynchronously before restoration.
- Pin important backups and remove old ones only after confirmation.
- Optionally prune by count, age, or total bytes while always preserving pinned, pending, and newest-two backups.

World deletion, recreation, renewal, and restoration are applied safely during the next dedicated-server restart, or after a singleplayer owner exits to the title screen and reopens the save. Delvefold never deletes a dimension while it is loaded.

Scheduled renewal is optional and includes configurable warnings plus mandatory recoverable backups.

Legacy backups remain visible and can be explicitly validated into the manifested format. Every restore rechecks integrity during startup and covers all six Classic/Expansive Flat, Cavern, and Wild dimension folders.

## Server diagnostics and accountability

Use `/delvefold doctor` or the Diagnostics GUI for a bounded report covering versions, dimensions, profile health, pending operations, backup integrity, retention, and disk estimates. Administrators can export the same redacted report for support without seeds, paths, confirmation tokens, addresses, complete profiles, or unrelated player data.

Accepted configuration and lifecycle mutations are written to a rotating JSON-lines audit log containing a format version plus timestamp, actor, operation, affected logical object, and old/new revisions.

## Gameplay controls

Choose **Safe**, **Hostile**, or **Normal** natural mob spawning, then fine-tune individual spawn categories. Delvefold also includes an advancement path for obtaining the frame, activating the portal, and entering the mining world.

## Getting started

1. Install Delvefold on both the client and server.
2. Open or create a world.
3. Run `/delvefold gui`.
4. Choose terrain, scale, ore, gameplay, and landmark presets.
5. Review and initialize the mining world.
6. Build the frame and light it with Flint and Steel.

## Requirements

- Minecraft Java Edition **1.21.1**
- NeoForge **21.1.244 or newer** for Minecraft 1.21.1
- Java **21**
- Installed on both client and server

Delvefold stores its settings per save and keeps configuration schema 2 and public API version 1 stable for the 1.x series. Schema-2 worlds from Delvefold 0.2–1.3 upgrade directly to 1.4.1 without rewriting existing exact/tag ore rules. Schema-1 saves remain available through non-destructive read-only compatibility handling. Delvefold 1.4.1 uses network protocol 14 and requires the identical JAR on every client and server. JEI and EMI remain optional and unchanged.
