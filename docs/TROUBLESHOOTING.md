# Troubleshooting

## The portal will not ignite

Run `/delvefold status` and confirm the world is initialized. Initialization happens only through `/delvefold gui` or `/delvefold initialize`; portal activation never chooses settings. Verify that the frame is a complete rectangle with a 2x3 through 21x21 interior, then right-click a frame block with vanilla Flint and Steel.

On a server, check `delvefold.use_portal` and `delvefold.configure` permissions. A failed ignition does not consume Flint and Steel durability.

## The GUI does not open

Use the same Delvefold version on the client and server. `/delvefold gui` is player-only; the dedicated-server console should use commands. The integrated singleplayer owner can administer the world with cheats disabled. On a dedicated server, grant the appropriate permission node or operator fallback.

## A modded ore does not appear

Run `/delvefold config validate`. Confirm the block ID exists or that the output tag has at least one installed member. Optional missing outputs warn and skip; required missing outputs reject the profile. Ensure the rule is enabled, includes the active terrain and biome, has a nonzero attempt rate, and targets the correct replacement tag.

Changes affect new chunks only. Travel beyond previously generated mining-world chunks or recreate the world after making a backup.

## JSON reload was rejected

Delvefold uses strict schema-2 JSON: misspelled, misplaced, and unknown fields are errors. Validate against the files in `schemas/`, keep files below documented size limits, and use `/delvefold config validate` before `/delvefold config reload`. A rejected edit is not written over and the last-known-good runtime snapshot remains active.

## Deletion, recreation, or restore is pending

These operations never delete a loaded dimension. Stop the dedicated server normally and restart it; in singleplayer, exit to title and reopen the save. Do not force-load the mining dimension during startup. `/delvefold world cancel` or the backup GUI can cancel an operation before it is applied.

If startup fails, preserve the save and inspect `latest.log`, `serverconfig/delvefold/`, and the Delvefold backup/operation metadata before changing files. Open a bug report with those files after removing personal information.

## A backup is archive-only or will not restore

Run `/delvefold backup list`, then `/delvefold backup verify <backup-id>`. Verification is asynchronous and may take time for a large world; completion is reported later without blocking server ticks.

A pre-1.3 backup is intentionally archive-only until this explicit validation succeeds. Delvefold checks its legacy operation/configuration/dimension layout, creates `manifest.json`, hashes every immutable file, and writes a verification receipt. It never repairs or guesses corrupted contents. If a file is missing, added, changed in size, or differs from its SHA-256 hash, verification fails and restore remains disabled.

Restore repeats the complete check during startup. If files changed after an earlier successful verification, the pending restore remains recoverable and Delvefold stops that startup before Minecraft can load or regenerate missing dimension folders. Pre-restore folder moves are transactional and roll back changes made by a failed attempt; data staged by an earlier interrupted attempt is preserved for a safe forward retry. Repair or replace the damaged selected backup, or cancel the pending restore journal only after confirming the active folders are intact. Do not edit a backup's manifest or verification file manually.

Backup and restore cover all six Delvefold folders: Classic and Expansive Flat, Cavern, and Wild. If an older restore appears to omit an Expansive folder, keep the original backup and report the Delvefold version that created it.

## Automatic retention did not prune a backup

This is normally a safety decision. Retention is disabled by default, `0` means unbounded for that limit, and Delvefold never automatically removes:

- a pinned backup;
- either of the newest two backups;
- the selected or pre-restore backup referenced by a pending restore;
- a recoverable backup referenced by pending deletion or recreation.

Run `/delvefold backup retention` and `/delvefold doctor`. Doctor reports the proposed prune reasons, protected/unmet limits, and the latest automatic result. A malformed pending-operation journal causes retention to skip the entire pass conservatively. Resolve the pending operation instead of deleting its files by hand.

## Central-hub routing does not work as expected

Run `/delvefold portal` and confirm the mode is `central_hub`. Hub coordinates must be inside the world border with room for the platform, and the configured radius must be 8–256 blocks. The hub is constructed when a player enters the mining dimension; it is not a general structure-generation feature.

Players without `delvefold.manage_world` cannot change blocks inside the protected horizontal radius. Explosions, pistons, fluids, trampling, and mob griefing are also blocked there. This is intentional. Delvefold portals transport players only in 1.3, so mobs, items, boats, and minecarts remaining behind is expected behavior.

## Client and server report a protocol mismatch

Delvefold 1.3 uses network protocol 12 and requires the identical Delvefold version on the client and server. Configuration schema 2 and public API version 1 do not make mismatched JARs network-compatible. Update every client and the server to the same file; optional JEI and EMI may still be installed independently and are not bundled.

## Getting useful diagnostics

Run `/delvefold doctor` first. Administrators can use `/delvefold doctor export` to create a redacted JSON report under `serverconfig/delvefold/exports/`; it includes bounded version, dimension, profile-health, pending-operation, backup, retention, and disk estimates without filesystem paths, seeds, confirmation tokens, server addresses, full profiles, or unrelated player data.

Also include the Delvefold version, NeoForge version, Java version, complete mod list, exact reproduction steps, and `latest.log`. Include relevant `ores.json` and `settings.json` when safe. The mutation audit lives in `serverconfig/delvefold/audit/`, rotates at 10 MiB, and can help establish which accepted operation happened before a fault. Review it before sharing even though Delvefold never records confirmation tokens, full profiles, server addresses, or unrelated player data. Never post authentication tokens, server control credentials, private addresses, or unrelated player data.
