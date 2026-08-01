package com.nightsta69.delvefold.diagnostics;

import com.nightsta69.delvefold.Delvefold;
import com.nightsta69.delvefold.admin.AdminLocalizedMessage;
import com.nightsta69.delvefold.api.DelvefoldApi;
import com.nightsta69.delvefold.config.ConfigJson;
import com.nightsta69.delvefold.config.ConfigPaths;
import com.nightsta69.delvefold.config.ConfigSnapshot;
import com.nightsta69.delvefold.config.DelvefoldConfigService;
import com.nightsta69.delvefold.config.MinecraftRegistryLookup;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.TerrainVariant;
import com.nightsta69.delvefold.config.model.WorldSettingsDocument;
import com.nightsta69.delvefold.config.validation.ConfigIssue;
import com.nightsta69.delvefold.config.validation.IssueSeverity;
import com.nightsta69.delvefold.config.validation.OreConfigValidator;
import com.nightsta69.delvefold.config.validation.RegistryLookup;
import com.nightsta69.delvefold.config.validation.ValidationReport;
import com.nightsta69.delvefold.network.DelvefoldNetwork;
import com.nightsta69.delvefold.reset.BackupRetentionPlanner;
import com.nightsta69.delvefold.reset.BackupRetentionRunState;
import com.nightsta69.delvefold.reset.BackupRetentionService;
import com.nightsta69.delvefold.reset.PendingWorldOperation;
import com.nightsta69.delvefold.reset.PendingWorldRestore;
import com.nightsta69.delvefold.reset.WorldBackupCatalog;
import com.nightsta69.delvefold.world.DelvefoldWorldgen;
import com.nightsta69.delvefold.world.feature.OreTargetResolution;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.internal.versions.neoforge.NeoForgeVersion;

/** Builds the bounded, redacted operational snapshot used by {@code /delvefold doctor}. */
public final class DelvefoldDoctorService {
    static final int MAX_PROFILE_FINDINGS = 256;
    static final int MAX_INEFFECTIVE_TARGETS = 256;
    static final int MAX_BACKUP_PROBLEMS = 128;
    static final int MAX_RETENTION_PRUNES = 128;
    static final int MAX_DISK_ESTIMATE_ENTRIES = 100_000;
    private static final long MEBIBYTE = 1024L * 1024L;
    private static final long MAX_PENDING_BYTES = 64L * 1024L;
    private static final long CACHE_TTL_MILLIS = 30_000L;
    private static final int MAX_CACHED_SAVES = 16;
    private static final AtomicInteger WORKER_IDS = new AtomicInteger();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(new DoctorThreadFactory());
    private static final DelvefoldDoctorService INSTANCE = new DelvefoldDoctorService(Clock.systemUTC());
    private final Clock clock;
    private final DoctorReportRenderer renderer = new DoctorReportRenderer();
    private final DoctorReportExporter exporter = new DoctorReportExporter();
    private final Object asyncLock = new Object();
    private final DoctorReportCache cache = new DoctorReportCache(MAX_CACHED_SAVES);
    private final Map<Path, CompletableFuture<DoctorReport>> inFlight = new LinkedHashMap<>();

    DelvefoldDoctorService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public static DelvefoldDoctorService get() {
        return INSTANCE;
    }

    public DoctorReport build(MinecraftServer server) {
        return buildCaptured(capture(Objects.requireNonNull(server, "server")));
    }

    /**
     * Captures Minecraft-owned state on the caller/server thread, then performs all directory walks, backup inspection,
     * retention planning, hashing metadata reads, and disk estimates on one bounded daemon worker. Concurrent requests
     * for the same save share one future.
     */
    public CompletableFuture<DoctorReport> refreshAsync(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        Path key = saveRoot(server);
        synchronized (asyncLock) {
            CompletableFuture<DoctorReport> existing = inFlight.get(key);
            if (existing != null) {
                return existing;
            }
            CapturedState captured = capture(server);
            CompletableFuture<DoctorReport> future =
                    CompletableFuture.supplyAsync(() -> buildCaptured(captured), WORKER);
            inFlight.put(key, future);
            future.whenComplete((report, failure) -> {
                synchronized (asyncLock) {
                    boolean currentSession = inFlight.remove(key, future);
                    if (currentSession && failure == null && report != null) {
                        cache.put(key, report, clock.millis());
                    }
                }
            });
            return future;
        }
    }

