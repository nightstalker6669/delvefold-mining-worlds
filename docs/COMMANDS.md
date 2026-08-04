# Commands

All Delvefold commands use the `/delvefold` root.

The integrated singleplayer owner may administer Delvefold even with cheats disabled. On dedicated servers, configuration falls back to operator level 2 and world deletion/recreation falls back to level 4. Permission mods may grant `delvefold.configure`, `delvefold.manage_world`, and `delvefold.use_portal` independently.

## Setup and GUI

```text
/delvefold gui
/delvefold config
/delvefold initialize <flat|cavern|wild> <balanced|rich|empty> <safe|hostile|normal>
/delvefold status
```

`gui` and `config` open the same screen. Before initialization it is the setup wizard; afterward it is the administration dashboard. GUI commands are player-only, while `initialize` works from a dedicated-server console.

## Seam Ledger guide

```text
/delvefold guide
/delvefold guide visibility
/delvefold guide visibility <public|operators|disabled>
```

For a player, `/delvefold guide` opens the server-authorized, read-only Seam Ledger screen; the player does not need to hold the item. Right-clicking a Seam Ledger uses the same authorization and snapshot path. The consulting advancement is awarded only after the client installs the authorized screen and returns the matching player-bound, single-use acknowledgement within ten seconds. The server rechecks visibility before awarding it, so merely requesting or receiving a payload is insufficient.

From a dedicated-server console, `/delvefold guide` prints a bounded text summary instead of opening a screen. It reports the world identity, terrain and profile, portal and renewal state, and up to 24 ore entries. The console counts as an operator for visibility checks and never awards a player advancement.

`/delvefold guide visibility` reports the current mode. Supplying a mode updates it immediately and requires the same configuration access as the administration GUI:

- `public` is the default and allows every source, including players and the server console.
- `operators` allows the integrated singleplayer owner, players granted `delvefold.configure`, and trusted console sources; without a permission mod, player access falls back to operator level 2.
- `disabled` denies player, command-block, and console guide access as well as Seam Ledger use. It does not remove the item.

Visibility controls only guide content. It does not change portal access, ore generation, or the permissions required to administer Delvefold.

## JSON diagnostics

```text
/delvefold config validate
/delvefold config reload
/delvefold doctor
/delvefold doctor export
```

`doctor` prepares the same bounded operational report used by the Diagnostics GUI on a deduplicated background worker: Delvefold, Minecraft, NeoForge, public API, network protocol, and schema versions; all six mining-dimension states; active-profile health and ineffective targets; pending lifecycle operations; backup integrity; retention preview/last-run state; and disk-space estimates. `doctor export` writes a redacted JSON copy under `serverconfig/delvefold/exports/` for attaching to a support report. Neither form includes world seeds, filesystem paths, confirmation tokens, server addresses, complete profile JSON, or unrelated player records.

Configuration validation, Doctor viewing, and Doctor export are administration operations. The integrated owner has access; dedicated servers use Delvefold's permission handler or operator fallback.

## World identity and renewal

```text
/delvefold identity
/delvefold identity name <name>
/delvefold identity landmarks <pure_mining|balanced|abundant>
/delvefold identity variant <classic|expansive>
/delvefold identity geology-theme <classic|volcanic|dripstone|lush|crystal>
/delvefold renewal
/delvefold renewal configure <interval_days> <warning_minutes>
/delvefold renewal disable
/delvefold renewal seed-mode
/delvefold renewal seed-mode <stable|rotate_on_recreate>
```

`identity variant` and `identity geology-theme` set recreation-locked choices only while the world is uninitialized. Once initialized, select the terrain scale and geology theme as part of a confirmed recreation in the World GUI or command below. Omitting them from a recreation preserves the current values. Landmark policy changes apply only to newly generated chunks. Renewal is opt-in, always retains a backup, warns online players, evacuates the mining dimensions when due, and waits for a restart before replacing terrain. Every renewal setting, including `seed-mode`, requires `delvefold.manage_world` (operator level 4 fallback); configure-only users may still edit the name and landmark policy in the GUI, but its renewal controls are read-only. `renewal seed-mode` reports or selects the layout policy for the next initialization or recreation: `stable` repeats the established ore, province, themed geology, and landmark layout, while `rotate_on_recreate` installs a new deterministic layout. Changing the selection never alters existing chunks, and a normal restart never rotates the layout.

