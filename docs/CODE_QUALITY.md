# Delvefold Code-Quality Baseline and Policy

This document preserves the measurable v1.3.0 baseline, defines the acceptance
policy for the behavior-preserving 1.3.1 cleanup, and records the completed
implementation evidence. Baseline measurements were taken from the released
source layout before formatting or structural edits; final measurements use the
release branch after extraction and documentation. Generated resources and
Gradle build output are excluded from both inventories.

## Baseline inventory

| Measure | v1.3.0 baseline |
| --- | ---: |
| Production Java files | 246 |
| Production Java lines | 36,043 |
| JUnit Java files | 86 |
| JUnit Java lines | 8,847 |
| Passing JUnit tests | 365 |
| Passing NeoForge GameTests | 35 |
| Production files containing at least one Javadoc block | 175 |
| Production files containing no Javadoc block | 71 |
| Explicit production nullability annotations | 10 |
| Existing `@SuppressWarnings` sites | 2 |
| Validated JSON files | 61 |
| Validated literal translation keys | 599 |

The source audit found no wildcard imports, trailing whitespace, debug
`System.out`/`printStackTrace` calls, or tracked build/run output. Those are
useful positives to preserve; they do not replace semantic analysis.

### Final 1.3.1 inventory

| Measure | 1.3.1 result |
| --- | ---: |
| Production Java files | 317 |
| Production Java lines | 51,673 |
| JUnit Java files | 157 |
| JUnit Java lines | 15,611 |
| Passing JUnit tests | 601 |
| Passing NeoForge GameTests | 35 |
| Production packages explicitly `@NullMarked` | 35 of 35 |
| Test packages explicitly `@NullMarked` | 24 of 24 |
| Explicit production `@Nullable` occurrences | 557 |
| Production suppression sites | 32 |
| Suppression sites including tests | 39 |
| Compiler, Javadoc, Checkstyle, Error Prone, and NullAway findings | 0 |

The additional files and lines represent package contracts, focused pure
collaborators, characterization tests, and exhaustive API/Javadoc documentation;
they are not a feature or protocol expansion.

### Existing automated enforcement

The v1.3.0 Gradle build uses Java 21 and currently enables only
`-Xlint:deprecation` and `-Xlint:unchecked`. `check` runs JUnit plus repository
tasks that parse shipped JSON and verify literal Delvefold translation keys.
GitHub Actions separately runs GameTests, a dedicated-server startup smoke, and
base/JEI/EMI/both client smokes. JAR ordering and timestamps are configured for
reproducible output.

Formatting, Javadoc/doclint, general compiler lint, nullness, Error Prone, and a
documentation-presence policy are not yet enforced at baseline.

### Baseline warnings and debt

- Javadoc reaches the tool's 100-warning output cap. The dominant category is
  undocumented record components, accompanied by missing public/protected type,
  constructor, method, and field contracts.
- Java compilation reports two Minecraft/NeoForge deprecation/removal warnings:
  the mock-server-player GameTest helper used by `PortalGameTests`, and the
  structure-placement construction seam used by
  `GenerationSaltedRandomSpreadPlacement`.
- The 10 explicit nullable boundaries use the legacy `javax.annotation.Nullable`
  annotation and are concentrated in portal event/transition integration.
- The two existing suppressions are a broad unchecked suppression in
  `DelvefoldPermissions` and a removal suppression at the JEI category boundary.
  Both require review, narrowing, and a written third-party rationale if they
  cannot be removed.

## Concentration and hotspot inventory

Line count does not prove poor design, but large controllers and multi-domain
services carry the highest regression and review risk. The baseline leaders are:

