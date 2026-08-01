# Delvefold Architecture

This document records the architecture of Delvefold 1.3.0 before the 1.3.1
code-quality refactor. It is both a map for maintainers and a compatibility
contract for the cleanup. Structural changes may improve ownership and reduce
duplication, but they must not change the behavior described here unless a
future feature release explicitly revises the relevant public contract.

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

The 1.3.1 cleanup is behavior-preserving. These identifiers and formats are
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

The baseline contains 246 production Java files. Counts below include nested
packages and are useful for locating concentration, not as size targets.

| Package | Files | Responsibility and allowed dependency direction |
| --- | ---: | --- |
| `config` | 60 | Immutable settings/ore models, strict JSON, validation, profile catalogs, forecasting, import planning, permissions, and the runtime snapshot. Models and pure analysis should not depend on GUI or command code. |
| `network` | 41 | Bounded wire models, codecs, payloads, registration, and the admin-service interface. Payloads depend on immutable views/models; domain services must not depend on client screens. |
| `world` | 39 | Feature registration, deterministic ore/province/geology generation, structure-backed landmarks, catalog snapshots, and discovery. Generation consumes published config/catalog views and registries, never editor state or filesystem services. |
| `reset` | 26 | Backups, manifests, verification, retention, delete/recreate, restore, renewal, journals, evacuation, and cross-operation coordination. This subsystem owns destructive save-tree transitions. |
| `client` | 21 | Client payload handling, requests, screens, layout calculations, and widgets. It consumes bounded network snapshots and has no authority to mutate server state directly. |
| `portal` | 11 | Frame recognition, ignition, POI registration, access policy, destination construction, hub routing, and hub protection. It reads active settings through server-side access services. |
| `guide` | 11 | Visibility policy, authorization, bounded read-only snapshots, icons, and console summaries. It exposes resource guidance without seeds, coordinates, paths, or administrative secrets. |
| `audit` | 7 | Actor scoping, accepted-mutation tracking, bounded asynchronous writes, JSON-lines serialization, rotation, and lifecycle ownership. Audit failure is contained and cannot undo an accepted gameplay mutation. |
| `diagnostics` | 6 | Bounded Doctor collection, caching, rendering, redaction, and export. Expensive disk inspection runs away from the server tick thread. |
| `compat` | 6 | Dependency-free portal-construction description plus isolated JEI and EMI adapters. Common/server packages must not reference viewer implementation classes. |
| `api` | 6 | API-v1 immutable world view and public events for lifecycle, profile activation, portal travel, and landmark discovery. This is the strongest binary-compatibility boundary. |
| `admin` | 4 | Server-side adapter between bounded GUI operations and domain/config/lifecycle services, plus localized result encoding and ore-import administration. |
| `command` | 3 | Brigadier registration, command handlers, canonical command names, and ore-rule construction. Commands call the same authoritative domain services as GUI requests. |
| `content` | 2 | Seam Ledger item behavior and its server-authorized advancements. |
| `server` | 1 | Per-save startup/shutdown orchestration. |
| `gameplay` | 1 | Natural mob-spawn policy for configured mining-world gameplay. |
| root | 1 | NeoForge composition root and mod ID. |

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

Refactoring seams must be protected by characterization tests before code moves.
The strongest invariants are public API descriptors, the complete Brigadier
tree and permissions, payload codec round trips and limits, schema-2 canonical
serialization, deterministic placement outputs, GUI draft/layout state, and
restart/recovery lifecycle behavior.

## Refactoring rules

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
