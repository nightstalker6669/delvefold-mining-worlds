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

## Getting useful diagnostics

Include the Delvefold version, NeoForge version, Java version, complete mod list, exact reproduction steps, and `latest.log`. Include relevant `ores.json` and `settings.json` when safe. Never post authentication tokens, server control credentials, private addresses, or unrelated player data.
