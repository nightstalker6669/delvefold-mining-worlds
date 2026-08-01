package com.nightsta69.delvefold.reset;

/**
 * Server-authoritative preview of a proposed delete, recreation, or restore operation.
 *
 * <p>Accepted previews contain a short-lived confirmation token. Rejected previews use zero for timestamps, byte
 * estimates, and player counts and expose no usable token.
 *
 * @param accepted whether the request passed current validation and conflict checks
 * @param message bounded localized result envelope
 * @param confirmationToken secret token required to confirm the exact preview
 * @param expiresAtEpochMillis token expiration in epoch milliseconds, or zero when rejected
 * @param estimatedBytes best-effort recursive byte estimate, or zero when rejected
 * @param playersToEvacuate number of players currently in managed mining dimensions
 * @param backupMode requested finalized-staging policy
 */
public record WorldOperationPreview(
        boolean accepted,
        String message,
        String confirmationToken,
        long expiresAtEpochMillis,
        long estimatedBytes,
        int playersToEvacuate,
        BackupMode backupMode) {
    /**
     * Creates a rejected preview with no confirmation capability.
     *
     * @param message bounded localized rejection envelope
     * @return immutable rejected preview with zero-valued estimates
     */
    public static WorldOperationPreview rejected(String message) {
        return new WorldOperationPreview(false, message, "", 0, 0, 0, BackupMode.KEEP_BACKUP);
    }
}