| Production class | Lines | Primary concern to separate |
| --- | ---: | --- |
| `DelvefoldCommands` | 1,726 | Brigadier tree construction, suggestions, permissions, handlers, formatting, and lifecycle/admin dispatch are co-located. |
| `DelvefoldOreRuleWizardScreen` | 1,448 | Draft state, validation, layout, navigation, rendering, widget ownership, and network submission are intertwined. |
| `DelvefoldDashboardScreen` | 1,252 | Multiple administration tabs, capabilities, scrolling, rendering, and action state share one controller. |
| `DelvefoldDoctorService` | 820 | Collection, filesystem inspection, concurrency, caching, redaction inputs, and report assembly span several diagnostic domains. |
| `DefaultDelvefoldAdminService` | 818 | Network-facing authorization, view assembly, config mutation, lifecycle dispatch, async completions, and localization share one adapter. |
| `WorldOperationService` | 746 | Request state, confirmation, journals, startup transaction, file trees, backups, rollback, history, and auditing are tightly coupled. |
| `DelvefoldConfigService` | 682 | Runtime publication, mutation coordination, persistence, profile operations, lifecycle transitions, events, and forecasts share one facade. |
| `DelvefoldOreImportScreen` | 669 | Paged scan/preview display, selection, session state, layout, rendering, and request handling are co-located. |
| `OreImportSessionService` | 623 | Admission, rate limiting, token binding, expiration, scan/preview/commit sessions, and result construction share one stateful service. |
| `OreProfileForecastBuilder` | 616 | Profile aggregation, terrain analysis, issue generation, paging, and network-budget truncation share one builder. |
| `DelvefoldSetupScreen` | 596 | Three-step draft state, responsive sections, rendering, and submission share one screen. |

The cleanup does not impose a maximum line count. A facade may remain large when
its breadth is public API or declarative registration. Success means each moved
responsibility has a clear owner, dependency direction, and characterization
test—not that every file falls below an arbitrary threshold.

### Performance-sensitive paths

The following paths require equivalence tests and before/after evidence:

- ore host/tag resolution, weighted output selection, band/province planning,
  and per-block placement;
- geology strata/decorations and landmark candidate/spacing/biome selection;
- portal frame scans, POI searches, safe-site searches, and hub integrity checks;
- client render loops that wrap text, rebuild derived lists, or lay out large ore
  and backup collections;
- backup catalog traversal, SHA-256 verification, retention measurement, restore
  staging, and Doctor disk estimates;
- config/profile parsing, validation, import discovery, and forecast aggregation;
- audit admission, queue drain, rotation, and stop-session races.

No optimization is accepted solely because it appears shorter or more clever.
It must remove measured repeated work, preserve ordering and deterministic
outputs, avoid moving blocking work onto the server thread, and have a focused
regression test.

## Target quality toolchain

The 1.3.1 branch pins every analysis tool and connects each check to Gradle's
`check` lifecycle and both build/release workflows.

| Concern | Required tool and policy |
| --- | --- |
| Formatting | Spotless 8.9.0 with Palantir Java Format 2.96.0, import ordering, unused-import removal, Javadoc formatting, annotation placement, UTF-8, LF, and final newline. |
| Compiler diagnostics | Temurin Java 21, complete applicable lint, and warnings promoted to errors after baseline deprecations are resolved. Use `-XDaddTypeAnnotationsToSymbol=true` for reliable JDK 21 type-use analysis. |
| General static analysis | Error Prone Gradle plugin 5.1.0 with Error Prone 2.50.0. Findings are errors unless a narrow, documented third-party exception is unavoidable. |
| Nullness | JSpecify 1.0.0 plus NullAway 0.13.8, applied to production and tests with `OnlyNullMarked=true` and explicit null marking required. |
| Documentation | Checkstyle 13.4.2 for public/protected documentation presence and Javadoc with full doclint plus warning-as-error for semantic validity. |
| Repository resources | Existing JSON parsing and translation-key validation remain mandatory. |
| Runtime compatibility | Existing unit tests, GameTests, dedicated-server smoke, and four recipe-viewer client smokes remain mandatory. |

JSpecify is a compile-time API contract (`compileOnlyApi`) so downstream API
consumers can see type-use annotations. JSpecify, NullAway, Error Prone,
Checkstyle, Spotless, Palantir Format, JEI, and EMI implementation libraries
must not be embedded in the release JAR.

NullAway's experimental full JSpecify generic mode is explicitly outside 1.3.1
because its documented wildcard/generic coverage is incomplete. Standard
JSpecify annotations and NullAway checking remain required. Enabling an
experimental mode later requires a separate compatibility review.

