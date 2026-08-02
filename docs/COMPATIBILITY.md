# Compatibility and Upgrades

## Supported platform

Delvefold 1.4 targets Minecraft Java Edition 1.21.1, NeoForge 21.1.244 or newer for Minecraft 1.21.1, and Java 21. Install the same Delvefold JAR on every client and the server. Singleplayer uses the same server-authoritative implementation through Minecraft's integrated server.

JEI, EMI, permission handlers, scripting mods, and mods that contribute ores are optional. Delvefold resolves blocks and tags after registration and does not declare a hard dependency on those integrations. JEI 19.18.3–19.x and EMI 1.1.x receive the Portal Frame recipe plus Delvefold's visual portal-construction guide.

## Save compatibility

Configuration schema 2 is stable for Delvefold 1.x. Worlds created with Delvefold 0.2 through 1.3 upgrade directly to 1.4. Fields introduced after 0.2 receive conservative defaults when absent. `guide_visibility` defaults to `public`, renewal `seed_mode` defaults to `stable`, `identity.geology_theme` defaults to `classic`, `backup_retention` defaults to disabled with unbounded limits, portal `routing_mode` defaults to `coordinate_linked`, the hub defaults to 0,0 with radius 16, and server-maintained `generation_salt` defaults to `0`. Existing schema-2 saves require no migration or load-time rewrite. Existing exact-block targets remain valid alongside 0.4's `block_tag` targets.

Unified Ores adds no persisted family field. Material grouping exists only in the bounded server catalog and client editor, while accepted additions become ordinary schema-2 exact targets. Existing rules are never auto-consolidated, renamed, or switched between exact and tag output. A family is hidden from the default library view when any candidate is already covered by an exact target or expanded block tag, but **Show configured** reveals it. The existing maximum of 16 targets per rule still applies.

The optional 1.1 target `weight` remains part of schema 2. It accepts integers from 1 through 1000 and defaults to `1` when absent. Exact targets use their configured weight directly, while one tag target's total weight is divided equally among its installed, registry-sorted members. Fractions are supported internally. Identical resolved block states are deduplicated first-wins, with later overlaps ignored and warned. Existing profiles and new profiles whose configured weights are all `1` preserve the established member-uniform per-vein output selection and exact random sequence; this compatibility path intentionally precedes tag-total weighting until any target in that host group uses a non-default weight.

The optional 1.2 band `placement` also remains part of schema 2. Omitted values load as `vein`, and a missing or null `province` object preserves established vein generation. Only bands explicitly changed to `province` use regional centers, density, vertical thickness, and per-chunk work caps. Province placement is deterministic across chunk borders but writes only inside the chunk currently generating. The existing aggregate attempt/work limits include province caps, so malformed or excessively expensive profiles are rejected before publication.

Schema-1 configuration is intentionally not migrated in place because its dimension contract predates the stable terrain and lifecycle model. Delvefold detects it, leaves every file and dimension folder untouched, disables mutations and portal entry, and explains that a new save is required.

Always back up the complete save before changing mod versions. Do not remove Delvefold while a deletion, recreation, renewal, or restore operation is pending.

Backups created by 1.3 include normalized-path SHA-256 manifests and verification receipts. Earlier Delvefold backups remain listed as legacy archives. They are not deleted or silently trusted: an administrator must explicitly run `/delvefold backup verify <backup>` to validate the old layout and create its manifest before restore becomes available. Every restore re-verifies the selected backup at startup. Restore coverage includes all six Classic/Expansive Flat, Cavern, and Wild save folders.

Automatic retention is disabled on upgrade. Enabling it never prunes pinned backups, pending-operation references, or the newest two backups. An unreadable pending journal causes pruning to be skipped. Audit and Doctor export files are additive operational data and do not change dimension or configuration compatibility.

## Generated chunks

Ore profile, biome-filter, state, landmark-catalog, and gameplay changes apply according to their documented runtime scope. Ore provinces, themed strata/decorations, and landmark changes affect only newly generated chunks. Delvefold does not silently retrogen existing chunks.

Delvefold 1.3.3 raises the shared Cavern dimension type's ambient-light floor from `0.0` to `0.1`, matching the conservative visual floor used by the vanilla Nether. That visual change applies to Classic and Expansive Cavern chunks after a full restart and does not require recreation. It does not add block light or skylight, change F3 light values, or relax hostile-mob spawn checks; ordinary lighting remains necessary to make an area spawn-proof.

