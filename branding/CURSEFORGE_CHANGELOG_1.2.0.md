# Delvefold 1.2.0 — Living Geology

Living Geology gives Delvefold's mining worlds more character, regional variety, and discoveries while preserving existing schema-2 saves.

## Geology themes

- Choose Classic, Volcanic, Dripstone, Lush, or Crystal geology during initialization or a confirmed recreation.
- The four themed options add bounded vanilla-block strata, decorations, sealed fluid pockets, particles, sounds, and ambience.
- Classic remains the compatibility default, so existing worlds keep their established material layout.
- Themes do not replace Delvefold's terrain generators or change registered dimension IDs.
- The Seam Ledger and `/delvefold guide` now show the active geology theme alongside terrain, scale, profile, and ore guidance.

## Reloadable landmarks

- Discover six bundled structure-system landmarks: Survey Camp, Collapsed Mine Entrance, Lift Station, Geode Vault, Motherlode Chamber, and Fault-line Grotto.
- Pure Mining disables candidates, Balanced deterministically accepts 25%, and Abundant accepts 75%.
- Existing survey-station, motherlode, and fault-line toggles can narrow the available landmarks further.
- Landmark loot is deterministic and assigned only once to empty marked containers, even if a player later empties them.
- Discovering a landmark awards the new **Signs in the Stone** advancement.
- Modpack authors can add or replace landmark definitions with datapacks. Invalid reloads retain the complete last-known-good catalog and report useful diagnostics.

## Regional ore provinces

- Ore bands can now use classic Vein placement or optional Province placement.
- Province controls include region size, horizontal radius, vertical thickness, density, and a hard per-chunk work cap.
- Regional centers remain deterministic across chunk borders, while each generating chunk writes only to itself.
- Configure provinces through the ore-rule GUI, canonical schema-2 JSON, or the new placement and province commands.
- Forecasts and safety validation include province work before a profile can be activated.

## Compatibility

- Configuration schema remains version 2.
- Public API remains version 1 and adds a standalone landmark-discovery event.
- Existing ore bands default to Vein placement; existing world settings default to Classic geology.
- This release uses client/server protocol 11 and guide format 2 for the bounded geology view. Install the same Delvefold version on every client and the server.
- Minecraft 1.21.1, NeoForge 21.1.x, and Java 21 remain required.

<!-- CurseForge publication is intentionally deferred to the project owner until the complete post-1.0 roadmap is finished. -->
