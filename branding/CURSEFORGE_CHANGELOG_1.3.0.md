# Delvefold 1.3.0 — Server Operations

Delvefold 1.3.0 strengthens backup safety, adds practical server diagnostics and auditing, and introduces an optional protected central portal hub. Existing schema-2 worlds keep their current terrain, generation, and coordinate-linked portal behavior unless an administrator opts into a new setting.

## Verified backups and safer restoration

- Every new lifecycle and pre-restore backup now receives a manifest containing normalized relative paths, file sizes, backup metadata, and SHA-256 hashes.
- Added `/delvefold backup verify <backup>` and a matching Backup GUI action.
- Verification runs asynchronously on dedicated workers instead of hashing large files on the server tick thread.
- Older backups remain visible as legacy archives. Explicitly verifying a valid legacy backup creates its manifest; nothing is overwritten or activated automatically.
- Restore is available only after successful verification and performs another complete integrity check during startup before active files are changed.
- Fixed backup and restore coverage for all six mining-world folders: Classic and Expansive Flat, Cavern, and Wild.
- Pre-restore snapshots are transactional. A path, move, or manifest failure rolls back folders moved by that attempt, preserves data staged by an earlier interrupted attempt, and stops startup before Minecraft can regenerate a partial mining world.

## Optional backup retention

- Added configurable limits by backup count, age in days, and total bytes.
- Retention is disabled by default, and a zero value leaves that limit unbounded.
- Every automatic pass previews its deterministic prune set and rechecks each candidate before deletion.
- Pinned, pending, newest-two, legacy, invalid, unverified, non-restorable, and incompletely measured backups are always protected.
- Invalid pending-operation metadata causes the retention pass to be skipped conservatively.
- Configure retention with `/delvefold backup retention configure <max_count> <max_age_days> <max_total_bytes>` and disable it with `/delvefold backup retention disable`.

## Doctor diagnostics and audit trail

- Added `/delvefold doctor` and the Diagnostics GUI.
- Doctor reports versions, protocol/schema, all mining-dimension states, active-profile health, ineffective ore targets, pending operations, backup integrity, retention status, and disk estimates.
- Added `/delvefold doctor export` for a redacted JSON support report in the save's Delvefold exports directory.
- Doctor reports exclude world seeds, filesystem paths, confirmation tokens, server addresses, complete profile JSON, and unrelated player data.
- Added a rotating JSON-lines audit log for accepted configuration and lifecycle mutations.
- Covered scheduled renewals, configuration reloads, profile lifecycle changes, portal/hub settings, landmark catalog publication, backup pins, and asynchronous backup mutations in the audit trail, including successful work whose requesting player disconnects before completion.
- Audit entries contain a format version plus timestamp, actor, operation, affected logical object, and old/new revisions. Logs rotate at 10 MiB and retain five files including the active log.
- Restore leaves the live audit trail, import/export transfer files, and lifecycle history intact while the selected settings and ore profiles are installed.

## Central-hub portal routing

- Added `coordinate_linked` and `central_hub` routing modes. Coordinate-linked remains the compatibility default.
- Central-hub mode routes incoming players to a configured location and creates a safe vanilla-block platform with a guaranteed return portal.
- The protected radius defaults to 16 blocks and accepts 8–256. Only players with world-management permission can modify protected positions; explosions, pistons, fluids, trampling, and mob griefing are also blocked.
- Added `/delvefold portal`, `/delvefold portal routing <coordinate_linked|central_hub>`, and `/delvefold portal hub <x> <z> <protection_radius>`.
- Portal travel remains player-only in 1.3. Mobs, dropped items, boats, and minecarts do not pass through Delvefold portals.

## Accessibility and compatibility

- Completed keyboard focus, narration, tooltip, translation-key, and color-independent status improvements across administration screens.
- JEI and EMI support is unchanged and remains optional. Delvefold works with neither viewer, either viewer, or both.
- Configuration schema remains **2**.
- Public Delvefold API remains **version 1**.
- Client/server network protocol is now **12**; install the identical Delvefold 1.3.0 JAR on the server and every client.
- Existing saves default to disabled retention and coordinate-linked portal routing without migration or load-time rewriting.

## Requirements

- Minecraft Java Edition **1.21.1**
- NeoForge **21.1.244 or newer** for Minecraft 1.21.1
- Java **21**
- Environment: **client and server**

Ore, landmark, and geology behavior from 1.2 remains compatible. This file is prepared for the project owner's manual CurseForge upload; Delvefold's repository does not publish to CurseForge automatically.
