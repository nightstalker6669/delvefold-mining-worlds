# Modpack and Mod Integration

Delvefold 1.4 exposes deterministic, server-authoritative integration points without requiring optional mods. The Java API is stable for the 1.x line; `DelvefoldApi.API_VERSION` is `1`. Additive methods and independent event types may appear in later 1.x releases, while existing public signatures retain source and binary compatibility.

## Datapack ore profiles

Place canonical schema-2 profile JSON at:

```text
data/<namespace>/delvefold/ore_profiles/<path>.json
```

For example, `data/examplepack/delvefold/ore_profiles/tagged_metals.json` becomes profile `examplepack:tagged_metals`. Nested paths are supported. The [complete example datapack](../examples/datapack) can be copied into a world's `datapacks` directory or packaged in a modpack.

Profiles are reloaded with server resources, validated before publication, limited to 256 KiB each, and read-only in Delvefold's GUI. Selection uses the normal profile GUI or:

```text
/delvefold profile select examplepack:tagged_metals
```

When resolving the same ID, script registration takes precedence over datapacks. Local per-save profiles use unnamespaced IDs and built-ins remain the final fallback.

## Conventional output tags

Use `block_tag` instead of `block` to support any installed mod that contributes to a conventional block tag:

```json
{
  "block_tag": "c:ores/tin",
  "weight": 2,
  "state": {},
  "replace_tag": "minecraft:stone_ore_replaceables"
}
```

Exactly one output source is required. Members are expanded in registry-ID order. `weight` is optional, accepts 1 through 1000, and defaults to `1`. Exact targets use their configured weight directly. A tag target's total weight is divided equally among its installed members, with fractional member weights supported internally, and only outputs sharing the same replacement-host tag compete during per-vein selection. For compatibility, a host group whose configured weights are all `1` retains the earlier member-uniform selection and exact random sequence; tag-total weighting begins when any target in that group has a non-default weight. If multiple sources resolve to the same block state, the first deterministically ordered candidate wins; later overlaps are ineffective, ignored, and warned rather than contributing more weight. Mark a cross-mod rule `required: false` if a pack should remain valid when no provider is installed.

These additions do not change configuration schema 2 or `DelvefoldApi.API_VERSION` 1. Datapack and script producers should treat an omitted weight as `1`; profiles whose configured target weights are all `1` retain the earlier member-uniform deterministic output-selection sequence. The 1.4.1 client/server protocol is version 14 and requires the identical 1.4.1 JAR. It retains guide format 2 and the bounded 1.3 operations views while carrying Unified Ores host classifications for stone, deepslate, netherrack, and end stone. Derived generation salts, catalog/scan tokens, registry fingerprints, filesystem paths, confirmation tokens, and administrative backup hashes are intentionally excluded from public API and player-facing guide views.

Unified Ores treats membership in a material-specific block tag shaped like `c:ores/<material>` as authoritative family metadata. Integrations get the best automatic grouping by contributing every provider-specific host variant to the matching tag. A conventional tag can group `example:copper_ore`, `other:copper_ore`, and their stone, deepslate, Nether, or End variants into one Copper family without changing any registry ID or saved target.

When a provider omits a material-specific tag, Delvefold may use clear conventional `<material>_ore`, `deepslate_<material>_ore`, `stone_<material>_ore`, or `ore_<material>` names as a safe fallback identity. Conflicting tag assignments, aggregate blocks, and unknown hosts remain review-required. When the resolved material agrees with a `nether`, `netherrack`, `end`, `endstone`, or `end_stone` filename affix, Delvefold maps that output to `c:netherracks` or `c:end_stones`. This agreement check prevents a material such as `end_steel` from being mistaken for an End-hosted Steel ore. Pack authors should still supply `c:ores/<material>` for the strongest cross-mod identity.

The Unified Ores library hides a family by default when an existing exact target or expanded tag target already covers any installed member. It never rewrites, merges, or auto-consolidates those existing rules. New family additions default to safe host candidates from Minecraft when present, otherwise the lexically first provider namespace; administrators may edit the resulting ordinary exact targets to enable or disable other providers or change any detected replacement tag. Each rule remains limited to 16 targets. Direct exact-ID commands and JSON remain available for blocks that intentionally should not share a family.

The guided profile importer uses the same discovery model. Suggestions remain previews only: Delvefold creates a new local inactive profile and never mutates a datapack/script profile or activates a result automatically. Material family IDs, catalog pages, and selections are transient administration data and are never serialized into schema-2 profiles.

Commands expose the same model:

```text
/delvefold ore target add-tag <rule> <block_tag> <replace_tag> [weight]
/delvefold ore target set-tag-weight <rule> <block_tag> <weight>
/delvefold ore target remove-tag <rule> <block_tag>
```

Omitting the optional add weight uses the compatibility default of `1`. Pack authors can also supply a non-default weight in schema-2 profile JSON or change it later with `set-tag-weight`.

## Regional ore provinces

A schema-2 spawn band may opt into deterministic regional placement. Omit `placement` to preserve classic vein behavior, or provide a province band:

