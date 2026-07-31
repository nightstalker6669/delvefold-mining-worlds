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
- **Pure Mining, Balanced, or Abundant:** control survey stations, motherlodes, and fault-line landmarks.

## Configure ores visually

Delvefold includes an in-game ore picker and rule editor built for both vanilla and modded blocks.

- Pick blocks using their real item icons.
- Search by name or namespace, browse ore candidates, or show every registered block.
- Add modded ores by icon, registry ID, exact block, or conventional tag such as `c:ores/tin`.
- Configure stone/deepslate variants and other output block states.
- Select replacement blocks or block tags.
- Control vein size, attempts per chunk, height ranges, and air-exposure discard.
- Use uniform, triangle, or trapezoid distributions with visual height and workload previews.
- Restrict rules by biome or terrain type and give one ore multiple spawn bands.

Ore rules can be managed through the GUI, `/delvefold` commands, or canonical per-save JSON. Changes apply to newly generated chunks; Delvefold never silently retrogenerates existing terrain.

## Profiles for players and modpacks

Start with **Vanilla-Balanced**, **Rich**, or **Empty**, then build named profiles for different progression stages or pack styles. Profiles can be created, duplicated, selected, imported, exported, and safely deleted.

Modpack authors can also provide read-only profiles through datapacks or startup scripts. Delvefold supports tag-driven outputs, native NeoForge permission nodes, optional JEI/EMI portal guidance, and a versioned integration API with lifecycle and portal events.

## A portal with clear rules

Craft the iron-tier **Delvefold Portal Frame**, build a complete rectangular frame, and ignite it with vanilla Flint and Steel. If the mining world has not been initialized—or the frame is incomplete—activation is rejected without consuming durability.

Portal access, cooldown, coordinate scaling, and Overworld-only entry can be configured by the server. Activation has its own reverse-fold particles and crystalline sound effects.

## Safe recreation and backups

Delete, recreate, renew, or restore the mining world through the GUI or commands. Confirmed operations can:

- Block new portal entry while work is pending.
- Evacuate players safely to the Overworld.
- Recreate the world with a different terrain shape or scale.
- Keep a timestamped backup by default.
- Restore backups through the in-game backup browser.
- Pin important backups and remove old ones only after confirmation.

Scheduled renewal is optional and includes configurable warnings plus mandatory recoverable backups.

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
- NeoForge **21.1.x**
- Java **21**
- Installed on both client and server

Delvefold stores its settings per save and stabilizes configuration schema 2 for the 1.x series. Schema-2 worlds from Delvefold 0.2–0.4 can upgrade directly; schema-1 saves remain available through non-destructive read-only compatibility handling.
