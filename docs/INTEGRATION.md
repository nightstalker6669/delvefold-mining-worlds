# Modpack and Mod Integration

Delvefold 1.0 exposes deterministic, server-authoritative integration points without requiring optional mods. The Java API is stable for the 1.x line; `DelvefoldApi.API_VERSION` is `1`. Additive methods and events may appear in later 1.x releases, while existing public signatures retain source and binary compatibility.

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
  "state": {},
  "replace_tag": "minecraft:stone_ore_replaceables"
}
```

Exactly one output source is required. Members are expanded in registry-ID order. Mark a cross-mod rule `required: false` if a pack should remain valid when no provider is installed.

Commands expose the same model:

```text
/delvefold ore target add-tag <rule> <block_tag> <replace_tag>
/delvefold ore target remove-tag <rule> <block_tag>
```

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
| `delvefold.manage_world` | Operator level 4 | Delete, recreate, renew, restore, and backup management |
| `delvefold.use_portal` | Everyone | Enter the active mining world |

The integrated singleplayer owner keeps administrative access with cheats disabled. Return travel is never denied, preventing players from being trapped by a permission change.

## Java API and events

`DelvefoldApi.activeWorld()` returns an optional immutable `MiningWorldView`. `DelvefoldApi.worldView(settings)` can convert a known settings snapshot. Never retain internal configuration services.

`DelvefoldApi.activeGuide()` is an additive API-v1 method returning `Optional<GuideSnapshot>` for the current server state. It is empty while the live configuration service is unavailable; otherwise it can describe an initialized or not-yet-initialized Delvefold world. The snapshot is immutable and read-only:

```java
import com.nightsta69.delvefold.api.DelvefoldApi;
import com.nightsta69.delvefold.guide.GuideSnapshot;

DelvefoldApi.activeGuide().ifPresent(guide -> {
    String worldName = guide.worldName();
    for (GuideSnapshot.OreEntry ore : guide.ores()) {
        // Publish or render the already-bounded player-facing data.
    }
});
```

The contract contains only whitelisted player-facing information: world display name, terrain and variant, active profile, coarse portal and renewal status, and enabled ore entries with output IDs or tags, representative block icons, terrain applicability, bounded biome include/exclude selectors, height summaries, vein sizes, and relative frequency. It deliberately excludes seeds, horizontal coordinates, filesystem paths, replacement-host details, world-operation confirmation data, permissions, validation reports, configuration hashes, and administration diagnostics.

Every guide snapshot is independently bounded: format version 1, at most 96 ore entries, eight outputs and eight height bands per entry, three applicable terrains, 16 biome selectors per include list and 16 per exclude list, 64 characters for the world name, 128 characters for identifiers, and a conservative 24 KiB estimated network budget. The `truncated` flags tell consumers when a large profile was shortened. Consumers must tolerate future additive enum values and should display truncation rather than attempting to recover omitted internal data.

`activeGuide()` has no player argument and returns content, not an authorization decision. Delvefold's built-in command and item enforce `guide_visibility` separately. An integration that republishes the snapshot to its own audience remains responsible for that audience decision.

For the built-in player UI, the server issues a random, player-bound authorization with a ten-second lifetime alongside the snapshot. The client acknowledges only after installing the guide screen; the acknowledgement is single-use, and the server rechecks current visibility before awarding the consulting advancement. This short-lived acknowledgement is transport state and is not part of `GuideSnapshot` or `activeGuide()`.

The NeoForge game bus posts:

- `DelvefoldWorldLifecycleEvent` after initialization, recreation, or deletion commits;
- `DelvefoldOreProfileActivatedEvent` after profile selection;
- `DelvefoldPortalTravelEvent` immediately before destination resolution. This event is cancellable.

Listeners should use the immutable values supplied by each event and should not mutate Delvefold configuration from inside a lifecycle callback.

## JEI and EMI

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
