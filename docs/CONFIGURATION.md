# Configuration

Schema 2 is the stable Delvefold 1.x configuration format. If schema-1 files are detected, Delvefold leaves them and all existing dimension data untouched, disables configuration mutations and portal entry, and reports that a new save is required.

Delvefold has one canonical configuration model. The GUI, commands, and JSON all validate and write the same immutable server snapshot.

## Save scope and loading

- `ores.json` contains the ore profile and independently revisioned ore rules.
- `settings.json` contains explicit initialization state, terrain shape and scale, world identity, landmark policy, renewal schedule, generation epoch, gameplay settings, and portal settings.
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
  "state": {},
  "replace_tag": "minecraft:stone_ore_replaceables"
}
```

`state` may set valid properties exposed by the selected block. The GUI normally leaves it empty, which uses the block's default state.

An output can instead resolve every installed block in a conventional tag. Set exactly one of `block` and `block_tag`:

```json
{
  "block_tag": "c:ores/tin",
  "state": {},
  "replace_tag": "minecraft:stone_ore_replaceables"
}
```

Tag members are expanded in registry-ID order for deterministic generation. A missing optional output tag warns and skips; a missing required output tag rejects the profile.

Common host tags are:

- `minecraft:stone_ore_replaceables`
- `minecraft:deepslate_ore_replaceables`

The output block is looked up lazily after all installed mods have registered their blocks. An optional missing modded block does not prevent the world from loading.

### Spawn band

| Field | Range and meaning |
|---|---|
| `id` | Unique within its rule; it also contributes to deterministic random salt. |
| `vein_size` | 1–64 attempted ore blocks per vein. |
| `attempts_per_chunk` | 0–256; fractions are supported. `0.25` means a 25% chance of one attempt. |
| `distribution` | `uniform`, `triangle`, or `trapezoid`. |
| `min_y`, `max_y` | Inclusive range inside -64..320. |
| `peak_y` | Required for triangle; it need not be centered. |
| `plateau_min_y`, `plateau_max_y` | Required for trapezoid and must lie inside the range. |
| `discard_on_air_exposure` | 0–1 chance to discard exposed ore positions. |

Unrelated rule changes do not shift other ores: each rule, band, chunk, and attempt uses its own stable deterministic salt.

To prevent an accidental world-generation stall, each terrain has aggregate safety limits of 4,096 ore attempts and 65,536 attempt×vein-size work units per chunk. Disabled rules do not count. Biome filters are conservatively counted because several rules can overlap in the same biome.

## Built-in presets

- **Vanilla-balanced**: Overworld coal, iron, copper, gold, redstone, lapis, diamond, and emerald families at familiar height patterns.
- **Rich**: the same heights and vein sizes with attempts doubled.
- **Empty**: no ore rules; geology remains.

Nether quartz, ancient debris, and End-specific ores are intentionally excluded. They can be added manually.

## Settings and initialization

`settings.json` begins with `initialized: false`. Initialization happens only through the setup GUI or `/delvefold initialize`; portal activation never initializes a world.

The `generation_epoch` increments on initialization, recreation, and deletion. Portal links and generation caches use the epoch to avoid reusing stale state.

Terrain is locked for the current mining world. Changing it requires **Recreate Mining World**, which removes the old active dimension safely on restart and begins a new epoch.

### World identity

The optional `identity` object is additive to schema 2. A schema-2 settings file written by 0.2 loads with safe defaults when the object is absent and is not rewritten merely by loading:

```json
{
  "identity": {
    "display_name": "Delvefold Mining World",
    "terrain_variant": "classic",
    "landmark_preset": "balanced",
    "survey_stations": true,
    "motherlodes": true,
    "fault_lines": true,
    "renewal": {
      "enabled": false,
      "interval_days": 30,
      "warning_minutes": 30,
      "next_renewal_at_epoch_millis": 0
    }
  }
}
```

`terrain_variant` is `classic` or `expansive`; it is chosen during initialization or confirmed recreation. Expansive Flat adds substantially more mineable depth while keeping its surface safely below the cloud layer, Expansive Cavern creates amplified subterranean ranges, and Expansive Wild uses amplified Overworld terrain. `landmark_preset` is `pure_mining`, `balanced`, or `abundant`. Pure Mining disables Delvefold landmarks. The individual landmark booleans can further narrow which bounded landmark types appear in newly generated chunks.

Scheduled renewal is disabled by default. When enabled, `interval_days` is 1–3650 and `warning_minutes` is 1–10080. The server announces the configured warning plus ten- and one-minute warnings when applicable. At the due time it blocks entry, evacuates players, schedules a restart-safe recreation, and always retains a timestamped backup. Singleplayer users apply it by exiting to title and reopening the save; dedicated servers apply it on restart.

Gameplay presets are live settings:

- **Safe**: no natural mobs, patrols, or phantoms.
- **Hostile**: hostile and ambient categories only.
- **Normal**: all available terrain-appropriate categories.

Spawn eggs, commands, breeding, and mob spawners are not treated as natural spawning and remain usable.

`settings.json` records `active_profile_id`, which must match the active ore document. Local profile IDs are simple lowercase names; datapack and script entries use namespaced paths such as `examplepack:metals/rich_tin`. Portal settings are validated. Cooldown is 1–3600 seconds, and coordinate scale must be finite and between 0.01 and 100. The one-second minimum prevents immediate partner-portal bounce loops. Portal travel is player-only; the default policy allows Overworld entry with a five-second cooldown and 1:1 coordinates.
