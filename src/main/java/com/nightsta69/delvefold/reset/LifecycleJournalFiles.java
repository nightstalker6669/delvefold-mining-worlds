package com.nightsta69.delvefold.reset;

import com.nightsta69.delvefold.config.ConfigJson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Canonical paths and authoritative typed readers for restart-bound lifecycle journals. */
final class LifecycleJournalFiles {
    static final String PENDING_WORLD_OPERATION_FILE_NAME = "pending_world_operation.json";
    static final String PENDING_RESTORE_FILE_NAME = "pending_restore.json";
    static final String OPERATION_MARKER_FILE_NAME = "operation.json";
    static final int MAX_JOURNAL_BYTES = 64 * 1024;

    private LifecycleJournalFiles() {}

    /**
     * Resolves the delete-or-recreate journal below a configuration directory.
     *
     * @param configDirectory configuration directory
     * @return the unresolved child journal path
     */
    static Path pendingWorldOperation(Path configDirectory) {
        return configDirectory.resolve(PENDING_WORLD_OPERATION_FILE_NAME);
    }

    /**
     * Resolves the restore journal below a configuration directory.
     *
     * @param configDirectory configuration directory
     * @return the unresolved child journal path
     */
    static Path pendingRestore(Path configDirectory) {
        return configDirectory.resolve(PENDING_RESTORE_FILE_NAME);
    }

    /**
     * Resolves the operation marker below a lifecycle backup directory.
     *
     * @param lifecycleDirectory backup or holding directory
     * @return the unresolved child marker path
     */
    static Path operationMarker(Path lifecycleDirectory) {
        return lifecycleDirectory.resolve(OPERATION_MARKER_FILE_NAME);
    }

    /**
     * Reads and validates the authoritative delete-or-recreate journal.
     *
     * @param path journal path
     * @return parsed operation
     * @throws IOException if the file fails safety checks or uses an unsupported schema
     * @throws IllegalArgumentException if its operation ID is not valid UUID text
     */
    static PendingWorldOperation readWorldOperation(Path path) throws IOException {
        requireReadable(path, "Pending operation file failed safety checks");
        PendingWorldOperation operation =
                ConfigJson.GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), PendingWorldOperation.class);
        if (operation == null || operation.schemaVersion() != PendingWorldOperation.CURRENT_SCHEMA_VERSION) {
            throw new IOException("Pending operation has an unsupported schema");
        }
        UUID.fromString(operation.operationId());
        return operation;
    }

    /**
     * Reads and validates the authoritative restore journal.
     *
     * @param path journal path
     * @return parsed restore operation
     * @throws IOException if the file fails safety checks or uses an unsupported schema
     * @throws IllegalArgumentException if its operation ID is not valid UUID text
     */
    static PendingWorldRestore readRestore(Path path) throws IOException {
        requireReadable(path, "Pending restore failed safety checks");
        PendingWorldRestore pending = ConfigJson.GSON.fromJson(Files.readString(path), PendingWorldRestore.class);
        if (pending == null || pending.schemaVersion() != PendingWorldRestore.CURRENT_SCHEMA_VERSION) {
            throw new IOException("Pending restore schema is unsupported");
        }
        UUID.fromString(pending.operationId());
        return pending;
    }

    private static void requireReadable(Path path, String failureMessage) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path) || Files.size(path) > MAX_JOURNAL_BYTES) {
            throw new IOException(failureMessage);
        }
    }
}
