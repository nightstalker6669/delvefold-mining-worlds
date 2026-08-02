# Changelog

## 1.4.0 — Unified Ores

### Added

- Replaced the one-block-at-a-time ore browser with a server-authoritative material library that groups equivalent ores across installed providers, such as multiple mods' copper ores, under one logical family.
- Made conventional `c:ores/<material>` block tags the authoritative family signal. Conservative ore-like registry-name fallback keeps untagged ores discoverable while marking uncertain identity or replacement-host inference for review.
- Added bounded server-side family search and paging. Families already represented by an exact target or an expanded block-tag target in the active profile are hidden by default and can be revealed with **Show configured**.
- Made **Previous** and **Next** move through adjacent visible result windows in both directions, including across server-page boundaries. Search filters, selections, and the current local window survive asynchronous refreshes and returns from the variant editor.
- Added persistent multi-selection across result pages and atomic batch addition of up to 128 material families in one accepted configuration mutation. The entire request is re-resolved and validated by the server before one revision is committed; stale, unknown, invalid, or over-budget requests add nothing.
- Added deterministic provider defaults that enable Minecraft's safe stone/deepslate variants when available, or the lexically first installed provider otherwise. Other providers remain visible for later editing instead of creating duplicate output by default.
- Expanded the ore-rule wizard so a material family exposes provider-specific stone, deepslate, and review-required variants. A rule may enable up to 16 exact targets, preserving the existing schema-2 safety bound.
- Retained direct registry-ID entry for unusual, ambiguous, or intentionally separate blocks that should not use material grouping.

### Compatibility and behavior

- Existing exact-block targets, block-tag targets, rule IDs, profiles, dimension IDs, and generation settings remain valid and are never automatically consolidated into material families.
- Material families exist only in discovery, network, and GUI views. Saved targets remain ordinary exact blocks or block tags in configuration schema 2, so no migration or load-time rewrite occurs.
- Ore edits affect newly generated mining-world chunks only. Existing chunks are never silently retrogened; recreate the mining world if a uniform result is required.
- Configuration schema remains **2** and `DelvefoldApi.API_VERSION` remains **1**.
- Client/server network protocol is now **13** for the bounded Unified Ores catalog and batch requests. Install the identical Delvefold 1.4.0 JAR on every client and the server.
- JEI and EMI remain optional client integrations and are not bundled or required.
- Verified 665 JUnit tests, all 40 required NeoForge GameTests, JSON and translation validation, strict static analysis and Javadocs, and a fresh dedicated-server startup with orderly saves for all six Delvefold dimensions. The acceptance client combined JEI, EMI, Mekanism, Ender IO with Athena, Silent's Gems, Applied Energistics 2, GuideME, and WorldEdit as test-only installations; none are bundled or required.
- The release artifact is `delvefold-1.21.1-1.4.0.jar`. GitHub tag automation publishes the JAR and checksum; CurseForge upload remains a manual project-owner step.

## 1.3.3 — Cavern Visibility and Generation

- Raised the shared Classic and Expansive Cavern ambient-light floor from `0.0` to `0.1`, matching the conservative visual baseline used by the vanilla Nether.
- Improved visibility in completely unlit cavern areas without adding block light, enabling skylight, changing F3 light values, or altering ordinary hostile-mob spawn checks.
- Rebuilt Classic and Expansive Cavern generation around continuous solid stone hosts with a compact, vertically bounded custom cave carver. This removes the enormous terrain-density voids and detached floating-island shelves produced by the previous shape.
- Kept Expansive Cavern taller and deeper than Classic while giving it the same solid-host underground topology instead of amplified terrain.
- Kept exposed Cavern surfaces stone/deepslate instead of painting them with Overworld grass and dirt.
- Removed the inherited global water table, aquifers, underground lava-lake feature, water springs, and lava springs from Cavern generation.
- Added sparse one-block-deep water pockets that probe from Y 32 through Y 112 and scan downward by at most 32 blocks for an exposed stone floor. Each candidate averages one half-attempt per chunk before floor checks, uses a radius of one or two blocks, and cannot replace enclosed walls.
- Retained natural bottom lava from cave carving only at Y −56 and below, producing localized low-level pockets without the former free-flowing spring pass.
- Enforced solid floor and roof safety bands around both carving ranges so Caverns stay enclosed.
- Applied the brighter visual floor to existing Cavern chunks after a full restart. Topology, material, and fluid changes apply only to newly generated Cavern chunks; recreation is strongly recommended for a uniform result.
- Added JSON contract tests and live NeoForge GameTests for the ambient-light contract, continuous stone hosts, bounded cave carving, dry stone defaults, generator ranges, enclosed floor/roof bands, bounded water-pocket codecs, and removal of the broad vanilla fluid features.

This patch does not change save formats, dimension IDs, configuration schema 2, public API version 1, or network protocol 12. Stable generation salts remain persisted, but Cavern block placement is intentionally not bit-for-bit identical because the generator's topology, surface, and fluid contracts changed. Clients and servers must use the identical 1.3.3 JAR. CurseForge publication remains a manual project-owner step.

