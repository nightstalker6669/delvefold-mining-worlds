package com.nightsta69.delvefold.config;

import com.mojang.logging.LogUtils;
import com.nightsta69.delvefold.admin.AdminLocalizedMessage;
import com.nightsta69.delvefold.api.DelvefoldApi;
import com.nightsta69.delvefold.api.event.DelvefoldOreProfileActivatedEvent;
import com.nightsta69.delvefold.api.event.DelvefoldWorldLifecycleEvent;
import com.nightsta69.delvefold.audit.AuditMutation;
import com.nightsta69.delvefold.audit.DelvefoldAuditService;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast;
import com.nightsta69.delvefold.config.model.GameplayPreset;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.model.OreRule;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.WorldIdentitySettings;
import com.nightsta69.delvefold.config.model.WorldSettingsDocument;
import com.nightsta69.delvefold.config.validation.ConfigIssue;
import com.nightsta69.delvefold.config.validation.OreConfigValidator;
import com.nightsta69.delvefold.config.validation.ValidationReport;
import com.nightsta69.delvefold.world.feature.MinecraftOreProfileForecastBuilder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Process-wide owner of the active immutable configuration snapshot and per-save profile catalog.
 *
 * <p>Chunk-generation threads perform lock-free reads from the atomic snapshot. Startup, reload, validation, optimistic
 * mutation, bounded disk persistence, audit planning, and publication are serialized by one mutation lock; a candidate
 * is fully validated and persisted before its atomic publication. Methods do not perform player authorization—command
 * and network boundaries must call {@link AdminAccess} before invoking administrative operations. Server shutdown
 * clears the snapshot only for the exact server instance that started it.
 */
