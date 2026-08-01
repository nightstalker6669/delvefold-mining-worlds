package com.nightsta69.delvefold.diagnostics;

import java.util.Comparator;
import java.util.List;

/**
 * Immutable, transport-neutral snapshot used by the doctor command, screen, and export.
 *
 * <p>The model intentionally contains no filesystem paths, network addresses, confirmation
 * tokens, player records, or complete configuration documents. Integrations should translate
 * validation results into stable codes before adding them to a report.</p>
 */
public record DoctorReport(
        int formatVersion,
        long generatedAtEpochMillis,
        VersionInfo versions,
        List<DimensionStatus> dimensions,
        ProfileHealth profile,
        List<PendingOperationStatus> pendingOperations,
        BackupHealth backups,
        RetentionPreview retention,
        DiskEstimate disk
) {
    public static final int CURRENT_FORMAT_VERSION = 1;

    public DoctorReport {
        if (formatVersion != CURRENT_FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported doctor report format: " + formatVersion);
        }
        if (generatedAtEpochMillis < 0L) {
            throw new IllegalArgumentException("generatedAtEpochMillis must not be negative");
        }
        versions = versions == null ? VersionInfo.unknown() : versions;
        dimensions = sortedCopy(dimensions, Comparator.comparing(DimensionStatus::dimensionId));
        profile = profile == null ? ProfileHealth.unknown() : profile;
        pendingOperations = sortedCopy(pendingOperations,
                Comparator.comparingLong(PendingOperationStatus::createdAtEpochMillis)
                        .thenComparing(PendingOperationStatus::operationId));
        backups = backups == null ? BackupHealth.empty() : backups;
        retention = retention == null ? RetentionPreview.disabled() : retention;
        disk = disk == null ? DiskEstimate.unknown() : disk;
    }

    /** Source-compatible constructor for integrations predating retention diagnostics. */
    public DoctorReport(
            int formatVersion,
            long generatedAtEpochMillis,
            VersionInfo versions,
            List<DimensionStatus> dimensions,
            ProfileHealth profile,
            List<PendingOperationStatus> pendingOperations,
            BackupHealth backups,
            DiskEstimate disk
    ) {
        this(formatVersion, generatedAtEpochMillis, versions, dimensions, profile, pendingOperations,
                backups, RetentionPreview.disabled(), disk);
    }

    public boolean healthy() {
        return dimensions.stream().allMatch(dimension -> dimension.state() == DimensionState.ACTIVE)
                && profile.healthy()
                && backups.healthy()
                && retention.healthy()
                && disk.sufficient();
    }

    private static <T> List<T> sortedCopy(List<T> values, Comparator<? super T> comparator) {
        return values == null ? List.of() : values.stream().sorted(comparator).toList();
    }

    public record VersionInfo(
            String delvefold,
            String minecraft,
            String neoForge,
            int publicApi,
            int networkProtocol,
            int configSchema
    ) {
        public VersionInfo {
            delvefold = fallback(delvefold);
            minecraft = fallback(minecraft);
            neoForge = fallback(neoForge);
            if (publicApi < 0 || networkProtocol < 0 || configSchema < 0) {
                throw new IllegalArgumentException("Version numbers must not be negative");
            }
        }

        public static VersionInfo unknown() {
            return new VersionInfo("unknown", "unknown", "unknown", 0, 0, 0);
        }
    }

    public record DimensionStatus(String dimensionId, String terrain, DimensionState state) {
        public DimensionStatus {
            dimensionId = fallback(dimensionId);
            terrain = fallback(terrain);
            state = state == null ? DimensionState.MISSING : state;
        }
    }

    public enum DimensionState {
        ACTIVE,
        UNLOADED,
        MISSING
    }

    public record ProfileHealth(
            String activeProfileId,
            long revision,
            int enabledRules,
            int totalRules,
            long errorCount,
            long warningCount,
            List<IneffectiveTarget> ineffectiveTargets,
            List<Finding> findings
    ) {
        public ProfileHealth {
            activeProfileId = fallback(activeProfileId);
            if (revision < 0L || enabledRules < 0 || totalRules < 0
                    || enabledRules > totalRules || errorCount < 0L || warningCount < 0L) {
                throw new IllegalArgumentException("Profile health counts are inconsistent");
            }
            ineffectiveTargets = sortedCopy(ineffectiveTargets,
                    Comparator.comparing(IneffectiveTarget::ruleId)
                            .thenComparing(IneffectiveTarget::targetId)
                            .thenComparing(IneffectiveTarget::reasonCode));
            findings = sortedCopy(findings,
                    Comparator.comparing(Finding::severity)
                            .thenComparing(Finding::code)
                            .thenComparing(Finding::objectId));
        }

        public static ProfileHealth unknown() {
            return new ProfileHealth("unknown", 0L, 0, 0, 0L, 0L, List.of(), List.of());
        }

        public boolean healthy() {
            return errorCount == 0L && findings.stream().noneMatch(finding -> finding.severity() == Severity.ERROR);
        }
    }

    public record IneffectiveTarget(String ruleId, String targetId, String reasonCode) {
        public IneffectiveTarget {
            ruleId = fallback(ruleId);
            targetId = fallback(targetId);
            reasonCode = fallback(reasonCode);
        }
    }

    public record Finding(Severity severity, String code, String objectId) {
        public Finding {
            severity = severity == null ? Severity.WARNING : severity;
            code = fallback(code);
            objectId = fallback(objectId);
        }
    }

    public enum Severity {
        ERROR,
        WARNING,
        INFO
    }

    public record PendingOperationStatus(
            String operationId,
            String operation,
            String state,
            long createdAtEpochMillis
    ) {
        public PendingOperationStatus {
            operationId = fallback(operationId);
            operation = fallback(operation);
            state = fallback(state);
            if (createdAtEpochMillis < 0L) {
                throw new IllegalArgumentException("Pending-operation timestamp must not be negative");
            }
        }
    }

    public record BackupHealth(
            int total,
            int verified,
            int invalid,
            int legacy,
            int pinned,
            long totalBytes,
            List<BackupProblem> problems
    ) {
        public BackupHealth {
            if (total < 0 || verified < 0 || invalid < 0 || legacy < 0 || pinned < 0
                    || verified > total || invalid > total || legacy > total || pinned > total
                    || (long) verified + invalid + legacy > total
                    || totalBytes < 0L) {
                throw new IllegalArgumentException("Backup health counts are inconsistent");
            }
            problems = sortedCopy(problems,
                    Comparator.comparing(BackupProblem::backupId)
                            .thenComparing(BackupProblem::reasonCode));
        }

        public static BackupHealth empty() {
            return new BackupHealth(0, 0, 0, 0, 0, 0L, List.of());
        }

        public boolean healthy() {
            return invalid == 0 && problems.isEmpty();
        }
    }

    public record BackupProblem(String backupId, String state, String reasonCode) {
        public BackupProblem {
            backupId = fallback(backupId);
            state = fallback(state);
            reasonCode = fallback(reasonCode);
        }
    }

    public record RetentionPreview(
            boolean enabled,
            int beforeCount,
            int afterCount,
            long beforeBytes,
            long afterBytes,
            boolean constraintsSatisfied,
            List<RetentionPrune> prunes,
            List<String> warnings,
            RetentionRun lastRun
    ) {
        public RetentionPreview {
            if (beforeCount < 0 || afterCount < 0 || afterCount > beforeCount
                    || beforeBytes < 0L || afterBytes < 0L || afterBytes > beforeBytes) {
                throw new IllegalArgumentException("Retention preview counts are inconsistent");
            }
            prunes = sortedCopy(prunes,
                    Comparator.comparingLong(RetentionPrune::createdAtEpochMillis)
                            .thenComparing(RetentionPrune::backupId));
            warnings = warnings == null ? List.of() : warnings.stream().map(DoctorReport::fallback).sorted().toList();
            lastRun = lastRun == null ? RetentionRun.none() : lastRun;
            if (!enabled && (!prunes.isEmpty() || beforeCount != afterCount || beforeBytes != afterBytes)) {
                throw new IllegalArgumentException("Disabled retention cannot preview pruning");
            }
        }

        /** Source-compatible constructor for callers predating automatic-run diagnostics. */
        public RetentionPreview(
                boolean enabled,
                int beforeCount,
                int afterCount,
                long beforeBytes,
                long afterBytes,
                boolean constraintsSatisfied,
                List<RetentionPrune> prunes,
                List<String> warnings
        ) {
            this(enabled, beforeCount, afterCount, beforeBytes, afterBytes, constraintsSatisfied,
                    prunes, warnings, RetentionRun.none());
        }

        public static RetentionPreview disabled() {
            return new RetentionPreview(false, 0, 0, 0L, 0L, true,
                    List.of(), List.of(), RetentionRun.none());
        }

        public long reclaimableBytes() {
            return beforeBytes - afterBytes;
        }

        public boolean healthy() {
            return constraintsSatisfied
                    && (!lastRun.available() || lastRun.constraintsSatisfied())
                    && (!lastRun.applyRecorded() || lastRun.failureCount() == 0);
        }
    }

    public record RetentionRun(
            boolean available,
            long evaluatedAtEpochMillis,
            boolean enabled,
            int beforeCount,
            int afterCount,
            int proposedCount,
            List<RetentionProposal> proposals,
            int protectedCount,
            boolean constraintsSatisfied,
            boolean applyRecorded,
            int appliedCount,
            int failureCount,
            List<String> warnings
    ) {
        public RetentionRun {
            if (evaluatedAtEpochMillis < 0L || beforeCount < 0 || afterCount < 0
                    || afterCount > beforeCount || proposedCount < 0 || protectedCount < 0
                    || appliedCount < 0 || failureCount < 0) {
                throw new IllegalArgumentException("Retention-run counts are inconsistent");
            }
            proposals = sortedCopy(proposals,
                    Comparator.comparing(RetentionProposal::backupId)
                            .thenComparing(proposal -> String.join(",", proposal.reasons())));
            warnings = warnings == null ? List.of()
                    : warnings.stream().map(DoctorReport::fallback).sorted().toList();
            if (!available && (proposedCount != 0 || !proposals.isEmpty() || appliedCount != 0
                    || failureCount != 0 || applyRecorded)) {
                throw new IllegalArgumentException("Unavailable retention run cannot contain results");
            }
            if (proposals.size() > proposedCount) {
                throw new IllegalArgumentException("Retention-run proposals exceed the reported count");
            }
        }

        public static RetentionRun none() {
            return new RetentionRun(false, 0L, false, 0, 0, 0, List.of(), 0,
                    true, false, 0, 0, List.of());
        }
    }

    public record RetentionProposal(String backupId, List<String> reasons) {
        public RetentionProposal {
            backupId = fallback(backupId);
            reasons = reasons == null ? List.of()
                    : reasons.stream().map(DoctorReport::fallback).sorted().toList();
        }
    }

    public record RetentionPrune(
            String backupId,
            long createdAtEpochMillis,
            long sizeBytes,
            List<String> reasons
    ) {
        public RetentionPrune {
            backupId = fallback(backupId);
            if (createdAtEpochMillis < 0L || sizeBytes < 0L) {
                throw new IllegalArgumentException("Retention prune values must not be negative");
            }
            reasons = reasons == null ? List.of() : reasons.stream().map(DoctorReport::fallback).sorted().toList();
        }
    }

    public record DiskEstimate(
            long usableBytes,
            long backupBytes,
            long estimatedNextBackupBytes,
            long requiredHeadroomBytes
    ) {
        public DiskEstimate {
            if (usableBytes < -1L || backupBytes < -1L || estimatedNextBackupBytes < -1L
                    || requiredHeadroomBytes < -1L) {
                throw new IllegalArgumentException("Disk estimates must be non-negative or -1 when unknown");
            }
        }

        public static DiskEstimate unknown() {
            return new DiskEstimate(-1L, -1L, -1L, -1L);
        }

        public boolean sufficient() {
            return usableBytes < 0L || requiredHeadroomBytes < 0L || usableBytes >= requiredHeadroomBytes;
        }
    }

    private static String fallback(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
