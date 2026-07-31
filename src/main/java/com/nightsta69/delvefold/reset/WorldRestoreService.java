package com.nightsta69.delvefold.reset;

import com.mojang.logging.LogUtils;
import com.nightsta69.delvefold.config.ConfigJson;
import com.nightsta69.delvefold.config.ConfigPaths;
import com.nightsta69.delvefold.config.model.WorldSettingsDocument;
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
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

/** Restart-safe restore journal. The selected restore point is always copied, never consumed. */
public final class WorldRestoreService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final WorldRestoreService INSTANCE = new WorldRestoreService();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long CONFIRMATION_WINDOW_MILLIS = 5L * 60L * 1000L;
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss", Locale.ROOT)
            .withZone(ZoneOffset.UTC);
    private static final Set<String> DIMENSIONS = Set.of("delve_flat", "delve_cavern", "delve_wild");
    private final Object lock = new Object();
    private final AtomicBoolean entryBlocked = new AtomicBoolean(false);
    private Draft draft;

    private WorldRestoreService() {
    }

    public static WorldRestoreService get() {
        return INSTANCE;
    }

    public WorldOperationPreview request(MinecraftServer server, String backupId, String requestedBy) {
        synchronized (lock) {
            if (WorldOperationService.get().hasPending(server) || Files.exists(pendingPath(server))) {
                return WorldOperationPreview.rejected("Another world operation is already pending");
            }
            try {
                WorldBackupCatalog catalog = new WorldBackupCatalog(saveRoot(server));
                WorldBackupCatalog.BackupSummary summary = catalog.list().stream()
                        .filter(candidate -> candidate.id().equals(backupId)).findFirst()
                        .orElseThrow(() -> new IOException("Unknown backup: " + backupId));
                if (!summary.valid() || !summary.restorable()) {
                    return WorldOperationPreview.rejected("That backup does not contain a complete restorable configuration and dimension snapshot");
                }
                String token = randomToken();
                long expires = System.currentTimeMillis() + CONFIRMATION_WINDOW_MILLIS;
                PendingWorldRestore operation = new PendingWorldRestore(
                        PendingWorldRestore.CURRENT_SCHEMA_VERSION, UUID.randomUUID().toString(), backupId,
                        PendingWorldRestore.Phase.REQUESTED, System.currentTimeMillis(), requestedBy);
                draft = new Draft(saveRoot(server), token, expires, operation, summary.sizeBytes(),
                        WorldOperationService.countMiningPlayersForOperation(server));
                return new WorldOperationPreview(true,
                        "Restore preserves the selected backup and creates a pre-restore backup of the current world",
                        token, expires, summary.sizeBytes(), draft.players(), BackupMode.KEEP_BACKUP);
            } catch (IOException exception) {
                return WorldOperationPreview.rejected("Could not inspect backup: " + exception.getMessage());
            }
        }
    }

    public WorldOperationResult confirm(MinecraftServer server, String token) {
        synchronized (lock) {
            if (draft == null) {
                return WorldOperationResult.failure("No restore is awaiting confirmation");
            }
            if (!draft.saveRoot().equals(saveRoot(server)) || System.currentTimeMillis() > draft.expiresAt()) {
                draft = null;
                return WorldOperationResult.failure("The restore request expired or belonged to another save");
            }
            if (!constantTimeEquals(draft.token(), token)) {
                return WorldOperationResult.failure("The restore confirmation token is invalid");
            }
            try {
                writePending(pendingPath(server), draft.operation());
                entryBlocked.set(true);
                WorldOperationService.evacuateMiningPlayersForOperation(server);
                draft = null;
                return WorldOperationResult.success(server.isDedicatedServer()
                        ? "Restore scheduled. Restart the server to apply it."
                        : "Restore scheduled. Exit to title and reopen the save to apply it.");
            } catch (IOException exception) {
                return WorldOperationResult.failure("Could not schedule restore: " + exception.getMessage());
            }
        }
    }

    public WorldOperationResult cancel(MinecraftServer server) {
        synchronized (lock) {
            try {
                if (Files.exists(pendingPath(server))) {
                    Files.delete(pendingPath(server));
                    entryBlocked.set(false);
                    return WorldOperationResult.success("Pending restore cancelled before restart");
                }
                if (draft != null) {
                    draft = null;
                    return WorldOperationResult.success("Unconfirmed restore request cancelled");
                }
                return WorldOperationResult.failure("No restore is pending");
            } catch (IOException exception) {
                return WorldOperationResult.failure("Could not cancel restore: " + exception.getMessage());
            }
        }
    }

    public boolean isEntryBlocked() {
        return entryBlocked.get();
    }

    public void stop() {
        synchronized (lock) {
            draft = null;
            entryBlocked.set(false);
        }
    }

    /** Runs before configuration and dimensions are loaded. */
    public void prepareStartup(MinecraftServer server) {
        synchronized (lock) {
            Path pendingPath = pendingPath(server);
            if (Files.notExists(pendingPath)) {
                entryBlocked.set(false);
                return;
            }
            entryBlocked.set(true);
            try {
                if (WorldOperationService.get().hasPending(server)) {
                    throw new IOException("A delete/recreate operation and restore cannot be pending together");
                }
                PendingWorldRestore pending = readPending(pendingPath);
                Path selected = new WorldBackupCatalog(saveRoot(server)).resolve(pending.backupId());
                Path staging = stagingRoot(server, pending);
                Path preRestore = preRestoreRoot(server, pending);

                if (pending.phase() == PendingWorldRestore.Phase.REQUESTED) {
                    stageSelectedBackup(selected, staging);
                    pending = advance(pendingPath, pending, PendingWorldRestore.Phase.STAGED);
                }
                if (pending.phase() == PendingWorldRestore.Phase.STAGED) {
                    backupCurrent(server, preRestore, pending);
                    pending = advance(pendingPath, pending, PendingWorldRestore.Phase.CURRENT_BACKED_UP);
                }
                if (pending.phase() == PendingWorldRestore.Phase.CURRENT_BACKED_UP) {
                    installStaged(server, staging);
                    pending = advance(pendingPath, pending, PendingWorldRestore.Phase.RESTORED);
                }
                if (pending.phase() == PendingWorldRestore.Phase.RESTORED) {
                    deleteTree(staging);
                    archivePending(server, pendingPath, pending);
                    entryBlocked.set(false);
                }
            } catch (Exception exception) {
                LOGGER.error("Delvefold restore remains pending and will resume at the recorded phase", exception);
            }
        }
    }

    private static void stageSelectedBackup(Path selected, Path staging) throws IOException {
        if (Files.exists(staging.resolve(".complete"))) {
            return;
        }
        if (Files.exists(staging)) {
            deleteTree(staging);
        }
        Files.createDirectories(staging);
        copyTree(selected.resolve("dimensions/delvefold"), staging.resolve("dimensions/delvefold"), false);
        copyTree(selected.resolve("config/serverconfig/delvefold"),
                staging.resolve("config/serverconfig/delvefold"), false);
        Files.writeString(staging.resolve(".complete"), "complete\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private static void backupCurrent(MinecraftServer server, Path preRestore, PendingWorldRestore pending)
            throws IOException {
        Files.createDirectories(preRestore);
        Path marker = preRestore.resolve("operation.json");
        if (Files.notExists(marker)) {
            WorldSettingsDocument settings = readSettings(ConfigPaths.forServer(server).settings());
            PendingWorldOperation metadata = new PendingWorldOperation(PendingWorldOperation.CURRENT_SCHEMA_VERSION,
                    pending.operationId(), WorldOperationType.RECREATE, settings.terrainMode(), settings.terrainMode(),
                    settings.orePreset(), settings.gameplay().preset(), BackupMode.KEEP_BACKUP, false,
                    pending.createdAtEpochMillis(), pending.requestedBy());
            writeJson(marker, metadata);
        }
        Path configBackup = preRestore.resolve("config/serverconfig/delvefold");
        if (Files.notExists(configBackup)) {
            copyTree(ConfigPaths.forServer(server).directory(), configBackup, true);
        }
        Path activeRoot = dimensionsRoot(server);
        Path backupRoot = preRestore.resolve("dimensions/delvefold");
        Files.createDirectories(backupRoot);
        for (String dimension : DIMENSIONS) {
            Path active = activeRoot.resolve(dimension);
            Path backup = backupRoot.resolve(dimension);
            if (Files.exists(active) && Files.notExists(backup)) {
                if (Files.isSymbolicLink(active) || !Files.isDirectory(active)) {
                    throw new IOException("Active dimension failed safety checks: " + dimension);
                }
                move(active, backup);
            }
        }
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
        copyTree(staging.resolve("config/serverconfig/delvefold"),
                ConfigPaths.forServer(server).directory(), false);
    }

    private static void copyTree(Path sourceRoot, Path destinationRoot, boolean excludeOperationFiles)
            throws IOException {
        if (Files.isSymbolicLink(sourceRoot) || !Files.isDirectory(sourceRoot)) {
            throw new IOException("Restore source failed safety checks: " + sourceRoot.getFileName());
        }
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            for (Path source : paths.toList()) {
                if (Files.isSymbolicLink(source)) {
                    throw new IOException("Restore source contains a symbolic link");
                }
                Path relative = sourceRoot.relativize(source);
                if (excludeOperationFiles && relative.getNameCount() == 1
                        && (relative.toString().equals("pending_restore.json")
                        || relative.toString().equals("pending_world_operation.json")
                        || relative.toString().equals("config_transaction.json"))) {
                    continue;
                }
                Path destination = destinationRoot.resolve(relative).normalize();
                if (!destination.startsWith(destinationRoot.normalize())) {
                    throw new IOException("Restore path escaped destination");
                }
                if (Files.isDirectory(source)) {
                    Files.createDirectories(destination);
                } else if (Files.isRegularFile(source)) {
                    Files.createDirectories(destination.getParent());
                    Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING,
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

    private static WorldSettingsDocument readSettings(Path path) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path)) {
            throw new IOException("Active settings failed safety checks");
        }
        WorldSettingsDocument settings = ConfigJson.GSON.fromJson(Files.readString(path), WorldSettingsDocument.class);
        if (settings == null || !settings.initialized()) {
            throw new IOException("Active settings are not initialized");
        }
        return settings;
    }

    private static void writePending(Path path, PendingWorldRestore pending) throws IOException {
        writeJson(path, pending);
    }

    private static void writeJson(Path target, Object value) throws IOException {
        byte[] bytes = (ConfigJson.GSON.toJson(value) + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        boolean moved = false;
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
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
        move(pendingPath, history.resolve(pending.createdAtEpochMillis() + "-restore-" + pending.operationId() + ".json"));
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

    private static Path dimensionsRoot(MinecraftServer server) {
        return saveRoot(server).resolve("dimensions/delvefold").normalize();
    }

    private static Path pendingPath(MinecraftServer server) {
        return ConfigPaths.forServer(server).directory().resolve("pending_restore.json");
    }

    private static Path stagingRoot(MinecraftServer server, PendingWorldRestore pending) {
        return saveRoot(server).resolve(".delvefold_restore_staging").resolve(pending.operationId()).normalize();
    }

    private static Path preRestoreRoot(MinecraftServer server, PendingWorldRestore pending) {
        return saveRoot(server).resolve("delvefold_backups").resolve(
                TIMESTAMP.format(Instant.ofEpochMilli(pending.createdAtEpochMillis()))
                        + "-pre-restore-" + pending.operationId()).normalize();
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

    private record Draft(Path saveRoot, String token, long expiresAt, PendingWorldRestore operation,
                         long estimatedBytes, int players) {
    }
}