The same patch gives both Cavern variants dedicated Delvefold noise settings built from continuous solid stone hosts. Compact, vertically bounded custom cave carving creates underground passages without the previous giant terrain-density voids or detached floating-island shelves. Expansive remains taller and deeper than Classic, but no longer inherits amplified terrain. Both scales use stone/deepslate surfaces, solid floor and roof safety bands, no sea-level fill or aquifers, no underground lava lake, and no water/lava springs. Sparse shallow water candidates probe from Y 32 through Y 112 and scan down by at most 32 blocks for an exposed stone floor; cave carving retains localized lava at Y −56 and below. Existing chunks are never rewritten, so old grass, dirt, lakes, flowing water, enormous voids, and floating shelves remain where they were generated and can meet new terrain at chunk borders. Use the confirmed recreation workflow for a uniform Cavern. Stable recreation still preserves Delvefold's generation salt, but ore air-exposure checks, geology placement success, landmark elevation, and final block positions can differ because the underlying Cavern topology, surfaces, fluids, and heightmaps intentionally changed.

Integrations should identify Cavern levels through Delvefold's stable dimension or biome keys rather than requiring the generator-settings holder to be `minecraft:caves` or `minecraft:amplified`; 1.3.3 uses `delvefold:delve_cavern` and `delvefold:delve_cavern_expansive`. The Cavern sea-level API value is now `-64`, below generated space, so third-party features that use sea level as a placement reference may behave differently. Third-party carvers or replacement features can still alter Delvefold's solid floor and roof safety bands. Wild dimensions continue using the vanilla settings keys and are unaffected.

Changing terrain shape, scale, or geology theme requires a confirmed recreation. The default workflow creates a timestamped backup, evacuates players, and applies the replacement only after restart (or after returning to title and reopening in singleplayer). Existing worlds default to Classic geology, preserving the pre-1.2 material layout.

Stable recreation mode preserves the established ore, province, themed geology, and landmark placement sequence. Rotating mode takes effect only when initialization or recreation commits; its derived salt is persisted, so ordinary restarts cannot silently move resources, themed strata, or landmarks in chunks generated later.

Landmark catalogs reload atomically. If any `data/<namespace>/delvefold/landmarks/*.json` definition or referenced template, processor list, or loot table is invalid, Delvefold retains the complete last-known-good catalog. Existing structure starts remain ordinary saved structure data; a later catalog change does not rewrite them.

Changing portal routing is live and does not recreate terrain. Existing saves remain coordinate-linked until an administrator opts into `central_hub`. Hub construction uses vanilla blocks in existing chunks, creates a guaranteed return portal, and protects the configured radius from unauthorized or environmental modification. Portal travel remains player-only throughout 1.x; entity transport is intentionally deferred.

Lifecycle-owned fields are locked during a live config reload. Delvefold keeps the active last-known-good snapshot if an edit attempts to replace the generation epoch, derived salt, terrain, terrain scale, geology theme, initialization state, or committed operation ID; restoring those values requires the normal restart-safe world or backup workflow.

## Multiplayer and network compatibility

The 1.4.0 client/server protocol is version 13. It retains guide format 2 and the established backup, Doctor, forecast, and portal-administration views, then adds bounded server-side material-family search, paging, and batch-add requests. Family catalogs and import capabilities are random, expiring, player-bound, revision-bound, and registry-bound; the server re-resolves selected IDs before accepting one atomic mutation. These views do not expose seeds, filesystem paths, confirmation tokens, server addresses, complete profiles, or raw server-side fingerprints. A client and server with incompatible protocol versions cannot safely exchange Delvefold administration or guide payloads, so use the identical 1.4.0 JAR everywhere. Commands and canonical JSON remain available from the server console when no graphical client is connected. `/delvefold guide` prints a bounded text summary there in `public` or `operators` mode because the trusted console passes the operator check; `disabled` rejects the console as well as every other built-in opening source.

Permission fallback behavior is operator level 2 for configuration, level 4 for world lifecycle operations, and allowed for portal use. A NeoForge permission handler can override these nodes. Return travel to the Overworld is never denied.

## Integration API

`DelvefoldApi.API_VERSION` remains `1`. The additive `activeGuide()` method, guide format 2 geology view, and 1.2 `DelvefoldLandmarkDiscoveredEvent` retain their contracts; 1.3's server-operations facilities and 1.4's Unified Ores catalog are internal administration facilities and do not alter the public API. `GuideSnapshot` retains its 1.1 constructor, defaulting legacy construction to Classic geology, while current `activeGuide()` snapshots use format 2. Existing public classes otherwise retain source and binary compatibility throughout the 1.x line. Later 1.x releases may add methods, record-independent event types, or enum values. Integrations should ignore unknown values where practical and must not depend on internal packages.
