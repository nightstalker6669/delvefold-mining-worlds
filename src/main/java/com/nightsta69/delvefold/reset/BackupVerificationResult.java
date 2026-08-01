package com.nightsta69.delvefold.reset;

/** Result returned by background backup integrity work. */
public record BackupVerificationResult(
        String backupId,
        Status status,
        String message,
        int fileCount,
        long totalBytes,
        long startedAtEpochMillis,
        long completedAtEpochMillis,
        String workerThread
) {
    public BackupVerificationResult {
        backupId = backupId == null ? "" : backupId;
        message = message == null ? "" : message;
        workerThread = workerThread == null ? "" : workerThread;
    }

    public boolean successful() {
        return status == Status.VERIFIED || status == Status.LEGACY_UPGRADED;
    }

    public enum Status {
        VERIFIED,
        LEGACY_UPGRADED,
        LEGACY_REQUIRES_VALIDATION,
        FAILED
    }
}