    /** Returns a cached report immediately and starts a deduplicated background refresh if stale. */
    public List<String> cachedRenderedLines(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        Path key = saveRoot(server);
        DoctorReportCache.Entry current = cache.get(key);
        long now = clock.millis();
        if (current == null || now - current.cachedAtEpochMillis() > CACHE_TTL_MILLIS) {
            refreshAsync(server);
        }
        return current == null
                ? List.of(AdminLocalizedMessage.encode("message.delvefold.doctor.preparing"))
                : renderer.render(current.report());
    }

    /** Invalidates one save and prevents an older in-flight session scan from repopulating it. */
    public void invalidate(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        invalidate(saveRoot(server));
    }

    /** Clears every cached save on lifecycle shutdown, including canceled in-flight sessions. */
    public void clear() {
        List<CompletableFuture<DoctorReport>> pending;
        synchronized (asyncLock) {
            pending = List.copyOf(inFlight.values());
            inFlight.clear();
            cache.clear();
        }
        pending.forEach(future -> future.cancel(false));
    }

    void invalidate(Path saveRoot) {
        Path key = saveRoot.toAbsolutePath().normalize();
        CompletableFuture<DoctorReport> pending;
        synchronized (asyncLock) {
            pending = inFlight.remove(key);
            cache.invalidate(key);
        }
        if (pending != null) {
            pending.cancel(false);
        }
    }

    public CompletableFuture<List<String>> renderedLinesAsync(MinecraftServer server) {
        return refreshAsync(server).thenApply(renderer::render);
    }

    public CompletableFuture<Path> exportAsync(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        ConfigPaths paths = ConfigPaths.forServer(server);
        return refreshAsync(server)
                .thenApplyAsync(
                        report -> {
                            try {
                                return exporter.export(ensureExportsDirectory(paths), report);
                            } catch (IOException exception) {
                                throw new CompletionException(exception);
                            }
                        },
                        WORKER);
    }

    private CapturedState capture(MinecraftServer server) {
        long generatedAt = Math.max(0L, clock.millis());
        DelvefoldConfigService configService = DelvefoldConfigService.get();
        ConfigSnapshot snapshot = configService.snapshot();
        ConfigPaths paths = ConfigPaths.forServer(server);
        Path saveRoot = saveRoot(server);
        DoctorReportBuilder builder = new DoctorReportBuilder(generatedAt)
                .versions(
                        modVersion(),
                        SharedConstants.getCurrentVersion().getName(),
                        NeoForgeVersion.getVersion(),
                        DelvefoldApi.API_VERSION,
                        protocolVersion(),
                        WorldSettingsDocument.CURRENT_SCHEMA_VERSION);

        addDimensions(server, builder);
        addProfileHealth(snapshot, builder, new MinecraftRegistryLookup(), configService.isReadOnlyIncompatible());
        return new CapturedState(
                generatedAt,
                snapshot,
                paths,
                saveRoot,
                builder,
                BackupRetentionRunState.get().current().orElse(null));
    }

    private DoctorReport buildCaptured(CapturedState captured) {
        DoctorReportBuilder builder = captured.builder();
        PendingScan pending = scanPending(captured.paths().directory());
        pending.statuses()
                .forEach(status -> builder.addPendingOperation(
                        status.operationId(), status.operation(), status.state(), status.createdAtEpochMillis()));

        BackupAnalysis backups = readBackups(captured.saveRoot());
        builder.backups(
                backups.total(),
                backups.verified(),
                backups.invalid(),
                backups.legacy(),
                backups.pinned(),
                backups.knownBytes());
        backups.problems()
                .forEach(
                        problem -> builder.addBackupProblem(problem.backupId(), problem.state(), problem.reasonCode()));

        try {
            BackupRetentionService.Preview retention = BackupRetentionService.preview(
                    captured.saveRoot(),
                    captured.snapshot().settings().backupRetention(),
                    Instant.ofEpochMilli(captured.generatedAtEpochMillis()));
            builder.retention(retentionPreview(retention.plan(), captured.retentionRun()));
        } catch (IOException | RuntimeException exception) {
            boolean enabled = captured.snapshot().settings().backupRetention().enabled();
            builder.retention(new DoctorReport.RetentionPreview(
                    enabled,
                    backups.total(),
                    backups.total(),
                    backups.knownBytes(),
                    backups.knownBytes(),
                    !enabled,
                    List.of(),
                    List.of("retention_preview_unavailable"),
                    retentionRun(captured.retentionRun())));
        }
        builder.disk(diskEstimate(captured.saveRoot(), captured.paths(), backups.diskBytes()));
        return builder.build();
    }

