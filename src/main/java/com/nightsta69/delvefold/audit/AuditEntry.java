package com.nightsta69.delvefold.audit;

/**
 * One immutable, already-redacted line in the mutation audit log.
 *
 * @param formatVersion JSON-lines entry format, currently {@value #CURRENT_FORMAT_VERSION}
 * @param timestamp UTC ISO-8601 instant text
 * @param actor validated player identifier, {@code console}, or {@code server}
 * @param operation stable lowercase operation name
 * @param affectedObject stable type-qualified logical identifier
 * @param oldRevision preceding domain revision, or {@code -1} when unavailable
 * @param newRevision resulting domain revision, or {@code -1} when unavailable
 */
public record AuditEntry(
        int formatVersion,
        String timestamp,
        String actor,
        String operation,
        String affectedObject,
        long oldRevision,
        long newRevision) {
    /** Current JSON-lines audit-entry format. */
    public static final int CURRENT_FORMAT_VERSION = 1;

    /**
     * Rejects incomplete entries before they can reach the rotating writer.
     *
     * @param formatVersion JSON-lines entry format
     * @param timestamp UTC ISO-8601 instant text
     * @param actor validated actor label
     * @param operation stable lowercase operation name
     * @param affectedObject type-qualified logical identifier
     * @param oldRevision preceding domain revision, or {@code -1}
     * @param newRevision resulting domain revision, or {@code -1}
     * @throws IllegalArgumentException if required text is absent or the format is unsupported
     */
    public AuditEntry {
        if (formatVersion != CURRENT_FORMAT_VERSION
                || timestamp == null
                || actor == null
                || operation == null
                || affectedObject == null) {
            throw new IllegalArgumentException("Audit entry is incomplete or uses an unsupported format");
        }
    }
}
