package com.nightsta69.delvefold.reset;

/**
 * Immutable result returned by background backup integrity work.
 *
 * @param backupId normalized backup directory identifier
 * @param status terminal verification outcome
 * @param message bounded localized status envelope or diagnostic text
 * @param fileCount number of manifest entries examined
 * @param totalBytes total verified file bytes, or the measured bytes before failure
 * @param startedAtEpochMillis verification start time in epoch milliseconds
 * @param completedAtEpochMillis verification completion time in epoch milliseconds
 * @param workerThread background worker name, retained for nonblocking-operation diagnostics
 */
public record BackupVerificationResult(
        String backupId,
        Status status,
        String message,
        int fileCount,
        long totalBytes,
        long startedAtEpochMillis,
        long completedAtEpochMillis,
        String workerThread) {
    /**
     * Normalizes nullable display strings while retaining measured counters and timestamps.
     *
     * @param backupId normalized backup directory identifier
     * @param status terminal verification outcome
     * @param message bounded localized status envelope or diagnostic text
     * @param fileCount number of manifest entries examined
     * @param totalBytes total verified file bytes
     * @param startedAtEpochMillis start time in epoch milliseconds
     * @param completedAtEpochMillis completion time in epoch milliseconds
     * @param workerThread background worker name
     */
    public BackupVerificationResult {
        backupId = backupId == null ? "" : backupId;
        message = message == null ? "" : message;
        workerThread = workerThread == null ? "" : workerThread;
    }

    /**
     * Reports whether verification established a current restorable manifest.
     *
     * @return {@code true} for an existing verified manifest or a successfully upgraded legacy backup
     */
    public boolean successful() {
        return status == Status.VERIFIED || status == Status.LEGACY_UPGRADED;
    }

    /** Terminal outcomes of manifest verification or legacy-manifest creation. */
    public enum Status {
        /** Every manifest path, size, and SHA-256 hash matched. */
        VERIFIED,

        /** Legacy validation succeeded and a new manifest and receipt were published. */
        LEGACY_UPGRADED,

        /** The backup predates manifests and must pass explicit legacy validation. */
        LEGACY_REQUIRES_VALIDATION,

        /** Safety validation, I/O, size, or hash verification failed. */
        FAILED
    }
}
