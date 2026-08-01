# Delvefold Architecture

This document records the final Delvefold 1.3.1 architecture after the
behavior-preserving code-quality refactor. It is both a map for maintainers and
a compatibility contract for subsequent maintenance. The released 1.3.0
architecture remains the behavioral baseline: 1.3.1 narrows ownership, makes
contracts explicit, and removes repeated client presentation work without
changing gameplay, save data, generation identity, or public interfaces.

## Architectural goals

Delvefold is a server-authoritative NeoForge mod. The server owns mining-world
identity, configuration, ore generation, portal routing, backups, lifecycle
operations, diagnostics, and permissions. A connected client presents bounded
views of that state and sends requests; it does not decide whether a mutation is
valid or accepted.

The architecture is built around five invariants:

1. **World generation is deterministic.** A world seed, persisted generation
   salt, rule set, terrain, and chunk position must produce the same placement
   decisions after a restart or refactor.
2. **Published configuration is immutable.** Generation workers read an atomic
   `ConfigSnapshot`; they do not parse JSON, inspect mutable editor state, or
   perform save-directory I/O.
3. **Lifecycle operations fail closed.** Delete, recreate, restore, and renewal
   use persisted journals and restart-bound transitions. A partial operation
   must not allow Minecraft to regenerate a missing or mixed dimension.
4. **Every remote input is bounded and revalidated.** Payload codecs enforce
   size/count limits, and the server checks permissions, revisions, registry
   state, and one-use capabilities before accepting a request.
5. **Optional integrations remain optional.** JEI and EMI adapters may depend on
   their APIs at compile time, but neither implementation nor viewer library is
   bundled into or required by the base/server runtime.

## Compatibility boundary

The 1.3.1 cleanup is behavior-preserving. These identifiers and formats remain
frozen against the 1.3.0 release:

| Contract | Baseline | Compatibility requirement |
| --- | ---: | --- |
| Public API | `DelvefoldApi.API_VERSION = 1` | Preserve public descriptors and event semantics. Add no breaking signatures. |
| Client/server protocol | `DelvefoldNetwork.PROTOCOL_VERSION = "12"` | Preserve payload IDs, field order, bounds, and matching-version behavior. |
| Settings JSON | schema `2` | Preserve field names, defaults, validation, and canonical serialization. |
| Ore-profile JSON | schema `2` | Preserve rule IDs, target semantics, defaults, weights, and canonical serialization. |
| Guide snapshot | format `2` | Preserve the bounded public guide view and legacy-constructor behavior. |
| Audit, Doctor, manifest, and journal data | existing format/schema versions | Preserve file locations, normalized paths, redaction, recovery, and validation rules. |
| Registry identity | existing `delvefold:*` IDs | Preserve all blocks, items, features, structures, placements, dimensions, biomes, recipes, and advancement IDs. |
| Generation identity | existing seed/salt mixing | Preserve random-call ordering and all stable-mode output. |

Record components are part of source and serialization contracts even when a
record is not in the public API package. They must not be renamed merely to
improve style. The same restriction applies to translation keys, command paths,
permission nodes, dimension IDs, profile IDs, confirmation tokens, and persisted
operation IDs.

## Composition and startup

`Delvefold` is the composition root. Its constructor performs registration only:

- world-generation, landmark, and portal registries are attached to the mod
  event bus;
- network payloads are registered at protocol 12;
- client event handlers are registered only on `Dist.CLIENT`;
- `DefaultDelvefoldAdminService` is installed behind the
  `DelvefoldAdminService` interface;
- server lifecycle, ambience, discovery, commands, reload listeners,
  permissions, spawn policy, and player-safety hooks are attached to NeoForge's
  game event bus.

`DelvefoldServerLifecycle` controls per-save service ownership. Startup ordering
is deliberate:

1. At highest priority, begin the save-scoped asynchronous-audit session,
   invalidate runtime caches, and prepare any pending restore or world operation.
2. At normal priority, load and validate the save configuration and publish its
   immutable snapshot.
