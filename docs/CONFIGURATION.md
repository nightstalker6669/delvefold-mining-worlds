# Configuration

Schema 2 is the stable Delvefold 1.x configuration format. If schema-1 files are detected, Delvefold leaves them and all existing dimension data untouched, disables configuration mutations and portal entry, and reports that a new save is required.

Delvefold has one canonical configuration model. The GUI, commands, and JSON all validate and write the same immutable server snapshot.

## Save scope and loading

- `ores.json` contains the ore profile and independently revisioned ore rules.
- `settings.json` contains explicit initialization state, terrain shape, scale and geology theme, world identity, landmark policy, renewal schedule, generation epoch, gameplay settings, portal routing, and optional backup retention.
- Missing files are created from built-in defaults.
- Invalid edits are rejected without overwriting the rejected files. If a valid configuration was already active, it remains the last-known-good runtime snapshot.
- Disk writes use temporary sibling files, forced flush, and atomic replacement where the filesystem supports it. A small transaction journal makes the two JSON documents restart-recoverable if a write is interrupted between them.
- Files larger than 4 MiB are rejected as a safety limit.

Use `/delvefold config validate` before reloading, then `/delvefold config reload`. A reload affects future chunks only.

## Ore profile

Top-level fields:

| Field | Meaning |
|---|---|
| `schema_version` | Currently `2`. Other schemas are rejected without overwriting them. |
| `revision` | Optimistic-concurrency revision. Do not decrease it. |
| `profile` | Descriptive profile name such as `vanilla_balanced`, `rich`, or `custom`. |
| `rules` | Ordered array of at most 512 ore rules. |

### Ore rule

| Field | Meaning |
|---|---|
| `id` | Unique stable ID using lowercase letters, numbers, dot, dash, or underscore. |
| `enabled` | Disables placement without deleting the rule. |
| `required` | Missing output blocks are errors when true; optional blocks warn and skip when false. |
| `terrain_modes` | One or more of `flat`, `cavern`, and `wild`. |
| `targets` | Output block variants and the host tags each can replace; maximum 16. |
| `biomes.include` / `exclude` | Biome IDs or `#tag` selectors. Mining biomes are the default. |
| `bands` | Independently salted placement bands; maximum 16. |

### Target

```json
{
  "block": "examplemod:tin_ore",
  "weight": 2,
  "state": {},
  "replace_tag": "minecraft:stone_ore_replaceables"
}
```

`state` may set valid properties exposed by the selected block. The GUI normally leaves it empty, which uses the block's default state. The optional `weight` is an integer from 1 through 1000 and defaults to `1`. It controls the relative chance of this output when several targets share the same `replace_tag`; it does not change vein count, vein size, height distribution, or the generation-work safety budget.

An output can instead resolve every installed block in a conventional tag. Set exactly one of `block` and `block_tag`:

```json
{
  "block_tag": "c:ores/tin",
  "weight": 3,
  "state": {},
  "replace_tag": "minecraft:stone_ore_replaceables"
}
```

Tag members are expanded in registry-ID order for deterministic generation. A tag target's configured total weight is divided equally among its installed members; for example, weight `3` across three members gives each member weight `1`, while two members receive `1.5` each internally. Exact-block targets use their configured weights directly. Selection occurs independently inside each host-tag group, so targets with different `replace_tag` values do not compete with one another. One compatibility exception applies when every configured target in a host group has weight `1`: Delvefold preserves the pre-weight member-uniform selection and its exact random sequence, so each expanded member remains equally likely. Tag-total weighting takes effect as soon as that group contains a non-default weight.

If exact targets, tag targets, or repeated tag membership resolve to the same block state in one host group, Delvefold keeps the first deterministically ordered candidate. Later overlaps are ineffective, do not add their weight to the first candidate, and produce a warning so the profile can be cleaned up.

Profiles written before 1.1.0 have no `weight` field and continue to behave as weight `1`. Profiles whose configured weights are all `1` retain the existing member-uniform deterministic per-vein output-selection sequence. Weighted targets are an additive schema-2 feature and require no schema migration. A missing optional output tag warns and skips; a missing required output tag rejects the profile.

### Forecast, Unified Ores, and guided import

