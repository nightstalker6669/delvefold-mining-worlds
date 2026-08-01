package com.nightsta69.delvefold.reset;

/** Small proof that the current manifest passed a complete content verification. */
public record BackupVerificationReceipt(
        int schemaVersion,
        String backupId,
        String manifestSha256,
        long manifestSizeBytes,
        long manifestLastModifiedEpochMillis,
        long verifiedAtEpochMillis,
        int fileCount,
        long totalBytes) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final String FILE_NAME = ".verification.json";

    public BackupVerificationReceipt {
        backupId = backupId == null ? "" : backupId;
        manifestSha256 = manifestSha256 == null ? "" : manifestSha256;
    }

    /** Source-compatible constructor for early 1.3 development callers. */
    public BackupVerificationReceipt(
            int schemaVersion,
            String backupId,
            String manifestSha256,
            long verifiedAtEpochMillis,
            int fileCount,
            long totalBytes) {
        this(schemaVersion, backupId, manifestSha256, -1L, -1L, verifiedAtEpochMillis, fileCount, totalBytes);
    }
}
