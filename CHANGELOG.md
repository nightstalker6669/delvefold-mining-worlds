# Changelog

## 0.4.0 — Modpack Ecosystem

- Added namespaced, read-only ore profiles loaded from datapacks.
- Added a dependency-free startup-script bridge with deterministic script-over-datapack precedence.
- Added tag-driven ore outputs for conventional tags such as `c:ores/tin` in JSON, commands, and the GUI.
- Added native NeoForge permission nodes for configuration, world management, and portal entry.
- Added an experimental versioned API with immutable world views and lifecycle, profile, and cancellable portal events.
- Added optional JEI and EMI information pages for portal setup and activation.
- Added a complete example datapack, integration guide, schemas, validation, and backward-compatibility tests.

Datapack and scripted profiles are read-only in Delvefold and affect newly generated chunks after selection. JEI, EMI, scripting mods, and permission mods remain optional.

## 0.3.0 — World Identity

- Added Classic and Expansive variants for Flat, Cavern, and Wild terrain.
- Added optional survey stations, ore motherlodes, and fault-line landmarks.
- Added Pure Mining, Balanced, and Abundant landmark presets.
- Added editable mining-world names through the GUI, commands, and JSON.
- Added opt-in scheduled renewal with configurable warnings, player evacuation, and mandatory recoverable backups.
- Added atomic terrain-shape and terrain-scale selection during confirmed recreation.
- Added a three-step onboarding advancement path for frames, activation, and first entry.
- Added distinctive portal activation audio, crystalline ambience, and reverse-fold particles.
- Extended restart-safe cleanup and player safety to all six Delvefold dimensions.
- Preserved additive compatibility with existing schema-2 save configuration.

Landmark and ore changes affect newly generated chunks. Scheduled renewal remains disabled until explicitly enabled.

## 0.2.0 — Control and Confidence

- Added visual ore height-distribution and workload previews.
- Reworked setup into a guided terrain, resources, and review workflow.
- Added GUI editing for biome selectors and output block-state properties.
- Added named ore profiles with GUI and command management.
- Added bounded clipboard and server-directory JSON import/export.
- Added editable portal policy and capability-aware administration controls.
- Added a backup browser, pinning, confirmed deletion, and restart-safe restore journals.
- Added pre-restore snapshots while preserving the selected restore point.
- Introduced configuration schema 2 with explicit active-profile state.
- Added non-destructive read-only handling for schema-1 saves.
- Embedded Delvefold branding and versioned release JAR names.

Ore and portal changes affect newly generated chunks or future travel. Existing chunks are not retrogened.