The Ores GUI forecast is derived at request time from a named profile and the server's current block/tag registry. It does not add fields to schema 2 or write files. Its configured totals include the profile's declared work; effective totals additionally account for enabled state, active terrain, biome applicability, resolved output blocks/tags, valid block states, and deduplicated host-specific outputs. The height overlay is available only after a terrain has been initialized.

The Ores GUI's **Add ores** library discovers logical material families across installed providers. Membership in `c:ores/<material>` is the authoritative identity signal. Blocks without a material-specific conventional tag may be grouped through a conservative `<material>_ore`, `deepslate_<material>_ore`, `stone_<material>_ore`, or `ore_<material>` name fallback; fallback identity and uncertain host variants are marked for review instead of being silently trusted. Ambiguous material-tag assignments are never forced into one family.

Family search and paging run on the server's current registry snapshot. A family is considered configured when any of its installed members is already covered by an exact target or by an expanded block-tag target in the active profile. Configured families are hidden by default and can be displayed with **Show configured**. Selections persist across pages, and one request may add up to 128 families atomically. The server re-resolves every family, checks the expected ore revision and complete profile, validates the normal work budget, and either commits one new revision or adds nothing.

For each new family, Delvefold enables safe stone/deepslate variants from one provider: Minecraft when available, otherwise the lexically first provider namespace. Other installed providers remain available in the family editor but are off by default, preventing duplicate copper, tin, or similar output unless an administrator deliberately enables it. Added rules use the existing Uncommon template. Each saved rule still has the schema-2 maximum of 16 enabled targets. Use the ore-rule wizard afterward to toggle provider and host variants, or use direct registry-ID entry for an unusual block that cannot be grouped safely.

Material families are transient discovery and GUI views, not a new JSON field. Existing exact targets, output tags, rules, and profiles are never automatically merged or rewritten. The Profiles GUI's guided importer uses the same family discovery while retaining its create-new workflow: it skips families covered by the base profile, previews the complete diff and per-terrain workload, creates a new inactive local profile, and never overwrites or activates a result automatically. Built-in, datapack, scripted, or local ID collisions are rejected and symlinks are not followed.

No forecast, family ID, catalog page, selection, scan token, registry fingerprint, diff, or import-session state is serialized into `ores.json` or `settings.json`.

Common host tags are:

- `minecraft:stone_ore_replaceables`
- `minecraft:deepslate_ore_replaceables`

The output block is looked up lazily after all installed mods have registered their blocks. An optional missing modded block does not prevent the world from loading.

### Spawn band

| Field | Range and meaning |
|---|---|
| `id` | Unique within its rule; it also contributes to deterministic random salt. |
| `placement` | Optional `vein` or `province`; omitted values use `vein` for schema-2 compatibility. |
| `vein_size` | 1–64 attempted ore blocks per vein. Retained as `1` but ignored for province placement. |
| `attempts_per_chunk` | 0–256; fractions are supported. `0.25` means a 25% chance of one attempt. Retained as `0` but ignored for province placement. |
| `distribution` | `uniform`, `triangle`, or `trapezoid`. |
| `min_y`, `max_y` | Inclusive range inside -64..320. |
| `peak_y` | Required for triangle; it need not be centered. |
| `plateau_min_y`, `plateau_max_y` | Required for trapezoid and must lie inside the range. |
| `discard_on_air_exposure` | 0–1 chance to discard exposed ore positions. |
| `province` | Required object when `placement` is `province`; omit it or set it to `null` for a vein. |

Classic vein bands retain the established behavior. A province band adds these controls:

