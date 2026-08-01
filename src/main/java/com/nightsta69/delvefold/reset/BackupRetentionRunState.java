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

    /**
     * Returns the process-wide holder updated by ordered server lifecycle retention work.
     *
     * @return singleton run-state holder
     */
    public static BackupRetentionRunState get() {
        return INSTANCE;
    }

    /**
     * Publishes a bounded, path-free projection of the latest retention preview.
     *
     * @param preview immutable evaluated plan, or {@code null} to clear current diagnostics
     */
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

    /**
     * Adds the bounded application outcome to the currently published preview.
     *
     * @param result immutable deletion result; {@code null} leaves the current snapshot unchanged
     */
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

    /**
     * Returns the latest immutable retention diagnostic without exposing save paths.
     *
     * @return optional snapshot, empty before a preview or after {@link #reset()}
     */
    public Optional<Snapshot> current() {
        return Optional.ofNullable(current.get());
    }

    /** Clears published retention diagnostics during server-session shutdown or failed preview initialization. */
    public void reset() {
        current.set(null);
    }

    /**
     * Bounded path-free deletion proposal retained for Doctor diagnostics.
     *
     * @param backupId normalized backup directory identifier
     * @param reasons immutable retention constraints selecting the backup
     */
    public record Proposal(String backupId, List<BackupRetentionPlanner.Reason> reasons) {
        /**
         * Normalizes the identifier and defensively copies proposal reasons.
         *
         * @param backupId normalized backup identifier
         * @param reasons retention constraints selecting the backup
         */
        public Proposal {
            backupId = backupId == null ? "" : backupId;
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
        }
    }

    /**
     * Immutable bounded record of one automatic-retention preview and its optional application result.
     *
     * @param evaluatedAtEpochMillis preview evaluation time in epoch milliseconds
     * @param enabled whether automatic retention was enabled
     * @param beforeCount eligible catalog count before planned pruning
     * @param afterCount projected retained count
     * @param beforeBytes normalized measured bytes before pruning
     * @param afterBytes projected measured bytes after pruning
     * @param constraintsSatisfied whether protected backups still allow every enabled limit to be met
     * @param proposals at most 128 path-free proposed deletions
     * @param omittedProposalCount additional proposals omitted from diagnostics
     * @param protectedBackupIds at most 128 normalized protected identifiers
     * @param omittedProtectedCount additional protected identifiers omitted from diagnostics
     * @param warnings at most 32 bounded unsatisfied-policy warnings
     * @param prunedBackupIds at most 128 identifiers actually deleted
     * @param omittedPrunedCount additional successful deletions omitted from diagnostics
     * @param failedBackupIds at most 128 identifiers whose deletion failed
     * @param omittedFailureCount additional failures omitted from diagnostics
     * @param applyRecorded whether an apply result has been merged into this preview
     */
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
        /**
         * Defensively copies every collection while preserving planner order.
         *
         * @param evaluatedAtEpochMillis preview evaluation time in epoch milliseconds
         * @param enabled whether retention was enabled
         * @param beforeCount pre-prune count
         * @param afterCount projected retained count
         * @param beforeBytes pre-prune measured bytes
         * @param afterBytes projected retained bytes
         * @param constraintsSatisfied whether every enabled constraint can be met
         * @param proposals bounded proposed deletions
         * @param omittedProposalCount omitted proposal count
         * @param protectedBackupIds bounded protected identifiers
         * @param omittedProtectedCount omitted protected-identifier count
         * @param warnings bounded planner warnings
         * @param prunedBackupIds bounded successful deletion identifiers
         * @param omittedPrunedCount omitted successful deletion count
         * @param failedBackupIds bounded failed deletion identifiers
         * @param omittedFailureCount omitted failed deletion count
         * @param applyRecorded whether an application outcome is present
         */
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