3. At lowest priority, finish a prepared world operation, preview/apply backup
   retention, and refresh the asynchronous backup catalog.

Shutdown reverses that ownership: stop accepting new asynchronous audit
mutations, release deletion reservations, drain tracked completions, stop world
and restore operations, clear caches and sessions, flush/stop audit output, then
unpublish the configuration. This order prevents a late callback from writing
into a subsequent save session or from being lost while its accepted mutation is
still completing.

## Package and subsystem map

The final 1.3.1 source tree contains 317 production Java files and 51,673 lines,
compared with 246 files and 36,043 lines in the 1.3.0 baseline. The increase is
primarily 35 documented `package-info.java` nullness contracts, focused
collaborators, and exhaustive Javadocs. Counts below are final, include nested
packages and package contracts, and are useful for locating responsibility—not
as size targets.

| Package | Files | Responsibility and allowed dependency direction |
| --- | ---: | --- |
| `config` | 73 | Immutable settings/ore models, strict JSON, validation, profile catalogs, forecasting, import planning, permissions, and the runtime snapshot. Models and pure analysis do not depend on GUI or command code. |
| `network` | 46 | Bounded wire models, codecs, payloads, registration, and the admin-service interface. Payloads depend on immutable views/models; domain services do not depend on client screens. |
| `client` | 43 | Client payload handling, requests, screens, pure draft/presentation/layout models, and widgets. It consumes bounded network snapshots and has no authority to mutate server state directly. |
| `world` | 43 | Feature registration, deterministic ore/province/geology generation, structure-backed landmarks, catalog snapshots, and discovery. Generation consumes published config/catalog views and registries, never editor state or filesystem services. |
| `reset` | 29 | Backups, manifests, verification, retention, delete/recreate, restore, renewal, shared lifecycle file/journal operations, evacuation, and cross-operation coordination. This subsystem owns destructive save-tree transitions. |
| `portal` | 12 | Frame recognition, ignition, POI registration, access policy, destination construction, hub routing, and hub protection. It reads active settings through server-side access services. |
| `guide` | 12 | Visibility policy, authorization, bounded read-only snapshots, icons, and console summaries. It exposes resource guidance without seeds, coordinates, paths, or administrative secrets. |
| `compat` | 9 | Dependency-free portal-construction description plus isolated JEI and EMI adapters. Common/server packages do not reference viewer implementation classes. |
| `admin` | 8 | Stable server-side adapter plus focused backup dispatch, snapshot assembly, ore mapping/import administration, and localized result encoding. |
| `api` | 8 | API-v1 immutable world view and public events for lifecycle, profile activation, portal travel, and landmark discovery. This is the strongest binary-compatibility boundary. |
| `audit` | 8 | Actor scoping, accepted-mutation tracking, bounded asynchronous writes, JSON-lines serialization, rotation, and lifecycle ownership. Audit failure is contained and cannot undo an accepted gameplay mutation. |
| `diagnostics` | 8 | Bounded Doctor collection, filesystem analysis, caching, rendering, redaction, and export. Expensive disk inspection runs away from the server tick thread. |
| `command` | 5 | Brigadier registration/handlers and pure ore-rule document edits. Commands call the same authoritative domain services as GUI requests. |
| `internal` | 4 | Package-private atomic-file and named-thread primitives shared without creating a service locator or shared worker queue. |
| `content` | 3 | Seam Ledger item behavior and its server-authorized advancements. |
| `server` | 2 | Per-save startup/shutdown orchestration. |
| `gameplay` | 2 | Natural mob-spawn policy for configured mining-world gameplay. |
| root | 2 | NeoForge composition root and mod ID. |

### 1.3.1 responsibility boundaries

The public facades and NeoForge entry points remain stable, while focused
package-private collaborators now own logic that can be characterized without a
running client or server:

- `DefaultDelvefoldAdminService` delegates backup operations to
  `AdminBackupOperations`, bounded view construction to `AdminSnapshotAssembler`,
  and ore-model conversion to `AdminOreDraftMapper`.
- `DelvefoldCommands` retains its exact Brigadier tree and dispatch behavior,
  while `OreRuleEdits` performs immutable ore-document transformations.
