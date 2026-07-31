# Commands

Both `/miningworlds` and `/delvefold` register the same command tree. Examples below use `/miningworlds`.

The integrated singleplayer owner may administer Delvefold even with cheats disabled. On dedicated servers, configuration requires operator level 2 and world deletion/recreation requires level 4.

## Setup and GUI

```text
/miningworlds gui
/miningworlds config
/miningworlds initialize <flat|cavern|wild> <balanced|rich|empty> <safe|hostile|normal>
/miningworlds status
```

`gui` and `config` open the same screen. Before initialization it is the setup wizard; afterward it is the administration dashboard. GUI commands are player-only, while `initialize` works from a dedicated-server console.

## JSON diagnostics

```text
/miningworlds config validate
/miningworlds config reload
```

## Ore rules

```text
/miningworlds ore list
/miningworlds ore show <rule>
/miningworlds ore add <block_id> <exact|detected> <common|uncommon|rare|very_rare>
/miningworlds ore enable <rule>
/miningworlds ore disable <rule>
/miningworlds ore remove <rule>
/miningworlds ore scan [namespace]
```

`detected` looks for matching normal/deepslate variants in the same namespace. The four rarity values are convenient starting templates; every value can then be refined in the GUI, JSON, or band commands.

Targets and bands:

```text
/miningworlds ore target add <rule> <block_id> <replace_tag>
/miningworlds ore target remove <rule> <block_id>
/miningworlds ore band add <rule> <band_id> <common|uncommon|rare|very_rare>
/miningworlds ore band remove <rule> <band_id>
/miningworlds ore band set <rule> <band_id> <field> <value>
```

Band fields are `vein_size`, `attempts`, `min_y`, `max_y`, `peak_y`, `plateau_min_y`, `plateau_max_y`, and `discard`.

## Delete and recreate

Default recoverable operations:

```text
/miningworlds world recreate request
/miningworlds world recreate request <flat|cavern|wild>
/miningworlds world delete request
```

The request reports estimated size, affected players, and a short-lived confirmation token:

```text
/miningworlds world confirm <token>
/miningworlds world cancel
```

To explicitly discard the old dimension without retaining its timestamped backup:

```text
/miningworlds world recreate request <terrain> permanent
/miningworlds world delete request permanent
```

Permanent mode still stages the old folders until the settings transaction commits. It is then deleted. Delvefold never modifies a loaded mining dimension.