Error Prone 2.50.0's non-correctness `StringConcatToTextBlock` checker is the
sole analyzer-level exception. It throws `NoSuchElementException` while scanning
valid translated-component string literals after Palantir formatting, aborting
compilation before diagnostics can complete. The Gradle configuration disables
only that stylistic rule; compiler lint, all other Error Prone checks, NullAway,
and explicit-null-marking enforcement remain active at error severity.

## Annotation policy

“Annotate everything” means every relevant contract is explicit; it does not
mean adding redundant metadata to every declaration.

### Nullness

- Add a documented `package-info.java` carrying `@NullMarked` to every production
  package and every test-only package.
- Under a null-marked package, unannotated reference types are non-null.
  `@Nullable` appears only where `null` is an intentional part of the contract.
- Replace `javax.annotation.Nullable` with `org.jspecify.annotations.Nullable`.
  Place it on the actual type use, including generic arguments, arrays, nested
  types, and varargs elements where applicable.
- Null returned by Minecraft/NeoForge callbacks must be modeled explicitly at
  the platform adapter. Internal pure code should receive a validated non-null
  value or a deliberately modeled `Optional`/result.
- Do not change a public JVM descriptor merely to eliminate null. In particular,
  do not replace a public nullable return type with `Optional` during this
  compatibility release.
- Do not use `Objects.requireNonNull` as a substitute for annotating the declared
  contract. It remains appropriate for fail-fast validation at trust boundaries.

### Standard semantic annotations

- Require `@Override` on every override and interface implementation method.
- Use `@FunctionalInterface` where an interface intentionally has one abstract
  method and that promise is part of maintainability.
- Use `@SafeVarargs` only where the body has been reviewed and generic varargs are
  demonstrably safe.
- Retain NeoForge/Minecraft registration, distribution, GameTest, codec, and event
  annotations where the platform consumes them.
- Do not add `@Deprecated`, serialization annotations, thread annotations, or
  framework metadata without a real consumer and documented compatibility need.
- Do not use annotations as decoration: redundant `@NonNull`, blanket generated
  markers, or unchecked claims that a tool does not verify are disallowed.

## Documentation policy

Javadoc is required for every public or protected production type, constructor,
method, field, record component, and enum contract, except an override whose
contract is completely inherited and whose behavior adds no relevant caveat.

Documentation must state the facts a caller cannot safely infer from the method
body or name:

- units (ticks, seconds, bytes, blocks, chunks, revisions, epoch milliseconds);
- valid ranges, bounds, normalization, and sentinel meanings;
- ownership, immutability, snapshot lifetime, and whether returned collections
  are defensive/unmodifiable views;
- calling thread, synchronization, asynchronous completion thread, and shutdown
  behavior;
- deterministic inputs, random-sequence compatibility, and chunk-write scope;
- permissions, token binding, redaction, and path-validation boundaries;
- failure containment, rejected versus exceptional results, and thrown
  exceptions;
- whether a setting applies live, to new chunks, or only after recreation.

Private implementation documentation is selective. It is required for
non-obvious concurrency, filesystem transactions, recovery states, token
security, deterministic seed mixing, codec compatibility, server/client thread
handoff, and intentionally surprising platform workarounds. It is not required
for trivial accessors, widgets, obvious local loops, or prose that merely repeats
the code.

Comments must remain true across refactors. A stale or speculative comment is a
defect. Public documentation must use contract language rather than promise a
specific private class layout.

## Suppression policy

Warnings are fixed at their source whenever possible. A suppression is allowed
only when all of these conditions hold:

1. the finding originates from a verified Minecraft, NeoForge, JEI, EMI, JDK, or
   analysis-tool limitation rather than Delvefold logic;
2. no supported API or type-safe expression removes it without changing behavior;
3. the annotation is placed on the smallest declaration that triggers the
   finding—never on a package or broad service class;
4. an adjacent comment names the external limitation and explains why the code
   remains safe;
5. a test covers the assumption when it can fail at runtime.

The cleanup adds a source-contract test that rejects package-wide or blanket
suppressions and rejects suppression categories outside an explicit allowlist.
`@SuppressWarnings("all")`, unchecked/raw suppression of an entire subsystem,
and disabling Error Prone or NullAway for a package are prohibited. Any new
allowlisted category requires review and an update to this document.