    public List<String> renderedLines(MinecraftServer server) {
        return renderer.render(build(server));
    }

    public List<String> renderedLines(DoctorReport report) {
        return renderer.render(report);
    }

    /** Creates the contained exports directory, then writes one redacted doctor report. */
    public Path export(MinecraftServer server) throws IOException {
        ConfigPaths paths = ConfigPaths.forServer(Objects.requireNonNull(server, "server"));
        Path exports = ensureExportsDirectory(paths);
        return exporter.export(exports, build(server));
    }

    private static Path saveRoot(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
    }

    private static void addDimensions(MinecraftServer server, DoctorReportBuilder builder) {
        Registry<LevelStem> stems = server.registryAccess().registryOrThrow(Registries.LEVEL_STEM);
        for (DimensionDefinition definition : dimensionDefinitions()) {
            boolean registered = stems.containsKey(definition.stem());
            boolean loaded = server.getLevel(definition.level()) != null;
            DoctorReport.DimensionState state = loaded
                    ? DoctorReport.DimensionState.ACTIVE
                    : registered ? DoctorReport.DimensionState.UNLOADED : DoctorReport.DimensionState.MISSING;
            builder.addDimension(
                    definition.level().location().toString(),
                    definition.terrain().serializedName() + "/"
                            + definition.variant().serializedName(),
                    state);
        }
    }

    /** Kept lazy so path/backup mapping tests do not bootstrap Minecraft registries. */
    private static List<DimensionDefinition> dimensionDefinitions() {
        return List.of(
                new DimensionDefinition(
                        DelvefoldWorldgen.FLAT_LEVEL,
                        DelvefoldWorldgen.FLAT_LEVEL_STEM,
                        TerrainMode.FLAT,
                        TerrainVariant.CLASSIC),
                new DimensionDefinition(
                        DelvefoldWorldgen.CAVERN_LEVEL,
                        DelvefoldWorldgen.CAVERN_LEVEL_STEM,
                        TerrainMode.CAVERN,
                        TerrainVariant.CLASSIC),
                new DimensionDefinition(
                        DelvefoldWorldgen.WILD_LEVEL,
                        DelvefoldWorldgen.WILD_LEVEL_STEM,
                        TerrainMode.WILD,
                        TerrainVariant.CLASSIC),
                new DimensionDefinition(
                        DelvefoldWorldgen.FLAT_EXPANSIVE_LEVEL,
                        DelvefoldWorldgen.FLAT_EXPANSIVE_LEVEL_STEM,
                        TerrainMode.FLAT,
                        TerrainVariant.EXPANSIVE),
                new DimensionDefinition(
                        DelvefoldWorldgen.CAVERN_EXPANSIVE_LEVEL,
                        DelvefoldWorldgen.CAVERN_EXPANSIVE_LEVEL_STEM,
                        TerrainMode.CAVERN,
                        TerrainVariant.EXPANSIVE),
                new DimensionDefinition(
                        DelvefoldWorldgen.WILD_EXPANSIVE_LEVEL,
                        DelvefoldWorldgen.WILD_EXPANSIVE_LEVEL_STEM,
                        TerrainMode.WILD,
                        TerrainVariant.EXPANSIVE));
    }

