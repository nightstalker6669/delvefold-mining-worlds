package com.nightsta69.delvefold.config;

import com.mojang.logging.LogUtils;
import com.nightsta69.delvefold.config.model.GameplayPreset;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.model.OreRule;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.WorldSettingsDocument;
import com.nightsta69.delvefold.config.validation.ConfigIssue;
import com.nightsta69.delvefold.config.validation.OreConfigValidator;
import com.nightsta69.delvefold.config.validation.ValidationReport;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import net.minecraft.server.MinecraftServer;
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

    private DelvefoldConfigService() {
    }

    public static DelvefoldConfigService get() {
        return INSTANCE;
    }

    public ConfigLoadResult start(MinecraftServer minecraftServer) throws IOException {
        synchronized (mutationLock) {
            server = Objects.requireNonNull(minecraftServer, "minecraftServer");
            repository = new FileConfigRepository(ConfigPaths.forServer(minecraftServer), new MinecraftRegistryLookup());
            ConfigLoadResult result = repository.loadOrCreate(current.get());
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
                current.set(null);
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
            ConfigLoadResult result = repository.loadOrCreate(current.get());
            if (!result.usedFallback() || current.get() == null) {
                current.set(result.snapshot());
            }
            logIssues("reload", result.issues());
            return result;
        }
    }

    /** Validates the files currently on disk without publishing them to worldgen threads. */
    public ConfigLoadResult validateDisk() throws IOException {
        synchronized (mutationLock) {
            ensureStarted();
            return repository.validateDisk(current.get());
        }
    }

    public ConfigWriteResult initialize(
            long expectedOreRevision,
            long expectedSettingsRevision,
            TerrainMode terrain,
            OrePreset orePreset,
            GameplayPreset gameplayPreset
    ) {
        synchronized (mutationLock) {
            try {
                ensureStarted();
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
                WorldSettingsDocument settings = before.settings().initialize(terrain, orePreset, gameplayPreset);
                ConfigSnapshot saved = repository.save(ores, settings);
                current.set(saved);
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

                OreProfileDocument candidate = before.ores().nextRevision(plan.rules(), "custom");
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
                ConfigSnapshot before = snapshot();
                if (before.settings().revision() != expectedRevision) {
                    return stale(before, "settings", expectedRevision, before.settings().revision());
                }
                WorldSettingsDocument candidate = Objects.requireNonNull(update.apply(before.settings()), "updated settings document");
                candidate = new WorldSettingsDocument(
                        WorldSettingsDocument.CURRENT_SCHEMA_VERSION,
                        before.settings().revision() + 1,
                        candidate.generationEpoch(),
                        candidate.lastWorldOperationId(),
                        candidate.initialized(),
                        candidate.terrainMode(),
                        candidate.orePreset(),
                        candidate.gameplay(),
                        candidate.portal()
                );
                ConfigSnapshot saved = repository.save(before.ores(), candidate);
                current.set(saved);
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

    private void ensureStarted() {
        if (repository == null || server == null) {
            throw new IllegalStateException("Delvefold configuration is not loaded");
        }
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
