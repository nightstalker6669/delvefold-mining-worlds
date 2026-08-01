package com.nightsta69.delvefold.config.model;

import java.util.Collections;
import java.util.NavigableSet;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * Opt-in, restart-applied renewal schedule.
 *
 * <p>Renewal first evacuates players and creates a retained backup, then stages replacement for server restart. The
 * schedule does not delete a loaded dimension in place. Existing schema-2 files default to stable seed mode.
 *
 * @param enabled whether automatic renewal scheduling is active
 * @param intervalDays interval in whole days, validated from 1 through 3650
 * @param warningMinutes advance warning in whole minutes, validated from 1 through 10080
 * @param nextRenewalAtEpochMillis next wall-clock deadline in epoch milliseconds, or {@code 0} when unscheduled
 * @param seedMode deterministic layout policy committed on initialization or recreation
 */
public record RenewalSettings(
        boolean enabled,
        int intervalDays,
        int warningMinutes,
        long nextRenewalAtEpochMillis,
        RenewalSeedMode seedMode) {
    /**
     * Creates renewal settings with the schema-2 compatibility seed mode when that additive field is absent.
     *
     * @param enabled whether automatic renewal is enabled
     * @param intervalDays interval in whole days
     * @param warningMinutes advance warning in whole minutes
     * @param nextRenewalAtEpochMillis next deadline in epoch milliseconds, or {@code 0}
     * @param seedMode layout policy, or {@code null} for stable layout
     */
    public RenewalSettings(
            boolean enabled,
            int intervalDays,
            int warningMinutes,
            long nextRenewalAtEpochMillis,
            @Nullable RenewalSeedMode seedMode) {
        this.enabled = enabled;
        this.intervalDays = intervalDays;
        this.warningMinutes = warningMinutes;
        this.nextRenewalAtEpochMillis = nextRenewalAtEpochMillis;
        this.seedMode = seedMode == null ? RenewalSeedMode.STABLE : seedMode;
    }

    /**
     * Creates source- and binary-compatible settings for schema-2 callers predating seed modes.
     *
     * @param enabled whether automatic renewal is enabled
     * @param intervalDays interval in whole days
     * @param warningMinutes advance warning in whole minutes
     * @param nextRenewalAtEpochMillis next deadline in epoch milliseconds, or {@code 0}
     */
    public RenewalSettings(boolean enabled, int intervalDays, int warningMinutes, long nextRenewalAtEpochMillis) {
        this(enabled, intervalDays, warningMinutes, nextRenewalAtEpochMillis, RenewalSeedMode.STABLE);
    }

    /**
     * Returns the compatibility-preserving disabled renewal policy.
     *
     * @return disabled 30-day interval, 30-minute warning, no deadline, and stable layout
     */
    public static RenewalSettings disabled() {
        return new RenewalSettings(false, 30, 30, 0L, RenewalSeedMode.STABLE);
    }

    /**
     * Calculates the next wall-clock deadline from a supplied instant when renewal is enabled.
     *
     * <p>The calculation is pure and uses exactly 86,400,000 milliseconds per configured day. Disabled settings are
     * returned unchanged.
     *
     * @param nowEpochMillis scheduling base time in epoch milliseconds
     * @return rescheduled immutable settings, or this instance when disabled
     */
    public RenewalSettings scheduledFrom(long nowEpochMillis) {
        if (!enabled) {
            return this;
        }
        return new RenewalSettings(
                true, intervalDays, warningMinutes, nowEpochMillis + intervalDays * 86_400_000L, seedMode);
    }

    /**
     * Copies this schedule with a replacement recreation seed policy.
     *
     * <p>Changing the selected policy is live configuration, but it does not alter generated chunks or the persisted
     * salt until a later initialization or recreation commits.
     *
     * @param replacement replacement seed policy
     * @return immutable schedule copy retaining timing fields
     */
    public RenewalSettings withSeedMode(RenewalSeedMode replacement) {
        return new RenewalSettings(enabled, intervalDays, warningMinutes, nextRenewalAtEpochMillis, replacement);
    }

    /**
     * Produces the immutable descending warning thresholds used by the server scheduler.
     *
     * <p>The configured threshold is clamped to at least one minute; ten- and one-minute reminders are added when they
     * occur strictly below it.
     *
     * @param configuredMinutes configured advance-warning threshold in whole minutes
     * @return unmodifiable, reverse-ordered set of distinct warning thresholds in whole minutes
     */
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