```json
{
  "id": "regional_pockets",
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

| Province field | Range and meaning |
|---|---|
| `region_size` | 16–8192 blocks and a multiple of 16. One deterministic center is derived per regional cell. |
| `radius` | 1 through `region_size`; horizontal radius around the regional center. |
| `vertical_thickness` | 1–385 blocks around the height selected by the band's distribution. |
| `density` | Greater than 0 through 1; fraction of the bounded province volume sampled as candidates. |
| `per_chunk_work_cap` | 1–4096; hard cap shared by every province slice that touches the current chunk. |

Province centers are derived from the world seed, persisted generation salt, region coordinates, and band ID. Neighboring chunks therefore agree on the same region, but placement writes only inside the chunk currently generating. The work cap is charged as both attempts and work units in forecasts and aggregate validation.

The ore-rule GUI exposes the same controls. Command-line administrators can switch algorithms with `/delvefold ore band placement <rule> <band> <vein|province>` and edit a province with `/delvefold ore band province <rule> <band> <region_size|radius|vertical_thickness|density|work_cap> <value>`. Every mutation validates the complete profile before it is committed.

Unrelated rule changes do not shift other ores: each rule, band, chunk, region, and attempt uses its own stable deterministic salt.

To prevent an accidental world-generation stall, each terrain has aggregate safety limits of 4,096 ore attempts and 65,536 attempt×vein-size work units per chunk. Disabled rules do not count. Biome filters are conservatively counted because several rules can overlap in the same biome.

## Built-in presets

- **Vanilla-balanced**: Overworld coal, iron, copper, gold, redstone, lapis, diamond, and emerald families at familiar height patterns.
- **Rich**: the same heights and vein sizes with attempts doubled.
- **Empty**: no ore rules; geology remains.

Nether quartz, ancient debris, and End-specific ores are intentionally excluded. They can be added manually.

## Settings and initialization

`settings.json` begins with `initialized: false`. Initialization happens only through the setup GUI or `/delvefold initialize`; portal activation never initializes a world.

The `generation_epoch` increments on initialization, recreation, and deletion. Portal links and generation caches use the epoch to avoid reusing stale state.

`generation_salt` is server-maintained lifecycle state used for deterministic ore, province, themed geology, and landmark placement. It is never sent to the administration GUI or Seam Ledger. Existing schema-2 settings that omit it load as `0` without being rewritten. Live reload rejects edits to the active generation epoch, salt, terrain, terrain scale, geology theme, initialization state, or world-operation ID; those values change only through initialization, confirmed recreation/deletion, restart-time restoration, or loading a save backup.

Terrain is locked for the current mining world. Changing it requires **Recreate Mining World**, which removes the old active dimension safely on restart and begins a new epoch.

### Seam Ledger visibility

`guide_visibility` is an additive, optional schema-2 setting controlling which sources may open the read-only guide through `/delvefold guide` or the Seam Ledger item:

```json
{
  "guide_visibility": "public"
}
```

Accepted values are:

- `public`: all player and command sources may open the guide. This is the default.
- `operators`: the integrated singleplayer owner, players with Delvefold configuration access, and trusted console sources may open it; the normal player fallback is operator level 2.
- `disabled`: every built-in opening source is denied, including player commands, Seam Ledger use, command blocks, and the console summary.

Existing schema-2 files that omit `guide_visibility` continue to load as `public`; no schema bump or manual migration is required, and loading alone does not rewrite the file. The field is validated by the [settings schema](../schemas/settings.schema.json). Changing it through `/delvefold guide visibility <mode>` is immediate. A JSON edit takes effect after a successful `/delvefold config reload` and never requires world recreation.

The setting governs every built-in guide entry point. A trusted dedicated-server console counts as an operator in `operators` mode, while `disabled` suppresses even the bounded console summary.

### Backup retention

`backup_retention` is an additive schema-2 object. Omission preserves the disabled compatibility default without rewriting the file:

```json
{
  "backup_retention": {
    "enabled": false,
    "max_count": 0,
    "max_age_days": 0,
    "max_total_bytes": 0
  }
}
```

All three limits are non-negative; `0` means unbounded. No automatic deletion occurs unless `enabled` is explicitly `true`. At startup, Delvefold builds a deterministic oldest-first preview, records the logical backup IDs, sizes, and reasons for diagnostics/auditing, and rechecks eligibility before each deletion. Pinned backups, the newest two backups, selected/pre-restore backups referenced by pending restores, recoverable backups referenced by pending deletion/recreation, and every legacy, invalid, unverified, non-restorable, unknown-size, or unknown-timestamp backup are never pruned. A malformed pending-operation journal causes the pass to be skipped rather than risking a referenced backup.

Use `/delvefold backup retention configure <max_count> <max_age_days> <max_total_bytes>` or canonical JSON to update this object. `/delvefold backup retention disable` restores the disabled, unbounded defaults.

### World identity

The optional `identity` object is additive to schema 2. A schema-2 settings file written by 0.2 loads with safe defaults when the object is absent and is not rewritten merely by loading:

```json
{
  "identity": {
    "display_name": "Delvefold Mining World",
    "terrain_variant": "classic",
    "geology_theme": "classic",
    "landmark_preset": "balanced",
    "survey_stations": true,
    "motherlodes": true,
    "fault_lines": true,
    "renewal": {
      "enabled": false,
      "interval_days": 30,
      "warning_minutes": 30,
      "next_renewal_at_epoch_millis": 0,
      "seed_mode": "stable"
    }
  }
}
```

`terrain_variant` is `classic` or `expansive`; it is chosen during initialization or confirmed recreation. Expansive Flat adds substantially more mineable depth while keeping its surface safely below the cloud layer, Expansive Cavern uses a taller and deeper carving range, and Expansive Wild uses amplified Overworld terrain. Both Cavern scales begin as continuous solid stone hosts and use a compact, vertically bounded custom cave carver, producing underground passages without giant density voids or detached floating shelves. They use exposed stone/deepslate instead of grass, solid floor and roof safety bands, no global water table or springs, sparse shallow water pockets, and cave-carver lava only at Y −56 and below.

`geology_theme` is additive to schema 2 and is locked to initialization or confirmed recreation. Accepted values are `classic`, `volcanic`, `dripstone`, `lush`, and `crystal`. Classic preserves the established stone composition. The other themes add bounded vanilla-block strata, decorations and sealed fluids plus client-visible particles, sounds, and ambience; they do not change registered dimension IDs or replace the terrain generator. Existing settings that omit the field load as `classic` and are not rewritten merely by loading.

`landmark_preset` is `pure_mining`, `balanced`, or `abundant`. Pure Mining disables all candidates, Balanced deterministically accepts 25%, and Abundant accepts 75%. The individual landmark booleans further narrow the catalog categories that can appear in newly generated chunks: `survey_stations` controls `survey_station`, `motherlodes` controls `motherlode`, and `fault_lines` controls `fault_line`.

Scheduled renewal is disabled by default. When enabled, `interval_days` is 1–3650 and `warning_minutes` is 1–10080. The server announces the configured warning plus ten- and one-minute warnings when applicable. At the due time it blocks entry, evacuates players, schedules a restart-safe recreation, and always retains a timestamped backup. Singleplayer users apply it by exiting to title and reopening the save; dedicated servers apply it on restart.

`seed_mode` controls the layout installed by the next initialization or recreation:

- `stable` is the compatibility default and reproduces the established vein, province, themed geology, and landmark layout for the same save, profile, and terrain.
- `rotate_on_recreate` incorporates the next generation epoch into ore, province, themed geology, and landmark placement so each recreated world receives a new deterministic layout.

Changing the selection does not retrogen chunks or alter the active mining world. The server derives and persists `generation_salt` only when initialization or recreation commits, so restarting an already-created world cannot change its layout. Existing schema-2 files that omit `seed_mode` continue to load as `stable`, without a rewrite or schema migration.

### Reloadable landmark catalog

Landmark definitions are server datapack resources at:

```text
data/<namespace>/delvefold/landmarks/<path>.json
```

The definition ID is `<namespace>:<path>`. Delvefold ships survey camp, collapsed mine entrance, lift station, geode vault, motherlode chamber, and fault-line grotto definitions backed by structure-system templates, so their pieces respect chunk boundaries and structure spacing.

```json
{
  "format": 1,
  "template": "examplepack:landmarks/crystal_camp",
  "weight": 8,
  "category": "survey_station",
  "terrain_modes": ["flat", "wild"],
  "placement_style": "surface",
  "min_y": -48,
  "max_y": 240,
  "biomes": {
    "include": ["#delvefold:mining_biomes"],
    "exclude": []
  },
  "processors": ["examplepack:landmark_weathering"],
  "loot_table": "examplepack:chests/crystal_camp"
}
```

| Field | Contract |
|---|---|
| `format` | Currently exactly `1`. |
| `template` | Existing compressed structure template resource ID; dimensions must be positive, no more than 96 blocks wide/deep, and no more than 384 blocks tall. |
| `weight` | Integer 1–1000 used among eligible definitions. |
| `category` | `survey_station`, `motherlode`, or `fault_line`; the matching world-identity toggle must be enabled. |
| `terrain_modes` | Non-empty unique list containing `flat`, `cavern`, and/or `wild`. |
| `placement_style` | `surface`, `cave_floor`, or `buried`. |
| `min_y`, `max_y` | Ordered placement bounds from -2048 through 2047. |
| `biomes.include`, `biomes.exclude` | Optional biome IDs or `#tag` selectors, at most 32 in each list. Omission defaults to `#delvefold:mining_biomes`. |
| `processors` | Optional list of existing structure processor-list IDs. |
| `loot_table` | Existing loot-table ID used by `loot` data markers. |