    private static void addProfileHealth(
            ConfigSnapshot snapshot,
            DoctorReportBuilder builder,
            RegistryLookup registries,
            boolean configSchemaIncompatible) {
        OreProfileDocument profile = snapshot.ores();
        ValidationReport validation = OreConfigValidator.validate(profile, registries);
        ProfileAnalysis runtime = analyzeTargets(profile);
        List<DoctorReport.Finding> findings = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (ConfigIssue issue : validation.issues()) {
            addFinding(
                    findings,
                    unique,
                    new DoctorReport.Finding(severity(issue.severity()), issue.code(), diagnosticObject(issue.path())));
        }
        for (DoctorReport.Finding finding : runtime.findings()) {
            addFinding(findings, unique, finding);
        }
        if (configSchemaIncompatible) {
            addFinding(
                    findings,
                    unique,
                    new DoctorReport.Finding(DoctorReport.Severity.ERROR, "config.schema_incompatible", "settings"));
        }
        boolean findingsTruncated = runtime.findingsTruncated() || findings.size() > MAX_PROFILE_FINDINGS;
        int markerCount = (findingsTruncated ? 1 : 0) + (runtime.ineffectiveTruncated() ? 1 : 0);
        int findingLimit = MAX_PROFILE_FINDINGS - markerCount;
        findings.stream()
                .limit(findingLimit)
                .forEach(finding -> builder.addProfileFinding(finding.severity(), finding.code(), finding.objectId()));
        if (findingsTruncated) {
            builder.addProfileFinding(DoctorReport.Severity.WARNING, "doctor.profile_findings_truncated", "profile");
        }
        if (runtime.ineffectiveTruncated()) {
            builder.addProfileFinding(DoctorReport.Severity.WARNING, "doctor.ineffective_targets_truncated", "profile");
        }
        builder.profile(
                snapshot.settings().activeProfileId(),
                profile.revision(),
                (int) profile.rules().stream().filter(rule -> rule.enabled()).count(),
                profile.rules().size(),
                saturatingAdd(
                        saturatingAdd(validation.errorCount(), runtime.errorCount()), configSchemaIncompatible ? 1 : 0),
                saturatingAdd(validation.warningCount(), runtime.warningCount()));
        runtime.ineffectiveTargets().stream()
                .limit(MAX_INEFFECTIVE_TARGETS)
                .forEach(target ->
                        builder.addIneffectiveTarget(target.ruleId(), target.targetId(), target.reasonCode()));
    }

    static ProfileAnalysis analyzeTargets(OreProfileDocument profile) {
        Map<String, DoctorReport.IneffectiveTarget> ineffective = new LinkedHashMap<>();
        List<DoctorReport.Finding> findings = new ArrayList<>();
        Set<String> uniqueFindings = new HashSet<>();
        long errorCount = 0L;
        long warningCount = 0L;
        for (var rule : profile.rules()) {
            try {
                OreTargetResolution.Result resolution = OreTargetResolution.resolve(rule);
                Map<Integer, OreTargetResolution.TargetResult> byIndex = new LinkedHashMap<>();
                for (OreTargetResolution.TargetResult target : resolution.targets()) {
                    byIndex.put(target.targetIndex(), target);
                    switch (target.status()) {
                        case SHADOWED, MISSING_OUTPUT, INVALID_HOST, INVALID_WEIGHT ->
                            addIneffective(
                                    ineffective,
                                    rule.id(),
                                    target.sourceId(),
                                    target.status().name().toLowerCase(Locale.ROOT));
                        case EFFECTIVE, PARTIALLY_SHADOWED -> {}
                    }
                }
                for (OreTargetResolution.Issue issue : resolution.issues()) {
                    if (issue.severity() == IssueSeverity.ERROR) {
                        errorCount = saturatingAdd(errorCount, 1L);
                    } else {
                        warningCount = saturatingAdd(warningCount, 1L);
                    }
                    addFinding(
                            findings,
                            uniqueFindings,
                            new DoctorReport.Finding(
                                    severity(issue.severity()),
                                    "ore_target." + issue.kind().name().toLowerCase(Locale.ROOT),
                                    issue.ruleId()));
                    if (issue.kind() == OreTargetResolution.IssueKind.MISSING_HOST_TAG) {
                        OreTargetResolution.TargetResult target = byIndex.get(issue.targetIndex());
                        if (target != null) {
                            addIneffective(ineffective, rule.id(), target.sourceId(), "missing_host_tag");
                        }
                    }
                }
                if (resolution.issuesTruncated()) {
                    warningCount = saturatingAdd(warningCount, 1L);
                    addFinding(
                            findings,
                            uniqueFindings,
                            new DoctorReport.Finding(
                                    DoctorReport.Severity.WARNING, "ore_target.issues_truncated", rule.id()));
                }
            } catch (RuntimeException exception) {
                errorCount = saturatingAdd(errorCount, 1L);
                addFinding(
                        findings,
                        uniqueFindings,
                        new DoctorReport.Finding(
                                DoctorReport.Severity.ERROR, "ore_target.resolution_failed", rule.id()));
            }
        }
        List<DoctorReport.IneffectiveTarget> ordered = ineffective.values().stream()
                .sorted(java.util.Comparator.comparing(DoctorReport.IneffectiveTarget::ruleId)
                        .thenComparing(DoctorReport.IneffectiveTarget::targetId)
                        .thenComparing(DoctorReport.IneffectiveTarget::reasonCode))
                .toList();
        boolean ineffectiveTruncated = ordered.size() > MAX_INEFFECTIVE_TARGETS;
        return new ProfileAnalysis(
                ordered.stream().limit(MAX_INEFFECTIVE_TARGETS).toList(),
                List.copyOf(findings),
                findings.size() > MAX_PROFILE_FINDINGS,
                ineffectiveTruncated,
                errorCount,
                warningCount);
    }