## Portal routing and central hub

```text
/delvefold portal
/delvefold portal routing <coordinate_linked|central_hub>
/delvefold portal hub <x> <z> <protection_radius>
```

`portal` reports the active routing mode, hub coordinates, protection radius, and player-only transport policy. `coordinate_linked` is the compatibility default and retains the configured coordinate scale. `central_hub` routes players entering a mining dimension to the configured hub, where Delvefold idempotently creates a safe vanilla-block platform and filled return portal.

Changing the routing mode requires configuration access. Changing hub coordinates or its radius requires `delvefold.manage_world` (operator level 4 fallback). Coordinates must remain inside Minecraft's safe world boundary. The radius accepts 8–256 blocks and defaults to 16. Inside the protected horizontal column, only a player with world-management permission can place, break, interact with, or otherwise modify blocks; explosions, pistons, fluids, trampling, and mob griefing are also prevented from damaging it.

The guaranteed return route cannot be denied by a portal permission change. Delvefold 1.x portals transport players only; mobs, dropped items, boats, and minecarts remain unsupported.

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

Local profiles are stored per save. Datapack and scripted profiles appear as namespaced read-only entries. Selecting one changes generation in future chunks only. Import reads only from `serverconfig/delvefold/imports/`, and export writes only to `serverconfig/delvefold/exports/`. Built-in profiles remain available even when a local override is removed; the active profile cannot be deleted.

The Profiles GUI also provides **Detect ores…**. This guided registry scan is intentionally GUI-only: it shows block icons, groups material families and their provider-specific host variants, automatically maps recognized stone, deepslate, netherrack, and end-stone hosts, flags genuinely uncertain identity or hosts for review, and requires an explicit diff/workload preview. The result is written under a new local profile ID without overwriting or activating it. Existing command-line workflows remain available through `ore scan`, `ore add`, exact target commands, and canonical profile import for administrators who do not use a graphical client.

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

The Ores GUI's **Add ores** action opens the Unified Ores library. It groups equivalent ores across provider namespaces by material, using `c:ores/<material>` as authoritative metadata and clear conventional registry names as a safe fallback when a mod does not supply that tag. Server-side search and paging keep large modpacks bounded; families already covered by an exact target or expanded output tag are hidden by default. Select across pages and add up to 128 families in one atomic request. Each new rule defaults to Minecraft's safe host variants when available, otherwise the lexically first provider. Recognized Nether and End variants use `#c:netherracks` and `#c:end_stones`; uncertain identities or hosts remain review-only. Edit the rule afterward to enable or disable other provider variants. The ordinary `/delvefold ore add <block_id> ...` command remains the exact-ID escape hatch.

The administration GUI's Ores tab also provides a whole-profile **Forecast**. It reports effective versus configured attempts/work for every terrain, the active height overlay, and missing, shadowed, biome-filtered, or terrain-ineffective rules. It is a read-only server calculation and does not change chunks or configuration.

Targets and bands:

```text
/delvefold ore target add <rule> <block_id> <replace_tag> [weight]
/delvefold ore target remove <rule> <block_id>
/delvefold ore target add-tag <rule> <block_tag> <replace_tag> [weight]
/delvefold ore target set-weight <rule> <block_id> <weight>
/delvefold ore target set-tag-weight <rule> <block_tag> <weight>
/delvefold ore target remove-tag <rule> <block_tag>
/delvefold ore band add <rule> <band_id> <common|uncommon|rare|very_rare>
/delvefold ore band remove <rule> <band_id>
/delvefold ore band set <rule> <band_id> <field> <value>
/delvefold ore band placement <rule> <band_id> <vein|province>
/delvefold ore band province <rule> <band_id> <region_size|radius|vertical_thickness|density|work_cap> <value>
```

Omitting `[weight]` from `add` or `add-tag` uses the compatibility default of `1`. Weights accept integers from 1 through 1000 and can also be changed with the setter commands, ore-rule GUI, or canonical JSON. The weight changes only the relative output selected among targets that share a replacement-host tag. An output tag's total weight is divided equally among its installed members once the group contains a non-default weight; an all-1 group preserves the earlier member-uniform random sequence. Identical block states are deduplicated first-wins; later overlaps are ineffective and warned rather than increasing the retained candidate's weight. If a rule contains the same source more than once with different hosts or states, the setter command rejects that ambiguous edit; change the specific target in canonical JSON.