public final class DelvefoldConfigService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DelvefoldConfigService INSTANCE = new DelvefoldConfigService();

    private final AtomicReference<@Nullable ConfigSnapshot> current = new AtomicReference<>();
    private final Object mutationLock = new Object();
    private volatile @Nullable MinecraftServer server;
    private volatile @Nullable FileConfigRepository repository;
    private volatile @Nullable OreProfileCatalog profileCatalog;
    private volatile boolean readOnlyIncompatible;
    private volatile String compatibilityMessage = "";

    private DelvefoldConfigService() {}

    /**
     * Returns the process-wide configuration service.
     *
     * @return singleton service instance
     */
    public static DelvefoldConfigService get() {
        return INSTANCE;
    }

    /**
     * Binds the service to a server save, recovers/creates configuration as needed, and publishes the resulting
     * snapshot.
     *
     * <p>Rejected disk state publishes an explicit last-known-good or built-in fallback and may put the service into
     * read-only compatibility mode when schema 2 is not supported. This lifecycle method must be called from server
     * startup before administrative or world-generation consumers use the service.
     *
     * @param minecraftServer running server whose save owns the configuration
     * @return accepted or fallback load result that was atomically published
     * @throws NullPointerException if {@code minecraftServer} is {@code null}
     * @throws IOException if safe directory/default creation or pending-transaction recovery fails
     */
    public ConfigLoadResult start(MinecraftServer minecraftServer) throws IOException {
        synchronized (mutationLock) {
            server = Objects.requireNonNull(minecraftServer, "minecraftServer");
            ConfigPaths paths = ConfigPaths.forServer(minecraftServer);
            MinecraftRegistryLookup registryLookup = new MinecraftRegistryLookup();
            FileConfigRepository activeRepository = new FileConfigRepository(paths, registryLookup);
            OreProfileCatalog activeCatalog = new OreProfileCatalog(paths, registryLookup);
            repository = activeRepository;
            profileCatalog = activeCatalog;
            ConfigLoadResult result = activeRepository.loadOrCreate(current.get());
            updateCompatibility(result);
            current.set(result.snapshot());
            logIssues("load", result.issues());
            return result;
        }
    }

    /**
     * Clears service state only when the exact bound server instance is stopping.
     *
     * <p>Reference identity prevents a delayed integrated-server stop callback from clearing configuration already
     * owned by a newer server instance. A nonmatching server is ignored.
     *
     * @param minecraftServer server instance issuing the lifecycle stop
     */
    @SuppressWarnings("ReferenceEquality")
    public void stop(MinecraftServer minecraftServer) {
        synchronized (mutationLock) {
            if (server == minecraftServer) {
                server = null;
                repository = null;
                profileCatalog = null;
                current.set(null);
                readOnlyIncompatible = false;
                compatibilityMessage = "";
            }
        }
    }

    /**
     * Performs a lock-free read of the current immutable publication snapshot.
     *
     * @return current snapshot, whose owned model/report values cannot be mutated by the caller
     * @throws IllegalStateException if the service is not started or has already stopped
     */
    public ConfigSnapshot snapshot() {
        ConfigSnapshot snapshot = current.get();
        if (snapshot == null) {
            throw new IllegalStateException("Delvefold configuration is not loaded");
        }
        return snapshot;
    }

    /**
     * Reloads canonical disk files, enforces live lifecycle locks, and publishes only an accepted candidate.
     *
     * <p>Terrain, terrain variant, geology theme, initialization state, generation epoch/salt, and lifecycle operation
     * ID cannot be changed by editing JSON on a live server. Rejected or incompatible input returns the active snapshot
     * as fallback without replacing it. Accepted mutations are audit-planned after publication.
     *
     * @return accepted reload result or a diagnosed fallback retaining the active snapshot
     * @throws IllegalStateException if the service is not started
     * @throws IOException if safe creation/recovery or repository access fails outside fallback containment
     */
    public ConfigLoadResult reload() throws IOException {
        synchronized (mutationLock) {
            ensureStarted();
            ConfigSnapshot before = snapshot();
            ConfigLoadResult result =
                    enforceLiveLifecycleLocks(before, requiredRepository().loadOrCreate(before));
            updateCompatibility(result);
            if (!result.usedFallback()) {
                current.set(result.snapshot());
                auditSavedConfiguration(before, result.snapshot());
            }
            logIssues("reload", result.issues());
            return result;
        }
    }

    /**
     * Validates the files currently on disk without creating, recovering, replacing, or publishing them.
     *
     * <p>The same live lifecycle locks used by reload are applied to the candidate. A pending transaction is reported
     * but not recovered, and the active snapshot remains unchanged.
     *
     * @return read-only accepted candidate or diagnosed fallback snapshot
     * @throws IllegalStateException if the service is not started
     * @throws IOException if repository validation encounters an uncontained low-level read failure
     */
    public ConfigLoadResult validateDisk() throws IOException {
        synchronized (mutationLock) {
            ensureStarted();
            ConfigSnapshot before = snapshot();
            return enforceLiveLifecycleLocks(before, requiredRepository().validateDisk(before));
        }
    }

    private static ConfigLoadResult enforceLiveLifecycleLocks(
            @Nullable ConfigSnapshot before, ConfigLoadResult result) {
        if (before == null || result.usedFallback()) {
            return result;
        }
        List<ConfigIssue> lifecycleIssues = LiveSettingsTransition.validate(
                before.settings(), result.snapshot().settings());
        if (lifecycleIssues.isEmpty()) {
            return result;
        }
        List<ConfigIssue> issues = new ArrayList<>(result.issues());
        issues.addAll(lifecycleIssues);
        issues.add(ConfigIssue.warning(
                "fallback.last_good",
                "$",
                "Lifecycle-owned settings changed on disk; continuing with the active snapshot"));
        return new ConfigLoadResult(before, true, issues);
    }

    /**
     * Initializes an uninitialized save using its current world identity.
     *
     * <p>Both observed revisions must match. Success installs a revision-incremented copy of the bundled ore preset,
     * advances settings lifecycle state, transactionally saves both documents, atomically publishes them, records audit
     * mutations, and posts the public initialized event. Expected-revision, state, validation, and persistence failures
     * are returned as unsaved results rather than thrown.
     *
     * @param expectedOreRevision ore revision observed by the caller
     * @param expectedSettingsRevision settings revision observed by the caller
     * @param terrain recreation-locked terrain family
     * @param orePreset bundled starting ore profile
     * @param gameplayPreset initial live natural-spawning policy
     * @return committed snapshot or a bounded rejection retaining the current snapshot when available
     */
    public ConfigWriteResult initialize(
            long expectedOreRevision,
            long expectedSettingsRevision,
            TerrainMode terrain,
            OrePreset orePreset,
            GameplayPreset gameplayPreset) {
        return initialize(expectedOreRevision, expectedSettingsRevision, terrain, orePreset, gameplayPreset, null);
    }

    /**
     * Initializes an uninitialized save with an optional complete replacement identity.
     *
     * <p>A {@code null} identity preserves the current configured identity. The selected renewal seed mode determines
     * the persisted generation salt at this transition; later restarts do not rotate it. Authorization must already
     * have been performed by the command/network boundary.
     *
     * @param expectedOreRevision ore revision observed by the caller
     * @param expectedSettingsRevision settings revision observed by the caller
     * @param terrain recreation-locked terrain family
     * @param orePreset bundled starting ore profile
     * @param gameplayPreset initial live natural-spawning policy
     * @param identity replacement identity, or {@code null} to retain the current identity
     * @return committed snapshot or a bounded rejection retaining the current snapshot when available
     */
    public ConfigWriteResult initialize(
            long expectedOreRevision,
            long expectedSettingsRevision,
            TerrainMode terrain,
            OrePreset orePreset,
            GameplayPreset gameplayPreset,
            @Nullable WorldIdentitySettings identity) {
        synchronized (mutationLock) {
            try {
                ensureStarted();
                ensureWritable();
                ConfigSnapshot before = snapshot();
                if (before.settings().revision() != expectedSettingsRevision) {
                    return stale(
                            before,
                            "settings",
                            expectedSettingsRevision,
                            before.settings().revision());
                }
                if (before.ores().revision() != expectedOreRevision) {
                    return stale(
                            before, "ores", expectedOreRevision, before.ores().revision());
                }
                if (before.settings().initialized()) {
                    return rejected(
                            before,
                            "world.already_initialized",
                            "Delvefold has already been initialized for this save");
                }

                OreProfileDocument preset = OrePresets.create(orePreset);
                OreProfileDocument ores = new OreProfileDocument(
                        OreProfileDocument.CURRENT_SCHEMA_VERSION,
                        before.ores().revision() + 1,
                        preset.profile(),
                        preset.rules());
                WorldSettingsDocument settings =
                        before.settings().initialize(terrain, orePreset, gameplayPreset, identity);
                ConfigSnapshot saved = requiredRepository().save(ores, settings);
                current.set(saved);
                auditSavedConfiguration(before, saved);
                NeoForge.EVENT_BUS.post(new DelvefoldWorldLifecycleEvent(
                        DelvefoldWorldLifecycleEvent.Action.INITIALIZED,
                        DelvefoldApi.worldView(before.settings()),
                        DelvefoldApi.worldView(saved.settings()),
                        ""));
                return new ConfigWriteResult(true, saved, List.of());
            } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
                LOGGER.error("Could not initialize Delvefold", exception);
                return rejected(current.get(), "initialize.failed", exception.getMessage());
            }
        }
    }

    /**
     * Applies a trusted ore-document transformation under the serialized mutation lock.
     *
     * <p>The callback receives the current immutable document and must return the complete candidate. The service
     * forces schema 2 and the next ore revision, validates registry references and deterministic aggregate work
     * budgets, then transactionally persists and atomically publishes it with unchanged settings. The active profile ID
     * must remain cross-file consistent. Callback, stale-revision, validation, and persistence failures become unsaved
     * results.
     *
     * @param expectedRevision ore revision observed by the caller
     * @param update nonblocking transformation that must not return {@code null}
     * @return committed snapshot plus warnings, or a bounded rejection retaining the current snapshot
     */
    public ConfigWriteResult updateOres(long expectedRevision, UnaryOperator<OreProfileDocument> update) {
        synchronized (mutationLock) {
            try {
                ensureStarted();
                ensureWritable();
                ConfigSnapshot before = snapshot();
                if (before.ores().revision() != expectedRevision) {
                    return stale(before, "ores", expectedRevision, before.ores().revision());
                }
                OreProfileDocument candidate =
                        Objects.requireNonNull(update.apply(before.ores()), "updated ore document");
                candidate = new OreProfileDocument(
                        OreProfileDocument.CURRENT_SCHEMA_VERSION,
                        before.ores().revision() + 1,
                        candidate.profile(),
                        candidate.rules());
                ValidationReport report = OreConfigValidator.validate(candidate, new MinecraftRegistryLookup());
                if (!report.valid()) {
                    return new ConfigWriteResult(false, before, report.issues());
                }
                ConfigSnapshot saved = requiredRepository().save(candidate, before.settings());
                current.set(saved);
                auditSavedConfiguration(before, saved);
                return new ConfigWriteResult(true, saved, report.issues());
            } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
                LOGGER.error("Could not save Delvefold ore configuration", exception);
                return rejected(current.get(), "ores.save_failed", exception.getMessage());
            }
        }
    }

    /**
     * Atomically creates or updates an ore rule.
     *
     * <p>When {@code createOnly} is true, an existing rule with the same ID is rejected instead of being overwritten.
     * The collision check and disk write share the ore mutation lock and revision check. The complete resulting profile
     * is registry- and work-budget-validated before transactional persistence and atomic publication.
     *
     * @param expectedRevision ore revision observed by the caller
     * @param replacement complete immutable replacement rule, matched by stable rule ID
     * @param createOnly whether an existing same-ID rule must cause a nonmutating collision rejection
     * @return committed snapshot plus warnings, or a bounded rejection retaining the current snapshot
     * @throws NullPointerException if {@code replacement} is {@code null}
     */
    public ConfigWriteResult saveOreRule(long expectedRevision, OreRule replacement, boolean createOnly) {
        Objects.requireNonNull(replacement, "replacement");
        synchronized (mutationLock) {
            try {
                ensureStarted();
                ensureWritable();
                ConfigSnapshot before = snapshot();
                if (before.ores().revision() != expectedRevision) {
                    return stale(before, "ores", expectedRevision, before.ores().revision());
                }

                OreRuleSavePlanner.Plan plan =
                        OreRuleSavePlanner.plan(before.ores().rules(), replacement, createOnly);
                if (plan.collision()) {
                    return rejected(
                            before,
                            "ores.rule_already_exists",
                            "An ore rule with ID '" + replacement.id()
                                    + "' already exists. Open that rule from the ore list to edit it.");
                }

                OreProfileDocument candidate =
                        before.ores().nextRevision(plan.rules(), before.ores().profile());
                candidate = new OreProfileDocument(
                        OreProfileDocument.CURRENT_SCHEMA_VERSION,
                        before.ores().revision() + 1,
                        candidate.profile(),
                        candidate.rules());
                ValidationReport report = OreConfigValidator.validate(candidate, new MinecraftRegistryLookup());
                if (!report.valid()) {
                    return new ConfigWriteResult(false, before, report.issues());
                }
                ConfigSnapshot saved = requiredRepository().save(candidate, before.settings());
                current.set(saved);
                auditSavedConfiguration(before, saved);
                return new ConfigWriteResult(true, saved, report.issues());
            } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
                LOGGER.error("Could not save Delvefold ore rule", exception);
                return rejected(current.get(), "ores.save_failed", exception.getMessage());
            }
        }
    }

    /**
     * Applies a trusted settings-document transformation under the serialized mutation lock.
     *
     * <p>The callback receives the current immutable document and must return a complete candidate. The service forces
     * schema 2 and the next settings revision, transactionally persists it with unchanged ores, then atomically
     * publishes and audits the result. This low-level copier is intended for authorized live-setting mutations; callers
     * must preserve lifecycle-owned generation fields and use the lifecycle service for initialization/recreation.
     * Callback, stale-revision, validation, and persistence failures become unsaved results.
     *
     * @param expectedRevision settings revision observed by the caller
     * @param update nonblocking transformation that must not return {@code null}
     * @return committed snapshot or a bounded rejection retaining the current snapshot
     */
    public ConfigWriteResult updateSettings(long expectedRevision, UnaryOperator<WorldSettingsDocument> update) {
        synchronized (mutationLock) {
            try {
                ensureStarted();
                ensureWritable();
                ConfigSnapshot before = snapshot();
                if (before.settings().revision() != expectedRevision) {
                    return stale(
                            before,
                            "settings",
                            expectedRevision,
                            before.settings().revision());
                }
                WorldSettingsDocument candidate =
                        Objects.requireNonNull(update.apply(before.settings()), "updated settings document");
                candidate = new WorldSettingsDocument(
                        WorldSettingsDocument.CURRENT_SCHEMA_VERSION,
                        before.settings().revision() + 1,
                        candidate.generationEpoch(),
                        candidate.generationSalt(),
                        candidate.lastWorldOperationId(),
                        candidate.initialized(),
                        candidate.terrainMode(),
                        candidate.orePreset(),
                        candidate.gameplay(),
                        candidate.portal(),
                        candidate.activeProfileId(),
                        candidate.identity(),
                        candidate.guideVisibility(),
                        candidate.backupRetention());
                ConfigSnapshot saved = requiredRepository().save(before.ores(), candidate);
                current.set(saved);
                auditSavedConfiguration(before, saved);
                return new ConfigWriteResult(true, saved, List.of());
            } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
                LOGGER.error("Could not save Delvefold settings", exception);
                return rejected(current.get(), "settings.save_failed", exception.getMessage());
            }
        }
    }

    /**
     * Performs a lock-free read of the currently bound server.
     *
     * @return running server passed to {@link #start(MinecraftServer)}, or {@code null} before start or after stop
     */
    public @Nullable MinecraftServer server() {
        return server;
    }

    /**
     * Returns a bounded immutable snapshot of bundled, ecosystem, and local profile summaries.
     *
     * @return summaries in lexical profile-ID order
     * @throws IllegalStateException if the service is not started
     * @throws IOException if catalog directory preparation or enumeration fails
     */
    public List<OreProfileCatalog.ProfileSummary> listProfiles() throws IOException {
        synchronized (mutationLock) {
            ensureStarted();
            return requiredProfileCatalog().list();
        }
    }

    /**
     * Loads a named profile without activating or rewriting it.
     *
     * <p>A null or blank ID selects the active ore document. An exact active ID returns the already published immutable
     * snapshot without filesystem access; other IDs are loaded through the safe catalog and current registry
     * validation.
     *
     * @param id local or namespaced profile ID, or null/blank for the active profile
     * @return immutable active or catalog profile snapshot
     * @throws IllegalStateException if the service is not started
     * @throws IllegalArgumentException if a supplied ID is invalid
     * @throws IOException if a non-active profile is unknown, unsafe, malformed, or invalid
     */
    public OreProfileDocument loadProfile(@Nullable String id) throws IOException {
        synchronized (mutationLock) {
            ensureStarted();
            String selected = id == null || id.isBlank() ? snapshot().ores().profile() : id.trim();
            if (selected.equals(snapshot().ores().profile())) {
                return snapshot().ores();
            }
            return requiredProfileCatalog().load(selected);
        }
    }

    /**
     * Builds one bounded, read-only forecast page from the current server registry state.
     *
     * <p>A null or blank ID selects the active profile. Page index is clamped to the available zero-based range and
     * page size to 1–16 rules. At most 512 rules and an estimated 24 KiB payload are retained; deterministic truncation
     * flags report omitted detail. The forecast includes configured/effective attempts, work units, heights, and
     * registry issues, but no seeds, generation salts, coordinates, filesystem paths, or administrative tokens.
     *
     * @param id local or namespaced profile ID, or null/blank for the active profile
     * @param page requested zero-based page index
     * @param pageSize requested rules per page
     * @return immutable bounded forecast derived from a coherent profile/settings/registry view
     * @throws IllegalStateException if the service is not started
     * @throws IllegalArgumentException if a supplied profile ID is invalid
     * @throws IOException if a non-active profile cannot be loaded and validated
     */
    public OreProfileForecast forecast(@Nullable String id, int page, int pageSize) throws IOException {
        synchronized (mutationLock) {
            ensureStarted();
            String selected = id == null || id.isBlank() ? snapshot().settings().activeProfileId() : id.trim();
            OreProfileDocument profile = loadProfile(selected);
            return MinecraftOreProfileForecastBuilder.build(
                    selected,
                    profile,
                    snapshot().settings().initialized() ? snapshot().settings().terrainMode() : null,
                    requiredServer().registryAccess(),
                    page,
                    pageSize);
        }
    }

    /**
     * Saves the active rule snapshot under a local profile ID without activating the saved copy.
     *
     * @param id destination simple lowercase local profile ID
     * @param overwrite whether an existing local profile may be revision-incremented and replaced
     * @return committed local profile or localized collision/validation rejection
     * @throws IllegalStateException if the service is not started or is schema-incompatible read-only
     * @throws IllegalArgumentException if the destination ID is invalid
     * @throws IOException if an existing entry is unsafe/malformed or persistence fails
     */
    public OreProfileCatalog.ProfileWriteResult saveCurrentProfileAs(String id, boolean overwrite) throws IOException {
        synchronized (mutationLock) {
            ensureStarted();
            ensureWritable();
            OreProfileCatalog.ProfileWriteResult result =
                    requiredProfileCatalog().saveAs(id, snapshot().ores(), overwrite);
            auditProfileWrite(result);
            return result;
        }
    }

    /**
     * Saves a bundled preset under a local profile ID without activating it.
     *
     * @param id destination simple lowercase local profile ID
     * @param preset bundled profile to copy
     * @param overwrite whether an existing local profile may be revision-incremented and replaced
     * @return committed inactive local profile or localized collision/validation rejection
     * @throws IllegalStateException if the service is not started or is schema-incompatible read-only
     * @throws IllegalArgumentException if the destination ID is invalid
     * @throws NullPointerException if {@code preset} is {@code null}
     * @throws IOException if an existing entry is unsafe/malformed or persistence fails
     */
    public OreProfileCatalog.ProfileWriteResult createProfileFromPreset(String id, OrePreset preset, boolean overwrite)
            throws IOException {
        synchronized (mutationLock) {
            ensureStarted();
            ensureWritable();
            OreProfileCatalog.ProfileWriteResult result =
                    requiredProfileCatalog().saveAs(id, OrePresets.create(preset), overwrite);
            auditProfileWrite(result);
            return result;
        }
    }

    /**
     * Persists a server-authored profile without overwriting or activating anything.
     *
     * <p>The destination must not collide with a bundled, ecosystem, or local profile, and a concurrent filesystem
     * creator wins atomically. The new local profile is normalized to schema 2 and revision zero.
     *
     * @param id destination simple lowercase local profile ID
     * @param source immutable server-authored source whose rules will be copied
     * @return created inactive local profile or localized collision/validation rejection
     * @throws IllegalStateException if the service is not started or is schema-incompatible read-only
     * @throws IllegalArgumentException if the destination ID is invalid
     * @throws NullPointerException if {@code source} is {@code null}
     * @throws IOException if catalog preparation or persistence fails
     */
    public OreProfileCatalog.ProfileWriteResult createNewProfile(String id, OreProfileDocument source)
            throws IOException {
        synchronized (mutationLock) {
            ensureStarted();
            ensureWritable();
            OreProfileCatalog.ProfileWriteResult result =
                    requiredProfileCatalog().createNew(id, source);
            auditProfileWrite(result);
            return result;
        }
    }

    /**
     * Loads a valid profile and saves its rules under a different local ID without activation.
     *
     * @param sourceId local, ecosystem, or bundled source profile ID
     * @param targetId destination simple lowercase local profile ID
     * @param overwrite whether an existing local destination may be revision-incremented and replaced
     * @return committed inactive duplicate or localized collision/validation rejection
     * @throws IllegalStateException if the service is not started or is schema-incompatible read-only
     * @throws IllegalArgumentException if either ID is invalid
     * @throws IOException if source loading, destination inspection, or persistence fails
     */
    public OreProfileCatalog.ProfileWriteResult duplicateProfile(String sourceId, String targetId, boolean overwrite)
            throws IOException {
        synchronized (mutationLock) {
            ensureStarted();
            ensureWritable();
            OreProfileCatalog.ProfileWriteResult result = requiredProfileCatalog()
                    .saveAs(targetId, requiredProfileCatalog().load(sourceId), overwrite);
            auditProfileWrite(result);
            return result;
        }
    }

    /**
     * Atomically activates a catalog profile for newly generated chunks.
     *
     * <p>The observed ore revision must match. Success registry-validates the selected profile, copies its rules into
     * the canonical ore document at the next revision, updates settings active-profile identity at the next settings
     * revision, transactionally saves both files, publishes and audits the snapshot, and posts the public activation
     * event. Existing chunks are unchanged. Load, validation, stale-revision, and persistence failures are returned as
     * unsaved results.
     *
     * @param expectedOreRevision ore revision observed by the caller
     * @param id local, ecosystem, or bundled profile ID to activate
     * @return committed snapshot or a bounded rejection retaining the current snapshot
     */
    public ConfigWriteResult activateProfile(long expectedOreRevision, String id) {
        synchronized (mutationLock) {
            try {
                ensureStarted();
                ensureWritable();
                ConfigSnapshot before = snapshot();
                if (before.ores().revision() != expectedOreRevision) {
                    return stale(
                            before, "ores", expectedOreRevision, before.ores().revision());
                }
                OreProfileDocument selected = requiredProfileCatalog().load(id);
                OreProfileDocument active = new OreProfileDocument(
                        OreProfileDocument.CURRENT_SCHEMA_VERSION,
                        before.ores().revision() + 1,
                        selected.profile(),
                        selected.rules());
                WorldSettingsDocument settings = new WorldSettingsDocument(
                        WorldSettingsDocument.CURRENT_SCHEMA_VERSION,
                        before.settings().revision() + 1,
                        before.settings().generationEpoch(),
                        before.settings().generationSalt(),
                        before.settings().lastWorldOperationId(),
                        before.settings().initialized(),
                        before.settings().terrainMode(),
                        before.settings().orePreset(),
                        before.settings().gameplay(),
                        before.settings().portal(),
                        selected.profile(),
                        before.settings().identity(),
                        before.settings().guideVisibility(),
                        before.settings().backupRetention());
                ConfigSnapshot saved = requiredRepository().save(active, settings);
                current.set(saved);
                auditSavedConfiguration(before, saved);
                NeoForge.EVENT_BUS.post(
                        new DelvefoldOreProfileActivatedEvent(before.settings().activeProfileId(), selected.profile()));
                return new ConfigWriteResult(true, saved, List.of());
            } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
                LOGGER.error("Could not activate Delvefold ore profile {}", id, exception);
                return rejected(current.get(), "profile.activate_failed", exception.getMessage());
            }
        }
    }

    /**
     * Deletes a non-active local profile and returns a localized outcome.
     *
     * <p>Bundled and ecosystem sources are never deleted. A local override of a bundled ID may be deleted, revealing
     * the bundled fallback. The active canonical profile is protected. Invalid, unsafe, missing, and I/O outcomes are
     * contained in the result; service lifecycle/read-only failures remain exceptional.
     *
     * @param id simple lowercase local profile ID; null/blank is treated as an invalid/missing ID
     * @return deletion status and encoded localized message
     * @throws IllegalStateException if the service is not started or is schema-incompatible read-only
     */
    public ProfileDeleteResult deleteProfile(@Nullable String id) {
        synchronized (mutationLock) {
            try {
                ensureStarted();
                ensureWritable();
                String normalizedId = id == null ? "" : id.trim();
                if (snapshot().ores().profile().equals(normalizedId)) {
                    return new ProfileDeleteResult(false, localized("message.delvefold.profile.delete_active"));
                }
                OreProfileCatalog.ProfileDeleteResult deletion =
                        requiredProfileCatalog().deleteLocalWithRevision(normalizedId);
                if (deletion.deleted()) {
                    auditProfileDelete(normalizedId, deletion);
                }
                return new ProfileDeleteResult(
                        deletion.deleted(),
                        deletion.deleted()
                                ? localized("message.delvefold.profile.deleted", normalizedId)
                                : localized("message.delvefold.profile.delete_missing", normalizedId));
            } catch (IOException | IllegalArgumentException exception) {
                return new ProfileDeleteResult(
                        false, localized("message.delvefold.profile.delete_failed", exception.getMessage()));
            }
        }
    }

    /**
     * Imports a bounded save-local transfer file into an inactive local profile.
     *
     * @param fileName simple {@code .json} filename inside the configured import directory
     * @param id destination simple lowercase local profile ID
     * @param overwrite whether an existing local destination may be replaced
     * @return committed inactive profile or localized size/parse/validation rejection
     * @throws IllegalStateException if the service is not started or is schema-incompatible read-only
     * @throws IOException if the source path/file is unsafe or persistence fails
     */
    public OreProfileCatalog.ProfileWriteResult importProfile(String fileName, String id, boolean overwrite)
            throws IOException {
        synchronized (mutationLock) {
            ensureStarted();
            ensureWritable();
            OreProfileCatalog.ProfileWriteResult result =
                    requiredProfileCatalog().importFile(fileName, id, overwrite);
            auditProfileWrite(result);
            return result;
        }
    }

    /**
     * Imports bounded strict JSON into an inactive local profile.
     *
     * @param id destination simple lowercase local profile ID
     * @param json complete schema-2 profile JSON, limited to 256 KiB when UTF-8 encoded
     * @param overwrite whether an existing local destination may be replaced
     * @return committed inactive profile or localized size/parse/validation rejection
     * @throws IllegalStateException if the service is not started or is schema-incompatible read-only
     * @throws IOException if destination inspection or persistence fails
     */
    public OreProfileCatalog.ProfileWriteResult importProfileJson(String id, String json, boolean overwrite)
            throws IOException {
        synchronized (mutationLock) {
            ensureStarted();
            ensureWritable();
            OreProfileCatalog.ProfileWriteResult result =
                    requiredProfileCatalog().importJson(id, json, overwrite);
            auditProfileWrite(result);
            return result;
        }
    }

    /**
     * Exports one currently valid profile as canonical pretty-printed JSON without writing disk.
     *
     * @param id local, ecosystem, or bundled profile ID
     * @return schema-2 JSON without a trailing line separator
     * @throws IllegalStateException if the service is not started
     * @throws IllegalArgumentException if the profile ID is invalid
     * @throws IOException if the profile cannot be loaded and registry-validated
     */
    public String exportProfileJson(String id) throws IOException {
        synchronized (mutationLock) {
            ensureStarted();
            return requiredProfileCatalog().exportJson(id);
        }
    }

    /**
     * Writes one currently valid profile to a bounded save-local export file.
     *
     * @param id local, ecosystem, or bundled profile ID
     * @param fileName simple {@code .json} filename inside the configured export directory
     * @return normalized path to the atomically written export
     * @throws IllegalStateException if the service is not started or is schema-incompatible read-only
     * @throws IllegalArgumentException if the profile ID is invalid
     * @throws IOException if profile loading, path safety, size enforcement, or persistence fails
     */
    public java.nio.file.Path exportProfile(String id, String fileName) throws IOException {
        synchronized (mutationLock) {
            ensureStarted();
            ensureWritable();
            return requiredProfileCatalog().exportFile(id, fileName);
        }
    }

    /**
     * Immutable service-level profile deletion response.
     *
     * @param deleted whether a local profile file was deleted
     * @param message encoded localized user-facing outcome
     */
    public record ProfileDeleteResult(boolean deleted, String message) {
        /**
         * Creates a deletion response and normalizes a missing message to a generic localized failure.
         *
         * @param deleted whether a local profile file was deleted
         * @param message encoded localized outcome
         */
        public ProfileDeleteResult {
            message = message == null || message.isBlank()
                    ? localized("message.delvefold.profile.delete_failed", "unknown")
                    : message;
        }
    }

    /**
     * Performs a lock-free check for unsupported on-disk configuration schema.
     *
     * @return {@code true} when mutations and portal entry must remain disabled to preserve incompatible save data
     */
    public boolean isReadOnlyIncompatible() {
        return readOnlyIncompatible;
    }

    /**
     * Returns the current encoded localized compatibility explanation.
     *
     * @return nonempty message in read-only compatibility mode, otherwise an empty string
     */
    public String compatibilityMessage() {
        return compatibilityMessage;
    }

    private static String localized(String translationKey, @Nullable Object... arguments) {
        return AdminLocalizedMessage.encode(translationKey, arguments);
    }

    private void ensureStarted() {
        if (repository == null || profileCatalog == null || server == null) {
            throw new IllegalStateException("Delvefold configuration is not loaded");
        }
    }

    private MinecraftServer requiredServer() {
        return Objects.requireNonNull(server, "Delvefold configuration server");
    }

    private FileConfigRepository requiredRepository() {
        return Objects.requireNonNull(repository, "Delvefold configuration repository");
    }

    private OreProfileCatalog requiredProfileCatalog() {
        return Objects.requireNonNull(profileCatalog, "Delvefold ore-profile catalog");
    }

    private static void auditSavedConfiguration(ConfigSnapshot before, ConfigSnapshot saved) {
        try {
            String actor = DelvefoldAuditService.get().currentActorOrServer();
            for (AuditMutation mutation : ConfigAuditPlanner.plan(before, saved, actor)) {
                DelvefoldAuditService.get().record(mutation);
            }
        } catch (IllegalArgumentException exception) {
            LOGGER.warn("Delvefold could not describe an accepted configuration mutation for auditing", exception);
        }
    }

    private static void auditProfileWrite(OreProfileCatalog.ProfileWriteResult result) {
        try {
            String actor = DelvefoldAuditService.get().currentActorOrServer();
            for (AuditMutation mutation : ConfigAuditPlanner.profileWrite(result, actor)) {
                DelvefoldAuditService.get().record(mutation);
            }
        } catch (IllegalArgumentException exception) {
            // Auditing is failure-contained and cannot roll back an already accepted profile mutation.
        }
    }

    private static void auditProfileDelete(String id, OreProfileCatalog.ProfileDeleteResult result) {
        try {
            String actor = DelvefoldAuditService.get().currentActorOrServer();
            for (AuditMutation mutation : ConfigAuditPlanner.profileDelete(id, result, actor)) {
                DelvefoldAuditService.get().record(mutation);
            }
        } catch (IllegalArgumentException exception) {
            // Auditing is failure-contained and cannot roll back an already accepted profile mutation.
        }
    }

    private void ensureWritable() {
        if (readOnlyIncompatible) {
            throw new IllegalStateException(compatibilityMessage);
        }
    }

    private void updateCompatibility(ConfigLoadResult result) {
        var incompatible = result.issues().stream()
                .filter(issue ->
                        "schema.unsupported".equals(issue.code()) || "settings.schema.unsupported".equals(issue.code()))
                .findFirst();
        readOnlyIncompatible = incompatible.isPresent();
        compatibilityMessage = incompatible
                .map(issue -> localized("message.delvefold.config.compatibility_incompatible"))
                .orElse("");
    }

    private static ConfigWriteResult stale(ConfigSnapshot snapshot, String document, long expected, long actual) {
        return new ConfigWriteResult(
                false,
                snapshot,
                List.of(ConfigIssue.error(
                        "revision.stale",
                        "$",
                        "The " + document + " configuration changed while it was open (expected " + expected
                                + ", current " + actual + ")")));
    }

    private static ConfigWriteResult rejected(
            @Nullable ConfigSnapshot snapshot, String code, @Nullable String message) {
        String safeMessage = message == null || message.isBlank() ? code : message;
        return new ConfigWriteResult(false, snapshot, List.of(ConfigIssue.error(code, "$", safeMessage)));
    }

    private static void logIssues(String operation, List<ConfigIssue> issues) {
        for (ConfigIssue issue : issues) {
            switch (issue.severity()) {
                case ERROR ->
                    LOGGER.error(
                            "Delvefold config {} [{} at {}]: {}",
                            operation,
                            issue.code(),
                            issue.path(),
                            issue.message());
                case WARNING ->
                    LOGGER.warn(
                            "Delvefold config {} [{} at {}]: {}",
                            operation,
                            issue.code(),
                            issue.path(),
                            issue.message());
            }
        }
    }
}
