# Commands

All administration commands use the `/delvefold` root.

The integrated singleplayer owner may administer Delvefold even with cheats disabled. On dedicated servers, configuration requires operator level 2 and world deletion/recreation requires level 4.

## Setup and GUI

```text
/delvefold gui
/delvefold config
/delvefold initialize <flat|cavern|wild> <balanced|rich|empty> <safe|hostile|normal>
/delvefold status
```

`gui` and `config` open the same screen. Before initialization it is the setup wizard; afterward it is the administration dashboard. GUI commands are player-only, while `initialize` works from a dedicated-server console.

## JSON diagnostics

```text
/delvefold config validate
/delvefold config reload
```

## Named ore profiles

```text
/delvefold profile list
/delvefold profile create <id> <balanced|rich|empty> [overwrite]
/delvefold profile duplicate <source> <id> [overwrite]
/delvefold profile save-current <id> [overwrite]
/delvefold profile select <id>
/delvefold profile delete <id>
/delvefold profile import <file.json> <id> [overwrite]
/delvefold profile export <id> <file.json>
```

Profiles are stored per save. Selecting one changes generation in future chunks only. Import reads only from `serverconfig/delvefold/imports/`, and export writes only to `serverconfig/delvefold/exports/`. Built-in profiles remain available even when a local override is removed; the active profile cannot be deleted.

## Ore rules

```text
/delvefold ore list
/delvefold ore show <rule>
/delvefold ore add <block_id> <exact|detected> <common|uncommon|rare|very_rare>
/delvefold ore enable <rule>
/delvefold ore disable <rule>
/delvefold ore remove <rule>
/delvefold ore scan [namespace]
```

`detected` looks for matching normal/deepslate variants in the same namespace. The four rarity values are convenient starting templates; every value can then be refined in the GUI, JSON, or band commands.

Targets and bands:

```text
/delvefold ore target add <rule> <block_id> <replace_tag>
/delvefold ore target remove <rule> <block_id>
/delvefold ore band add <rule> <band_id> <common|uncommon|rare|very_rare>
/delvefold ore band remove <rule> <band_id>
/delvefold ore band set <rule> <band_id> <field> <value>
```

Band fields are `vein_size`, `attempts`, `min_y`, `max_y`, `peak_y`, `plateau_min_y`, `plateau_max_y`, and `discard`.

## Delete and recreate

Default recoverable operations:

```text
/delvefold world recreate request
/delvefold world recreate request <flat|cavern|wild>
/delvefold world delete request
```

The request reports estimated size, affected players, and a short-lived confirmation token:

```text
/delvefold world confirm <token>
/delvefold world cancel
```

To explicitly discard the old dimension without retaining its timestamped backup:

```text
/delvefold world recreate request <terrain> permanent
/delvefold world delete request permanent
```

Permanent mode still stages the old folders until the settings transaction commits. It is then deleted. Delvefold never modifies a loaded mining dimension.

## Backups and restore

```text
/delvefold backup list
/delvefold backup pin <backup>
/delvefold backup unpin <backup>
/delvefold backup delete <backup> confirm
/delvefold backup restore request <backup>
/delvefold backup restore confirm <token>
/delvefold backup restore cancel
```

Only backups created by versions that capture both dimension and configuration data are restorable; older entries remain visible as archive-only. Restore preserves the selected backup, creates a pre-restore backup of the current mining world, evacuates players, and applies during the next restart. Pinned backups must be unpinned before deletion.
