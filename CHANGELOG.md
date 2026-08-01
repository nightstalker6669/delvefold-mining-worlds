# Changelog

## 1.1.0 — The Surveying Update (in development)

- Expanded optional JEI and EMI support with a visual Portal Construction category, minimum-frame diagram, Flint and Steel catalyst, initialization guidance, isolated development launch profiles, and client compatibility smoke tests.
- Kept both recipe viewers out of the release JAR and all common/server code; Delvefold continues to run with neither viewer installed.
- Prevented duplicate information entries when EMI and JEI are installed together.
- Added the craftable Seam Ledger to the Delvefold creative tab with advancements for obtaining it and for successfully consulting a server-authorized guide.
- Added a responsive, scrollable `/delvefold guide` screen showing server-authoritative world identity, portal and renewal state, ore outputs and icons, best height bands, vein sizes, relative frequency, terrain applicability, and bounded biome include/exclude selectors.
- Added a bounded console summary for `/delvefold guide` and source visibility modes `public`, `operators`, and `disabled`; trusted consoles count as operators, disabled blocks every built-in source, and existing schema-2 saves default to public when the additive field is absent.
- Added strict count, string, enum, biome-selector, and estimated-size limits to guide snapshots, with explicit truncation for unusually large profiles.
- Required a short-lived, player-bound, single-use client-open acknowledgement and a fresh server visibility check before awarding the consulting advancement.
- Added `DelvefoldApi.activeGuide()` as an additive API-v1 read-only snapshot that excludes seeds, coordinates, filesystem paths, world-operation confirmation data, and administration diagnostics.
- Added optional ore-target weights from 1 through 1000. Exact outputs use their configured weights, each tag target divides its total weight equally among installed members once a host group uses non-default weighting, duplicate states are first-wins with warnings, and omitted or all-1 weights preserve the established member-uniform deterministic selection sequence.
- Added stable and rotate-on-recreation seed modes. Stable remains the compatibility default; rotating layouts incorporate the generation epoch, and the server persists the derived salt so restarts cannot change an already-created ore or landmark layout.
- Exposed recreation layout selection in setup, the administration dashboard, and `/delvefold renewal seed-mode`, with changes applying only on the next initialization or recreation.
- Made setup and identity administration vertically scrollable at compact resolutions and high GUI scales, while keeping renewal controls visibly read-only for configure-only users.
- Hardened live configuration reloads so lifecycle-owned terrain, epoch, operation, and generation-salt fields cannot be changed around confirmed world operations; oversized integer and long values are rejected before deserialization can narrow them.
- Kept landmark rarity data-driven through a registered placement modifier while preserving the exact legacy random sequence in stable mode and separating rotated placement from landmark contents.
- Advanced the identical-version client/server protocol to 9 for weighted administration payloads and renewal seed-mode identity data while keeping the derived salt server-only and retaining configuration schema 2 and public API version 1.

Configuration schema 2 and public API version 1 remain unchanged.

## 1.0.1 — Stability and Correctness

- Fixed overlapping descriptions in the setup wizard and added readable help for every landmark-density choice.
- Moved the Expansive Flat surface safely below the vanilla cloud layer.
- Awarded Strike the Seam only after a complete portal successfully ignites.
- Corrected multi-output ore rules so blocks sharing one host tag are selected deterministically per vein instead of shadowing later outputs.
- Deduplicated repeated runtime ore outputs and added clear warnings for empty optional output tags.
- Added NeoForge GameTests for portal frame integrity and multi-output ore selection.
- Added a dedicated GameTest job to continuous integration.

Configuration schema 2 and public API version 1 remain unchanged. Existing exact-block targets and stable world layouts remain compatible.

## 1.0.0 — Stable Foundations

- Stabilized configuration schema 2 and public API version 1 for the Delvefold 1.x line.
- Preserved direct upgrade compatibility for schema-2 worlds created by Delvefold 0.2–0.4.
- Added reproducible release JARs, source JARs, manifest version metadata, and explicit optional JEI/EMI declarations.
- Added build-time validation for all shipped JSON, datapack examples, and literal Delvefold translation keys.
- Migrated the setup wizard, ore picker, ore editor, backup browser, dashboard navigation, and major administration labels to translatable components.
- Added clean-build CI, pull-request artifacts, monthly dependency updates, and tag-driven GitHub releases with SHA-256 checksums.
- Added compatibility, troubleshooting, contribution, security, and structured bug-report documentation.
- Removed deprecated client event-bus registration usage and completed client/dedicated-server release smoke testing.

Delvefold 1.0 remains server-authoritative, requires installation on both client and server, and never retrogenerates existing chunks silently.

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