```json
{
  "id": "tin_province",
  "placement": "province",
  "vein_size": 1,
  "attempts_per_chunk": 0.0,
  "distribution": "triangle",
  "min_y": -48,
  "max_y": 96,
  "peak_y": 12,
  "plateau_min_y": null,
  "plateau_max_y": null,
  "discard_on_air_exposure": 0.25,
  "province": {
    "region_size": 512,
    "radius": 192,
    "vertical_thickness": 48,
    "density": 0.08,
    "per_chunk_work_cap": 256
  }
}
```

Regional centers are derived independently of chunk generation order. Each chunk calculates the same centers but writes only within its own borders, and the shared `per_chunk_work_cap` bounds all province slices touching that chunk. Province work participates in the normal 4,096-attempt and 65,536-work-unit profile limits. Pack authors should run `/delvefold config validate` after installing or changing a profile. See [Configuration](CONFIGURATION.md#spawn-band) for every bound and the command equivalents.

## Reloadable landmark datapacks

Add landmark definitions under `data/<namespace>/delvefold/landmarks/<path>.json`. A complete entry references normal Minecraft datapack resources:

- structure template: `data/<namespace>/structure/<path>.nbt`;
- processor list: `data/<namespace>/worldgen/processor_list/<path>.json`;
- loot table: `data/<namespace>/loot_table/<path>.json`.

Definitions select a template, weight, category, terrain modes, placement style, height bounds, biome selectors, processor lists, and loot table. Their full schema and example are documented in [Configuration](CONFIGURATION.md#reloadable-landmark-catalog). Use the `survey_station`, `motherlode`, or `fault_line` category to honor the corresponding existing world-identity toggle. A template data marker named `loot` directly above an empty container installs the definition's loot table once.

The catalog reload is all-or-nothing. `/reload` validates every definition and dependency, including each compressed template's declared dimensions. Templates must be positive on every axis, at most 96 blocks wide/deep, and at most 384 blocks tall; an invalid or oversized dependency cannot replace the last-known-good catalog. Any error retains the prior complete catalog and publishes the failure to Delvefold diagnostics and the server log. Structure-system placement handles chunk boundaries and spacing. Pure Mining accepts no candidates, Balanced deterministically accepts 25%, and Abundant accepts 75% before definition weights are applied.

## Startup scripts

Scripting mods can call the dependency-free Java bridge during server startup. A KubeJS-style example is:

```javascript
const DelvefoldApi = Java.loadClass('com.nightsta69.delvefold.api.DelvefoldApi')
DelvefoldApi.registerOreProfileJson('my_pack', 'my_pack:scripted_metals', JSON.stringify(profileObject))
```

The owner and profile ID are validated, JSON is bounded and strictly decoded, and registrations exist for the current game process. The same owner may remove its own entry with `unregisterOreProfile(owner, profileId)`. Scripts take precedence over a datapack entry with the same ID.

## Permission nodes

NeoForge permission handlers can grant:

| Node | Default fallback | Purpose |
|---|---:|---|
| `delvefold.configure` | Operator level 2 | GUI, profiles, ores, gameplay, portal, and identity settings |
| `delvefold.manage_world` | Operator level 4 | Delete, recreate, renew, restore, backup management, and protected central-hub modification |
| `delvefold.use_portal` | Everyone | Enter the active mining world |

The integrated singleplayer owner keeps administrative access with cheats disabled. Return travel is never denied, preventing players from being trapped by a permission change.

## Server-operations files and automation

Delvefold 1.3 backup manifests, verification receipts, Doctor exports, retention reports, and audit entries are administration formats rather than public Java API. Modpacks may archive them, but should not edit them or assume undocumented fields are stable.

- `<save>/delvefold_backups/<id>/manifest.json` inventories normalized relative paths, sizes, and SHA-256 hashes. The selected backup is fully re-verified before restoration.
- `<save>/serverconfig/delvefold/exports/delvefold-doctor-*.json` is a field-whitelisted support report. It omits seeds, paths, confirmation tokens, addresses, complete profiles, and unrelated player information.
- `<save>/serverconfig/delvefold/audit/delvefold-audit.jsonl` is a rotating format-1 JSON-lines record of accepted mutations. It rotates at 10 MiB and retains five files including the active log.

Legacy backup migration is intentionally explicit: invoke `/delvefold backup verify <id>` or the Backup GUI action. Verification and SHA-256 hashing run off the tick thread. A successful legacy check creates a manifest but never activates or consumes that backup. Retention is disabled unless configured, always preserves pinned/pending/newest-two backups, and exposes its preview and last result through Doctor diagnostics before/after automatic deletion.

Portal routing remains `coordinate_linked` unless the server opts into `central_hub`. The hub uses only vanilla blocks plus Delvefold's existing portal blocks, creates a guaranteed return portal, and guards its configured horizontal radius from users without `delvefold.manage_world` and from environmental mutation. This does not add an entity-transport API: portal travel remains player-only throughout 1.x.

CurseForge publication is deliberately outside repository automation. GitHub tag releases may publish the JAR and checksum, while the project owner uploads the verified artifact and prepared changelog to CurseForge manually.

## Java API and events

`DelvefoldApi.activeWorld()` returns an optional immutable `MiningWorldView`. `DelvefoldApi.worldView(settings)` can convert a known settings snapshot. Never retain internal configuration services.

`DelvefoldApi.activeGuide()` is an additive API-v1 method returning `Optional<GuideSnapshot>` for the current server state. It is empty while the live configuration service is unavailable; otherwise it can describe an initialized or not-yet-initialized Delvefold world. The snapshot is immutable and read-only:

```java
import com.nightsta69.delvefold.api.DelvefoldApi;
import com.nightsta69.delvefold.guide.GuideSnapshot;

DelvefoldApi.activeGuide().ifPresent(guide -> {
    String worldName = guide.worldName();
    String geologyTheme = guide.geologyTheme();
    for (GuideSnapshot.OreEntry ore : guide.ores()) {
        // Publish or render the already-bounded player-facing data.
    }
});
```

The contract contains only whitelisted player-facing information: world display name, terrain, terrain variant, geology theme, active profile, coarse portal and renewal status, and enabled ore entries with output IDs or tags, representative block icons, terrain applicability, bounded biome include/exclude selectors, height summaries, vein sizes or province distributions, and relative frequency. It deliberately excludes seeds, horizontal coordinates, filesystem paths, replacement-host details, world-operation confirmation data, permissions, validation reports, configuration hashes, and administration diagnostics.

The active 1.4 guide remains format version 2. It includes the bounded `geologyTheme()` identifier and keeps the same limits: at most 96 ore entries, eight outputs and eight height bands per entry, three applicable terrains, 16 biome selectors per include list and 16 per exclude list, 64 characters for the world name, 128 characters for identifiers, and a conservative 24 KiB estimated network budget. The `truncated` flags tell consumers when a large profile was shortened. Consumers must tolerate future additive enum values and should display truncation rather than attempting to recover omitted internal data.

For API-v1 source and binary compatibility, `GuideSnapshot` retains the 1.1 constructor signature; it supplies `classic` geology when that legacy constructor is used, and the class still recognizes legacy format version 1 objects. `activeGuide()` returns current format 2, and protocol 14 transmits format 2 snapshots so the geology field cannot be silently omitted between a matching 1.4.1 client and server.

`activeGuide()` has no player argument and returns content, not an authorization decision. Delvefold's built-in command and item enforce `guide_visibility` separately. An integration that republishes the snapshot to its own audience remains responsible for that audience decision.

For the built-in player UI, the server issues a random, player-bound authorization with a ten-second lifetime alongside the snapshot. The client acknowledges only after installing the guide screen; the acknowledgement is single-use, and the server rechecks current visibility before awarding the consulting advancement. This short-lived acknowledgement is transport state and is not part of `GuideSnapshot` or `activeGuide()`.

The NeoForge game bus posts:

- `DelvefoldWorldLifecycleEvent` after initialization, recreation, or deletion commits;
- `DelvefoldOreProfileActivatedEvent` after profile selection;
- `DelvefoldPortalTravelEvent` immediately before destination resolution. This event is cancellable;
- `DelvefoldLandmarkDiscoveredEvent` when a server player newly enters a Delvefold landmark visit. It exposes the player, landmark definition ID, dimension key, and structure start chunk.

The landmark event is an additive API-v1 independent type; it does not change `DelvefoldApi.API_VERSION`. The built-in advancement remains a one-time player reward, while an integration may observe later visits as new discovery events after the player leaves and enters a landmark again. Listeners should use the immutable values supplied by each event and should not mutate Delvefold configuration from inside a lifecycle callback.

## JEI and EMI

JEI and EMI behavior is unchanged in 1.4.

When JEI or EMI is installed, the Portal Frame receives both an information page and a visual **Portal Construction** category. The category shows the minimum 2x3-interior frame, identifies Flint and Steel as the ignition catalyst, documents the supported 2x3 through 21x21 interior range, and reminds players that `/delvefold gui` initialization must happen first. The ordinary frame crafting recipe is discovered from vanilla recipe data and is not registered twice.

Both integrations are optional, client-only adapters. Delvefold does not load either API from common or server code, and the same release JAR works with neither viewer, JEI only, EMI only, or both installed. Development launch profiles are available for each combination:

```text
./gradlew runClient                    # Delvefold only
./gradlew runClientJei                 # JEI only
./gradlew runClientEmi                 # EMI only
./gradlew runClientRecipeViewers       # JEI and EMI together
```

IDE run configurations can opt into the same runtime classpaths with
`-Pdelvefold_recipe_viewers=jei`, `emi`, or `both`. These dependencies are development-only and are never bundled into Delvefold's JAR.

When JEI and EMI are installed together, Delvefold keeps EMI's native visual category and lets EMI's JEMI bridge import the JEI information page. The native EMI information page is suppressed in that combination, preventing duplicate help entries.

## Localization

Integration-provided user text should use translatable components. Delvefold's base keys live in `assets/delvefold/lang/en_us.json`; translations can be shipped by resource packs or contributed by adding the matching locale JSON without changing server behavior.
