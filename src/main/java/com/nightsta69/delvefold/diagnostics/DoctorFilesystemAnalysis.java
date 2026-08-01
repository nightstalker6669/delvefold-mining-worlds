package com.nightsta69.delvefold.diagnostics;

import com.nightsta69.delvefold.config.ConfigJson;
import com.nightsta69.delvefold.config.ConfigPaths;
import com.nightsta69.delvefold.reset.PendingWorldOperation;
import com.nightsta69.delvefold.reset.PendingWorldRestore;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * Performs the bounded, tolerant filesystem inspection used by the doctor report.
 *
 * <p>This class intentionally does not share the reset subsystem's authoritative journal reader. Doctor diagnostics
 * treat unsafe or malformed pending files as reportable state instead of failing lifecycle execution, and they retain
 * their own redacted fallback records and unknown-value sentinels.
 */
final class DoctorFilesystemAnalysis {
    static final int MAX_DISK_ESTIMATE_ENTRIES = 100_000;
    static final long MAX_PENDING_BYTES = 64L * 1024L;
    private static final long MEBIBYTE = 1024L * 1024L;

    private DoctorFilesystemAnalysis() {}

    /**
     * Reads both optional lifecycle journals using the doctor's tolerant safety policy.
     *
     * @param configDirectory Delvefold's server configuration directory
     * @return immutable redacted pending-operation state and referenced backup IDs
     */
    static PendingJournalScan scanPending(Path configDirectory) {
        Path directory = configDirectory.toAbsolutePath().normalize();
        List<DoctorReport.PendingOperationStatus> statuses = new ArrayList<>();
        Set<String> backupIds = new HashSet<>();
        readPendingWorldOperation(directory.resolve("pending_world_operation.json"), statuses);
        readPendingRestore(directory.resolve("pending_restore.json"), statuses, backupIds);
        return new PendingJournalScan(statuses, backupIds);
    }

    private static void readPendingWorldOperation(Path path, List<DoctorReport.PendingOperationStatus> statuses) {
        if (Files.notExists(path)) {
            return;
        }
        try {
            @Nullable PendingWorldOperation pending = readBounded(path, PendingWorldOperation.class);
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
            @Nullable PendingWorldRestore pending = readBounded(path, PendingWorldRestore.class);
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

    private static <T> @Nullable T readBounded(Path path, Class<T> type) throws IOException {
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
            // Invalid optional metadata retains the documented unknown-time sentinel.
        }
        return new DoctorReport.PendingOperationStatus(id, operation, "invalid", modified);
    }

    private static boolean validOperationId(@Nullable String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException | NullPointerException exception) {
            return false;
        }
    }

    private static boolean validBackupId(@Nullable String value) {
        return value != null && value.matches("[a-zA-Z0-9_.-]{1,200}");
    }

    /**
     * Estimates regular-file bytes below a safe directory without following symbolic links.
     *
     * @param root directory to inspect, or {@code null} to report an unavailable estimate
     * @param maximumEntries maximum number of filesystem entries, including the root
     * @return the saturated byte count, {@code 0} for a missing root, or {@code -1} when unsafe or unavailable
     */
    static long estimateTreeBytes(@Nullable Path root, int maximumEntries) {
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

    /**
     * Estimates the bytes copied by a new world backup from dimension and active configuration files.
     *
     * @param saveRoot normalized or unnormalized server save root
     * @param paths active Delvefold configuration paths
     * @return the saturated byte estimate, or {@code -1} when a path is unsafe or unavailable
     */
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

    /**
     * Produces advisory disk-space values without turning unavailable platform data into a report failure.
     *
     * @param saveRoot server save root used to find its backing file store
     * @param paths active Delvefold configuration paths
     * @param backupBytes known catalog bytes, or a negative unknown sentinel
     * @return normalized usable-space, backup-size, next-backup, and headroom estimates
     */
    static DoctorReport.DiskEstimate diskEstimate(Path saveRoot, ConfigPaths paths, long backupBytes) {
        long usable = -1L;
        try {
            FileStore store = Files.getFileStore(saveRoot);
            usable = Math.max(0L, store.getUsableSpace());
        } catch (IOException | RuntimeException ignored) {
            // Disk-space reporting is advisory; unavailable values retain the -1 sentinel.
        }
        long nextBackup = estimateCurrentBackupBytes(saveRoot, paths);
        long headroom = nextBackup < 0L ? -1L : Math.min(nextBackup, 64L * MEBIBYTE) + 16L * MEBIBYTE;
        return new DoctorReport.DiskEstimate(usable, Math.max(-1L, backupBytes), nextBackup, headroom);
    }

    /**
     * Creates and verifies the one-level contained directory used for redacted doctor exports.
     *
     * @param paths active Delvefold configuration paths
     * @return the normalized absolute exports directory
     * @throws IOException when the configured location escapes containment or is not a safe directory
     */
    static Path ensureExportsDirectory(ConfigPaths paths) throws IOException {
        Path directory = paths.directory().toAbsolutePath().normalize();
        Path exports = paths.exports().toAbsolutePath().normalize();
        @Nullable Path parent = exports.getParent();
        if (!exports.startsWith(directory)
                || exports.equals(directory)
                || parent == null
                || !parent.equals(directory)) {
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

    /** Returns the sum of two nonnegative byte counts, saturated at {@link Long#MAX_VALUE}. */
    static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    /** Returns the sum of two normalized nonnegative counts, saturated at {@link Integer#MAX_VALUE}. */
    static int saturatingCount(int first, int second) {
        long result = (long) Math.max(0, first) + Math.max(0, second);
        return (int) Math.min(Integer.MAX_VALUE, result);
    }

    /** Immutable result of the doctor's tolerant pending-journal scan. */
    record PendingJournalScan(List<DoctorReport.PendingOperationStatus> statuses, Set<String> pendingBackupIds) {
        PendingJournalScan {
            statuses = List.copyOf(statuses);
            pendingBackupIds = Set.copyOf(pendingBackupIds);
        }
    }
}
