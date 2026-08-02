# Delvefold 1.3.3 — Cavern Visibility and Generation

Delvefold 1.3.3 makes Classic and Expansive Cavern mining worlds easier to see and makes their terrain feel like true underground mining spaces.

## Changes

- Raised the shared Cavern ambient-light floor from `0.0` to `0.1`, matching the conservative visual baseline used by the vanilla Nether.
- Improved visibility in completely unlit cavern areas without adding block light or enabling skylight.
- Preserved F3 block/sky light readings and ordinary hostile-mob spawn checks, so torches and other light sources are still required to make an area spawn-proof.
- Rebuilt both Cavern scales around continuous solid stone hosts with a compact, vertically bounded custom cave carver.
- Removed the giant terrain-density voids and detached floating-island shelves produced by the previous Cavern shape.
- Kept Expansive Cavern taller and deeper than Classic without using amplified terrain.
- Removed grass and dirt surface painting from both Cavern variants; exposed terrain now remains stone or deepslate.
- Removed the broad inherited water table, aquifers, underground lava lakes, and free-flowing water/lava springs.
- Added sparse, shallow water pockets with a one-block depth and a radius of one or two blocks. Candidates probe from Y 32 through Y 112 and scan down by at most 32 blocks for an exposed stone floor.
- Kept localized natural lava only near the bottom, at Y −56 and below, through cave carving.
- Added solid floor and roof safety bands around both carving ranges so Caverns remain enclosed.
- Applied ambient visibility to existing Cavern chunks after a full restart. Terrain and fluid changes apply to new chunks only; recreate the mining world for a uniform result.
- Added unit and live NeoForge GameTest coverage for lighting, continuous stone hosts, bounded cave carving, dry stone defaults, bounded water pockets, generator ranges, and enclosed floor/roof bands.

## Compatibility

- Configuration schema remains **2**.
- Public Delvefold API remains **version 1**.
- Client/server network protocol remains **12**.
- Install the identical Delvefold 1.3.3 JAR on the server and every client.
- Existing saves, settings, ore profiles, dimension IDs, and portal behavior remain compatible. Existing chunks are not modified.
- Stable generation salts remain persisted, but newly generated Cavern blocks intentionally differ because the topology, surface, and fluid rules changed.

## Requirements

- Minecraft Java Edition **1.21.1**
- NeoForge **21.1.244 or newer** for Minecraft 1.21.1
- Java **21**
- Environment: **client and server**

CurseForge publication remains a manual project-owner step.