    static BackupAnalysis analyzeBackups(List<WorldBackupCatalog.BackupSummary> supplied) {
        List<WorldBackupCatalog.BackupSummary> summaries = supplied == null ? List.of() : supplied;
        int verified = 0;
        int invalid = 0;
        int legacy = 0;
        int pinned = 0;
        long knownBytes = 0L;
        boolean sizesKnown = true;
        List<DoctorReport.BackupProblem> problems = new ArrayList<>();
        List<BackupRetentionPlanner.Candidate> candidates = new ArrayList<>();
        for (WorldBackupCatalog.BackupSummary summary : summaries) {
            if (summary == null) {
                continue;
            }
            if (summary.pinned()) {
                pinned++;
            }
            if (summary.sizeBytes() < 0L) {
                sizesKnown = false;
                addBackupProblem(problems, summary.id(), "unknown", "size_unavailable");
            } else {
                knownBytes = saturatingAdd(knownBytes, summary.sizeBytes());
            }
            if (!summary.valid()) {
                invalid++;
                addBackupProblem(problems, summary.id(), "invalid", "backup_invalid");
            } else if (summary.legacy()) {
                legacy++;
                addBackupProblem(problems, summary.id(), "legacy", "manifest_required");
            } else if (summary.verified()) {
                verified++;
            } else {
                addBackupProblem(problems, summary.id(), "unverified", "verification_required");
            }
            candidates.add(BackupRetentionPlanner.fromSummary(summary));
        }
        return new BackupAnalysis(
                summaries.size(),
                verified,
                invalid,
                legacy,
                pinned,
                knownBytes,
                sizesKnown ? knownBytes : -1L,
                problems.stream().limit(MAX_BACKUP_PROBLEMS).toList(),
                List.copyOf(candidates));
    }

    private static BackupAnalysis readBackups(Path saveRoot) {
        try {
            return analyzeBackups(new WorldBackupCatalog(saveRoot).list());
        } catch (IOException | RuntimeException exception) {
            return new BackupAnalysis(
                    0,
                    0,
                    0,
                    0,
                    0,
                    0L,
                    -1L,
                    List.of(new DoctorReport.BackupProblem("catalog", "unavailable", "backup_catalog_unavailable")),
                    List.of());
        }
    }

    static DoctorReport.RetentionPreview retentionPreview(BackupRetentionPlanner.Plan plan) {
        return retentionPreview(plan, null);
    }

    static DoctorReport.RetentionPreview retentionPreview(
            BackupRetentionPlanner.Plan plan, BackupRetentionRunState.Snapshot lastRun) {
        List<DoctorReport.RetentionPrune> prunes = plan.prunes().stream()
                .limit(MAX_RETENTION_PRUNES)
                .map(prune -> new DoctorReport.RetentionPrune(
                        prune.id(),
                        Math.max(0L, prune.createdAtEpochMillis()),
                        Math.max(0L, prune.sizeBytes()),
                        prune.reasons().stream()
                                .map(reason -> reason.name().toLowerCase(Locale.ROOT))
                                .toList()))
                .toList();
        List<String> warnings = plan.warnings().stream()
                .map(DelvefoldDoctorService::retentionWarningCode)
                .distinct()
                .sorted()
                .toList();
        if (plan.prunes().size() > MAX_RETENTION_PRUNES) {
            List<String> bounded = new ArrayList<>(warnings);
            bounded.add("retention_preview_truncated");
            warnings = bounded.stream().distinct().sorted().toList();
        }
        return new DoctorReport.RetentionPreview(
                plan.enabled(),
                plan.beforeCount(),
                plan.afterCount(),
                Math.max(0L, plan.beforeBytes()),
                Math.max(0L, plan.afterBytes()),
                plan.constraintsSatisfied(),
                prunes,
                warnings,
                retentionRun(lastRun));
    }