Band fields supported by `ore band set` are `vein_size`, `attempts`, `min_y`, `max_y`, `peak_y`, `plateau_min_y`, `plateau_max_y`, and `discard`.

The ore-rule GUI, commands, and canonical schema-2 JSON also support `placement: "province"`. Switching to `province` installs conservative defaults; switching back to `vein` removes the province object and restores a valid basic vein. Use `ore band province` to set region size, horizontal radius, vertical thickness, density, or the hard per-chunk work cap. The server validates the complete profile and aggregate safety budget before accepting each change.

## Delete and recreate

Default recoverable operations:

```text
/delvefold world recreate request
/delvefold world recreate request <flat|cavern|wild>
/delvefold world recreate request <flat|cavern|wild> <classic|expansive>
/delvefold world recreate request <flat|cavern|wild> <classic|expansive> <classic|volcanic|dripstone|lush|crystal>
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
/delvefold world recreate request <terrain> <classic|expansive> permanent
/delvefold world recreate request <terrain> <classic|expansive> <classic|volcanic|dripstone|lush|crystal> permanent
/delvefold world delete request permanent
```

Permanent mode still stages the old folders until the settings transaction commits. It is then deleted. Delvefold never modifies a loaded mining dimension. A geology theme changes only through initialization or this confirmed recreation path; existing schema-2 worlds default to Classic, and omitting the theme argument preserves the current theme.

Landmark catalog datapacks use Minecraft's standard `/reload` command rather than a Delvefold-specific mutation command. A bad candidate catalog is rejected atomically, the last-known-good definitions stay active, and the errors appear in the Delvefold diagnostics view and server log.

## Backups and restore

```text
/delvefold backup list
/delvefold backup verify <backup>
/delvefold backup pin <backup>
/delvefold backup unpin <backup>
/delvefold backup delete <backup> confirm
/delvefold backup retention
/delvefold backup retention configure <max_count> <max_age_days> <max_total_bytes>
/delvefold backup retention disable
/delvefold backup restore request <backup>
/delvefold backup restore confirm <token>
/delvefold backup restore cancel
```

`backup verify` performs SHA-256 hashing on a dedicated worker rather than the server tick thread and reports completion asynchronously. New backups contain a manifest of normalized relative paths, sizes, and hashes. A legacy backup remains visible but archive-only until an administrator explicitly verifies it; successful legacy validation creates the manifest without overwriting active world data. Failed verification removes restore eligibility. Restore requires a current successful verification receipt and repeats the complete check during startup before staging or replacing anything.

Restore preserves the selected backup, creates and verifies a transactional pre-restore backup of the current mining world, evacuates players, and applies during the next restart. Backup and restore cover all six Delvefold save folders: Classic and Expansive Flat, Cavern, and Wild. If a pre-restore move or manifest step fails, Delvefold rolls back that attempt and stops startup before partial folders can regenerate. Pinned backups must be unpinned before manual deletion.

Retention is disabled by default. `backup retention` reports its state. `configure` enables it with optional count, age-in-days, and total-byte limits; `0` means that particular limit is unbounded. Retention is evaluated deterministically during startup. Before deletion, Delvefold previews the proposed IDs and reasons, then rechecks every candidate. It never prunes pinned backups, the newest two backups, backups referenced by a pending delete, recreation, or restore, or any legacy, invalid, unverified, non-restorable, or incompletely measured backup. Invalid pending journals cause the automatic pass to be skipped conservatively. The preview and result are included in Doctor diagnostics and the mutation audit trail.

## Mutation audit log

Accepted configuration and lifecycle mutations are appended to:

```text
<save>/serverconfig/delvefold/audit/delvefold-audit.jsonl
```

Each JSON line contains a format version, timestamp, actor, operation, affected logical object, and old/new revisions. At 10 MiB the active file rotates; Delvefold retains five files total, including the active file. The log never records world-operation confirmation tokens, complete profile JSON, server addresses, or unrelated player data. There is no command that disables or clears the audit trail.
