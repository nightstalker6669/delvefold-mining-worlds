# Configuration

Delvefold has one canonical configuration model. The GUI, commands, and JSON all validate and write the same immutable server snapshot.

## Save scope and loading

- `ores.json` contains the ore profile and independently revisioned ore rules.
- `settings.json` contains explicit initialization state, locked terrain choice, generation epoch, gameplay settings, and portal settings.
- Missing files are created from built-in defaults.
- Invalid edits are rejected without overwriting the rejected files. If a valid configuration was already active, it remains the last-known-good runtime snapshot.
- Disk writes use temporary sibling files, forced flush, and atomic replacement where the filesystem supports it. A small transaction journal makes the two JSON documents restart-recoverable if a write is interrupted between them.
- Files larger than 4 MiB are rejected as a safety limit.

Use `/delvefold config validate` before reloading, then `/delvefold config reload`. A reload affects future chunks only.

## Ore profile

Top-level fields:

| Field | Meaning |
|---|---|
| `schema_version` | Currently `1`. Unknown future schemas are rejected. |
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

Gameplay presets are live settings:

- **Safe**: no natural mobs, patrols, or phantoms.
- **Hostile**: hostile and ambient categories only.
- **Normal**: all available terrain-appropriate categories.

Spawn eggs, commands, breeding, and mob spawners are not treated as natural spawning and remain usable.

Portal settings are validated. Cooldown is 1–3600 seconds, and coordinate scale must be finite and between 0.01 and 100. The one-second minimum prevents immediate partner-portal bounce loops. The default is player-only, Overworld-only source access, five seconds, and 1:1 coordinates.
