package com.nightsta69.delvefold.config.model;

/**
 * Optional limits for automatic backup retention.
 *
 * <p>A zero limit is unbounded. Retention is compatibility-safe and performs no pruning unless {@link #enabled()} is
 * explicitly set to {@code true}.
 *
 * @param enabled whether automatic retention pruning is enabled; the schema-2 compatibility default is {@code false}
 * @param maxCount maximum retained backup count, or {@code 0} for no count limit
 * @param maxAgeDays maximum backup age in whole days, or {@code 0} for no age limit
 * @param maxTotalBytes maximum combined backup size in bytes, or {@code 0} for no size limit
 */
public record BackupRetentionSettings(boolean enabled, int maxCount, long maxAgeDays, long maxTotalBytes) {
    /**
     * Validates and creates retention settings.
     *
     * @param enabled whether automatic retention pruning is enabled
     * @param maxCount maximum retained backup count, or {@code 0} for no count limit
     * @param maxAgeDays maximum backup age in whole days, or {@code 0} for no age limit
     * @param maxTotalBytes maximum combined backup size in bytes, or {@code 0} for no size limit
     * @throws IllegalArgumentException if any limit is negative
     */
    public BackupRetentionSettings {
        if (maxCount < 0) {
            throw new IllegalArgumentException("Backup retention max_count cannot be negative");
        }
        if (maxAgeDays < 0) {
            throw new IllegalArgumentException("Backup retention max_age_days cannot be negative");
        }
        if (maxTotalBytes < 0) {
            throw new IllegalArgumentException("Backup retention max_total_bytes cannot be negative");
        }
    }

    /**
     * Returns the compatibility-preserving retention policy used when schema-2 settings omit this section.
     *
     * @return disabled retention with every limit unbounded
     */
    public static BackupRetentionSettings defaults() {
        return new BackupRetentionSettings(false, 0, 0, 0);
    }
}
