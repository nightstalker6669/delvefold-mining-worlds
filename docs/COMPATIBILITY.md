# Compatibility and Upgrades

## Supported platform

Delvefold 1.0 targets Minecraft Java Edition 1.21.1, NeoForge 21.1.x, and Java 21. Install the same Delvefold JAR on every client and the server. Singleplayer uses the same server-authoritative implementation through Minecraft's integrated server.

JEI, EMI, permission handlers, scripting mods, and mods that contribute ores are optional. Delvefold resolves blocks and tags after registration and does not declare a hard dependency on those integrations. JEI 19.18.3–19.x and EMI 1.1.x receive the Portal Frame recipe plus Delvefold's visual portal-construction guide.

## Save compatibility

Configuration schema 2 is stable for Delvefold 1.x. Worlds created with Delvefold 0.2, 0.3, or 0.4 upgrade directly to 1.0. Identity fields introduced after 0.2 receive conservative defaults when absent. The additive `guide_visibility` field defaults to `public` when absent, so existing schema-2 saves require no migration. Existing exact-block targets remain valid alongside 0.4's `block_tag` targets.

The optional 1.1 target `weight` remains part of schema 2. It accepts integers from 1 through 1000 and defaults to `1` when absent. Exact targets use their configured weight directly, while one tag target's total weight is divided equally among its installed, registry-sorted members. Fractions are supported internally. Identical resolved block states are deduplicated first-wins, with later overlaps ignored and warned. Existing profiles and new profiles whose configured weights are all `1` preserve the established member-uniform per-vein output selection and exact random sequence; this compatibility path intentionally precedes tag-total weighting until any target in that host group uses a non-default weight.

Schema-1 configuration is intentionally not migrated in place because its dimension contract predates the stable terrain and lifecycle model. Delvefold detects it, leaves every file and dimension folder untouched, disables mutations and portal entry, and explains that a new save is required.

Always back up the complete save before changing mod versions. Do not remove Delvefold while a deletion, recreation, renewal, or restore operation is pending.

## Generated chunks

Ore profile, biome-filter, state, landmark, and gameplay changes apply according to their documented runtime scope. Ore and landmark generation changes affect only newly generated chunks. Delvefold does not silently retrogen existing chunks.

Changing terrain shape or scale requires a confirmed recreation. The default workflow creates a timestamped backup, evacuates players, and applies the replacement only after restart (or after returning to title and reopening in singleplayer).

## Multiplayer and network compatibility

The 1.1.0 client/server protocol is version 8. It carries weighted ore-target drafts in addition to bounded guide snapshots and the short-lived client-open acknowledgement used for the consulting advancement. A client and server with incompatible protocol versions cannot safely exchange Delvefold administration or guide payloads; use identical mod versions. Commands and canonical JSON remain available from the server console when no graphical client is connected. `/delvefold guide` prints a bounded text summary there in `public` or `operators` mode because the trusted console passes the operator check; `disabled` rejects the console as well as every other built-in opening source.

Permission fallback behavior is operator level 2 for configuration, level 4 for world lifecycle operations, and allowed for portal use. A NeoForge permission handler can override these nodes. Return travel to the Overworld is never denied.

## Integration API

`DelvefoldApi.API_VERSION` is `1`. The additive `activeGuide()` method does not change that version. Public classes under `com.nightsta69.delvefold.api` retain source and binary compatibility throughout the 1.x line. Later 1.x releases may add methods, record-independent event types, or enum values. Integrations should ignore unknown values where practical and must not depend on internal packages.
