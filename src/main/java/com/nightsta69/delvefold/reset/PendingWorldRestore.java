package com.nightsta69.delvefold.reset;

/**
 * Restart journal for restoring one verified mining-world backup.
 *
 * <p>Each phase is durably replaced before startup advances to the next filesystem transition. The selected backup is
 * copied rather than consumed, and a completed journal remains until staging cleanup and history archival succeed.
 *
 * @param schemaVersion on-disk journal schema, currently {@value #CURRENT_SCHEMA_VERSION}
 * @param operationId UUID text binding all retry phases
 * @param backupId normalized identifier of the selected verified backup
 * @param phase latest durably completed restore phase
 * @param createdAtEpochMillis acceptance time in epoch milliseconds
 * @param requestedBy bounded actor label retained in operation history
 */
public record PendingWorldRestore(
        int schemaVersion,
        String operationId,
        String backupId,
        Phase phase,
        long createdAtEpochMillis,
        String requestedBy) {
    /** Current schema accepted by restore startup recovery. */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    /**
     * Normalizes absent persisted text and defaults an absent phase to {@link Phase#REQUESTED}.
     *
     * @param schemaVersion on-disk journal schema
     * @param operationId lifecycle operation UUID text
     * @param backupId normalized selected-backup identifier
     * @param phase latest completed restore phase
     * @param createdAtEpochMillis acceptance time in epoch milliseconds
     * @param requestedBy actor label
     */
    public PendingWorldRestore {
        operationId = operationId == null ? "" : operationId;
        backupId = backupId == null ? "" : backupId;
        phase = phase == null ? Phase.REQUESTED : phase;
        requestedBy = requestedBy == null ? "unknown" : requestedBy;
    }

    /**
     * Returns a new immutable journal with only its restart phase advanced.
     *
     * @param replacement phase that will be persisted before subsequent work begins
     * @return journal retaining the operation identity, selected backup, timestamp, and actor
     */
    public PendingWorldRestore withPhase(Phase replacement) {
        return new PendingWorldRestore(
                schemaVersion, operationId, backupId, replacement, createdAtEpochMillis, requestedBy);
    }

    /** Ordered, restart-recoverable restore transaction phases. */
    public enum Phase {
        /** The request is confirmed, but the selected backup has not yet been copied into restore staging. */
        REQUESTED,

        /** The selected backup and restorable configuration are completely copied into staging. */
        STAGED,

        /** The currently active mining dimensions have been moved into the pre-restore safety backup. */
        CURRENT_BACKED_UP,

        /** Staged dimensions and configuration are installed; only idempotent cleanup and archival remain. */
        RESTORED
    }
}