Each catalog contains at most 256 definitions and each definition is limited to 64 KiB. Every referenced template, processor list, and loot table must exist. `/reload` validates the complete candidate catalog atomically: if any definition is invalid, Delvefold rejects the entire candidate, retains the last-known-good catalog, and exposes the errors through administration diagnostics and the server log.

Templates may use a structure data marker named `loot` directly above an empty randomizable container. Delvefold assigns the definition's loot table and deterministic seed only when the container has neither an existing loot table nor contents, then persists an initialized marker on that block entity. Reprocessing the piece therefore cannot roll the container again even after a player empties it.

Gameplay presets are live settings:

- **Safe**: no natural mobs, patrols, or phantoms.
- **Hostile**: hostile and ambient categories only.
- **Normal**: all available terrain-appropriate categories.

Spawn eggs, commands, breeding, and mob spawners are not treated as natural spawning and remain usable.

`settings.json` records `active_profile_id`, which must match the active ore document. Local profile IDs are simple lowercase names; datapack and script entries use namespaced paths such as `examplepack:metals/rich_tin`.

### Portal routing

Portal settings remain additive inside schema 2:

```json
{
  "portal": {
    "enabled": true,
    "allow_from_overworld_only": true,
    "cooldown_seconds": 5,
    "coordinate_scale": 1.0,
    "routing_mode": "coordinate_linked",
    "hub": {
      "x": 0,
      "z": 0,
      "protection_radius": 16
    }
  }
}
```