### Reviewed 1.3.1 exceptions

The contract pass leaves only the following narrowly scoped categories. Every
site has an adjacent source comment describing its concrete external or
identity-based invariant; the source-hygiene test requires the allowlist to
match the production tree exactly, so both new and stale entries fail the build.
Each occurrence is keyed to its owning declaration signature instead of a line
number, and repeated uses of one category in the same file remain distinct.
Both shorthand and named-`value` annotation syntax pass through the same parser.

| Category | Reviewed use |
| --- | --- |
| `ReferenceEquality` | Minecraft registry singletons, exact lifecycle/server ownership, throwable cause-cycle traversal, and last-known-good immutable snapshot identity. Value equality would weaken the contract being implemented or tested. |
| `EnumOrdinal` | Protocol 12 enum encoding, declaration-ordered GUI choices, and a guide compatibility assertion. Changing these sites to another order would change wire or visible compatibility. |
| `try` | Actor scopes whose required effect is their LIFO `close()` behavior; the resource binding is intentionally otherwise unused. |
| `ThreadLocalUsage` | The bounded audit-actor scope owned by a command/network caller thread and explicitly transferred as a captured string before asynchronous work. |
| `unchecked` | NeoForge's permission-node factory exposes a generic boundary that cannot be expressed without the external API's unchecked conversion. |
| `deprecation` / `removal` | Minecraft 1.21.1 structure-placement and connected-player GameTest seams, plus the retained JEI 19.x compatibility method, where no supported replacement exists for the targeted versions. |
| `UnusedVariable` | A source/binary-compatible guide constructor parameter retained for released callers even though the newer bounded model no longer stores it. |
| `NullableOptional` | The released lifecycle-event constructor intentionally normalizes both a null `Optional` container and `Optional.empty()`; the narrow suppression keeps that tolerant public contract explicit without changing its JVM descriptor or behavior. |

There are no `NullAway`, `all`, package-level, or multi-category suppressions.

## Cleanup controls

The refactor is divided into auditable stages to make semantic drift visible:

1. **Characterization baseline:** freeze public JVM descriptors and add tests for
   command trees, codecs, schema-2 serialization, seeds, GUI state/layout, and
   lifecycle recovery before moving code.
2. **Mechanical formatting:** apply the formatter in one isolated commit with no
   strings, resources, data, protocol, or behavioral edits.
3. **Contracts:** introduce package null marking, nullable boundaries, semantic
   annotations, Javadocs, and zero-warning enforcement independently from class
   extraction.
4. **Shared infrastructure:** consolidate atomic-file, safe-tree, journal, and
   thread-factory primitives only behind failure and concurrency tests.
5. **Responsibility extraction:** retain stable facades while moving commands,
   admin handlers, diagnostics collectors, GUI state/layout, configuration
   coordination, and import security into package-private collaborators.
6. **Performance review:** compare deterministic counters and Java Flight
   Recorder evidence; accept only tested, behavior-equivalent improvements.
7. **Release audit:** compare the final API, wire/data contracts, deterministic
   outputs, JAR contents, runtime smokes, and checksum with the expected 1.3.1
   artifact.

Each stage receives a focused commit and must pass the relevant gates before the
next stage begins. If a formatter or extraction obscures a semantic change, the
change is split again.

## Required acceptance gates

The completed branch must satisfy all of the following:

- `spotlessCheck`, Checkstyle, Javadoc/doclint, Error Prone, NullAway, and full
  compiler lint pass with zero unexplained warnings;
- all JUnit tests, JSON validation, translation validation, and
  `git diff --check` pass;
- public API JVM descriptors match the v1.3.0 baseline;
- command paths, arguments, aliases, suggestions, localization, and permissions
  match their characterization snapshot;
- every network codec round-trips valid boundary values and rejects oversized,
  malformed, or wrong-format data before unsafe allocation;
- schema-2 settings/profiles parse and serialize canonically with existing
  defaults and without load-only rewrites;
- stable generation produces the same ore, province, geology, and landmark
  decisions for fixed seeds/salts/chunks;
