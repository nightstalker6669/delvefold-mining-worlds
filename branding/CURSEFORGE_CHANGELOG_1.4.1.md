# Delvefold 1.4.1 — Host-Aware Unified Ores

This maintenance release makes Unified Ores trust the obvious cases while keeping genuinely ambiguous blocks safe.

## Fixed

- Clear conventional ore names no longer receive a blanket **Needs review** warning when a mod does not provide `c:ores/<material>`.
- A family with safe variants and one unusual variant now marks only that unusual variant for review instead of treating the whole family as unsafe.
- Tagged Nether ore variants now automatically replace `#c:netherracks`.
- Tagged End ore variants now automatically replace `#c:end_stones`.
- Prefix and suffix forms are supported, while material names such as End Steel remain correctly classified as their own material.
- Safe variants now explain the detected host and replacement tag in their tooltip. The detected tag remains editable in the ore-rule screen.

## Compatibility

- No configuration migration or save rewrite is required.
- Configuration schema remains 2 and the public API remains version 1.
- Client/server protocol is now 14. Install the identical Delvefold 1.4.1 JAR on every client and server.
- JEI, EMI, Mekanism and its modules, Ender IO, Silent's Gems, Applied Energistics 2, WorldEdit, and all other compatibility fixtures remain optional and are not bundled or required dependencies.
- Verified 683 unit tests, all 40 required NeoForge GameTests, JSON and translation validation, a clean release build, and a dedicated-server startup and orderly shutdown.

Release file: `delvefold-1.21.1-1.4.1.jar`
