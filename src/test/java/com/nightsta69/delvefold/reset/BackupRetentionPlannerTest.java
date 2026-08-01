package com.nightsta69.delvefold.reset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.model.BackupRetentionSettings;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BackupRetentionPlannerTest {
    private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

    @Test
    void disabledPolicyNeverSelectsADeletion() {
        var candidates = List.of(candidate("old", 100, 20, false));
        var plan = BackupRetentionPlanner.plan(BackupRetentionSettings.defaults(), candidates, Set.of(), NOW);

        assertFalse(plan.enabled());
        assertTrue(plan.prunes().isEmpty());
        assertEquals(plan.beforeBytes(), plan.afterBytes());
    }

    @Test
    void alwaysProtectsPinnedPendingAndNewestTwoBackups() {
        List<BackupRetentionPlanner.Candidate> candidates = List.of(
                candidate("old-free-a", 100, 100, false),
                candidate("old-free-b", 90, 100, false),
                candidate("old-pinned", 80, 100, true),
                candidate("old-pending", 70, 100, false),
                candidate("newer", 2, 100, false),
                candidate("newest", 1, 100, false));
        BackupRetentionSettings settings = new BackupRetentionSettings(true, 2, 30, 150);

        var plan = BackupRetentionPlanner.plan(settings, candidates, Set.of("old-pending"), NOW);

        assertEquals(
                List.of("old-free-a", "old-free-b"),
                plan.prunes().stream()
                        .map(BackupRetentionPlanner.Prune::id)
                        .sorted()
                        .toList());
        assertEquals(4, plan.afterCount());
        assertFalse(plan.constraintsSatisfied());
        assertTrue(plan.warnings().stream().anyMatch(message -> message.contains("max_count")));
        assertTrue(plan.warnings().stream().anyMatch(message -> message.contains("max_total_bytes")));
        assertTrue(plan.warnings().stream().anyMatch(message -> message.contains("max_age_days")));
    }

    @Test
    void tieBreakingAndPruneOrderAreDeterministic() {
        long old = NOW.minus(10, ChronoUnit.DAYS).toEpochMilli();
        List<BackupRetentionPlanner.Candidate> candidates = List.of(
                new BackupRetentionPlanner.Candidate("c", old, 10, false),
                new BackupRetentionPlanner.Candidate("a", old, 10, false),
                new BackupRetentionPlanner.Candidate("b", old, 10, false),
                candidate("newer", 2, 10, false),
                candidate("newest", 1, 10, false));
        BackupRetentionSettings settings = new BackupRetentionSettings(true, 3, 0, 0);

        var forward = BackupRetentionPlanner.plan(settings, candidates, Set.of(), NOW);
        var reverse = BackupRetentionPlanner.plan(settings, candidates.reversed(), Set.of(), NOW);

        assertEquals(
                List.of("a", "b"),
                forward.prunes().stream().map(BackupRetentionPlanner.Prune::id).toList());
        assertEquals(forward, reverse);
        assertTrue(forward.prunes().stream()
                .allMatch(prune -> prune.reasons().equals(List.of(BackupRetentionPlanner.Reason.COUNT))));
    }

    @Test
    void onlyVerifiedRestorableCandidatesWithKnownMetadataAreEligible() {
        List<BackupRetentionPlanner.Candidate> candidates = List.of(
                candidate("eligible-old", 100, 10, false),
                unsafeCandidate("legacy", 90, 10, true, false, false, false),
                unsafeCandidate("unverified", 80, 10, true, true, false, false),
                unsafeCandidate("invalid", 70, 10, false, true, false, false),
                new BackupRetentionPlanner.Candidate(
                        "unknown-size",
                        NOW.minus(60, ChronoUnit.DAYS).toEpochMilli(),
                        -1L,
                        false,
                        true,
                        true,
                        true,
                        true),
                new BackupRetentionPlanner.Candidate("unknown-time", 0L, 10L, false, true, true, true, true),
                candidate("newer", 2, 10, false),
                candidate("newest", 1, 10, false));

        var plan = BackupRetentionPlanner.plan(new BackupRetentionSettings(true, 2, 0, 0), candidates, Set.of(), NOW);

        assertEquals(
                List.of("eligible-old"),
                plan.prunes().stream().map(BackupRetentionPlanner.Prune::id).toList());
        assertTrue(plan.protections().get("legacy").contains(BackupRetentionPlanner.ProtectionReason.MANIFEST_MISSING));
        assertTrue(plan.protections()
                .get("unverified")
                .contains(BackupRetentionPlanner.ProtectionReason.VERIFICATION_NOT_CURRENT));
        assertTrue(plan.protections().get("invalid").contains(BackupRetentionPlanner.ProtectionReason.INVALID));
        assertTrue(
                plan.protections().get("unknown-size").contains(BackupRetentionPlanner.ProtectionReason.SIZE_UNKNOWN));
        assertTrue(plan.protections()
                .get("unknown-time")
                .contains(BackupRetentionPlanner.ProtectionReason.TIMESTAMP_UNKNOWN));
    }

    @Test
    void retentionSettingsRejectNegativeLimits() {
        assertThrows(IllegalArgumentException.class, () -> new BackupRetentionSettings(true, -1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new BackupRetentionSettings(true, 0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new BackupRetentionSettings(true, 0, 0, -1));
    }

    private static BackupRetentionPlanner.Candidate candidate(String id, long ageDays, long sizeBytes, boolean pinned) {
        return new BackupRetentionPlanner.Candidate(
                id, NOW.minus(ageDays, ChronoUnit.DAYS).toEpochMilli(), sizeBytes, pinned);
    }

    private static BackupRetentionPlanner.Candidate unsafeCandidate(
            String id,
            long ageDays,
            long sizeBytes,
            boolean valid,
            boolean manifestPresent,
            boolean verified,
            boolean restorable) {
        return new BackupRetentionPlanner.Candidate(
                id,
                NOW.minus(ageDays, ChronoUnit.DAYS).toEpochMilli(),
                sizeBytes,
                false,
                valid,
                manifestPresent,
                verified,
                restorable);
    }
}