- lifecycle tests cover request, confirm, cancel, expiration, simultaneous
  operation rejection, shutdown, startup commit, rollback, corrupt journal,
  verified restore, legacy upgrade, and retained staging;
- GUI state/layout tests cover 854x480 and 1080p at GUI scales 1 through 4,
  including keyboard focus, narration, scrolling, resize, and unsaved drafts;
- all 35-or-more NeoForge GameTests pass, followed by dedicated-server startup
  and orderly shutdown;
- base, JEI-only, EMI-only, and JEI+EMI clients start with the expected optional
  integrations and no server-side viewer dependency;
- Java Flight Recorder review finds no new blocking server-thread I/O and no
  material regression in generation or rendering hotspots;
- the release JAR is reproducible, contains the expected API/version manifest,
  excludes analysis/viewer libraries, and matches its published SHA-256 file.

## Evidence ledger

Fill this ledger during implementation. Evidence should reference a test name,
Gradle task output, JFR recording/summary, API descriptor file, or release asset;
subjective statements such as “looks faster” are not sufficient.

### Static quality

| Measure | v1.3.0 before | 1.3.1 after | Evidence |
| --- | ---: | ---: | --- |
| Compiler warnings | 2 deprecation/removal | 0 | `compileJava` and `compileTestJava` with complete applicable lint and `-Werror` in `./gradlew check --rerun-tasks`. |
| Javadoc warnings | at least 100 (output capped) | 0 | `javadoc` with full doclint and `-Werror` in `check`. |
| Production packages with `@NullMarked` | 0 | 35 of 35 | `PackageContractCoverageTest` plus the production `package-info.java` inventory. |
| Test-only packages with `@NullMarked` | 0 | 24 of 24 | `PackageContractCoverageTest` plus the test `package-info.java` inventory. |
| Explicit nullable boundaries | 10 legacy annotations | 557 JSpecify type uses | NullAway, explicit-null-marking enforcement, and the production source inventory. |
| Suppression sites/categories | 2 sites | 32 sites in 9 reviewed categories | `SourceHygieneCharacterizationTest` verifies each declaration-owned site and rejects new, stale, broad, or multi-category suppressions. |
| Error Prone findings | not enforced | 0 | Error Prone 2.50.0 runs at error severity during both Java compile tasks. |
| NullAway findings | not enforced | 0 | NullAway 0.13.8 and `RequireExplicitNullMarking` run at error severity during both Java compile tasks. |
| Checkstyle findings | not enforced | 0 | Checkstyle 13.4.2 runs with a zero-warning threshold in `checkstyleMain`. |

### Structure

