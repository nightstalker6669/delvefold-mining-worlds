package com.nightsta69.delvefold.reset;

import java.util.List;

/** Immutable inventory of every restorable file in one Delvefold backup. */
public record BackupManifest(
        int schemaVersion, String hashAlgorithm, Metadata metadata, long totalBytes, List<FileEntry> files) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final String HASH_ALGORITHM = "SHA-256";
    public static final String FILE_NAME = "manifest.json";

    public BackupManifest {
        hashAlgorithm = hashAlgorithm == null ? "" : hashAlgorithm;
        files = files == null ? List.of() : List.copyOf(files);
    }

    public record Metadata(
            String backupId,
            long createdAtEpochMillis,
            String operationId,
            String operation,
            String terrain,
            String requestedBy) {
        public Metadata {
            backupId = backupId == null ? "" : backupId;
            operationId = operationId == null ? "" : operationId;
            operation = operation == null ? "unknown" : operation;
            terrain = terrain == null ? "unknown" : terrain;
            requestedBy = requestedBy == null ? "unknown" : requestedBy;
        }
    }

    public record FileEntry(String path, long sizeBytes, String sha256) {
        public FileEntry {
            path = path == null ? "" : path;
            sha256 = sha256 == null ? "" : sha256;
        }
    }
}
