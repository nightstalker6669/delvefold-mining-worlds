package com.nightsta69.delvefold.audit;

/** One immutable, already-redacted line in the mutation audit log. */
public record AuditEntry(
        int formatVersion,
        String timestamp,
        String actor,
        String operation,
        String affectedObject,
        long oldRevision,
        long newRevision
) {
    public static final int CURRENT_FORMAT_VERSION = 1;

    public AuditEntry {
        if (formatVersion != CURRENT_FORMAT_VERSION || timestamp == null || actor == null
                || operation == null || affectedObject == null) {
            throw new IllegalArgumentException("Audit entry is incomplete or uses an unsupported format");
        }
    }
}
