package com.nightsta69.delvefold.config;

import com.nightsta69.delvefold.admin.AdminLocalizedMessage;
import com.mojang.logging.LogUtils;
import com.nightsta69.delvefold.audit.AuditMutation;
import com.nightsta69.delvefold.audit.DelvefoldAuditService;
import com.nightsta69.delvefold.config.model.GameplayPreset;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.model.OreRule;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.WorldSettingsDocument;
import com.nightsta69.delvefold.config.model.WorldIdentitySettings;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast;
import com.nightsta69.delvefold.world.feature.MinecraftOreProfileForecastBuilder;
import com.nightsta69.delvefold.config.validation.ConfigIssue;
import com.nightsta69.delvefold.config.validation.OreConfigValidator;
import com.nightsta69.delvefold.config.validation.ValidationReport;
import com.nightsta69.delvefold.api.DelvefoldApi;
import com.nightsta69.delvefold.api.event.DelvefoldOreProfileActivatedEvent;
import com.nightsta69.delvefold.api.event.DelvefoldWorldLifecycleEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Owns the immutable runtime snapshot. Chunk-generation threads only read the
 * atomic snapshot; validation and disk writes happen before a replacement is
 * published.
 */
public final class DelvefoldConfigService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DelvefoldConfigService INSTANCE = new DelvefoldConfigService();

    private final AtomicReference<ConfigSnapshot> current = new AtomicReference<>();
    private final Object mutationLock = new Object();
    private volatile MinecraftServer server;
    private volatile FileConfigRepository repository;
    private volatile OreProfileCatalog profileCatalog;
    private volatile boolean readOnlyIncompatible;
    private volatile String compatibilityMessage = "";

    private DelvefoldConfigService() {
    }

    public static DelvefoldConfigService get() {
        return INSTANCE;
    }

    public ConfigLoadResult start(MinecraftServer minecraftServer) throws IOException {
        synchronized (mutationLock) {
            server = Objects.requireNonNull(minecraftServer, "minecraftServer");
            ConfigPaths paths = ConfigPaths.forServer(minecraftServer);
            MinecraftRegistryLookup registryLookup = new MinecraftRegistryLookup();
            repository = new FileConfigRepository(paths, registryLookup);
            profileCatalog = new OreProfileCatalog(paths, registryLookup);
            ConfigLoadResult result = repository.loadOrCreate(current.get());
            updateCompatibility(result);
            current.set(result.snapshot());
            logIssues("load", result.issues());
            return result;
        }
    }

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

    public ConfigSnapshot snapshot() {
        ConfigSnapshot snapshot = current.get();
        if (snapshot == null) {
            throw new IllegalStateException("Delvefold configuration is not loaded");
        }
        return snapshot;
    }

    public ConfigLoadResult reload() throws IOException {
        synchronized (mutationLock) {
            ensureStarted();
            ConfigSnapshot before = current.get();
            ConfigLoadResult result = enforceLiveLifecycleLocks(
                    before, repository.loadOrCreate(before));
            updateCompatibility(result);
            if (!result.usedFallback()) {
                current.set(result.snapshot());
                auditSavedConfiguration(before, result.snapshot());
            }
            logIssues("reload", result.issues());
            return result;
        }
    }

    /** Validates the files currently on disk without publishing them to worldgen threads. */
    public ConfigLoadResult validateDisk() throws IOException {
        synchronized (mutationLock) {
            ensureStarted();
            ConfigSnapshot before = current.get();
            return enforceLiveLifecycleLocks(before, repository.validateDisk(before));
        }
    }

    private static ConfigLoadResult enforceLiveLifecycleLocks(
            ConfigSnapshot before, ConfigLoadResult result) {
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
        issues.add(ConfigIssue.warning("fallback.last_good", "$",
                "Lifecycle-owned settings changed on disk; continuing with the active snapshot"));
        return new ConfigLoadResult(before, true, issues);
    }

    public ConfigWriteResult initialize(
            long expectedOreRevision,
            long expectedSettingsRevision,
            TerrainMode terrain,
            OrePreset orePreset,
            GameplayPreset gameplayPreset
    ) {
        return initialize(expectedOreRevision, expectedSettingsRevision, terrain, orePreset, gameplayPreset, null);
    }

    public ConfigWriteResult initialize(
            long expectedOreRevision,
            long expectedSettingsRevision,
            TerrainMode terrain,
            OrePreset orePreset,
            GameplayPreset gameplayPreset,
            WorldIdentitySettings identity
    ) {
        synchronized (mutationLock) {
            try {
                ensureStarted();
                ensureWritable();
                ConfigSnapshot before = snapshot();
                if (before.settings().revision() != expectedSettingsRevision) {
                    return stale(before, "settings", expectedSettingsRevision, before.settings().revision());
                }
                if (before.ores().revision() != expectedOreRevision) {
                    return stale(before, "ores", expectedOreRevision, before.ores().revision());
                }
                if (before.settings().initialized()) {
                    return rejected(before, "world.already_initialized", "Delvefold has already been initialized for this save");
                }

                OreProfileDocument preset = OrePresets.create(orePreset);
                OreProfileDocument ores = new OreProfileDocument(
                        OreProfileDocument.CURRENT_SCHEMA_VERSION,
                        before.ores().revision() + 1,
                        preset.profile(),
                        preset.rules()
                );
                WorldSettingsDocument settings = before.settings().initialize(
                        terrain, orePreset, gameplayPreset, identity);
                ConfigSnapshot saved = repository.save(ores, settings);
                current.set(saved);
                auditSavedConfiguration(before, saved);
                NeoForge.EVENT_BUS.post(new DelvefoldWorldLifecycleEvent(
                        DelvefoldWorldLifecycleEvent.Action.INITIALIZED,
                        DelvefoldApi.worldView(before.settings()), DelvefoldApi.worldView(saved.settings()), ""));
                return new ConfigWriteResult(true, saved, List.of());
            } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
                LOGGER.error("Could not initialize Delvefold", exception);
                return rejected(current.get(), "initialize.failed", exception.getMessage());
            }
        }
    }

    public ConfigWriteResult updateOres(long expectedRevision, UnaryOperator<OreProfileDocument> update) {
        synchronized (mutationLock) {
            try {
                ensureStarted();
                ensureWritable();
                ConfigSnapshot before = snapshot();
                if (before.ores().revision() != expectedRevision) {
                    return stale(before, "ores", expectedRevision, before.ores().revision());
                }
                OreProfileDocument candidate = Objects.requireNonNull(update.apply(before.ores()), "updated ore document");
                candidate = new OreProfileDocument(
                        OreProfileDocument.CURRENT_SCHEMA_VERSION,
                        before.ores().revision() + 1,
                        candidate.profile(),
                        candidate.rules()
                );
                ValidationReport report = OreConfigValidator.validate(candidate, new MinecraftRegistryLookup());
                if (!report.valid()) {
                    return new ConfigWriteResult(false, before, report.issues());
                }
                ConfigSnapshot saved = repository.save(candidate, before.settings());
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
     * <p>When {@code createOnly} is true, an existing rule with the same ID is
     * rejected instead of being overwritten. The collision check and disk
     * write share the ore mutation lock and revision check.</p>
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

                OreProfileDocument candidate = before.ores().nextRevision(plan.rules(), before.ores().profile());
                candidate = new OreProfileDocument(
                        OreProfileDocument.CURRENT_SCHEMA_VERSION,
                        before.ores().revision() + 1,
                        candidate.profile(),
                        candidate.rules()
                );
                ValidationReport report = OreConfigValidator.validate(candidate, new MinecraftRegistryLookup());
                if (!report.valid()) {
                    return new ConfigWriteResult(false, before, report.issues());
                }
                ConfigSnapshot saved = repository.save(candidate, before.settings());
                current.set(saved);
                auditSavedConfiguration(before, saved);
                return new ConfigWriteResult(true, saved, report.issues());
            } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
                LOGGER.error("Could not save Delvefold ore rule", exception);
                return rejected(current.get(), "ores.save_failed", exception.getMessage());
            }
        }
    }

    public ConfigWriteResult updateSettings(long expectedRevision, UnaryOperator<WorldSettingsDocument> update) {
        synchronized (mutationLock) {
            try {
                ensureStarted();
                ensureWritable();
                ConfigSnapshot before = snapshot();
                if (before.settings().revision() != expectedRevision) {
                    return stale(before, "settings", expectedRevision, before.settings().revision());
                }
                WorldSettingsDocument candidate = Objects.requireNonNull(update.apply(before.settings()), "updated settings document");
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
                        candidate.backupRetention()
                );
                ConfigSnapshot saved = repository.save(before.ores(), candidate);
                current.set(saved);
                auditSavedConfiguration(before, saved);
                return new ConfigWriteResult(true, saved, List.of());
            } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
                LOGGER.error("Could not save Delvefold settings", exception);
                return rejected(current.get(), "settings.save_failed", exception.getMessage());
            }
        }
    }

    public MinecraftServer server() {
        return server;
    }

    public List<OreProfileCatalog.ProfileSummary> listProfiles() throws IOException {
        synchronized (mutationLock) {
            ensureStarted();
            return profileCatalog.list();
        }
    }

    /** Loads a named profile without activating or rewriting it. */
    public OreProfileDocument loadProfile(String id) throws IOException {
        synchronized (mutationLock) {
            ensureStarted();
            String selected = id == null || id.isBlank() ? snapshot().ores().profile() : id.trim();
            if (selected.equals(snapshot().ores().profile())) {
                return snapshot().ores();
            }
            return profileCatalog.load(selected);
        }
    }

    /** Builds one bounded, read-only forecast page from the current server registry state. */
    public OreProfileForecast forecast(String id, int page, int pageSize) throws IOException {
        synchronized (mutationLock) {
            ensureStarted();
            String selected = id == null || id.isBlank()
                    ? snapshot().settings().activeProfileId() : id.trim();
            OreProfileDocument profile = loadProfile(selected);
            return MinecraftOreProfileForecastBuilder.build(
                    selected, profile, snapshot().settings().initialized()
                            ? snapshot().settings().terrainMode() : null,
                    server.registryAccess(), page, pageSize);
        }
    }

    public OreProfileCatalog.ProfileWriteResult saveCurrentProfileAs(String id, boolean overwrite) throws IOException {
        synchronized (mutationLock) {
            ensureStarted();
            ensureWritable();
            OreProfileCatalog.ProfileWriteResult result = profileCatalog.saveAs(id, snapshot().ores(), overwrite);
            auditProfileWrite(result);
            return result;
        }
    }

    public OreProfileCatalog.ProfileWriteResult createProfileFromPreset(
            String id, OrePreset preset, boolean overwrite) throws IOException {
        synchronized (mutationLock) {
            ensureStarted();
            ensureWritable();
            OreProfileCatalog.ProfileWriteResult result = profileCatalog.saveAs(id, OrePresets.create(preset), overwrite);
            auditProfileWrite(result);
            return result;
        }
    }

    /** Persists a server-authored profile without overwriting or activating anything. */
    public OreProfileCatalog.ProfileWriteResult createNewProfile(
            String id, OreProfileDocument source) throws IOException {
        synchronized (mutationLock) {
            ensureStarted();
            ensureWritable();
            OreProfileCatalog.ProfileWriteResult result = profileCatalog.createNew(id, source);
            auditProfileWrite(result);
            return result;
        }
    }

    public OreProfileCatalog.ProfileWriteResult duplicateProfile(
            String sourceId, String targetId, boolean overwrite) throws IOException {
        synchronized (mutationLock) {
            ensureStarted();
            ensureWritable();
            OreProfileCatalog.ProfileWriteResult result =
                    profileCatalog.saveAs(targetId, profileCatalog.load(sourceId), overwrite);
            auditProfileWrite(result);
            return result;
        }
    }

    public ConfigWriteResult activateProfile(long expectedOreRevision, String id) {
        synchronized (mutationLock) {
            try {
                ensureStarted();
                ensureWritable();
                ConfigSnapshot before = snapshot();
                if (before.ores().revision() != expectedOreRevision) {
                    return stale(before, "ores", expectedOreRevision, before.ores().revision());
                }
                OreProfileDocument selected = profileCatalog.load(id);
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
                ConfigSnapshot saved = repository.save(active, settings);
                current.set(saved);
                auditSavedConfiguration(before, saved);
                NeoForge.EVENT_BUS.post(new DelvefoldOreProfileActivatedEvent(
                        before.settings().activeProfileId(), selected.profile()));
                return new ConfigWriteResult(true, saved, List.of());
            } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
                LOGGER.error("Could not activate Delvefold ore profile {}", id, exception);
                return rejected(current.get(), "profile.activate_failed", exception.getMessage());
            }
        }
    }

    public ProfileDeleteResult deleteProfile(String id) {
        synchronized (mutationLock) {
            try {
                ensureStarted();
                ensureWritable();
                String normalizedId = id == null ? "" : id.trim();
                if (snapshot().ores().profile().equals(normalizedId)) {
                    return new ProfileDeleteResult(false, localized(
                            "message.delvefold.profile.delete_active"));
                }
                OreProfileCatalog.ProfileDeleteResult deletion = profileCatalog.deleteLocalWithRevision(normalizedId);
                if (deletion.deleted()) {
                    auditProfileDelete(normalizedId, deletion);
                }
                return new ProfileDeleteResult(deletion.deleted(),
                        deletion.deleted()
                                ? localized("message.delvefold.profile.deleted", normalizedId)
                                : localized("message.delvefold.profile.delete_missing", normalizedId));
            } catch (IOException | IllegalArgumentException exception) {
                return new ProfileDeleteResult(false, localized(
                        "message.delvefold.profile.delete_failed", exception.getMessage()));
            }
        }
    }

    public OreProfileCatalog.ProfileWriteResult importProfile(
            String fileName, String id, boolean overwrite) throws IOException {
        synchronized (mutationLock) {
            ensureStarted();
            ensureWritable();
            OreProfileCatalog.ProfileWriteResult result = profileCatalog.importFile(fileName, id, overwrite);
            auditProfileWrite(result);
            return result;
        }
    }

    public OreProfileCatalog.ProfileWriteResult importProfileJson(
            String id, String json, boolean overwrite) throws IOException {
        synchronized (mutationLock) {
            ensureStarted();
            ensureWritable();
            OreProfileCatalog.ProfileWriteResult result = profileCatalog.importJson(id, json, overwrite);
            auditProfileWrite(result);
            return result;
        }
    }

    public String exportProfileJson(String id) throws IOException {
        synchronized (mutationLock) {
            ensureStarted();
            return profileCatalog.exportJson(id);
        }
    }

    public java.nio.file.Path exportProfile(String id, String fileName) throws IOException {
        synchronized (mutationLock) {
            ensureStarted();
            ensureWritable();
            return profileCatalog.exportFile(id, fileName);
        }
    }

    public record ProfileDeleteResult(boolean deleted, String message) {
        public ProfileDeleteResult {
            message = message == null || message.isBlank()
                    ? localized("message.delvefold.profile.delete_failed", "unknown")
                    : message;
        }
    }

    public boolean isReadOnlyIncompatible() {
        return readOnlyIncompatible;
    }

    public String compatibilityMessage() {
        return compatibilityMessage;
    }

    private static String localized(String translationKey, Object... arguments) {
        return AdminLocalizedMessage.encode(translationKey, arguments);
    }

    private void ensureStarted() {
        if (repository == null || profileCatalog == null || server == null) {
            throw new IllegalStateException("Delvefold configuration is not loaded");
        }
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
                .filter(issue -> "schema.unsupported".equals(issue.code())
                        || "settings.schema.unsupported".equals(issue.code()))
                .findFirst();
        readOnlyIncompatible = incompatible.isPresent();
        compatibilityMessage = incompatible
                .map(issue -> localized("message.delvefold.config.compatibility_incompatible"))
                .orElse("");
    }

    private static ConfigWriteResult stale(ConfigSnapshot snapshot, String document, long expected, long actual) {
        return new ConfigWriteResult(false, snapshot, List.of(ConfigIssue.error(
                "revision.stale",
                "$",
                "The " + document + " configuration changed while it was open (expected " + expected + ", current " + actual + ")"
        )));
    }

    private static ConfigWriteResult rejected(ConfigSnapshot snapshot, String code, String message) {
        String safeMessage = message == null || message.isBlank() ? code : message;
        return new ConfigWriteResult(false, snapshot, List.of(ConfigIssue.error(code, "$", safeMessage)));
    }

    private static void logIssues(String operation, List<ConfigIssue> issues) {
        for (ConfigIssue issue : issues) {
            switch (issue.severity()) {
                case ERROR -> LOGGER.error("Delvefold config {} [{} at {}]: {}", operation, issue.code(), issue.path(), issue.message());
                case WARNING -> LOGGER.warn("Delvefold config {} [{} at {}]: {}", operation, issue.code(), issue.path(), issue.message());
            }
        }
    }
}