- `DelvefoldConfigService` remains the synchronized publication/persistence
  facade; `ConfigDocumentTransitions`, `LiveConfigReloadPolicy`, and
  `ProfileCatalogOperations` isolate pure transition and catalog decisions.
- Dashboard, ore-rule wizard, ore-import, forecast, picker, backup, and setup
  screens delegate draft, presentation, validation, scrolling, and responsive
  geometry to focused models. Screens still own Minecraft widgets, rendering,
  narration, and network submission.
- `AtomicFiles`, `LifecycleFileOperations`, and `LifecycleJournalFiles` centralize
  tested persistence mechanics. `NamedDaemonThreadFactory` standardizes thread
  identity without combining the separately owned worker queues.

These boundaries are deliberately package-private. They improve testability and
reviewability without expanding API version 1 or making private implementation
layout a downstream contract.

### Intended dependency flow

The preferred direction is:

```text
NeoForge events / commands / network payload handlers
                    |
                    v
      server adapters and authorization
                    |
                    v
 config, guide, portal, diagnostics, and lifecycle services
                    |
                    v
 immutable models, validation, deterministic planners, registries
                    |
                    v
        Minecraft / NeoForge platform APIs
```

Client screens sit beside this server flow rather than inside it. They render
network views and emit payloads. Compatibility adapters sit at the outermost
edge. Public API views/events may observe authoritative state but must not expose
mutable internal objects or acquire dependencies on screens, commands, backup
implementation details, or viewer APIs.

## Authoritative data flows

### Configuration mutation

1. A command or client payload reaches a permission-checked server adapter.
2. The request carries the revision observed by the caller where optimistic
   concurrency is required.
3. `DelvefoldConfigService` serializes mutation under `mutationLock`, verifies
   that the service is started and writable, checks the expected revision, and
   creates a new immutable document.
4. Validators check structure, registry references, profile consistency, and
   generation-work budgets before persistence.
5. `FileConfigRepository` writes a two-document transaction marker and uses
   bounded atomic-file replacement for `ores.json` and `settings.json`.
6. Only after persistence succeeds does the service publish a new atomic
   `ConfigSnapshot`, record audit mutations, and post applicable public events.
7. A bounded result/snapshot returns to the caller. Stale revisions are rejected;
   the server never merges an old GUI draft implicitly.

Live reload follows the same validation boundary. If disk data is invalid,
incompatible, or changes recreation-locked lifecycle fields, the active
last-known-good snapshot remains published.

### Client administration

`DelvefoldClientRequests` sends typed payloads. `DelvefoldNetwork` decodes them,
enforces protocol limits, dispatches them on the server, and invokes the
installed `DelvefoldAdminService`. `DefaultDelvefoldAdminService` rechecks
configuration or world-management permission and produces `AdminSnapshot` or a
localized service result. Server-to-client open/result payloads are then applied
by `DelvefoldClientPayloadHandler` on the Minecraft client thread.

The dashboard is a projection, not a cache of truth. Page counts, backup status,
capabilities, pending-operation state, diagnostics, and revisions originate on
the server. The ore import flow additionally uses random, expiring,
player-bound, state-bound tokens; previewing does not activate or overwrite a
profile, and a commit capability is single-use.

### World generation

`DelvefoldWorldgen` registers six stable level keys: Classic and Expansive
variants of Flat, Cavern, and Wild. Terrain resources and dimension IDs are data
driven, while custom features provide ores, geology decorations/strata, and
legacy feature integration.

At chunk generation time:

- `MiningOreFeature` obtains a runtime profile derived from the immutable config
  snapshot and registry state;
- host states and tag outputs are resolved deterministically;
- seed mixing includes only the documented world seed, generation salt, rule,
  band/province, and chunk inputs;
- provinces may consider neighboring deterministic centers but write only in the
  chunk currently being generated;
- configured work caps bound attempts even for adversarial profiles;
- no generation path reads JSON or writes save-management files.

