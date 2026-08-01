package com.nightsta69.delvefold.reset;

/**
 * Small proof that the current manifest passed a complete content verification.
 *
 * <p>A receipt is trusted only after its manifest digest, size, modification time, entry count, and total bytes still
 * match the current manifest. It is a fast listing hint; restoration performs full verification again.
 *
 * @param schemaVersion receipt schema, currently {@value #CURRENT_SCHEMA_VERSION}
 * @param backupId normalized backup directory identifier
 * @param manifestSha256 lowercase hexadecimal digest of the manifest file
 * @param manifestSizeBytes manifest size in bytes, or {@code -1} in the legacy constructor
 * @param manifestLastModifiedEpochMillis manifest modification time in epoch milliseconds, or {@code -1} when unknown
 * @param verifiedAtEpochMillis successful verification time in epoch milliseconds
 * @param fileCount number of manifest entries verified
 * @param totalBytes total verified content bytes
 */
public record BackupVerificationReceipt(
        int schemaVersion,
        String backupId,
        String manifestSha256,
        long manifestSizeBytes,
        long manifestLastModifiedEpochMillis,
        long verifiedAtEpochMillis,
        int fileCount,
        long totalBytes) {
    /** Current on-disk verification-receipt schema. */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    /** Receipt filename stored beside the backup manifest. */
    public static final String FILE_NAME = ".verification.json";

    /**
     * Normalizes nullable persisted identifiers without validating manifest contents.
     *
     * @param schemaVersion receipt schema
     * @param backupId normalized backup identifier
     * @param manifestSha256 lowercase hexadecimal manifest digest
     * @param manifestSizeBytes manifest size in bytes
     * @param manifestLastModifiedEpochMillis manifest modification time in epoch milliseconds
     * @param verifiedAtEpochMillis successful verification time in epoch milliseconds
     * @param fileCount number of verified entries
     * @param totalBytes total verified bytes
     */
    public BackupVerificationReceipt {
        backupId = backupId == null ? "" : backupId;
        manifestSha256 = manifestSha256 == null ? "" : manifestSha256;
    }

    /**
     * Creates a source-compatible receipt without manifest file metadata.
     *
     * @param schemaVersion receipt schema
     * @param backupId normalized backup identifier
     * @param manifestSha256 lowercase hexadecimal manifest digest
     * @param verifiedAtEpochMillis successful verification time in epoch milliseconds
     * @param fileCount number of verified entries
     * @param totalBytes total verified bytes
     */
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
