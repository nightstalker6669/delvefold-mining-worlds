# Compatibility and Upgrades

## Supported platform

Delvefold 1.3 targets Minecraft Java Edition 1.21.1, NeoForge 21.1.244 or newer for Minecraft 1.21.1, and Java 21. Install the same Delvefold JAR on every client and the server. Singleplayer uses the same server-authoritative implementation through Minecraft's integrated server.

JEI, EMI, permission handlers, scripting mods, and mods that contribute ores are optional. Delvefold resolves blocks and tags after registration and does not declare a hard dependency on those integrations. JEI 19.18.3–19.x and EMI 1.1.x receive the Portal Frame recipe plus Delvefold's visual portal-construction guide.

## Save compatibility

Configuration schema 2 is stable for Delvefold 1.x. Worlds created with Delvefold 0.2 through 1.2 upgrade directly to 1.3. Fields introduced after 0.2 receive conservative defaults when absent. `guide_visibility` defaults to `public`, renewal `seed_mode` defaults to `stable`, `identity.geology_theme` defaults to `classic`, `backup_retention` defaults to disabled with unbounded limits, portal `routing_mode` defaults to `coordinate_linked`, the hub defaults to 0,0 with radius 16, and server-maintained `generation_salt` defaults to `0`. Existing schema-2 saves require no migration or load-time rewrite. Existing exact-block targets remain valid alongside 0.4's `block_tag` targets.

The optional 1.1 target `weight` remains part of schema 2. It accepts integers from 1 through 1000 and defaults to `1` when absent. Exact targets use their configured weight directly, while one tag target's total weight is divided equally among its installed, registry-sorted members. Fractions are supported internally. Identical resolved block states are deduplicated first-wins, with later overlaps ignored and warned. Existing profiles and new profiles whose configured weights are all `1` preserve the established member-uniform per-vein output selection and exact random sequence; this compatibility path intentionally precedes tag-total weighting until any target in that host group uses a non-default weight.

The optional 1.2 band `placement` also remains part of schema 2. Omitted values load as `vein`, and a missing or null `province` object preserves established vein generation. Only bands explicitly changed to `province` use regional centers, density, vertical thickness, and per-chunk work caps. Province placement is deterministic across chunk borders but writes only inside the chunk currently generating. The existing aggregate attempt/work limits include province caps, so malformed or excessively expensive profiles are rejected before publication.

Schema-1 configuration is intentionally not migrated in place because its dimension contract predates the stable terrain and lifecycle model. Delvefold detects it, leaves every file and dimension folder untouched, disables mutations and portal entry, and explains that a new save is required.

Always back up the complete save before changing mod versions. Do not remove Delvefold while a deletion, recreation, renewal, or restore operation is pending.

Backups created by 1.3 include normalized-path SHA-256 manifests and verification receipts. Earlier Delvefold backups remain listed as legacy archives. They are not deleted or silently trusted: an administrator must explicitly run `/delvefold backup verify <backup>` to validate the old layout and create its manifest before restore becomes available. Every restore re-verifies the selected backup at startup. Restore coverage includes all six Classic/Expansive Flat, Cavern, and Wild save folders.

Automatic retention is disabled on upgrade. Enabling it never prunes pinned backups, pending-operation references, or the newest two backups. An unreadable pending journal causes pruning to be skipped. Audit and Doctor export files are additive operational data and do not change dimension or configuration compatibility.

## Generated chunks

Ore profile, biome-filter, state, landmark-catalog, and gameplay changes apply according to their documented runtime scope. Ore provinces, themed strata/decorations, and landmark changes affect only newly generated chunks. Delvefold does not silently retrogen existing chunks.

Changing terrain shape, scale, or geology theme requires a confirmed recreation. The default workflow creates a timestamped backup, evacuates players, and applies the replacement only after restart (or after returning to title and reopening in singleplayer). Existing worlds default to Classic geology, preserving the pre-1.2 material layout.

Stable recreation mode preserves the established ore, province, themed geology, and landmark placement sequence. Rotating mode takes effect only when initialization or recreation commits; its derived salt is persisted, so ordinary restarts cannot silently move resources, themed strata, or landmarks in chunks generated later.

Landmark catalogs reload atomically. If any `data/<namespace>/delvefold/landmarks/*.json` definition or referenced template, processor list, or loot table is invalid, Delvefold retains the complete last-known-good catalog. Existing structure starts remain ordinary saved structure data; a later catalog change does not rewrite them.

Changing portal routing is live and does not recreate terrain. Existing saves remain coordinate-linked until an administrator opts into `central_hub`. Hub construction uses vanilla blocks in existing chunks, creates a guaranteed return portal, and protects the configured radius from unauthorized or environmental modification. Portal travel remains player-only in 1.3; entity transport is intentionally deferred.

Lifecycle-owned fields are locked during a live config reload. Delvefold keeps the active last-known-good snapshot if an edit attempts to replace the generation epoch, derived salt, terrain, terrain scale, geology theme, initialization state, or committed operation ID; restoring those values requires the normal restart-safe world or backup workflow.

## Multiplayer and network compatibility

The 1.3.x client/server protocol is version 12. It retains 1.2 guide format 2 and adds bounded backup-integrity, retention, Doctor, and portal-routing administration data. Matching 1.3 clients render the same geology-aware Seam Ledger plus the new operational states. Forecasts, imports, Doctor reports, and backup snapshots do not expose seeds, filesystem paths, confirmation tokens, server addresses, complete profiles, or server-side registry/profile fingerprints. Import capabilities remain random, expiring, player-bound, current-state-bound, and single-use for commit. A client and server with incompatible protocol versions cannot safely exchange Delvefold administration or guide payloads; use identical mod versions. Commands and canonical JSON remain available from the server console when no graphical client is connected. `/delvefold guide` prints a bounded text summary there in `public` or `operators` mode because the trusted console passes the operator check; `disabled` rejects the console as well as every other built-in opening source.

Permission fallback behavior is operator level 2 for configuration, level 4 for world lifecycle operations, and allowed for portal use. A NeoForge permission handler can override these nodes. Return travel to the Overworld is never denied.

## Integration API

`DelvefoldApi.API_VERSION` remains `1`. The additive `activeGuide()` method, guide format 2 geology view, and 1.2 `DelvefoldLandmarkDiscoveredEvent` retain their contracts; 1.3's backup, diagnostics, audit, retention, and central-hub services are internal administration facilities and do not alter the public API. `GuideSnapshot` retains its 1.1 constructor, defaulting legacy construction to Classic geology, while current `activeGuide()` snapshots use format 2. Existing public classes otherwise retain source and binary compatibility throughout the 1.x line. Later 1.x releases may add methods, record-independent event types, or enum values. Integrations should ignore unknown values where practical and must not depend on internal packages.
