# Changelog

## 1.3.0 — Server Operations

- Added SHA-256 backup manifests containing normalized relative paths, file sizes, and backup metadata. New lifecycle and pre-restore backups are manifested and verified before they become restorable.
- Added asynchronous `/delvefold backup verify <backup>` and a matching Backup GUI action. Large backup files are hashed on dedicated worker threads rather than the server tick thread.
- Kept legacy backups visible but archive-only until an administrator explicitly verifies them. Successful legacy validation creates a manifest; restore then performs another full integrity check at startup before touching active data.
- Corrected restore coverage so Classic and Expansive Flat, Cavern, and Wild dimension folders are all backed up and restored.
- Made pre-restore snapshots transactional: path, move, or manifest failures roll back folders moved by that attempt, preserve earlier crash-staged data for retry, and stop startup before Minecraft can regenerate a missing or mixed mining world.
- Added optional automatic backup retention with count, age, and total-byte limits. Retention is disabled by default, previews every prune, and always protects pinned, pending, newest-two, legacy, invalid, unverified, non-restorable, and incompletely measured backups.
- Added `/delvefold doctor` and the Diagnostics GUI for bounded version, protocol/schema, dimension, profile, ineffective-target, pending-operation, backup-integrity, retention, and disk-space reporting.
- Added `/delvefold doctor export`, which writes a redacted JSON report under the existing per-save exports directory without seeds, filesystem paths, confirmation tokens, server addresses, complete profiles, or unrelated player data.
- Added a rotating JSON-lines audit log for accepted configuration and lifecycle mutations. Entries contain a format version plus timestamp, actor, operation, affected logical object, and old/new revisions; logs rotate at 10 MiB and retain five files including the active log.
- Completed mutation-audit coverage for scheduled renewals, configuration reloads, profile creation/update/deletion, portal and hub changes, landmark catalog publication, backup pin state changes, and asynchronous backup operations even when the requesting player disconnects.
- Kept operational audit logs, transfer files, Doctor exports, and lifecycle history intact across restoration; restoring an older world now replaces its selected settings and ore profiles without rolling newer operational records backward.
- Added `coordinate_linked` and `central_hub` portal-routing modes. Coordinate-linked routing remains the compatibility default.
- Central-hub routing creates an idempotent vanilla-block platform and guaranteed return portal at the configured mining-world hub. Its horizontal protection radius defaults to 16 blocks, and only users with world-management permission may modify protected blocks.
- Kept portal travel player-only for 1.3. Mobs, dropped items, boats, and minecarts do not traverse Delvefold portals.
- Completed keyboard focus, narration, tooltip, translation-key, and color-independent status work across administration screens.
- Kept JEI and EMI optional and unchanged: the same JAR works with neither viewer, either viewer, or both.
- Kept configuration schema 2 and public API version 1. Absent retention and portal-routing fields default to disabled retention and coordinate-linked routing without requiring a migration or load-time rewrite.
- Advanced the identical-version client/server protocol to 12 for backup-integrity, diagnostics, retention, and portal-routing administration data.

CurseForge publication remains a manual project-owner step. The repository does not upload this release to CurseForge automatically.

## 1.2.0 — Living Geology

- Replaced the fixed landmark feature with a server-reloadable catalog at `data/<namespace>/delvefold/landmarks/`, backed by Minecraft's structure system so templates obey chunk boundaries and configured spacing.
- Shipped six vanilla-block landmark templates: Survey Camp, Collapsed Mine Entrance, Lift Station, Geode Vault, Motherlode Chamber, and Fault-line Grotto.
- Made landmark definitions data-driven across template, weight, category, terrain modes, placement style, height bounds, biome selectors, processor lists, and loot table. Catalog reloads are atomic and retain the complete last-known-good revision when any definition or dependency is invalid.
- Applied landmark density deterministically: Pure Mining accepts no candidates, Balanced accepts 25%, and Abundant accepts 75%, with the existing survey-station, motherlode, and fault-line toggles narrowing the eligible catalog further.
- Processed `loot` template markers into deterministically seeded containers only when they have no existing loot table or contents, preventing repeated population.
- Added the **Signs in the Stone** landmark-discovery advancement and the additive API-v1 `DelvefoldLandmarkDiscoveredEvent` with player, landmark ID, dimension, and structure-start chunk views.
- Added recreation-locked Classic, Volcanic, Dripstone, Lush, and Crystal geology themes. Non-Classic themes add bounded vanilla-block strata, decorations, sealed fluids, particles, sounds, and ambience without replacing Delvefold terrain generators or dimension IDs.
- Published the active geology theme through the Seam Ledger, console guide, and additive API-v1 guide view. Current guide snapshots use format 2; the legacy constructor remains available with Classic as its compatibility default.
- Added deterministic regional ore-province bands with configurable region size, radius, vertical thickness, density, and a hard per-chunk work cap. Neighboring chunks agree on regional centers while generation writes only inside the current chunk.
- Added province controls to the ore-rule GUI and command parity through `/delvefold ore band placement` and `/delvefold ore band province`.
- Extended forecasts and the existing aggregate safety budget to include province work, rejecting configurations that exceed bounded per-terrain limits.
- Kept configuration schema 2 and public API version 1. Existing settings default to Classic geology, existing ore bands default to Vein placement, and absent fields do not trigger a load-time rewrite.
- Advanced the identical-version client/server protocol to 11 for geology identity, guide format 2, and province-band administration payloads.

CurseForge upload is intentionally deferred to the project owner until the complete post-1.0 roadmap is finished.

## 1.1.0 — The Surveying Update

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
- Added a bounded, server-authoritative whole-profile forecast from the Ores dashboard. It compares configured and effective attempts/work across all terrain modes, graphs the active height distribution, and reports missing blocks/tags, invalid states, shadowed outputs, biome exclusions, and terrain-ineffective rules.
- Added a guided **Detect ores** wizard that discovers conventional `c:ores/*` tags and conservative ore-like registry entries, groups probable stone/deepslate variants, and lets administrators select ore groups by icon.
- Added a server-validated import diff and per-terrain workload preview. Imported rules use the existing Uncommon template and are saved only to a new inactive named profile; the wizard never overwrites or activates a profile.
- Bound ore discovery, paging, strings, diagnostics, selection counts, and network payloads. Scan and commit capabilities are random, player-bound, revision/registry/profile-bound, expiring, rate-limited, and single-use where mutation occurs.
- Exposed recreation layout selection in setup, the administration dashboard, and `/delvefold renewal seed-mode`, with changes applying only on the next initialization or recreation.
- Made setup and identity administration vertically scrollable at compact resolutions and high GUI scales, while keeping renewal controls visibly read-only for configure-only users.
- Hardened live configuration reloads so lifecycle-owned terrain, epoch, operation, and generation-salt fields cannot be changed around confirmed world operations; oversized integer and long values are rejected before deserialization can narrow them.
- Kept landmark rarity data-driven through a registered placement modifier while preserving the exact legacy random sequence in stable mode and separating rotated placement from landmark contents.
- Advanced the identical-version client/server protocol to 10 for weighted administration, renewal identity, bounded forecasts, and guided import sessions while keeping seeds, coordinates, filesystem paths, and server-only fingerprints out of client views.

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