The reloadable landmark catalog is separately published as an immutable
last-known-good snapshot. Minecraft's structure system owns multi-chunk starts
and spacing. Catalog selection, generation salt, landmark density, terrain,
biomes, and category toggles narrow deterministic candidates without rewriting
existing structures.

### Portal travel

Portal ignition validates a complete frame before filling it or awarding the
activation advancement. Travel is deliberately player-only in 1.3. Access
policy selects the active mining dimension, checks permissions and pending-world
blocks, and posts the cancellable API event. Destination logic either links
coordinates or ensures the configured central hub and guaranteed return portal.
It bounds portal searches, respects the world border, creates safe fallback
geometry, and retains no forced-chunk ticket. Hub protection is server enforced
and bypassable only through world-management permission.

### Delete, recreate, restore, and renewal

World lifecycle mutation is restart-bound. `LifecycleOperationCoordinator`
serializes request, confirmation, cancellation, startup application, and stop so
world-operation and restore journals cannot both be accepted. Confirmation
tokens are short-lived and bound to the requested operation.

Accepted operations evacuate players and persist a journal before restart.
Startup examines journals before normal configuration publication, stages or
moves all six dimension folders transactionally, creates/verifies manifests,
and either commits the complete transition or keeps recoverable staging for a
later retry. A restore must have a successfully verified manifest; legacy
backups require explicit validation and manifest creation first. Retention never
prunes pinned, pending, legacy/invalid, or newest-two protected backups.

This safety model is intentionally not a live dimension unloader. Refactoring
must not turn a restart-bound operation into best-effort deletion of a loaded
level.

## Persistence and filesystem ownership

All paths are normalized and checked against an expected root. Symlinks and
non-regular files are rejected at security-sensitive boundaries.

| Location under the save root | Owner and contents |
| --- | --- |
| `serverconfig/delvefold/settings.json` | Schema-2 world identity, terrain, gameplay, portal, renewal, and retention settings. |
| `serverconfig/delvefold/ores.json` | Schema-2 active ore profile document. |
| `serverconfig/delvefold/config_transaction.json` | Recoverable two-document configuration transaction marker. |
| `serverconfig/delvefold/profiles/` | Named local profile files and overrides. |
| `serverconfig/delvefold/imports/` and `exports/` | Administrator-controlled bounded interchange and redacted Doctor exports. |
| `serverconfig/delvefold/pending_world_operation.json` | Restart-bound delete/recreate journal. |
| `serverconfig/delvefold/pending_restore.json` | Restart-bound restore journal. |
| `serverconfig/delvefold/world_operations/` | Completed lifecycle history. |
| `serverconfig/delvefold/audit/` | Active and rotated JSON-lines mutation audit logs. |
| `dimensions/delvefold/` | Region data for all Delvefold dimension IDs. |
| `delvefold_backups/` | Timestamped backups, operation metadata, manifests, verification receipts, and optional pin marker. |
| `.delvefold_delete_staging/` and `.delvefold_restore_staging/` | Recoverable transaction staging; not user-facing backup catalogs. |

Operational history, audit output, and newer diagnostics are not rolled backward
when restoring an older mining-world snapshot. Only the explicitly restorable
configuration and Delvefold dimension data are replaced.

## Threading and I/O boundaries

Thread ownership is part of the behavioral contract:

| Context | Allowed work | Prohibited work |
| --- | --- | --- |
| Server/event thread | Permissions, authoritative mutation coordination, snapshot publication, lifecycle ordering, portal/world interaction, and final completion callbacks. | Hashing large backups, unbounded directory traversal, or waiting indefinitely for background tasks. |
| Chunk-generation workers | Read immutable config/catalog/runtime views and execute bounded deterministic placement. | Mutable GUI/session access, config writes, backup/Doctor I/O, or nondeterministic shared random state. |
| Client thread | Screen construction, input, narration, layout, and application of server snapshots. | Authoritative permission/config decisions or save-directory access. |
| Audit writer | Serialize accepted audit entries and rotate bounded JSON-lines files. | Gameplay mutation or calls back into server state. |
| Backup verifier pool | Hash manifest entries and validate/upgrade legacy backups; operations for the same backup are ordered. | Minecraft world mutation; completion rejoins the server thread where needed. |
| Backup catalog worker | Traverse/measure the backup catalog, refresh snapshots, and perform guarded asynchronous deletion. | Publishing mutable catalog structures or bypassing deletion reservations. |
| Doctor worker | Collect bounded disk/backup diagnostics and write redacted exports. | Accessing client state or leaking paths, tokens, seeds, addresses, or complete profiles. |

