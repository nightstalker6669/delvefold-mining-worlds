package com.nightsta69.delvefold.config.model;

import java.util.Collections;
import java.util.NavigableSet;
import java.util.TreeSet;

/** Opt-in, restart-applied renewal schedule. Times are wall-clock epoch milliseconds. */
public record RenewalSettings(
        boolean enabled,
        int intervalDays,
        int warningMinutes,
        long nextRenewalAtEpochMillis
) {
    public static RenewalSettings disabled() {
        return new RenewalSettings(false, 30, 30, 0L);
    }

    public RenewalSettings scheduledFrom(long nowEpochMillis) {
        if (!enabled) {
            return this;
        }
        return new RenewalSettings(true, intervalDays, warningMinutes,
                nowEpochMillis + intervalDays * 86_400_000L);
    }

    /** Pure warning schedule used by both the server scheduler and unit tests. */
    public static NavigableSet<Integer> warningThresholds(int configuredMinutes) {
        TreeSet<Integer> thresholds = new TreeSet<>(Collections.reverseOrder());
        thresholds.add(Math.max(1, configuredMinutes));
        if (configuredMinutes > 10) {
            thresholds.add(10);
        }
        if (configuredMinutes > 1) {
            thresholds.add(1);
        }
        return Collections.unmodifiableNavigableSet(thresholds);
    }
}
