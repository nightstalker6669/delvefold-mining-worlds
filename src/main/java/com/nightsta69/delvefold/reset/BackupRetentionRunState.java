package com.nightsta69.delvefold.reset;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Latest bounded, path-free automatic-retention report for diagnostics. */
public final class BackupRetentionRunState {
    private static final int MAX_ENTRIES = 128;
    private static final int MAX_WARNINGS = 32;
    private static final BackupRetentionRunState INSTANCE = new BackupRetentionRunState();

    private final AtomicReference<Snapshot> current = new AtomicReference<>();

    private BackupRetentionRunState() {}

    public static BackupRetentionRunState get() {
        return INSTANCE;
    }

    public void recordPreview(BackupRetentionService.Preview preview) {
        if (preview == null) {
            reset();
            return;
        }
        BackupRetentionPlanner.Plan plan = preview.plan();
        List<Proposal> proposals = plan.prunes().stream()
                .limit(MAX_ENTRIES)
                .map(prune -> new Proposal(prune.id(), prune.reasons()))
                .toList();
        List<String> protectedIds = preview.protectedBackupIds().stream()
                .sorted()
                .limit(MAX_ENTRIES)
                .toList();
        current.set(new Snapshot(
                preview.evaluatedAt().toEpochMilli(),
                plan.enabled(),
                plan.beforeCount(),
                plan.afterCount(),
                plan.beforeBytes(),
                plan.afterBytes(),
                plan.constraintsSatisfied(),
                proposals,
                Math.max(0, plan.prunes().size() - proposals.size()),
                protectedIds,
                Math.max(0, preview.protectedBackupIds().size() - protectedIds.size()),
                plan.warnings().stream().limit(MAX_WARNINGS).toList(),
                List.of(),
                0,
                List.of(),
                0,
                false));
    }

    public void recordResult(BackupRetentionService.ApplyResult result) {
        if (result == null) {
            return;
        }
        List<String> pruned =
                result.prunedIds().stream().sorted().limit(MAX_ENTRIES).toList();
        List<String> failed =
                result.failures().keySet().stream().sorted().limit(MAX_ENTRIES).toList();
        current.updateAndGet(previous -> {
            Snapshot base = previous == null ? Snapshot.empty() : previous;
            return new Snapshot(
                    base.evaluatedAtEpochMillis(),
                    base.enabled(),
                    base.beforeCount(),
                    base.afterCount(),
                    base.beforeBytes(),
                    base.afterBytes(),
                    base.constraintsSatisfied(),
                    base.proposals(),
                    base.omittedProposalCount(),
                    base.protectedBackupIds(),
                    base.omittedProtectedCount(),
                    base.warnings(),
                    pruned,
                    Math.max(0, result.prunedIds().size() - pruned.size()),
                    failed,
                    Math.max(0, result.failures().size() - failed.size()),
                    true);
        });
    }

    public Optional<Snapshot> current() {
        return Optional.ofNullable(current.get());
    }

    public void reset() {
        current.set(null);
    }

    public record Proposal(String backupId, List<BackupRetentionPlanner.Reason> reasons) {
        public Proposal {
            backupId = backupId == null ? "" : backupId;
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
        }
    }

    public record Snapshot(
            long evaluatedAtEpochMillis,
            boolean enabled,
            int beforeCount,
            int afterCount,
            long beforeBytes,
            long afterBytes,
            boolean constraintsSatisfied,
            List<Proposal> proposals,
            int omittedProposalCount,
            List<String> protectedBackupIds,
            int omittedProtectedCount,
            List<String> warnings,
            List<String> prunedBackupIds,
            int omittedPrunedCount,
            List<String> failedBackupIds,
            int omittedFailureCount,
            boolean applyRecorded) {
        public Snapshot {
            proposals = proposals == null ? List.of() : List.copyOf(proposals);
            protectedBackupIds = protectedBackupIds == null ? List.of() : List.copyOf(protectedBackupIds);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
            prunedBackupIds = prunedBackupIds == null ? List.of() : List.copyOf(prunedBackupIds);
            failedBackupIds = failedBackupIds == null ? List.of() : List.copyOf(failedBackupIds);
        }

        private static Snapshot empty() {
            return new Snapshot(
                    0L, false, 0, 0, 0L, 0L, true, List.of(), 0, List.of(), 0, List.of(), List.of(), 0, List.of(), 0,
                    false);
        }
    }
}