The audit writer, backup verifier, backup catalog, and Doctor service intentionally
use separate executors so one slow workload cannot starve another. Refactoring
may share thread-construction code, but must not collapse these queues into a
single executor. Background callbacks that affect authoritative state must use
`MinecraftServer.execute(...)` to re-enter the server thread. Each worker must
have explicit lifecycle, daemon behavior, names, failure containment, and test
coverage for shutdown races.

`DelvefoldConfigService` uses a synchronized mutation lock for persistence and
an `AtomicReference` for read-mostly publication. Audit actor scopes are
thread-local and must be closed in reverse order on their owning thread.
`AsyncAuditMutationTracker` is save-session scoped; callbacks from an old session
must never be admitted to a newer save with the same process.

## Security and privacy boundaries

- Configuration and ordinary administration require the configure permission;
  delete/recreate/restore and protected-hub mutation require the stronger
  world-management permission. NeoForge permission providers may override the
  documented operator fallbacks.
- Payload sizes, list counts, identifiers, text, pages, and aggregate forecast,
  guide, import, backup, and diagnostics views are bounded before allocation or
  publication.
- Confirmation and import tokens never appear in audit logs, Doctor exports, or
  ordinary snapshots.
- Guide and API views exclude world seeds, exact resource coordinates, file
  paths, confirmation data, and administrative diagnostics.
- Doctor export and audit output use logical IDs and redacted values rather than
  complete configuration JSON, server addresses, or unrelated player data.
- Backup manifests store normalized relative paths and hashes. Restoration
  rejects traversal, symlink, missing, extra, size-mismatched, or hash-mismatched
  content before replacing active data.

## Tests as architectural constraints

Pure JUnit tests live in `src/test/java`; NeoForge-discovered GameTests live in
`src/main/java` because they execute inside the game-test runtime. The v1.3.0
release baseline consists of 365 passing JUnit tests and 35 passing GameTests.

The existing CI architecture has four independent checks:

- clean unit-test/build plus JSON and translation validation;
- NeoForge GameTests;
- dedicated-server startup smoke;
- four client smokes: base, JEI, EMI, and both viewers.

The completed 1.3.1 branch contains 157 test Java files and 15,611 lines. It
passes 601 JUnit tests and all 35 required NeoForge GameTests, compared with 86
files, 8,847 lines, and 365 JUnit tests at the 1.3.0 baseline. The strongest
invariants are public API descriptors, the complete Brigadier tree and
permissions, payload codec round trips and limits, schema-2 canonical
serialization, deterministic placement outputs, GUI draft/layout state, and
restart/recovery lifecycle behavior.

## Maintenance rules

1. Keep current public facades (`DelvefoldApi`, `DelvefoldConfigService`,
   `DelvefoldAdminService`, screen entry classes, and command registration)
   stable while moving implementation into package-private collaborators.
2. Extract pure planning, validation, state, layout, file, and rendering logic
   before changing orchestration. Pure collaborators should accept explicit
   inputs and return immutable results.
3. Centralize atomic-file and safe-tree primitives only after failure-injection
   tests prove that existing rollback, fsync, traversal, symlink, and cleanup
   behavior is retained.
4. Do not introduce an all-purpose service locator or executor. Ownership should
   become narrower and more explicit.
5. Do not optimize seeded loops, codec ordering, or lifecycle sequencing without
   a before/after equivalence test.
6. Prefer package-private implementation classes. A refactor is not a reason to
   expand the API surface.
7. Treat logging, translations, narration, redaction, and audit coverage as
   observable behavior, not incidental presentation.