/** Pure mutation planner kept separate from the Minecraft lifecycle facade for deterministic tests. */
final class ConfigAuditPlanner {
    private ConfigAuditPlanner() {
    }

    static List<AuditMutation> plan(ConfigSnapshot before, ConfigSnapshot saved, String actor) {
        if (before == null || saved == null) {
            return List.of();
        }
        List<AuditMutation> mutations = new ArrayList<>();
        if (!before.settings().equals(saved.settings())) {
            mutations.add(new AuditMutation(
                    actor,
                    AuditMutation.Operation.CONFIGURATION_ACCEPTED,
                    AuditMutation.ObjectType.SETTINGS,
                    "world_settings",
                    before.settings().revision(),
                    saved.settings().revision()));
        }
        if (!before.ores().equals(saved.ores())) {
            mutations.add(new AuditMutation(
                    actor,
                    AuditMutation.Operation.CONFIGURATION_ACCEPTED,
                    AuditMutation.ObjectType.PROFILE,
                    saved.ores().profile(),
                    before.ores().revision(),
                    saved.ores().revision()));
        }
        if (!before.settings().activeProfileId().equals(saved.settings().activeProfileId())) {
            mutations.add(new AuditMutation(
                    actor,
                    AuditMutation.Operation.PROFILE_ACTIVATED,
                    AuditMutation.ObjectType.PROFILE,
                    saved.settings().activeProfileId(),
                    before.ores().revision(),
                    saved.ores().revision()));
        }
        if (before.settings().portal().routingMode() != saved.settings().portal().routingMode()) {
            mutations.add(new AuditMutation(
                    actor,
                    AuditMutation.Operation.PORTAL_ROUTING_CHANGED,
                    AuditMutation.ObjectType.PORTAL,
                    "routing",
                    before.settings().revision(),
                    saved.settings().revision()));
        }
        if (!before.settings().portal().hub().equals(saved.settings().portal().hub())) {
            mutations.add(new AuditMutation(
                    actor,
                    AuditMutation.Operation.HUB_PROTECTION_CHANGED,
                    AuditMutation.ObjectType.HUB,
                    "central_hub",
                    before.settings().revision(),
                    saved.settings().revision()));
        }
        return List.copyOf(mutations);
    }

    static List<AuditMutation> profileWrite(
            OreProfileCatalog.ProfileWriteResult result, String actor) {
        if (result == null || !result.saved() || result.profile() == null) {
            return List.of();
        }
        AuditMutation.Operation operation = result.previousRevision() < 0L
                ? AuditMutation.Operation.PROFILE_CREATED
                : AuditMutation.Operation.PROFILE_UPDATED;
        return List.of(new AuditMutation(
                actor,
                operation,
                AuditMutation.ObjectType.PROFILE,
                result.profile().profile(),
                result.previousRevision(),
                result.profile().revision()));
    }

    static List<AuditMutation> profileDelete(
            String id, OreProfileCatalog.ProfileDeleteResult result, String actor) {
        if (result == null || !result.deleted()) {
            return List.of();
        }
        return List.of(new AuditMutation(
                actor,
                AuditMutation.Operation.PROFILE_DELETED,
                AuditMutation.ObjectType.PROFILE,
                id,
                result.previousRevision(),
                -1L));
    }
}
