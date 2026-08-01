package com.nightsta69.delvefold.config.model;

/**
 * Optional limits for automatic backup retention.
 *
 * <p>A zero limit is unbounded. Retention is compatibility-safe and performs no pruning unless {@link #enabled()} is
 * explicitly set to {@code true}.
 */
public record BackupRetentionSettings(boolean enabled, int maxCount, long maxAgeDays, long maxTotalBytes) {
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

    public static BackupRetentionSettings defaults() {
        return new BackupRetentionSettings(false, 0, 0, 0);
    }
}
