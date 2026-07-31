package com.nightsta69.delvefold.reset;

public record WorldOperationPreview(
        boolean accepted,
        String message,
        String confirmationToken,
        long expiresAtEpochMillis,
        long estimatedBytes,
        int playersToEvacuate,
        BackupMode backupMode
) {
    public static WorldOperationPreview rejected(String message) {
        return new WorldOperationPreview(false, message, "", 0, 0, 0, BackupMode.KEEP_BACKUP);
    }
}