| Hotspot | Before | Intended after | Evidence |
| --- | ---: | --- | --- |
| `DelvefoldCommands` | 1,726 lines | Stable facade plus domain command modules; exact tree unchanged. | Ore document transformations moved to the 322-line `OreRuleEdits`; `OreRuleEditsTest`, `DelvefoldCommandsTest`, and the command-tree characterization gate cover exact edits, paths, arguments, suggestions, and permissions. |
| `DelvefoldOreRuleWizardScreen` | 1,448 lines | Stable screen plus immutable draft, validation, layout, and section collaborators. | Draft, parsing, band inputs, validation, responsive layout, preview, and scroll state moved to `OreRuleWizardDraftState`, `OreRuleWizardBandInputs`, `OreRuleWizardValidation`, `OreRuleWizardLayout`, `OreBandPreviewModel`, and `ScrollableWidgetModel`; focused model/layout tests and `OreRuleWizardRefactorSourceContractTest` pass. |
| `DelvefoldDashboardScreen` | 1,252 lines | Stable screen plus tab/section controllers and cached derived layout. | `DashboardDraftState`, `DashboardPresentation`, and `DashboardTabLayout` now own draft, immutable derived presentation, and tab geometry; their focused tests plus `DashboardRefactorSourceContractTest` preserve revision, resize, tab, and action behavior. |
| `DelvefoldDoctorService` | 820 lines | Stable service plus independent collectors and unchanged redaction/rendering. | The fully documented service was reduced from 1,003 to 825 lines by extracting the 276-line `DoctorFilesystemAnalysis`; `DoctorFilesystemAnalysisTest` covers tolerant journals, exact 64-KiB bounds, symlink/path containment, disk estimates, and saturation. |
| `DefaultDelvefoldAdminService` | 818 lines | Stable adapter plus package-private operation handlers. | The documented adapter is 479 lines and delegates backup dispatch to `AdminBackupOperations`, bounded snapshot assembly to `AdminSnapshotAssembler`, and exact ore conversion to `AdminOreDraftMapper`; focused behavior/source-contract tests, lifecycle contracts, and the API descriptor gate pass. |
| `WorldOperationService` | 746 lines | Stable lifecycle service using tested shared journal/file primitives. | `AtomicFilesTest`, `LifecycleFileOperationsTest`, `LifecycleJournalFilesTest`, `RestartBoundLifecycleCharacterizationTest`, and `SharedInfrastructureCharacterizationTest`; API/schema/protocol descriptors unchanged. |
| `DelvefoldConfigService` | 682 lines | Stable facade with separated mutation, publication, and transition planning. | `ConfigDocumentTransitions`, `LiveConfigReloadPolicy`, and `ProfileCatalogOperations` isolate pure document transitions, recreation-lock reload decisions, and named-profile catalog mutations. Their focused tests plus existing repository, JSON, save-order, revision, audit, and API gates preserve facade behavior. |
| `DelvefoldOreImportScreen` | 669 lines | Stable screen with separated paged display/selection/session state. | The screen delegates its 444-line state owner and responsive geometry to `OreImportScreenState` and `OreImportPanelLayout`; focused state/layout tests and `OreImportScreenSourceContractTest` cover selection, paging, preview truncation, capability resets, resize, and request transitions. |
| `OreImportSessionService` | 623 lines | Stable synchronized state owner plus isolated token-security and validation policy. | The fully documented facade was reduced from 952 to 813 lines; `OreImportTokenSecurityTest`, `OreImportSessionValidationTest`, and expanded service tests cover token format/digests/collisions, expiry/cooldown boundaries, binding precedence, normalization, replay, and invalidation. |
| `OreProfileForecastBuilder` | 616 lines | Stable public facade plus deterministic rule analysis and network-budget fitting. | The fully documented facade was reduced from 713 to 192 lines; `OreForecastRuleAnalysisTest`, `OreForecastNetworkBudgetFitterTest`, and existing forecast contracts cover status/order/formulas, province overlays, bounds, fair issue admission, exact byte limits, and truncation. |

### Performance and threading

| Scenario | Before evidence | After evidence | Acceptance |
| --- | --- | --- | --- |
| Representative dense ore/province chunk generation | v1.3.0 fixed seed/salt/chunk vectors and work-budget fixtures | The generation path was not rewritten; `GenerationSeedCompatibilityCharacterizationTest`, `GenerationSeedTest`, `ProvincePlacementPlannerTest`, ore-analysis tests, and all GameTests preserve decisions and bounds. | Identical placement inputs/outputs and work caps; no new allocation or I/O was added to chunk generation. |
| Landmark selection and structure placement | v1.3.0 catalog, spacing, seed, and resource fixtures | Landmark generation code was unchanged; seed compatibility, landmark resource contracts, catalog tests, and structure GameTests remain green. | Candidate decisions, structure resources, spacing, and immutable last-known-good publication remain bounded and deterministic. |
| Ore wizard/dashboard render and resize | Baseline source/render characterization and compact-layout fixtures | Immutable guide/forecast/icon/distribution presentations, wrapped tooltips, and responsive layout are cached outside render loops; dashboard, wizard, forecast, picker, import, backup, and scroll-model tests cover compact and normal geometry. | Revision/resize-invariant data is no longer rebuilt per frame; 320x240 logical-floor controls remain reachable with correct focus and scrolling. |
| Backup catalog with large backup set | Existing bounded catalog/cache and deletion-guard fixtures | `BackupCatalogCacheTest`, `WorldBackupCatalogTest`, `BackupDeletionGuardTest`, `BackupRetentionServiceTest`, and `BackupCatalogAsyncContractTest` pass after shared file/thread extraction. | Traversal remains on the dedicated catalog worker; immutable snapshots, retention protection, and results are unchanged. |
| Large backup verification | Existing manifest/hash, validation, and asynchronous-operation contracts | Manifest, admin-operation, async-isolation, and source-contract tests confirm hashing remains on the verifier pool and only completion coordination returns to the server thread. | No large-file hashing or wait was introduced on the server tick thread. |
| Doctor refresh/export | Existing cache, redaction, filesystem-analysis, renderer, and exporter fixtures | `DoctorFilesystemAnalysisTest`, `DoctorReportCacheTest`, `DoctorReportBuilderTest`, `DoctorReportRendererTest`, and `DoctorReportExporterTest` pass with the collector split preserved. | Disk work remains on the Doctor worker; bounded reports and export redaction are unchanged. |
| Audit shutdown with in-flight mutation | v1.3.0 admission, queue, rotation, and lifecycle fixtures | `AsyncAuditMutationTrackerTest`, `AuditWriteQueueTest`, `DelvefoldAuditServiceTest`, `RotatingAuditLogTest`, and `AsyncIsolationCharacterizationTest` pass with named worker infrastructure. | No cross-save writes, lost accepted mutations, shared worker queue, or indefinite drain. |

