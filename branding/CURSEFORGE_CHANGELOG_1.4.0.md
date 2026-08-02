# Delvefold 1.4.0 — Unified Ores

Delvefold 1.4.0 makes large modpacks much easier to configure by grouping equivalent ores from different mods into logical material families.

## New: Unified Ores

- Copper, tin, silver, and other provider variants now appear under one material family instead of as an unorganized block list.
- `c:ores/<material>` tags provide authoritative grouping across mods.
- Conservative registry-name fallback keeps untagged ore-like blocks discoverable and marks uncertain identities or replacement hosts for review.
- Search and paging run on the server, keeping very large registries bounded.
- **Previous** and **Next** now move through adjacent visible windows in both directions, including when crossing server-page boundaries, while preserving search filters and selections.
- Families already represented by an exact target or output tag are hidden by default; **Show configured** reveals them.
- Select families across pages and add up to 128 in one atomic request. If the server finds a stale revision, unknown family, invalid target, or unsafe workload, nothing is partially added.
- New families default to Minecraft's safe stone/deepslate variants when available, otherwise the lexically first provider namespace.
- Other provider variants remain visible but disabled by default, preventing duplicate output until you intentionally enable it in the ore-rule editor.
- Each rule still supports up to 16 enabled targets.
- Direct registry-ID entry remains available for unusual ores or blocks that should stay separate.

## Compatibility

- Existing exact-block targets, block-tag targets, rule IDs, profiles, dimensions, portal settings, and world-generation settings remain compatible.
- Existing rules are never automatically merged, consolidated, or rewritten.
- Material families are a GUI/discovery feature; saved rules continue using configuration schema **2**.
- Public Delvefold API remains **version 1**.
- Client/server network protocol is now **13**. Install the identical Delvefold 1.4.0 JAR on the server and every client.
- Ore changes affect newly generated mining-world chunks only. Recreate the mining world for a uniform result.
- JEI and EMI remain optional and are not bundled.

## Validation

- Passed 665 JUnit tests and all 40 required NeoForge GameTests.
- Passed JSON, translation, formatting, static-analysis, Javadoc, build, and dedicated-server startup checks.
- In-game acceptance testing used JEI, EMI, Mekanism, Ender IO with Athena, Silent's Gems, Applied Energistics 2, GuideME, and WorldEdit together. These remain test-only installations and are not bundled or required.

## Requirements

- Minecraft Java Edition **1.21.1**
- NeoForge **21.1.244 or newer** for Minecraft 1.21.1
- Java **21**
- Environment: **client and server**

Release file: `delvefold-1.21.1-1.4.0.jar`

CurseForge publication remains a manual project-owner step.
