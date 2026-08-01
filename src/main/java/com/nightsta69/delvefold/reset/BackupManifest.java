package com.nightsta69.delvefold.reset;

import java.util.List;

/**
 * Immutable inventory of every restorable file in one Delvefold backup.
 *
 * @param schemaVersion manifest schema version, currently {@value #CURRENT_SCHEMA_VERSION}
 * @param hashAlgorithm digest algorithm applied to every file, currently {@value #HASH_ALGORITHM}
 * @param metadata normalized lifecycle metadata captured when the backup was created
 * @param totalBytes sum of nonnegative file sizes in bytes
 * @param files immutable normalized-path entries sorted by path
 */
public record BackupManifest(
        int schemaVersion, String hashAlgorithm, Metadata metadata, long totalBytes, List<FileEntry> files) {
    /** Current on-disk manifest schema. */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    /** Required cryptographic digest name for all manifest file entries. */
    public static final String HASH_ALGORITHM = "SHA-256";

    /** File name stored at the root of each manifest-backed backup. */
    public static final String FILE_NAME = "manifest.json";

    /**
     * Normalizes absent algorithm text and defensively snapshots file entries.
     *
     * @param schemaVersion manifest schema version
     * @param hashAlgorithm digest algorithm name
     * @param metadata lifecycle metadata
     * @param totalBytes sum of file sizes in bytes
     * @param files ordered file entries to snapshot
     */
    public BackupManifest {
        hashAlgorithm = hashAlgorithm == null ? "" : hashAlgorithm;
        files = files == null ? List.of() : List.copyOf(files);
    }

    /**
     * Redacted lifecycle metadata attached to a backup manifest.
     *
     * @param backupId normalized backup identifier
     * @param createdAtEpochMillis creation time in epoch milliseconds
     * @param operationId lifecycle operation UUID text
     * @param operation lifecycle operation type
     * @param terrain serialized terrain mode or {@code unknown}
     * @param requestedBy redacted requesting actor label
     */
    public record Metadata(
            String backupId,
            long createdAtEpochMillis,
            String operationId,
            String operation,
            String terrain,
            String requestedBy) {
        /**
         * Normalizes missing persisted text to stable, non-sensitive sentinel values.
         *
         * @param backupId normalized backup identifier
         * @param createdAtEpochMillis creation time in epoch milliseconds
         * @param operationId lifecycle operation UUID text
         * @param operation lifecycle operation type
         * @param terrain serialized terrain mode
         * @param requestedBy redacted actor label
         */
        public Metadata {
            backupId = backupId == null ? "" : backupId;
            operationId = operationId == null ? "" : operationId;
            operation = operation == null ? "unknown" : operation;
            terrain = terrain == null ? "unknown" : terrain;
            requestedBy = requestedBy == null ? "unknown" : requestedBy;
        }
    }

    /**
     * Expected state of one regular file under the backup root.
     *
     * @param path normalized forward-slash relative path with no traversal segments
     * @param sizeBytes expected file size in bytes
     * @param sha256 lowercase hexadecimal SHA-256 digest
     */
    public record FileEntry(String path, long sizeBytes, String sha256) {
        /**
         * Normalizes nullable persisted text while leaving validation to the manifest service.
         *
         * @param path normalized relative path
         * @param sizeBytes expected file size in bytes
         * @param sha256 lowercase hexadecimal digest
         */
        public FileEntry {
            path = path == null ? "" : path;
            sha256 = sha256 == null ? "" : sha256;
        }
    }
}