A 30-second Java Flight Recorder profile on a freshly started dedicated server
sampled 29 server ticks: 0.543 ms mean, 0.229 ms median, and 7.700 ms maximum.
The recording reported zero file-read, file-write, socket-read/write,
Java-monitor-enter, or garbage-collection events during the sample. The server
then shut down cleanly and saved vanilla plus all six Delvefold dimensions. This
profile is evidence for server-thread/I/O health; deterministic generation and
client presentation equivalence are established by the focused tests above
rather than inferred from an idle-server sample.

### Compatibility and release

| Contract | Baseline | Final evidence |
| --- | --- | --- |
| API descriptors | v1.3.0 JAR, API 1 | `PublicApiCompatibilityTest` passes with API version 1 and unchanged public JVM descriptors. |
| Network protocol | 12 | Protocol 12 remains declared; payload codec boundary/round-trip tests and the hosted build pass without wire-model changes. |
| Settings and ore schema | 2 / 2 | Canonical JSON, schema, compatibility-default, repository-safety, transition, and profile fixtures all pass with schema 2 unchanged. |
| Stable generation | v1.3.0 fixed vectors | Seed, ore selection, province, geology, and landmark characterization tests plus 35 GameTests preserve the fixed decisions. |
| Unit/GameTests | 365 / 35 passing | 601 of 601 JUnit tests and 35 of 35 required NeoForge GameTests pass with no failures, errors, or skips. |
| Dedicated server | startup and orderly stop pass | Local smoke reached `Done`, then saved vanilla and all six Delvefold dimensions during orderly shutdown; the hosted dedicated-server smoke is green. |
| Recipe viewers | base, JEI, EMI, both pass | Hosted build and JEI-only, EMI-only, and JEI+EMI client-smoke jobs are green; optional viewer libraries remain isolated from common/server runtime. |
| Release artifact | `delvefold-1.21.1-1.3.1.jar` | Two clean builds produced the identical SHA-256 `ad69165417166ad27797eb0a468de87879a0150d884b1dde94eaee5941b7fc10`; the tagged workflow publishes the verified [GitHub release asset](https://github.com/nightstalker6669/delvefold-mining-worlds/releases/download/v1.3.1/delvefold-1.21.1-1.3.1.jar). |

## Out of scope for 1.3.1

- feature, command, payload, schema, registry, dimension, or generation changes;
- protocol or API-version increments;
- live unloading/deletion of a loaded mining dimension;
- experimental NullAway JSpecify generic mode;
- mandatory coverage percentages, SpotBugs, PMD, or an architectural rewrite
  driven only by tool count;
- Fabric support, multiple simultaneous mining worlds, or unrestricted entity
  portal travel;
- automatic CurseForge publishing.

If static analysis exposes a defect whose correct fix would change an observable
contract, stop and document it for a separately scoped patch instead of hiding a
behavior change inside the cleanup.