    static DoctorReport.RetentionRun retentionRun(BackupRetentionRunState.Snapshot snapshot) {
        if (snapshot == null) {
            return DoctorReport.RetentionRun.none();
        }
        List<DoctorReport.RetentionProposal> proposals = snapshot.proposals().stream()
                .limit(MAX_RETENTION_PRUNES)
                .map(proposal -> new DoctorReport.RetentionProposal(
                        proposal.backupId(),
                        proposal.reasons().stream()
                                .map(reason -> reason.name().toLowerCase(Locale.ROOT))
                                .toList()))
                .toList();
        int proposalCount = saturatingCount(snapshot.proposals().size(), snapshot.omittedProposalCount());
        int protectedCount = saturatingCount(snapshot.protectedBackupIds().size(), snapshot.omittedProtectedCount());
        int appliedCount = saturatingCount(snapshot.prunedBackupIds().size(), snapshot.omittedPrunedCount());
        int failureCount = saturatingCount(snapshot.failedBackupIds().size(), snapshot.omittedFailureCount());
        int before = Math.max(0, snapshot.beforeCount());
        int after = Math.min(before, Math.max(0, snapshot.afterCount()));
        return new DoctorReport.RetentionRun(
                true,
                Math.max(0L, snapshot.evaluatedAtEpochMillis()),
                snapshot.enabled(),
                before,
                after,
                proposalCount,
                proposals,
                protectedCount,
                snapshot.constraintsSatisfied(),
                snapshot.applyRecorded(),
                appliedCount,
                failureCount,
                snapshot.warnings().stream()
                        .map(DelvefoldDoctorService::retentionWarningCode)
                        .distinct()
                        .sorted()
                        .toList());
    }

    static PendingScan scanPending(Path configDirectory) {
        Path directory = configDirectory.toAbsolutePath().normalize();
        List<DoctorReport.PendingOperationStatus> statuses = new ArrayList<>();
        Set<String> backupIds = new HashSet<>();
        readPendingWorldOperation(directory.resolve("pending_world_operation.json"), statuses);
        readPendingRestore(directory.resolve("pending_restore.json"), statuses, backupIds);
        return new PendingScan(statuses, backupIds);
    }

    private static void readPendingWorldOperation(Path path, List<DoctorReport.PendingOperationStatus> statuses) {
        if (Files.notExists(path)) {
            return;
        }
        try {
            PendingWorldOperation pending = readBounded(path, PendingWorldOperation.class);
            if (pending == null
                    || pending.schemaVersion() != PendingWorldOperation.CURRENT_SCHEMA_VERSION
                    || !validOperationId(pending.operationId())
                    || pending.type() == null
                    || pending.createdAtEpochMillis() < 0L) {
                throw new IOException("Pending world operation is invalid");
            }
            statuses.add(new DoctorReport.PendingOperationStatus(
                    pending.operationId(),
                    pending.type().name().toLowerCase(Locale.ROOT),
                    "restart_required",
                    pending.createdAtEpochMillis()));
        } catch (IOException | RuntimeException exception) {
            statuses.add(invalidPending("pending_world_operation", "world_operation", path));
        }
    }

    private static void readPendingRestore(
            Path path, List<DoctorReport.PendingOperationStatus> statuses, Set<String> backupIds) {
        if (Files.notExists(path)) {
            return;
        }
        try {
            PendingWorldRestore pending = readBounded(path, PendingWorldRestore.class);
            if (pending == null
                    || pending.schemaVersion() != PendingWorldRestore.CURRENT_SCHEMA_VERSION
                    || !validOperationId(pending.operationId())
                    || !validBackupId(pending.backupId())
                    || pending.phase() == null
                    || pending.createdAtEpochMillis() < 0L) {
                throw new IOException("Pending restore is invalid");
            }
            statuses.add(new DoctorReport.PendingOperationStatus(
                    pending.operationId(),
                    "restore",
                    pending.phase().name().toLowerCase(Locale.ROOT),
                    pending.createdAtEpochMillis()));
            backupIds.add(pending.backupId());
        } catch (IOException | RuntimeException exception) {
            statuses.add(invalidPending("pending_restore", "restore", path));
        }
    }

