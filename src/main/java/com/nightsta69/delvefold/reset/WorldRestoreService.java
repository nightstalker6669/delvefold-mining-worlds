package com.nightsta69.delvefold.reset;

import com.mojang.logging.LogUtils;
import com.nightsta69.delvefold.admin.AdminLocalizedMessage;
import com.nightsta69.delvefold.audit.AuditMutation;
import com.nightsta69.delvefold.audit.DelvefoldAuditService;
import com.nightsta69.delvefold.config.ConfigJson;
import com.nightsta69.delvefold.config.ConfigPaths;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Coordinates confirmation-gated, restart-safe restoration of a mining-world backup.
 *
 * <p>The selected restore point is always copied and is never consumed. Confirmation writes a journal, blocks portal
 * entry, and evacuates players; dimension and configuration files are changed only during the next startup before
 * levels load. Public request methods coordinate with backup deletion so a selected backup cannot disappear between
 * selection and reservation.
 */
public final class WorldRestoreService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final WorldRestoreService INSTANCE = new WorldRestoreService();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long CONFIRMATION_WINDOW_MILLIS = 5L * 60L * 1000L;
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss", Locale.ROOT).withZone(ZoneOffset.UTC);
    private static final List<String> DIMENSIONS = DelvefoldDimensionFolders.ALL;
    private final Object lock = new Object();
    private final AtomicBoolean entryBlocked = new AtomicBoolean(false);
    private @Nullable Draft draft;

    private WorldRestoreService() {}

    /**
     * Returns the process-wide restore coordinator.
     *
     * @return the singleton service
     */
    public static WorldRestoreService get() {
        return INSTANCE;
    }

    /**
     * Creates a restore preview after resolving the backup from the on-disk catalog.
     *
     * <p>A backup must have a current manifest and successful verification receipt before it is restorable. A
     * successful preview contains a one-use confirmation token valid for five minutes in this JVM. This method does not
     * alter the backup or active world. Callers are responsible for enforcing the world-management permission.
     *
     * @param server the server whose normalized save root owns the backup catalog
     * @param backupId the catalog's logical backup ID, not a filesystem path
     * @param requestedBy the actor name persisted for auditing and restart recovery
     * @return an accepted preview, or a localized rejection if the backup is unknown, unverified, unsafe, busy, or
     *     cannot be inspected
     */
    public WorldOperationPreview request(MinecraftServer server, String backupId, String requestedBy) {
        return BackupDeletionGuard.get()
                .coordinateRestoreRequest(
                        server,
                        backupId,
                        () -> LifecycleOperationCoordinator.coordinate(
                                () -> requestUncoordinated(server, backupId, requestedBy)),
                        () -> WorldOperationPreview.rejected(
                                localized("message.delvefold.backup_delete.in_progress", backupId)));
    }

    private WorldOperationPreview requestUncoordinated(MinecraftServer server, String backupId, String requestedBy) {
        synchronized (lock) {
            if (LifecycleOperationCoordinator.conflictingOperationExists(
                            server, LifecycleOperationCoordinator.Kind.RESTORE)
                    || Files.exists(pendingPath(server))) {
                return WorldOperationPreview.rejected(localized("message.delvefold.restore.already_pending"));
            }
            try {
                WorldBackupCatalog catalog = new WorldBackupCatalog(saveRoot(server));
                WorldBackupCatalog.BackupSummary summary = catalog.list().stream()
                        .filter(candidate -> candidate.id().equals(backupId))
                        .findFirst()
                        .orElse(null);
                if (summary == null) {
                    return WorldOperationPreview.rejected(
                            localized("message.delvefold.restore.unknown_backup", backupId));
                }
                if (!summary.valid() || !summary.restorable()) {
                    return WorldOperationPreview.rejected(
                            summary.legacy()
                                    ? localized("message.delvefold.restore.legacy_requires_validation")
                                    : localized("message.delvefold.restore.manifest_required"));
                }
                String token = randomToken();
                long expires = System.currentTimeMillis() + CONFIRMATION_WINDOW_MILLIS;
                PendingWorldRestore operation = new PendingWorldRestore(
                        PendingWorldRestore.CURRENT_SCHEMA_VERSION,
                        UUID.randomUUID().toString(),
                        backupId,
                        PendingWorldRestore.Phase.REQUESTED,
                        System.currentTimeMillis(),
                        requestedBy);
                Draft createdDraft = new Draft(
                        saveRoot(server),
                        token,
                        expires,
                        operation,
                        summary.sizeBytes(),
                        WorldOperationService.countMiningPlayersForOperation(server));
                draft = createdDraft;
                return new WorldOperationPreview(
                        true,
                        localized("message.delvefold.restore.preview"),
                        token,
                        expires,
                        summary.sizeBytes(),
                        createdDraft.players(),
                        BackupMode.KEEP_BACKUP);
            } catch (IOException exception) {
                return WorldOperationPreview.rejected(
                        localized("message.delvefold.restore.inspect_failed", exception.getMessage()));
            }
        }
    }

    /**
     * Creates a restore preview from a server-authoritative cached catalog summary.
     *
     * <p>This GUI-oriented path avoids parsing the catalog on the server thread. The summary must be non-null, match
     * {@code backupId}, and report a valid, restorable backup. The same deletion reservation, conflict checks,
     * confirmation window, and caller-enforced permission requirements as {@link #request(MinecraftServer, String,
     * String)} apply.
     *
     * @param server the server whose normalized save root owns the backup
     * @param backupId the requested logical backup ID
     * @param requestedBy the actor name persisted for auditing and restart recovery
     * @param summary the matching cached summary, or {@code null} if the cache no longer contains the backup
     * @return an accepted preview, or a localized rejection without a confirmation token
     */
    public WorldOperationPreview requestCached(
            MinecraftServer server,
            String backupId,
            String requestedBy,
            WorldBackupCatalog.@Nullable BackupSummary summary) {
        return BackupDeletionGuard.get()
                .coordinateRestoreRequest(
                        server,
                        backupId,
                        () -> LifecycleOperationCoordinator.coordinate(
                                () -> requestCachedUncoordinated(server, backupId, requestedBy, summary)),
                        () -> WorldOperationPreview.rejected(
                                localized("message.delvefold.backup_delete.in_progress", backupId)));
    }

    private WorldOperationPreview requestCachedUncoordinated(
            MinecraftServer server,
            String backupId,
            String requestedBy,
            WorldBackupCatalog.@Nullable BackupSummary summary) {
        synchronized (lock) {
            if (LifecycleOperationCoordinator.conflictingOperationExists(
                            server, LifecycleOperationCoordinator.Kind.RESTORE)
                    || Files.exists(pendingPath(server))) {
                return WorldOperationPreview.rejected(localized("message.delvefold.restore.already_pending"));
            }
            if (summary == null || !summary.id().equals(backupId)) {
                return WorldOperationPreview.rejected(localized("message.delvefold.restore.unknown_backup", backupId));
            }
            if (!summary.valid() || !summary.restorable()) {
                return WorldOperationPreview.rejected(
                        summary.legacy()
                                ? localized("message.delvefold.restore.legacy_requires_validation")
                                : localized("message.delvefold.restore.manifest_required"));
            }
            String token = randomToken();
            long expires = System.currentTimeMillis() + CONFIRMATION_WINDOW_MILLIS;
            PendingWorldRestore operation = new PendingWorldRestore(
                    PendingWorldRestore.CURRENT_SCHEMA_VERSION,
                    UUID.randomUUID().toString(),
                    backupId,
                    PendingWorldRestore.Phase.REQUESTED,
                    System.currentTimeMillis(),
                    requestedBy);
            Draft createdDraft = new Draft(
                    saveRoot(server),
                    token,
                    expires,
                    operation,
                    summary.sizeBytes(),
                    WorldOperationService.countMiningPlayersForOperation(server));
            draft = createdDraft;
            return new WorldOperationPreview(
                    true,
                    localized("message.delvefold.restore.preview"),
                    token,
                    expires,
                    summary.sizeBytes(),
                    createdDraft.players(),
                    BackupMode.KEEP_BACKUP);
        }
    }

    /**
     * Confirms the current restore preview and persists its restart journal.
     *
     * <p>Successful confirmation blocks portal entry and evacuates players, but does not modify loaded dimension
     * folders. The token is compared without early-exit timing and is never persisted.
     *
     * @param server the server for which the preview was created
     * @param token the confirmation token supplied by the authorized caller
     * @return a successful scheduling result, or a localized failure when the draft is absent, expired, mismatched,
     *     conflicting, or cannot be journaled
     */
    public WorldOperationResult confirm(MinecraftServer server, String token) {
        return LifecycleOperationCoordinator.coordinate(() -> confirmCoordinated(server, token));
    }

    private WorldOperationResult confirmCoordinated(MinecraftServer server, String token) {
        synchronized (lock) {
            @Nullable Draft currentDraft = draft;
            if (currentDraft == null) {
                return WorldOperationResult.failure(localized("message.delvefold.restore.confirm.none"));
            }
            if (!currentDraft.saveRoot().equals(saveRoot(server))
                    || System.currentTimeMillis() > currentDraft.expiresAt()) {
                draft = null;
                return WorldOperationResult.failure(localized("message.delvefold.restore.confirm.expired_or_mismatch"));
            }
            if (!constantTimeEquals(currentDraft.token(), token)) {
                return WorldOperationResult.failure(localized("message.delvefold.restore.confirm.invalid"));
            }
            if (LifecycleOperationCoordinator.conflictingOperationExists(
                    server, LifecycleOperationCoordinator.Kind.RESTORE)) {
                return WorldOperationResult.failure(localized("message.delvefold.restore.already_pending"));
            }
            try {
                writePending(pendingPath(server), currentDraft.operation());
                entryBlocked.set(true);
                WorldOperationService.evacuateMiningPlayersForOperation(server);
                draft = null;
                return WorldOperationResult.success(
                        server.isDedicatedServer()
                                ? localized("message.delvefold.restore.scheduled.dedicated")
                                : localized("message.delvefold.restore.scheduled.integrated"));
            } catch (IOException exception) {
                return WorldOperationResult.failure(
                        localized("message.delvefold.restore.schedule_failed", exception.getMessage()));
            }
        }
    }

    /**
     * Cancels an unconfirmed restore preview or a confirmed journal before restart processing begins.
     *
     * <p>Cancellation never deletes the selected backup. A persisted journal is removed and its entry block is released
     * only if deletion succeeds.
     *
     * @param server the server that owns the draft or pending journal
     * @return the cancellation result, including a localized failure when nothing is pending or journal deletion fails
     */
    public WorldOperationResult cancel(MinecraftServer server) {
        return LifecycleOperationCoordinator.coordinate(() -> cancelCoordinated(server));
    }

    private WorldOperationResult cancelCoordinated(MinecraftServer server) {
        synchronized (lock) {
            try {
                if (Files.exists(pendingPath(server))) {
                    Files.delete(pendingPath(server));
                    entryBlocked.set(false);
                    return WorldOperationResult.success(localized("message.delvefold.restore.cancel.pending_success"));
                }
                if (draft != null) {
                    draft = null;
                    return WorldOperationResult.success(
                            localized("message.delvefold.restore.cancel.unconfirmed_success"));
                }
                return WorldOperationResult.failure(localized("message.delvefold.restore.cancel.none"));
            } catch (IOException exception) {
                return WorldOperationResult.failure(
                        localized("message.delvefold.restore.cancel.failed", exception.getMessage()));
            }
        }
    }

    /**
     * Reports whether a confirmed or startup-active restore blocks portal entry.
     *
     * @return {@code true} while restore entry is blocked
     */
    public boolean isEntryBlocked() {
        return entryBlocked.get();
    }

    /**
     * Tests whether the active save contains a persisted restore journal.
     *
     * @param server the server whose configuration directory is inspected
     * @return {@code true} when the pending-restore path exists
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
                    || System.currentTimeMillis() > currentDraft.expiresAt()) {
                draft = null;
                return false;
            }
            return true;
        }
    }

    /**
     * Returns the logical backup ID selected by the current draft or persisted restore.
     *
     * <p>The fallback value {@code pending_restore} is returned when a journal exists but cannot be safely read,
     * keeping audit output useful without exposing a filesystem path or damaged contents.
     *
     * @param server the server whose draft or journal is inspected
     * @return the selected logical ID, or the redacted fallback value when no readable selection is available
     */
    public String selectedBackupId(MinecraftServer server) {
        synchronized (lock) {
            Path root = saveRoot(server);
            @Nullable Draft currentDraft = draft;
            if (currentDraft != null && currentDraft.saveRoot().equals(root)) {
                return currentDraft.operation().backupId();
            }
            Path pending = pendingPath(server);
            if (Files.exists(pending)) {
                try {
                    return readPending(pending).backupId();
                } catch (IOException ignored) {
                    // Cancellation remains available for a damaged journal; the audit ID stays redacted.
                }
            }
            return "pending_restore";
        }
    }

    /**
     * Returns backup IDs that retention must protect while a restore is drafted or pending.
     *
     * <p>The immutable result includes the selected backup and, once journaled, the deterministic pre-restore backup
     * ID. It never contains absolute filesystem paths.
     *
     * @param server the server whose draft and persisted journal are inspected
     * @return an immutable set of protected logical backup IDs
     * @throws IOException if an existing pending journal cannot be safely read or validated
     */
    public Set<String> referencedBackupIds(MinecraftServer server) throws IOException {
        synchronized (lock) {
            Set<String> result = new LinkedHashSet<>();
            Path root = saveRoot(server);
            @Nullable Draft currentDraft = draft;
            if (currentDraft != null && currentDraft.saveRoot().equals(root)) {
                result.add(currentDraft.operation().backupId());
            }
            Path pending = pendingPath(server);
            if (Files.exists(pending)) {
                PendingWorldRestore restore = readPending(pending);
                result.add(restore.backupId());
                result.add(preRestoreRoot(server, restore).getFileName().toString());
            }
            return Set.copyOf(result);
        }
    }

    /**
     * Clears JVM-local draft and entry-block state when the server stops.
     *
     * <p>Persisted journals, staging data, and backups remain intact for restart recovery.
     */
    public void stop() {
        LifecycleOperationCoordinator.coordinate(() -> {
            synchronized (lock) {
                draft = null;
                entryBlocked.set(false);
            }
        });
    }

    /**
     * Executes or resumes a journaled restore before configuration and dimensions load.
     *
     * <p>The method validates the selected backup and its manifest, copies it into operation-specific staging, creates
     * a verified pre-restore backup of the current managed data, installs staged dimensions and configuration, and
     * advances the journal after each durable phase. Symbolic links, path escapes, missing managed data, corrupt
     * manifests, and incomplete filesystem transactions fail closed. The original selected backup is never modified.
     *
     * @param server the starting server whose save root owns the restore journal and backup catalog
     * @throws IllegalStateException if verification, staging, current-world backup, installation, rollback, manifest
     *     creation, or journal archival cannot complete safely
     */
    public void prepareStartup(MinecraftServer server) {
        synchronized (lock) {
            Path pendingPath = pendingPath(server);
            if (Files.notExists(pendingPath)) {
                entryBlocked.set(false);
                return;
            }
            entryBlocked.set(true);
            @Nullable PendingWorldRestore pending = null;
            try {
                if (WorldOperationService.get().hasPending(server)) {
                    throw new IOException("A delete/recreate operation and restore cannot be pending together");
                }
                pending = readPending(pendingPath);
                Path staging = stagingRoot(server, pending);
                Path preRestore = preRestoreRoot(server, pending);

                if (pending.phase() != PendingWorldRestore.Phase.RESTORED) {
                    Path selected = new WorldBackupCatalog(saveRoot(server)).resolve(pending.backupId());
                    new BackupManifestService().verify(selected);
                    if (pending.phase() == PendingWorldRestore.Phase.REQUESTED) {
                        stageSelectedBackup(selected, staging);
                        pending = advance(pendingPath, pending, PendingWorldRestore.Phase.STAGED);
                    }
                    if (pending.phase() == PendingWorldRestore.Phase.STAGED) {
                        backupCurrent(server, preRestore, pending);
                        auditBackupManifest(
                                pending.requestedBy(), preRestore.getFileName().toString());
                        pending = advance(pendingPath, pending, PendingWorldRestore.Phase.CURRENT_BACKED_UP);
                    }
                    if (pending.phase() == PendingWorldRestore.Phase.CURRENT_BACKED_UP) {
                        installStaged(server, staging);
                        pending = advance(pendingPath, pending, PendingWorldRestore.Phase.RESTORED);
                    }
                }
                if (pending.phase() == PendingWorldRestore.Phase.RESTORED) {
                    entryBlocked.set(false);
                    try {
                        deleteTree(staging);
                        archivePending(server, pendingPath, pending);
                    } catch (IOException cleanupFailure) {
                        LOGGER.warn(
                                "Delvefold restored the selected backup but could not finish cleanup; "
                                        + "cleanup will retry at the next startup",
                                cleanupFailure);
                    }
                }
            } catch (Exception exception) {
                LOGGER.error("Delvefold restore remains pending and will resume at the recorded phase", exception);
                throw new IllegalStateException(
                        "Delvefold could not safely complete its pending restore; startup was stopped", exception);
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

    private static void stageSelectedBackup(Path selected, Path staging) throws IOException {
        if (Files.isSymbolicLink(staging)) {
            throw new IOException("Restore staging root failed safety checks");
        }
        Path complete = staging.resolve(".complete");
        if (Files.exists(complete) || Files.isSymbolicLink(complete)) {
            if (Files.isSymbolicLink(complete) || !Files.isRegularFile(complete)) {
                throw new IOException("Restore staging completion marker failed safety checks");
            }
            return;
        }
        if (Files.exists(staging)) {
            deleteTree(staging);
        }
        Files.createDirectories(staging);
        copyTree(selected.resolve("dimensions/delvefold"), staging.resolve("dimensions/delvefold"));
        RestoreConfigSnapshot.install(
                selected.resolve("config/serverconfig/delvefold"), staging.resolve("config/serverconfig/delvefold"));
        Files.writeString(
                staging.resolve(".complete"),
                "complete\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
    }

    private static void backupCurrent(MinecraftServer server, Path preRestore, PendingWorldRestore pending)
            throws IOException {
        backupCurrent(ConfigPaths.forServer(server).directory(), dimensionsRoot(server), preRestore, pending);
    }

    /** Path-based transactional seam used by startup recovery tests. */
    static void backupCurrent(Path configDirectory, Path activeRoot, Path preRestore, PendingWorldRestore pending)
            throws IOException {
        RestoreCurrentBackupTransaction.backup(configDirectory, activeRoot, preRestore, pending);
    }

    private static void installStaged(MinecraftServer server, Path staging) throws IOException {
        Path stagedDimensions = staging.resolve("dimensions/delvefold");
        Path activeDimensions = dimensionsRoot(server);
        Files.createDirectories(activeDimensions);
        for (String dimension : DIMENSIONS) {
            Path staged = stagedDimensions.resolve(dimension);
            Path active = activeDimensions.resolve(dimension);
            if (Files.exists(staged)) {
                if (Files.exists(active)) {
                    throw new IOException("Restore target already exists unexpectedly: " + dimension);
                }
                move(staged, active);
            }
        }
        RestoreConfigSnapshot.install(
                staging.resolve("config/serverconfig/delvefold"),
                ConfigPaths.forServer(server).directory());
    }

    private static void copyTree(Path sourceRoot, Path destinationRoot) throws IOException {
        if (Files.isSymbolicLink(sourceRoot) || !Files.isDirectory(sourceRoot)) {
            throw new IOException("Restore source failed safety checks: " + sourceRoot.getFileName());
        }
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            for (Path source : paths.toList()) {
                if (Files.isSymbolicLink(source)) {
                    throw new IOException("Restore source contains a symbolic link");
                }
                Path relative = sourceRoot.relativize(source);
                Path destination = destinationRoot.resolve(relative).normalize();
                if (!destination.startsWith(destinationRoot.normalize())) {
                    throw new IOException("Restore path escaped destination");
                }
                if (Files.isDirectory(source)) {
                    Files.createDirectories(destination);
                } else if (Files.isRegularFile(source)) {
                    Files.createDirectories(destination.getParent());
                    Files.copy(
                            source,
                            destination,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES);
                } else {
                    throw new IOException("Restore source contains a non-regular file");
                }
            }
        }
    }

    private static PendingWorldRestore advance(
            Path pendingPath, PendingWorldRestore pending, PendingWorldRestore.Phase phase) throws IOException {
        PendingWorldRestore replacement = pending.withPhase(phase);
        writePending(pendingPath, replacement);
        return replacement;
    }

    private static PendingWorldRestore readPending(Path path) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path) || Files.size(path) > 64 * 1024) {
            throw new IOException("Pending restore failed safety checks");
        }
        PendingWorldRestore pending = ConfigJson.GSON.fromJson(Files.readString(path), PendingWorldRestore.class);
        if (pending == null || pending.schemaVersion() != PendingWorldRestore.CURRENT_SCHEMA_VERSION) {
            throw new IOException("Pending restore schema is unsupported");
        }
        UUID.fromString(pending.operationId());
        return pending;
    }

    private static void writePending(Path path, PendingWorldRestore pending) throws IOException {
        writeJson(path, pending);
    }

    private static void writeJson(Path target, Object value) throws IOException {
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

    private static void archivePending(MinecraftServer server, Path pendingPath, PendingWorldRestore pending)
            throws IOException {
        Path history = ConfigPaths.forServer(server).directory().resolve("world_operations");
        Files.createDirectories(history);
        move(
                pendingPath,
                history.resolve(pending.createdAtEpochMillis() + "-restore-" + pending.operationId() + ".json"));
    }

    private static void move(Path source, Path destination) throws IOException {
        Files.createDirectories(destination.getParent());
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, destination);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (Files.notExists(root)) {
            return;
        }
        if (Files.isSymbolicLink(root)) {
            throw new IOException("Refusing to delete symbolic-link restore staging");
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private static Path saveRoot(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
    }

    private static String localized(String translationKey, @Nullable Object @Nullable ... arguments) {
        return AdminLocalizedMessage.encode(translationKey, arguments);
    }

    private static Path dimensionsRoot(MinecraftServer server) {
        return saveRoot(server).resolve("dimensions/delvefold").normalize();
    }

    private static Path pendingPath(MinecraftServer server) {
        return ConfigPaths.forServer(server).directory().resolve("pending_restore.json");
    }

    private static Path stagingRoot(MinecraftServer server, PendingWorldRestore pending) {
        return saveRoot(server)
                .resolve(".delvefold_restore_staging")
                .resolve(pending.operationId())
                .normalize();
    }

    private static Path preRestoreRoot(MinecraftServer server, PendingWorldRestore pending) {
        return saveRoot(server)
                .resolve("delvefold_backups")
                .resolve(TIMESTAMP.format(Instant.ofEpochMilli(pending.createdAtEpochMillis())) + "-pre-restore-"
                        + pending.operationId())
                .normalize();
    }

    /**
     * Returns the canonical managed dimension-folder names used by both backup and restore.
     *
     * @return an immutable ordered list of save-root-relative folder names, never absolute paths
     */
    public static List<String> managedDimensionFolders() {
        return DIMENSIONS;
    }

    private static String randomToken() {
        byte[] bytes = new byte[12];
        RANDOM.nextBytes(bytes);
        return java.util.HexFormat.of().formatHex(bytes);
    }

    private static boolean constantTimeEquals(String expected, String supplied) {
        byte[] left = expected.getBytes(StandardCharsets.UTF_8);
        byte[] right = (supplied == null ? "" : supplied).getBytes(StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(left, right);
    }

    private record Draft(
            Path saveRoot,
            String token,
            long expiresAt,
            PendingWorldRestore operation,
            long estimatedBytes,
            int players) {}
}