Cooldown is 1–3600 seconds, and coordinate scale must be finite and between 0.01 and 100. The one-second minimum prevents immediate partner-portal bounce loops. `routing_mode` accepts `coordinate_linked` or `central_hub`; omission defaults to `coordinate_linked`, preserving established 1:1 or configured-scale links. Hub X/Z coordinates must stay within ±29,999,936 and `protection_radius` accepts 8–256 blocks, defaulting to 16.

In `central_hub` mode, incoming players are routed to the configured horizontal location at a safe terrain-specific height. Delvefold creates an idempotent 11×11 vanilla-block platform with a filled return portal and protects the configured horizontal radius through the mining world's vertical column. Only users with `delvefold.manage_world` may modify protected positions. Explosions, pistons, fluids, trampling, and mob griefing are prevented from altering the protected area. Return travel remains guaranteed. Portal transport is player-only throughout 1.x.

### Backup manifests and operational files

Recoverable snapshots live under `<save>/delvefold_backups/<backup-id>/`. A 1.3 backup contains `manifest.json` with backup metadata plus a registry-ID-independent, lexically sorted inventory of normalized relative paths, byte sizes, and SHA-256 hashes. Mutable `.pinned` and verification-control files are excluded from the immutable inventory. Successful verification writes a small receipt; restoration still performs a complete content check during startup.

Only restorable configuration is installed from a selected snapshot. The live `audit/`, `exports/`, `imports/`, and `world_operations/` directories are operational or transfer history and remain unchanged across restoration, so selecting an older world cannot roll the audit trail or newer support records backward. New snapshots omit those directories; restore also excludes them defensively when reading a compatible older backup.

Legacy snapshot folders remain visible but are not restorable merely because their old configuration and dimensions are present. `/delvefold backup verify <backup>` explicitly validates the legacy layout, creates its manifest only after validation succeeds, and performs the full hash comparison on a background worker. New lifecycle backups and pre-restore backups receive verified manifests automatically. Backup and restore include all six dimension folders for Classic/Expansive Flat, Cavern, and Wild.

Accepted mutation records live under `<save>/serverconfig/delvefold/audit/`. `delvefold-audit.jsonl` rotates at 10 MiB and retains five files including the active file. Each line is a redacted format-1 entry with timestamp, actor, operation, affected logical object, and old/new revisions. Confirmation tokens, full profiles, server addresses, and unrelated player data are never fields in the audit model.

`/delvefold doctor export` creates a redacted `delvefold-doctor-*.json` report in the existing `serverconfig/delvefold/exports/` directory. Doctor reports whitelist version/protocol/schema, dimension, profile-health, pending-operation, backup, retention, and disk-estimate fields and contain no filesystem paths.