    private static <T> T readBounded(Path path, Class<T> type) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path) || Files.size(path) > MAX_PENDING_BYTES) {
            throw new IOException("Pending operation file failed safety checks");
        }
        try {
            return ConfigJson.GSON.fromJson(Files.readString(path), type);
        } catch (RuntimeException exception) {
            throw new IOException("Pending operation JSON is invalid", exception);
        }
    }

    private static DoctorReport.PendingOperationStatus invalidPending(String id, String operation, Path path) {
        long modified = 0L;
        try {
            modified = Math.max(0L, Files.getLastModifiedTime(path).toMillis());
        } catch (IOException ignored) {
        }
        return new DoctorReport.PendingOperationStatus(id, operation, "invalid", modified);
    }

    private static boolean validOperationId(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException | NullPointerException exception) {
            return false;
        }
    }

    private static boolean validBackupId(String value) {
        return value != null && value.matches("[a-zA-Z0-9_.-]{1,200}");
    }

    static long estimateTreeBytes(Path root, int maximumEntries) {
        if (root == null || maximumEntries < 1 || Files.notExists(root)) {
            return root == null || maximumEntries < 1 ? -1L : 0L;
        }
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root)) {
            return -1L;
        }
        long total = 0L;
        int visited = 0;
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.limit((long) maximumEntries + 1L).toList()) {
                if (++visited > maximumEntries || Files.isSymbolicLink(path)) {
                    return -1L;
                }
                if (Files.isRegularFile(path)) {
                    total = saturatingAdd(total, Files.size(path));
                } else if (!Files.isDirectory(path)) {
                    return -1L;
                }
            }
            return total;
        } catch (IOException | RuntimeException exception) {
            return -1L;
        }
    }

    static long estimateCurrentBackupBytes(Path saveRoot, ConfigPaths paths) {
        Path normalizedRoot = saveRoot.toAbsolutePath().normalize();
        Path dimensions = normalizedRoot.resolve("dimensions/delvefold").normalize();
        if (!dimensions.startsWith(normalizedRoot)) {
            return -1L;
        }
        long total = estimateTreeBytes(dimensions, MAX_DISK_ESTIMATE_ENTRIES);
        if (total < 0L) {
            return -1L;
        }
        for (Path config : List.of(paths.ores(), paths.settings())) {
            Path normalized = config.toAbsolutePath().normalize();
            if (!normalized.startsWith(normalizedRoot) || Files.isSymbolicLink(normalized)) {
                return -1L;
            }
            try {
                if (Files.exists(normalized)) {
                    if (!Files.isRegularFile(normalized)) {
                        return -1L;
                    }
                    total = saturatingAdd(total, Files.size(normalized));
                }
            } catch (IOException exception) {
                return -1L;
            }
        }
        return total;
    }

    static DoctorReport.DiskEstimate diskEstimate(Path saveRoot, ConfigPaths paths, long backupBytes) {
        long usable = -1L;
        try {
            FileStore store = Files.getFileStore(saveRoot);
            usable = Math.max(0L, store.getUsableSpace());
        } catch (IOException | RuntimeException ignored) {
        }
        long nextBackup = estimateCurrentBackupBytes(saveRoot, paths);
        long headroom = nextBackup < 0L ? -1L : Math.min(nextBackup, 64L * MEBIBYTE) + 16L * MEBIBYTE;
        return new DoctorReport.DiskEstimate(usable, Math.max(-1L, backupBytes), nextBackup, headroom);
    }

    static Path ensureExportsDirectory(ConfigPaths paths) throws IOException {
        Path directory = paths.directory().toAbsolutePath().normalize();
        Path exports = paths.exports().toAbsolutePath().normalize();
        if (!exports.startsWith(directory)
                || exports.equals(directory)
                || !exports.getParent().equals(directory)) {
            throw new IOException("Doctor exports directory escaped Delvefold serverconfig");
        }
        if (Files.exists(directory) && (Files.isSymbolicLink(directory) || !Files.isDirectory(directory))) {
            throw new IOException("Delvefold serverconfig directory failed safety checks");
        }
        Files.createDirectories(directory);
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory)) {
            throw new IOException("Delvefold serverconfig directory failed safety checks");
        }
        if (Files.notExists(exports)) {
            Files.createDirectory(exports);
        }
        if (Files.isSymbolicLink(exports) || !Files.isDirectory(exports)) {
            throw new IOException("Doctor exports directory failed safety checks");
        }
        return exports;
    }

    private static String modVersion() {
        String value = Delvefold.class.getPackage().getImplementationVersion();
        return value == null || value.isBlank() ? "development" : value;
    }

    private static int protocolVersion() {
        try {
            return Integer.parseInt(DelvefoldNetwork.PROTOCOL_VERSION);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static DoctorReport.Severity severity(IssueSeverity severity) {
        return severity == IssueSeverity.ERROR ? DoctorReport.Severity.ERROR : DoctorReport.Severity.WARNING;
    }

    private static String diagnosticObject(String path) {
        if (path == null || path.isBlank() || path.equals("$")) {
            return "root";
        }
        String value = path.replace("$", "root")
                .replace('[', '.')
                .replace("]", "")
                .replaceAll("[^A-Za-z0-9_.:/#-]", ".")
                .replaceAll("\\.{2,}", ".");
        return value.length() > 160 ? value.substring(0, 160) : value;
    }

    private static void addFinding(
            List<DoctorReport.Finding> findings, Set<String> unique, DoctorReport.Finding finding) {
        if (findings.size() > MAX_PROFILE_FINDINGS) {
            return;
        }
        String key = finding.severity() + "\u0000" + finding.code() + "\u0000" + finding.objectId();
        if (unique.add(key)) {
            findings.add(finding);
        }
    }

    private static void addIneffective(
            Map<String, DoctorReport.IneffectiveTarget> targets, String ruleId, String targetId, String reason) {
        if (targets.size() > MAX_INEFFECTIVE_TARGETS) {
            return;
        }
        DoctorReport.IneffectiveTarget target = new DoctorReport.IneffectiveTarget(ruleId, targetId, reason);
        String key = target.ruleId() + "\u0000" + target.targetId() + "\u0000" + target.reasonCode();
        targets.putIfAbsent(key, target);
    }

    private static void addBackupProblem(
            List<DoctorReport.BackupProblem> problems, String id, String state, String reason) {
        if (problems.size() < MAX_BACKUP_PROBLEMS) {
            problems.add(new DoctorReport.BackupProblem(id, state, reason));
        }
    }

    private static String retentionWarningCode(String warning) {
        if (warning == null) {
            return "retention_constraint_unmet";
        }
        if (warning.startsWith("max_age_days")) {
            return "max_age_days_protected";
        }
        if (warning.startsWith("max_count")) {
            return "max_count_protected";
        }
        if (warning.startsWith("max_total_bytes")) {
            return "max_total_bytes_protected";
        }
        return "retention_constraint_unmet";
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static int saturatingCount(int first, int second) {
        long result = (long) Math.max(0, first) + Math.max(0, second);
        return (int) Math.min(Integer.MAX_VALUE, result);
    }

    private record DimensionDefinition(
            ResourceKey<Level> level, ResourceKey<LevelStem> stem, TerrainMode terrain, TerrainVariant variant) {}

    record ProfileAnalysis(
            List<DoctorReport.IneffectiveTarget> ineffectiveTargets,
            List<DoctorReport.Finding> findings,
            boolean findingsTruncated,
            boolean ineffectiveTruncated,
            long errorCount,
            long warningCount) {
        ProfileAnalysis {
            ineffectiveTargets = List.copyOf(ineffectiveTargets);
            findings = List.copyOf(findings);
        }
    }

    record BackupAnalysis(
            int total,
            int verified,
            int invalid,
            int legacy,
            int pinned,
            long knownBytes,
            long diskBytes,
            List<DoctorReport.BackupProblem> problems,
            List<BackupRetentionPlanner.Candidate> candidates) {
        BackupAnalysis {
            problems = List.copyOf(problems);
            candidates = List.copyOf(candidates);
        }
    }

    record PendingScan(List<DoctorReport.PendingOperationStatus> statuses, Set<String> pendingBackupIds) {
        PendingScan {
            statuses = List.copyOf(statuses);
            pendingBackupIds = Set.copyOf(pendingBackupIds);
        }
    }

    private record CapturedState(
            long generatedAtEpochMillis,
            ConfigSnapshot snapshot,
            ConfigPaths paths,
            Path saveRoot,
            DoctorReportBuilder builder,
            BackupRetentionRunState.Snapshot retentionRun) {}

    private static final class DoctorThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "Delvefold Doctor " + WORKER_IDS.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
