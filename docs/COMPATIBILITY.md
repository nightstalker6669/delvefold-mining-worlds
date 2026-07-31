# Compatibility and Upgrades

## Supported platform

Delvefold 1.0 targets Minecraft Java Edition 1.21.1, NeoForge 21.1.x, and Java 21. Install the same Delvefold JAR on every client and the server. Singleplayer uses the same server-authoritative implementation through Minecraft's integrated server.

JEI, EMI, permission handlers, scripting mods, and mods that contribute ores are optional. Delvefold resolves blocks and tags after registration and does not declare a hard dependency on those integrations.

## Save compatibility

Configuration schema 2 is stable for Delvefold 1.x. Worlds created with Delvefold 0.2, 0.3, or 0.4 upgrade directly to 1.0. Identity fields introduced after 0.2 receive conservative defaults when absent. Existing exact-block targets remain valid alongside 0.4's `block_tag` targets.

Schema-1 configuration is intentionally not migrated in place because its dimension contract predates the stable terrain and lifecycle model. Delvefold detects it, leaves every file and dimension folder untouched, disables mutations and portal entry, and explains that a new save is required.

Always back up the complete save before changing mod versions. Do not remove Delvefold while a deletion, recreation, renewal, or restore operation is pending.

## Generated chunks

Ore profile, biome-filter, state, landmark, and gameplay changes apply according to their documented runtime scope. Ore and landmark generation changes affect only newly generated chunks. Delvefold does not silently retrogen existing chunks.

Changing terrain shape or scale requires a confirmed recreation. The default workflow creates a timestamped backup, evacuates players, and applies the replacement only after restart (or after returning to title and reopening in singleplayer).

## Multiplayer and network compatibility

The administration protocol is versioned. A client and server with incompatible protocol versions cannot safely exchange Delvefold GUI payloads; use identical mod versions. Commands and canonical JSON remain available from the server console when no graphical client is connected.

Permission fallback behavior is operator level 2 for configuration, level 4 for world lifecycle operations, and allowed for portal use. A NeoForge permission handler can override these nodes. Return travel to the Overworld is never denied.

## Integration API

`DelvefoldApi.API_VERSION` is `1`. Public classes under `com.nightsta69.delvefold.api` retain source and binary compatibility throughout the 1.x line. Later 1.x releases may add methods, record-independent event types, or enum values. Integrations should ignore unknown values where practical and must not depend on internal packages.
