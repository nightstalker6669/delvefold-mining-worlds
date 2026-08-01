package com.nightsta69.delvefold.reset;

import com.mojang.logging.LogUtils;
import com.nightsta69.delvefold.admin.AdminLocalizedMessage;
import com.nightsta69.delvefold.api.DelvefoldApi;
import com.nightsta69.delvefold.api.event.DelvefoldWorldLifecycleEvent;
import com.nightsta69.delvefold.audit.AuditMutation;
import com.nightsta69.delvefold.audit.DelvefoldAuditService;
import com.nightsta69.delvefold.config.ConfigJson;
import com.nightsta69.delvefold.config.ConfigPaths;
import com.nightsta69.delvefold.config.ConfigSnapshot;
import com.nightsta69.delvefold.config.ConfigWriteResult;
import com.nightsta69.delvefold.config.DelvefoldConfigService;
import com.nightsta69.delvefold.config.OrePresets;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.model.WorldSettingsDocument;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Two-phase, restart-safe mining-world deletion and recreation.
 *
 * <p>Loaded dimension directories are never modified. Confirmation writes a pending operation and blocks new portal
 * entry; filesystem work occurs during the next server startup before the levels are made available.
 */
public final class WorldOperationService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final WorldOperationService INSTANCE = new WorldOperationService();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final long CONFIRMATION_WINDOW_MILLIS = 5L * 60L * 1000L;
    private static final DateTimeFormatter BACKUP_TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss", Locale.ROOT).withZone(ZoneOffset.UTC);
    private static final Set<String> DIMENSION_PATHS = DelvefoldDimensionFolders.ALL_SET;

    private final Object lock = new Object();
    private final AtomicBoolean entryBlocked = new AtomicBoolean(false);
    private @Nullable Draft draft;
    private @Nullable Applied applied;

    private WorldOperationService() {}

    /**
     * Returns the process-wide world-operation coordinator.
     *
     * @return the singleton service
     */
    public static WorldOperationService get() {
        return INSTANCE;
    }

    /**
     * Creates a time-limited preview for deleting or recreating the active mining world.
     *
     * <p>This method does not modify dimension data. A successful preview contains a one-use confirmation token that
     * remains valid for five minutes in this JVM. Callers are responsible for enforcing the world-management permission
     * before requesting a preview. The returned size is an estimate in bytes and may be {@code -1} when the dimension
     * folders cannot be completely inspected.
     *
     * @param server the server whose normalized save root owns the mining world
     * @param request the requested operation and its replacement settings
     * @param requestedBy the actor name stored in the restart journal and audit trail
     * @return an accepted preview with confirmation details, or a localized rejection without a token
     */
    public WorldOperationPreview request(MinecraftServer server, WorldOperationRequest request, String requestedBy) {
        return LifecycleOperationCoordinator.coordinate(() -> requestCoordinated(server, request, requestedBy));
    }

    private WorldOperationPreview requestCoordinated(
            MinecraftServer server, WorldOperationRequest request, String requestedBy) {
        synchronized (lock) {
            ConfigSnapshot snapshot;
            try {
                snapshot = DelvefoldConfigService.get().snapshot();
            } catch (IllegalStateException exception) {
                return WorldOperationPreview.rejected(
                        localized("message.delvefold.world_operation.config_unavailable"));
            }
            if (!snapshot.settings().initialized()) {
                return WorldOperationPreview.rejected(
                        request.type() == WorldOperationType.DELETE
                                ? localized("message.delvefold.world_operation.no_world.delete")
                                : localized("message.delvefold.world_operation.no_world.recreate"));
            }
            Path pendingPath = pendingPath(server);
            if (Files.exists(pendingPath)) {
                return WorldOperationPreview.rejected(localized("message.delvefold.world_operation.already_pending"));
            }
            if (LifecycleOperationCoordinator.conflictingOperationExists(
                    server, LifecycleOperationCoordinator.Kind.WORLD_OPERATION)) {
                return WorldOperationPreview.rejected(
                        localized("message.delvefold.world_operation.conflicting_operation"));
            }
            if (request.type() == WorldOperationType.RECREATE && request.targetTerrain() == null) {
                return WorldOperationPreview.rejected(localized("message.delvefold.world_operation.choose_terrain"));
            }
            String token = randomToken();
            long expires = System.currentTimeMillis() + CONFIRMATION_WINDOW_MILLIS;
            PendingWorldOperation operation = new PendingWorldOperation(
                    PendingWorldOperation.CURRENT_SCHEMA_VERSION,
                    UUID.randomUUID().toString(),
                    request.type(),
                    snapshot.settings().terrainMode(),
                    request.targetTerrain(),
                    request.targetVariant(),
                    request.targetGeologyTheme() == null
                            ? snapshot.settings().identity().geologyTheme()
                            : request.targetGeologyTheme(),
                    request.targetOrePreset(),
                    request.targetGameplayPreset(),
                    request.backupMode(),
                    request.resetOreConfiguration(),
                    System.currentTimeMillis(),
                    requestedBy);
            List<Path> targets = resolveTargets(server);
            long bytes = estimateSize(targets);
            int players = countMiningPlayersForOperation(server);
            draft = new Draft(saveRoot(server), token, expires, operation, bytes, players);
            String message = request.backupMode() == BackupMode.KEEP_BACKUP
                    ? localized("message.delvefold.world_operation.preview.backup")
                    : localized("message.delvefold.world_operation.preview.permanent");
            return new WorldOperationPreview(true, message, token, expires, bytes, players, request.backupMode());
        }
    }

    /**
     * Confirms the current preview and persists its restart journal.
     *
     * <p>Successful confirmation blocks new portal entry and evacuates players from managed mining dimensions, but it
     * does not move or delete loaded dimension folders. Filesystem mutation is deferred to the next startup. The token
     * is compared without early-exit timing and is never persisted.
     *
     * @param server the server for which the preview was created
     * @param token the confirmation token supplied by the authorized caller
     * @return a successful scheduling result, or a localized failure when the preview is absent, expired, belongs to
     *     another save, conflicts with another lifecycle operation, or cannot be journaled
     */
    public WorldOperationResult confirm(MinecraftServer server, String token) {
        return LifecycleOperationCoordinator.coordinate(() -> confirmCoordinated(server, token));
    }

    private WorldOperationResult confirmCoordinated(MinecraftServer server, String token) {
        synchronized (lock) {
            @Nullable Draft currentDraft = draft;
            if (currentDraft == null) {
                return WorldOperationResult.failure(localized("message.delvefold.world_operation.confirm.none"));
            }
            if (!currentDraft.saveRoot().equals(saveRoot(server))) {
                draft = null;
                return WorldOperationResult.failure(localized("message.delvefold.world_operation.save_mismatch"));
            }
            if (System.currentTimeMillis() > currentDraft.expiresAtEpochMillis()) {
                draft = null;
                return WorldOperationResult.failure(localized("message.delvefold.world_operation.confirm.expired"));
            }
            if (!constantTimeEquals(currentDraft.token(), token)) {
                return WorldOperationResult.failure(localized("message.delvefold.world_operation.confirm.invalid"));
            }
            if (LifecycleOperationCoordinator.conflictingOperationExists(
                    server, LifecycleOperationCoordinator.Kind.WORLD_OPERATION)) {
                return WorldOperationResult.failure(
                        localized("message.delvefold.world_operation.conflicting_operation"));
            }
            try {
                writeJsonAtomically(pendingPath(server), currentDraft.operation());
                entryBlocked.set(true);
                evacuateMiningPlayersForOperation(server);
                String instruction = server.isDedicatedServer()
                        ? localized("message.delvefold.world_operation.scheduled.dedicated")
                        : localized("message.delvefold.world_operation.scheduled.integrated");
                draft = null;
                return WorldOperationResult.success(instruction);
            } catch (IOException exception) {
                LOGGER.error("Could not persist pending Delvefold world operation", exception);
                return WorldOperationResult.failure(
                        localized("message.delvefold.world_operation.schedule_failed", exception.getMessage()));
            }
        }
    }

    /**
     * Cancels the current unconfirmed preview.
     *
     * <p>A preview cannot be cancelled through this method after its journal has been confirmed; use
     * {@link #cancelConfirmed(MinecraftServer)} while the current world remains loaded.
     *
     * @param server the server whose save root must match the preview
     * @return the cancellation result, including a localized reason when no cancellable preview exists
     */
    public WorldOperationResult cancel(MinecraftServer server) {
        return LifecycleOperationCoordinator.coordinate(() -> cancelCoordinated(server));
    }

    private WorldOperationResult cancelCoordinated(MinecraftServer server) {
        synchronized (lock) {
            if (Files.exists(pendingPath(server))) {
                return WorldOperationResult.failure(
                        localized("message.delvefold.world_operation.cancel.already_confirmed"));
            }
            @Nullable Draft currentDraft = draft;
            if (currentDraft == null) {
                return WorldOperationResult.failure(
                        localized("message.delvefold.world_operation.cancel.none_unconfirmed"));
            }
            if (!currentDraft.saveRoot().equals(saveRoot(server))) {
                draft = null;
                return WorldOperationResult.failure(localized("message.delvefold.world_operation.save_mismatch"));
            }
            draft = null;
            return WorldOperationResult.success(
                    localized("message.delvefold.world_operation.cancel.unconfirmed_success"));
        }
    }

    /**
     * Cancels a confirmed operation while the current world is still loaded.
     *
     * <p>This removes only the pending journal and releases the entry block. It is not a recovery mechanism after
     * {@link #prepareStartup(MinecraftServer)} has begun moving dimension data.
     *
     * @param server the server that owns the pending journal
     * @return the cancellation result, or a localized failure if the journal is absent or cannot be deleted
     */
    public WorldOperationResult cancelConfirmed(MinecraftServer server) {
        return LifecycleOperationCoordinator.coordinate(() -> cancelConfirmedCoordinated(server));
    }

    private WorldOperationResult cancelConfirmedCoordinated(MinecraftServer server) {
        synchronized (lock) {
            Path pending = pendingPath(server);
            try {
                if (Files.notExists(pending)) {
                    return WorldOperationResult.failure(
                            localized("message.delvefold.world_operation.cancel.none_confirmed"));
                }
                Files.delete(pending);
                entryBlocked.set(false);
                return WorldOperationResult.success(
                        localized("message.delvefold.world_operation.cancel.confirmed_success"));
            } catch (IOException exception) {
                return WorldOperationResult.failure(
                        localized("message.delvefold.world_operation.cancel.failed", exception.getMessage()));
            }
        }
    }

    /**
     * Reports whether any destructive lifecycle operation currently blocks mining-world entry.
     *
     * @return {@code true} for this service's confirmed operation or a restore service entry block
     */
    public boolean isEntryBlocked() {
        return entryBlocked.get() || WorldRestoreService.get().isEntryBlocked();
    }

    /**
     * Reports whether a confirmed delete or recreate operation is pending in this service.
     *
     * @return {@code true} only for this service's entry block; pending restores are excluded
     */
    public boolean isWorldOperationPending() {
        return entryBlocked.get();
    }

    /**
     * Tests whether the active save contains a persisted delete or recreate journal.
     *
     * @param server the server whose configuration directory is inspected
     * @return {@code true} when the pending-operation path exists
     */
    public boolean hasPending(MinecraftServer server) {
        return Files.exists(pendingPath(server));
    }

    /** Includes this service's unexpired draft as well as its persisted restart journal. */
    boolean hasActiveLifecycleOperation(MinecraftServer server) {
        synchronized (lock) {
            if (Files.exists(pendingPath(server))) {
                return true;
            }
            @Nullable Draft currentDraft = draft;
            if (currentDraft == null) {
                return false;
            }
            if (!currentDraft.saveRoot().equals(saveRoot(server))
                    || System.currentTimeMillis() > currentDraft.expiresAtEpochMillis()) {
                draft = null;
                return false;
            }
            return true;
        }
    }

    /**
     * Returns backup IDs that retention must protect while a world operation is in flight.
     *
     * <p>The immutable result may include IDs derived from an unexpired in-memory draft, its persisted restart journal,
     * and a startup operation awaiting finalization. Permanent-delete staging is deliberately excluded.
     *
     * @param server the server whose save and journal are inspected
     * @return an immutable set of logical backup IDs; never absolute filesystem paths
     * @throws IOException if an existing pending journal cannot be safely read or validated
     */
    public Set<String> referencedBackupIds(MinecraftServer server) throws IOException {
        synchronized (lock) {
            Set<String> result = new HashSet<>();
            Path root = saveRoot(server);
            @Nullable Draft currentDraft = draft;
            if (currentDraft != null
                    && currentDraft.saveRoot().equals(root)
                    && currentDraft.operation().backupMode() == BackupMode.KEEP_BACKUP) {
                result.add(holdingRoot(server, currentDraft.operation())
                        .getFileName()
                        .toString());
            }
            Path pending = pendingPath(server);
            if (Files.exists(pending)) {
                PendingWorldOperation operation = readPending(pending);
                if (operation.backupMode() == BackupMode.KEEP_BACKUP) {
                    result.add(holdingRoot(server, operation).getFileName().toString());
                }
            }
            @Nullable Applied currentApplied = applied;
            if (currentApplied != null && currentApplied.operation().backupMode() == BackupMode.KEEP_BACKUP) {
                result.add(currentApplied.holdingRoot().getFileName().toString());
            }
            return Set.copyOf(result);
        }
    }

    /**
     * Clears JVM-local state when an integrated or dedicated server stops.
     *
     * <p>Persisted journals and staged data are intentionally retained for restart recovery.
     *
     * @param server the stopping server; used to associate this reset with the lifecycle callback
     */
    public void stop(MinecraftServer server) {
        LifecycleOperationCoordinator.coordinate(() -> {
            synchronized (lock) {
                draft = null;
                applied = null;
                entryBlocked.set(false);
            }
        });
    }

    /**
     * Applies the filesystem preparation phase of a confirmed operation before dimensions load.
     *
     * <p>This method must run at highest priority in the server-about-to-start event. It validates normalized paths,
     * rejects symbolic-link dimension targets, moves managed dimension folders into an operation-specific holding
     * directory, and creates a verified manifest when a backup was requested. A committed journal is resumed at its
     * finalization phase. Any unsafe or incomplete preparation stops startup so recoverable data is not silently
     * replaced.
     *
     * @param server the starting server whose save root contains the pending journal
     * @throws IllegalStateException if the journal, paths, storage, move, rollback, configuration snapshot, or backup
     *     manifest cannot be handled safely
     */
    public void prepareStartup(MinecraftServer server) {
        synchronized (lock) {
            Path pending = pendingPath(server);
            if (Files.notExists(pending)) {
                entryBlocked.set(false);
                applied = null;
                return;
            }
            entryBlocked.set(true);
            try {
                PendingWorldOperation operation = readPending(pending);
                List<Path> targets = resolveTargets(server);
                Path holdingRoot = holdingRoot(server, operation);
                validateTargets(server, targets, holdingRoot);
                if (isOperationCommitted(server, operation)) {
                    applied = new Applied(operation, pending, holdingRoot, List.of());
                    LOGGER.info(
                            "Resuming final cleanup for committed Delvefold {} operation {}",
                            operation.type(),
                            operation.operationId());
                    return;
                }
                if (operation.backupMode() == BackupMode.KEEP_BACKUP) {
                    ensureBackupCapacity(holdingRoot, estimateSize(targets));
                }
                ensureHoldingMarker(holdingRoot, operation);
                if (operation.backupMode() == BackupMode.KEEP_BACKUP) {
                    captureConfiguration(server, holdingRoot);
                }
                List<MoveRecord> moves = moveIntoHolding(server, targets, holdingRoot);
                if (operation.backupMode() == BackupMode.KEEP_BACKUP) {
                    Files.createDirectories(holdingRoot.resolve("dimensions/delvefold"));
                    new BackupManifestService().createVerifiedManifest(holdingRoot);
                    auditBackupManifest(
                            operation.requestedBy(), holdingRoot.getFileName().toString());
                }
                applied = new Applied(operation, pending, holdingRoot, moves);
                LOGGER.info(
                        "Prepared Delvefold {} operation {} with {} moved dimension folder(s)",
                        operation.type(),
                        operation.operationId(),
                        moves.size());
            } catch (Exception exception) {
                applied = null;
                LOGGER.error(
                        "Delvefold refused to apply the pending world operation; dimension data was left recoverable",
                        exception);
                throw new IllegalStateException(
                        "Delvefold could not safely prepare its pending world operation; startup was stopped",
                        exception);
            }
        }
    }

    private static void auditBackupManifest(String requestedBy, String backupId) {
        String actor = requestedBy == null || requestedBy.isBlank() ? "server" : requestedBy;
        try {
            DelvefoldAuditService.get()
                    .record(new AuditMutation(
                            actor,
                            AuditMutation.Operation.BACKUP_MANIFEST_CREATED,
                            AuditMutation.ObjectType.BACKUP,
                            backupId,
                            -1L,
                            -1L));
        } catch (IllegalArgumentException exception) {
            DelvefoldAuditService.get()
                    .record(new AuditMutation(
                            "server",
                            AuditMutation.Operation.BACKUP_MANIFEST_CREATED,
                            AuditMutation.ObjectType.BACKUP,
                            backupId,
                            -1L,
                            -1L));
        }
    }

    /**
     * Commits settings and finalizes a prepared operation after configuration has loaded.
     *
     * <p>The operation ID makes settings finalization restart-safe. Recreation settings and an optional ore-profile
     * reset are committed before permanent staging is deleted or a retained backup is archived. Entry remains blocked
     * and the journal remains recoverable when finalization fails.
     *
     * @param server the server used during the matching preparation phase
     * @throws IllegalStateException if settings, profile activation, cleanup, archival, or lifecycle publication cannot
     *     complete safely
     */
    public void finishStartup(MinecraftServer server) {
        synchronized (lock) {
            @Nullable Applied currentApplied = applied;
            if (currentApplied == null) {
                return;
            }
            PendingWorldOperation operation = currentApplied.operation();
            try {
                DelvefoldConfigService configs = DelvefoldConfigService.get();
                ConfigSnapshot before = configs.snapshot();
                if (!operation.operationId().equals(before.settings().lastWorldOperationId())) {
                    ConfigWriteResult settingsResult = configs.updateSettings(
                            before.settings().revision(), settings -> applyOperationToSettings(settings, operation));
                    if (!settingsResult.saved()) {
                        throw new IOException("Settings update was rejected: " + settingsResult.issues());
                    }
                }
                if (operation.resetOreConfiguration()) {
                    ConfigSnapshot afterSettings = configs.snapshot();
                    var preset = operation.targetOrePreset() == null
                            ? afterSettings.settings().orePreset()
                            : operation.targetOrePreset();
                    OreProfileDocument replacement = OrePresets.create(preset);
                    if (!replacement.profile().equals(afterSettings.ores().profile())) {
                        ConfigWriteResult oresResult =
                                configs.activateProfile(afterSettings.ores().revision(), replacement.profile());
                        if (!oresResult.saved()) {
                            throw new IOException("Ore preset reset was rejected: " + oresResult.issues());
                        }
                    }
                }
                if (operation.backupMode() == BackupMode.PERMANENT) {
                    deleteTree(currentApplied.holdingRoot());
                }
                archiveOperationRecord(server, currentApplied);
                ConfigSnapshot after = configs.snapshot();
                NeoForge.EVENT_BUS.post(new DelvefoldWorldLifecycleEvent(
                        operation.type() == WorldOperationType.DELETE
                                ? DelvefoldWorldLifecycleEvent.Action.DELETED
                                : DelvefoldWorldLifecycleEvent.Action.RECREATED,
                        DelvefoldApi.worldView(before.settings()),
                        DelvefoldApi.worldView(after.settings()),
                        operation.operationId()));
                applied = null;
                entryBlocked.set(false);
                LOGGER.info("Completed Delvefold {} operation {}", operation.type(), operation.operationId());
            } catch (Exception exception) {
                LOGGER.error(
                        "Could not finalize Delvefold world operation {}; keeping its data in {} and leaving the pending record in place",
                        operation.operationId(),
                        currentApplied.holdingRoot(),
                        exception);
                throw new IllegalStateException(
                        "Delvefold could not safely finalize its pending world operation; startup was stopped",
                        exception);
            }
        }
    }

    private static PendingWorldOperation readPending(Path path) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path) || Files.size(path) > 64 * 1024) {
            throw new IOException("Pending operation file failed safety checks");
        }
        PendingWorldOperation operation =
                ConfigJson.GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), PendingWorldOperation.class);
        if (operation == null || operation.schemaVersion() != PendingWorldOperation.CURRENT_SCHEMA_VERSION) {
            throw new IOException("Pending operation has an unsupported schema");
        }
        UUID.fromString(operation.operationId());
        return operation;
    }

    private static boolean isOperationCommitted(MinecraftServer server, PendingWorldOperation operation) {
        Path settingsPath = ConfigPaths.forServer(server).settings();
        try {
            if (Files.isSymbolicLink(settingsPath)
                    || !Files.isRegularFile(settingsPath)
                    || Files.size(settingsPath) > 4L * 1024L * 1024L) {
                return false;
            }
            WorldSettingsDocument settings = ConfigJson.GSON.fromJson(
                    Files.readString(settingsPath, StandardCharsets.UTF_8), WorldSettingsDocument.class);
            return settings != null && operation.operationId().equals(settings.lastWorldOperationId());
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    private static List<Path> resolveTargets(MinecraftServer server) {
        Path root = dimensionsRoot(server);
        return DelvefoldDimensionFolders.ALL.stream()
                .map(path -> root.resolve(path).normalize())
                .toList();
    }

    private static Path dimensionsRoot(MinecraftServer server) {
        Path saveRoot = saveRoot(server);
        return saveRoot.resolve("dimensions").resolve("delvefold").normalize();
    }

    private static Path holdingRoot(MinecraftServer server, PendingWorldOperation operation) {
        Path saveRoot = saveRoot(server);
        String timestamp = BACKUP_TIMESTAMP.format(Instant.ofEpochMilli(operation.createdAtEpochMillis()));
        String prefix =
                operation.backupMode() == BackupMode.KEEP_BACKUP ? "delvefold_backups" : ".delvefold_delete_staging";
        return saveRoot.resolve(prefix)
                .resolve(timestamp + '-' + operation.operationId())
                .normalize();
    }

    private static void validateTargets(MinecraftServer server, List<Path> targets, Path holdingRoot)
            throws IOException {
        Path saveRoot = saveRoot(server);
        Path dimensionsRoot = dimensionsRoot(server);
        if (!holdingRoot.startsWith(saveRoot) || holdingRoot.equals(saveRoot)) {
            throw new IOException("Holding directory escaped the active save");
        }
        Set<Path> unique = new HashSet<>();
        for (Path target : targets) {
            if (!target.startsWith(dimensionsRoot)
                    || target.equals(dimensionsRoot)
                    || !DIMENSION_PATHS.contains(target.getFileName().toString())) {
                throw new IOException("Refusing unsafe dimension path: " + target);
            }
            if (!unique.add(target)) {
                throw new IOException("Duplicate dimension path in operation: " + target);
            }
            if (Files.isSymbolicLink(target)) {
                throw new IOException("Refusing symbolic-link dimension path: " + target);
            }
        }
    }

    private static List<MoveRecord> moveIntoHolding(MinecraftServer server, List<Path> targets, Path holdingRoot)
            throws IOException {
        List<MoveRecord> moves = new ArrayList<>();
        try {
            for (Path source : targets) {
                Path relative = saveRoot(server).relativize(source);
                Path destination = holdingRoot.resolve(relative).normalize();
                if (!destination.startsWith(holdingRoot)) {
                    throw new IOException("Backup destination escaped its holding directory");
                }
                if (Files.exists(destination)) {
                    if (Files.isSymbolicLink(destination) || !Files.isDirectory(destination)) {
                        throw new IOException("Existing staged dimension failed safety checks: " + destination);
                    }
                    // A previous startup already staged the old data. Any source now present is
                    // replacement terrain generated while finalization was waiting to retry.
                    moves.add(new MoveRecord(source, destination, false));
                    continue;
                }
                if (Files.notExists(source)) {
                    continue;
                }
                Files.createDirectories(destination.getParent());
                moveDirectory(source, destination);
                moves.add(new MoveRecord(source, destination, true));
            }
            return List.copyOf(moves);
        } catch (Exception exception) {
            rollbackMoves(moves);
            if (exception instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Could not move dimension folders", exception);
        }
    }

    private static void rollbackMoves(List<MoveRecord> moves) throws IOException {
        IOException failure = null;
        for (int index = moves.size() - 1; index >= 0; index--) {
            MoveRecord move = moves.get(index);
            if (!move.movedThisStartup()) {
                continue;
            }
            try {
                Files.createDirectories(move.source().getParent());
                moveDirectory(/* source= */ move.destination(), /* destination= */ move.source());
            } catch (IOException rollbackException) {
                if (failure == null) {
                    failure = new IOException("World-operation rollback was incomplete");
                }
                failure.addSuppressed(rollbackException);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static void ensureBackupCapacity(Path holdingRoot, long estimatedBytes) throws IOException {
        Files.createDirectories(holdingRoot.getParent());
        FileStore store = Files.getFileStore(holdingRoot.getParent());
        // Moves on one filesystem need little extra space, but reserve room for metadata and a future manual copy.
        long requiredHeadroom = Math.min(estimatedBytes, 64L * 1024L * 1024L) + 16L * 1024L * 1024L;
        if (store.getUsableSpace() < requiredHeadroom) {
            throw new IOException("Not enough free disk space to stage a recoverable backup");
        }
    }

    private static void ensureHoldingMarker(Path holdingRoot, PendingWorldOperation operation) throws IOException {
        if (Files.isSymbolicLink(holdingRoot)) {
            throw new IOException("Refusing symbolic-link world-operation holding directory");
        }
        Files.createDirectories(holdingRoot);
        Path marker = holdingRoot.resolve("operation.json");
        if (Files.notExists(marker)) {
            try (Stream<Path> contents = Files.list(holdingRoot)) {
                if (contents.findAny().isPresent()) {
                    throw new IOException("Unidentified data already exists in the world-operation holding directory");
                }
            }
            writeJsonAtomically(marker, operation);
            return;
        }
        PendingWorldOperation staged = readPending(marker);
        if (!staged.equals(operation)) {
            throw new IOException("The staged world-operation marker does not match the pending operation");
        }
    }

    private static void moveDirectory(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, destination);
        }
    }

    private static void captureConfiguration(MinecraftServer server, Path holdingRoot) throws IOException {
        RestoreConfigSnapshot.capture(ConfigPaths.forServer(server).directory(), holdingRoot);
    }

    private static void archiveOperationRecord(MinecraftServer server, Applied applied) throws IOException {
        Path history = ConfigPaths.forServer(server).directory().resolve("world_operations");
        Files.createDirectories(history);
        Path destination = history.resolve(applied.operation().createdAtEpochMillis() + "-"
                + applied.operation().operationId() + ".json");
        try {
            Files.move(applied.pendingPath(), destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(applied.pendingPath(), destination);
        }
    }

    private static long estimateSize(List<Path> targets) {
        long total = 0;
        for (Path target : targets) {
            if (Files.notExists(target)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(target)) {
                total += paths.filter(Files::isRegularFile)
                        .mapToLong(path -> {
                            try {
                                return Files.size(path);
                            } catch (IOException ignored) {
                                return 0L;
                            }
                        })
                        .sum();
            } catch (IOException ignored) {
                return -1L;
            }
        }
        return total;
    }

    static int countMiningPlayersForOperation(MinecraftServer server) {
        int count = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (isMiningLevel(player.level().dimension())) {
                count++;
            }
        }
        return count;
    }

    static void evacuateMiningPlayersForOperation(MinecraftServer server) {
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            if (isMiningLevel(player.level().dimension())) {
                MiningPlayerSafety.evacuate(player);
            }
        }
    }

    private static boolean isMiningLevel(ResourceKey<Level> key) {
        return "delvefold".equals(key.location().getNamespace())
                && DIMENSION_PATHS.contains(key.location().getPath());
    }

    private static Path pendingPath(MinecraftServer server) {
        return ConfigPaths.forServer(server).directory().resolve("pending_world_operation.json");
    }

    private static Path saveRoot(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
    }

    private static String localized(String translationKey, @Nullable Object @Nullable ... arguments) {
        return AdminLocalizedMessage.encode(translationKey, arguments);
    }

    private static WorldSettingsDocument applyOperationToSettings(
            WorldSettingsDocument settings, PendingWorldOperation operation) {
        if (operation.type() == WorldOperationType.DELETE) {
            return settings.markDeleted(operation.operationId());
        }
        var targetTerrain = operation.targetTerrain();
        var targetVariant = operation.targetVariant();
        var targetGeologyTheme = operation.targetGeologyTheme();
        if (targetTerrain == null || targetVariant == null || targetGeologyTheme == null) {
            throw new IllegalStateException("Pending recreation is missing required world identity settings");
        }
        var configuredOrePreset = operation.targetOrePreset();
        var targetOrePreset = configuredOrePreset == null ? settings.orePreset() : configuredOrePreset;
        var configuredGameplayPreset = operation.targetGameplayPreset();
        var targetGameplayPreset =
                configuredGameplayPreset == null ? settings.gameplay().preset() : configuredGameplayPreset;
        return settings.recreate(
                targetTerrain,
                targetVariant,
                targetGeologyTheme,
                targetOrePreset,
                targetGameplayPreset,
                operation.operationId());
    }

    private static String randomToken() {
        byte[] bytes = new byte[4];
        SECURE_RANDOM.nextBytes(bytes);
        StringBuilder result = new StringBuilder(8);
        for (byte value : bytes) {
            result.append(String.format(Locale.ROOT, "%02X", value & 0xFF));
        }
        return result.toString();
    }

    private static boolean constantTimeEquals(String expected, String supplied) {
        if (expected == null || supplied == null) {
            return false;
        }
        return java.security.MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                supplied.toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    private static void writeJsonAtomically(Path target, Object value) throws IOException {
        byte[] bytes = (ConfigJson.GSON.toJson(value) + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
        Files.createDirectories(target.getParent());
        Path temporary =
                Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        boolean moved = false;
        try {
            try (FileChannel channel =
                    FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (Files.notExists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            List<Path> ordered = paths.sorted(Comparator.reverseOrder()).toList();
            for (Path path : ordered) {
                Files.delete(path);
            }
        }
    }

    private record Draft(
            Path saveRoot,
            String token,
            long expiresAtEpochMillis,
            PendingWorldOperation operation,
            long estimatedBytes,
            int playersToEvacuate) {}

    private record MoveRecord(Path source, Path destination, boolean movedThisStartup) {}

    private record Applied(
            PendingWorldOperation operation, Path pendingPath, Path holdingRoot, List<MoveRecord> moves) {}
}
