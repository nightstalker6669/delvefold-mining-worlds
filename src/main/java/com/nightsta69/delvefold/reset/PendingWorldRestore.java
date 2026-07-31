package com.nightsta69.delvefold.reset;

public record PendingWorldRestore(
        int schemaVersion,
        String operationId,
        String backupId,
        Phase phase,
        long createdAtEpochMillis,
        String requestedBy) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public PendingWorldRestore {
        operationId = operationId == null ? "" : operationId;
        backupId = backupId == null ? "" : backupId;
        phase = phase == null ? Phase.REQUESTED : phase;
        requestedBy = requestedBy == null ? "unknown" : requestedBy;
    }

    public PendingWorldRestore withPhase(Phase replacement) {
        return new PendingWorldRestore(schemaVersion, operationId, backupId, replacement,
                createdAtEpochMillis, requestedBy);
    }

    public enum Phase {
        REQUESTED,
        STAGED,
        CURRENT_BACKED_UP,
        RESTORED
    }
}
