# Delvefold 1.1.0 — The Surveying Update

Delvefold 1.1.0 adds a server-authoritative field guide, whole-profile forecasting, guided modded-ore discovery, weighted ore outputs, recreation seed modes, and expanded optional JEI/EMI support.

## Seam Ledger and guide

- Added the craftable **Seam Ledger** to the Delvefold creative tab. Its shapeless recipe uses a Book, Compass, and Copper Ingot.
- Added `/delvefold guide`; using the command or the Seam Ledger opens the same read-only guide for players, while the server console receives a bounded text summary.
- Added `public`, `operators`, and `disabled` guide-visibility modes. Existing configurations default to `public`.
- The guide reports the mining-world name, terrain, geology, active ore profile, portal status, renewal countdown, ore targets, applicable terrain and biomes, best heights, distributions, vein sizes, and relative frequency.
- Guide data is bounded and never exposes seeds, exact coordinates, filesystem paths, confirmation tokens, or administrative diagnostics.
- Added advancements for obtaining and successfully consulting the Seam Ledger. The consulting advancement is awarded only after the server authorizes and opens the guide.

## Forecasting and modded-ore import

- Added a read-only **Forecast** action to the Ores dashboard.
- Forecasts compare configured and effective attempts/work for every terrain, display the active height distribution, and identify missing blocks or tags, invalid states, shadowed outputs, biome exclusions, and rules that cannot affect the selected terrain.
- Added a guided **Detect ores…** wizard to the Profiles dashboard.
- The wizard discovers conventional `c:ores/*` tags and conservative ore-like registered blocks, groups probable stone/deepslate variants, and clearly flags ambiguous hosts for review.
- Administrators can select ore groups by their real block icons, inspect the exact added/skipped blocks, and review the workload change before saving.
- Imported rules use the existing Uncommon template and are saved only as a new inactive named profile. The wizard never overwrites or activates a profile automatically.
- Import capabilities are short-lived, random, player-bound, revision/registry/profile-bound, rate-limited, and single-use when a profile is created.

## Ore and recreation controls

- Added optional ore-target weights from 1–1000. Existing profiles and targets with no weight continue to behave as weight `1`.
- Exact outputs use their configured relative weight. Members expanded from one output tag share that tag target's weight equally.
- Preserved the established deterministic selection sequence for existing all-1 profiles.
- Added `stable` and `rotate_on_recreate` renewal seed modes.
- `stable` remains the compatibility default and reproduces the same ore and landmark layout after recreation.
- `rotate_on_recreate` incorporates the generation epoch, producing a new deterministic layout after each recreation while remaining stable across server restarts.
- Seed-mode selection is available in setup, the administration dashboard, and `/delvefold renewal seed-mode`.

## JEI and EMI

- Expanded optional JEI and EMI integration with a visual Portal Construction category, minimum-frame diagram, Flint and Steel catalyst, and initialization guidance.
- Prevented duplicate information when JEI and EMI are installed together.
- JEI and EMI remain optional and are not bundled in the release JAR. Delvefold continues to work when neither is installed.

## Usability and safety

- Made setup and identity administration scroll correctly at compact resolutions and high GUI scales.
- Kept renewal controls visibly read-only for users who may view administration but cannot configure the world.
- Hardened live configuration reloads so lifecycle-owned terrain, epoch, operation, and generation-salt values cannot change around confirmed world operations.
- Added strict size, count, string, selection, and diagnostic limits to all new guide, forecast, and import network data.
- Advanced the identical-version client/server protocol to `10` for the new 1.1 payloads.

## Compatibility

- Minecraft: **1.21.1**
- Mod loader: **NeoForge 21.1.x**
- Java: **21**
- Environment: **Client and server**
- Configuration schema remains **2**.
- Public Delvefold API remains **version 1**.
- Existing dimension IDs, exact-block ore targets, profile IDs, portal behavior, and stable renewal layouts remain compatible.

Install the same Delvefold 1.1.0 JAR on the server and every connecting client. Ore/profile changes affect newly generated chunks; changing a recreation seed mode takes effect only after initialization or recreation.