## 1.3.2 — Client Stability and Compatibility

- Fixed a client crash when **Add Ore** built a picker entry for a modded ore. Registry IDs are now converted to supported text values before they are supplied to Minecraft's translation formatter.
- Corrected the same unsafe translation-argument pattern in profile-export feedback and `/delvefold ore scan` result rows, preventing equivalent failures when those paths display filesystem names or registry IDs.
- Fixed the Seam Ledger's unreadable text and icons by eliminating its second vanilla background/blur pass. Guide content now renders once, after the blur and before interactive widgets and tooltips.
- Marked Delvefold's portal-construction and fallback information pages as synthetic EMI recipes. EMI no longer reports that `delvefold:portal_construction` is missing from Minecraft's recipe manager; normal EMI category and JEI identifiers remain unchanged.
- Added regression checks for every corrected translation call and audited all production translation arguments for unsupported object types.
- Added release-version, guide render-order, and EMI synthetic-ID regression guards so documentation, artifact naming, background ordering, and viewer compatibility cannot silently drift.
- Verified the complete build and quality suite with 607 unit tests, 61 JSON resources, and 595 literal translation references. The modded-client acceptance instance includes JEI, EMI, Silent's Gems, Silent Lib, Applied Energistics 2, GuideME, and WorldEdit as test-only installations; none are bundled or made required dependencies.

This patch does not change gameplay rules, saves, configuration schema 2, public API version 1, or network protocol 12. Clients and servers must use the identical 1.3.2 JAR. CurseForge publication remains a manual project-owner step.

## 1.3.1 — Code Quality and Maintainability

- Completed a behavior-preserving internal cleanup of the 1.3 codebase. Public API version 1, network protocol 12, configuration and ore-profile schema 2, registry IDs, command paths, permission rules, and deterministic world-generation inputs remain unchanged.
- Standardized all Java sources with a pinned formatter and made formatting, import ordering, unused-import removal, UTF-8/LF handling, and final-newline checks part of the normal build.
- Enabled complete applicable Java compiler lint with warnings treated as errors, Error Prone correctness analysis, JSpecify nullness contracts, NullAway, Checkstyle documentation coverage, and full Javadoc/doclint validation.
- Marked every production and test package as null-marked, replaced legacy nullable annotations with precise JSpecify type-use annotations, and documented every public or protected production contract, including record components, ranges, units, ownership, thread expectations, redaction boundaries, and lifecycle effects where applicable.
- Narrowed warning suppressions to declaration-owned, reviewed Minecraft, NeoForge, JEI, or compatibility boundaries. Source-hygiene tests now reject broad, stale, package-level, multi-category, or unapproved suppressions.
- Added shared, tested infrastructure for atomic file replacement, validated lifecycle trees and journals, and named executor/thread ownership while preserving the separate audit, backup-verification, backup-catalog, and Doctor worker queues.
- Separated backup administration from the network-facing admin adapter and moved ore-rule document edits out of the Brigadier command controller. Commands and GUI requests still reach the same server-authoritative services with the same permissions, revisions, localization, and result ordering.
- Split configuration transitions, live-reload policy, and profile-catalog operations into focused collaborators behind the stable `DelvefoldConfigService` facade. Persistence ordering, immutable snapshot publication, lifecycle locks, audit events, and last-known-good reload behavior are preserved.
- Extracted dashboard, ore-rule wizard, ore-import, forecast, picker, backup, and shared scrolling state/layout models from screen rendering. Drafts survive resize, compact layouts retain reachable controls, keyboard scrolling only consumes input when movement occurs, and focus can reveal off-screen widgets.
- Cached immutable guide, forecast, icon, distribution, tooltip-wrapping, and layout presentation data outside render loops, eliminating repeated frame-invariant work without changing server snapshots or visible values.
- Expanded characterization and pure behavior coverage from 365 to 601 JUnit tests. New tests lock public JVM descriptors, command structure, configuration transitions, ore edits, admin backup dispatch, GUI drafts/layout/scrolling, source contracts, null-marking coverage, suppressions, asynchronous isolation, and deterministic generation behavior.
- Re-ran all 35 NeoForge GameTests covering portal behavior, travel, terrain variants, ore placement, and deterministic selection. A dedicated-server smoke reached ready state and shut down with orderly saves for vanilla and all six Delvefold dimensions.
- Kept JEI and EMI optional and isolated. The release JAR continues to support base, JEI-only, EMI-only, and combined client installations without bundling either recipe-viewer implementation library.
- Documented the final package architecture, compatibility boundary, threading model, quality policy, analyzer exception, acceptance gates, and implementation evidence for future maintainers.

This release changes maintainability and validation, not gameplay or save data. Existing Delvefold 1.3.0 worlds and profiles load without migration, and identical 1.3.1 clients and servers continue to use protocol 12. CurseForge publication remains a manual project-owner step.

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
