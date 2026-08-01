package com.nightsta69.delvefold.diagnostics;

import java.util.Comparator;
import java.util.List;

/**
 * Immutable, transport-neutral snapshot used by the doctor command, screen, and export.
 *
 * <p>The model intentionally contains no filesystem paths, network addresses, confirmation tokens, player records, or
 * complete configuration documents. Integrations should translate validation results into stable codes before adding
 * them to a report.
 *
 * @param formatVersion diagnostic document format; must equal {@link #CURRENT_FORMAT_VERSION}
 * @param generatedAtEpochMillis report generation time in milliseconds since the Unix epoch
 * @param versions redacted mod, game, loader, API, protocol, and configuration versions
 * @param dimensions immutable statuses sorted by dimension ID
 * @param profile active ore-profile validation summary
 * @param pendingOperations immutable lifecycle-operation summaries sorted by creation time and operation ID
 * @param backups backup catalog and verification summary
 * @param retention retention preview and most recent automatic-run summary
 * @param disk non-authoritative byte estimates, using {@code -1} for unknown values
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
        DiskEstimate disk) {
    /** Current version of the redacted diagnostic report format. */
    public static final int CURRENT_FORMAT_VERSION = 1;

    /**
     * Validates and normalizes an immutable diagnostic snapshot.
     *
     * <p>Null sections receive conservative unknown or empty values. Collection sections are defensively copied and
     * sorted, so later source-list mutations cannot alter the report.
     *
     * @param formatVersion diagnostic document format; must equal {@link #CURRENT_FORMAT_VERSION}
     * @param generatedAtEpochMillis report generation time in milliseconds since the Unix epoch
     * @param versions version and protocol summary, or {@code null} for unknown values
     * @param dimensions dimension summaries, or {@code null} for an empty immutable list
     * @param profile profile summary, or {@code null} for an unknown profile
     * @param pendingOperations lifecycle summaries, or {@code null} for an empty immutable list
     * @param backups backup summary, or {@code null} for an empty catalog
     * @param retention retention summary, or {@code null} for disabled retention
     * @param disk byte estimates, or {@code null} for unknown estimates
     * @throws IllegalArgumentException if the format is unsupported or the timestamp is negative
     */
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
        pendingOperations = sortedCopy(
                pendingOperations,
                Comparator.comparingLong(PendingOperationStatus::createdAtEpochMillis)
                        .thenComparing(PendingOperationStatus::operationId));
        backups = backups == null ? BackupHealth.empty() : backups;
        retention = retention == null ? RetentionPreview.disabled() : retention;
        disk = disk == null ? DiskEstimate.unknown() : disk;
    }

    /**
     * Creates a report with disabled retention diagnostics for source compatibility with older integrations.
     *
     * @param formatVersion diagnostic document format; must equal {@link #CURRENT_FORMAT_VERSION}
     * @param generatedAtEpochMillis report generation time in milliseconds since the Unix epoch
     * @param versions version and protocol summary
     * @param dimensions dimension summaries
     * @param profile active profile summary
     * @param pendingOperations lifecycle-operation summaries
     * @param backups backup catalog summary
     * @param disk non-authoritative byte estimates
     * @throws IllegalArgumentException if any delegated report invariant is violated
     */
    public DoctorReport(
            int formatVersion,
            long generatedAtEpochMillis,
            VersionInfo versions,
            List<DimensionStatus> dimensions,
            ProfileHealth profile,
            List<PendingOperationStatus> pendingOperations,
            BackupHealth backups,
            DiskEstimate disk) {
        this(
                formatVersion,
                generatedAtEpochMillis,
                versions,
                dimensions,
                profile,
                pendingOperations,
                backups,
                RetentionPreview.disabled(),
                disk);
    }

    /**
     * Reports whether every modeled subsystem currently passes its conservative health criteria.
     *
     * @return {@code true} when all dimensions are active and profile, backup, retention, and disk checks are healthy
     */
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

    /**
     * Redacted software and compatibility versions active when the report was built.
     *
     * @param delvefold Delvefold version string
     * @param minecraft Minecraft version string
     * @param neoForge NeoForge version string
     * @param publicApi Delvefold public API version
     * @param networkProtocol Delvefold network protocol version
     * @param configSchema Delvefold configuration schema version
     */
    public record VersionInfo(
            String delvefold, String minecraft, String neoForge, int publicApi, int networkProtocol, int configSchema) {
        /**
         * Normalizes textual versions and validates numeric versions.
         *
         * @param delvefold Delvefold version string, or blank for {@code unknown}
         * @param minecraft Minecraft version string, or blank for {@code unknown}
         * @param neoForge NeoForge version string, or blank for {@code unknown}
         * @param publicApi non-negative public API version
         * @param networkProtocol non-negative network protocol version
         * @param configSchema non-negative configuration schema version
         * @throws IllegalArgumentException if any numeric version is negative
         */
        public VersionInfo {
            delvefold = fallback(delvefold);
            minecraft = fallback(minecraft);
            neoForge = fallback(neoForge);
            if (publicApi < 0 || networkProtocol < 0 || configSchema < 0) {
                throw new IllegalArgumentException("Version numbers must not be negative");
            }
        }

        /**
         * Creates a conservative placeholder when runtime version discovery is unavailable.
         *
         * @return a version summary with unknown text and zero numeric versions
         */
        public static VersionInfo unknown() {
            return new VersionInfo("unknown", "unknown", "unknown", 0, 0, 0);
        }
    }

    /**
     * Status of one managed dimension without exposing its save path.
     *
     * @param dimensionId namespaced dimension identifier
     * @param terrain configured terrain description
     * @param state current loading or availability state
     */
    public record DimensionStatus(String dimensionId, String terrain, DimensionState state) {
        /**
         * Normalizes a dimension summary.
         *
         * @param dimensionId namespaced dimension identifier, or blank for {@code unknown}
         * @param terrain configured terrain description, or blank for {@code unknown}
         * @param state state value, or {@code null} to conservatively report {@link DimensionState#MISSING}
         */
        public DimensionStatus {
            dimensionId = fallback(dimensionId);
            terrain = fallback(terrain);
            state = state == null ? DimensionState.MISSING : state;
        }
    }

    /** Loading state of a managed dimension at report-generation time. */
    public enum DimensionState {
        /** The dimension is currently loaded and available. */
        ACTIVE,
        /** The dimension is configured or known but is not currently loaded. */
        UNLOADED,
        /** The dimension is absent or could not be resolved. */
        MISSING
    }

    /**
     * Validation and effectiveness summary for the active ore profile.
     *
     * @param activeProfileId logical profile ID, never a configuration file path
     * @param revision non-negative configuration revision
     * @param enabledRules number of enabled ore rules
     * @param totalRules total number of ore rules
     * @param errorCount number of error-level validation issues
     * @param warningCount number of warning-level validation issues
     * @param ineffectiveTargets immutable, deterministically sorted ineffective target summaries
     * @param findings immutable, deterministically sorted profile findings
     */
    public record ProfileHealth(
            String activeProfileId,
            long revision,
            int enabledRules,
            int totalRules,
            long errorCount,
            long warningCount,
            List<IneffectiveTarget> ineffectiveTargets,
            List<Finding> findings) {
        /**
         * Validates counts and defensively sorts profile details.
         *
         * @param activeProfileId logical profile ID, or blank for {@code unknown}
         * @param revision non-negative configuration revision
         * @param enabledRules enabled rule count, not greater than {@code totalRules}
         * @param totalRules non-negative total rule count
         * @param errorCount non-negative error count
         * @param warningCount non-negative warning count
         * @param ineffectiveTargets target summaries, or {@code null} for an empty list
         * @param findings profile findings, or {@code null} for an empty list
         * @throws IllegalArgumentException if counts are negative or enabled rules exceed total rules
         */
        public ProfileHealth {
            activeProfileId = fallback(activeProfileId);
            if (revision < 0L
                    || enabledRules < 0
                    || totalRules < 0
                    || enabledRules > totalRules
                    || errorCount < 0L
                    || warningCount < 0L) {
                throw new IllegalArgumentException("Profile health counts are inconsistent");
            }
            ineffectiveTargets = sortedCopy(
                    ineffectiveTargets,
                    Comparator.comparing(IneffectiveTarget::ruleId)
                            .thenComparing(IneffectiveTarget::targetId)
                            .thenComparing(IneffectiveTarget::reasonCode));
            findings = sortedCopy(
                    findings,
                    Comparator.comparing(Finding::severity)
                            .thenComparing(Finding::code)
                            .thenComparing(Finding::objectId));
        }

        /**
         * Creates an empty placeholder when profile diagnostics are unavailable.
         *
         * @return a zero-count profile summary with the ID {@code unknown}
         */
        public static ProfileHealth unknown() {
            return new ProfileHealth("unknown", 0L, 0, 0, 0L, 0L, List.of(), List.of());
        }

        /**
         * Reports whether the profile has no error count and no error-severity finding.
         *
         * @return {@code true} when no modeled profile errors exist
         */
        public boolean healthy() {
            return errorCount == 0L && findings.stream().noneMatch(finding -> finding.severity() == Severity.ERROR);
        }
    }

    /**
     * Stable-code explanation for an ore target that cannot affect the active terrain or registry state.
     *
     * @param ruleId logical ore-rule ID
     * @param targetId block or tag resource identifier
     * @param reasonCode stable diagnostic reason code
     */
    public record IneffectiveTarget(String ruleId, String targetId, String reasonCode) {
        /**
         * Normalizes an ineffective-target summary.
         *
         * @param ruleId logical rule ID, or blank for {@code unknown}
         * @param targetId resource identifier, or blank for {@code unknown}
         * @param reasonCode stable reason code, or blank for {@code unknown}
         */
        public IneffectiveTarget {
            ruleId = fallback(ruleId);
            targetId = fallback(targetId);
            reasonCode = fallback(reasonCode);
        }
    }

    /**
     * Stable validation finding associated with a logical configuration object.
     *
     * @param severity finding severity
     * @param code stable diagnostic code
     * @param objectId logical object ID, never a filesystem path
     */
    public record Finding(Severity severity, String code, String objectId) {
        /**
         * Normalizes a profile finding.
         *
         * @param severity severity, or {@code null} for {@link Severity#WARNING}
         * @param code stable code, or blank for {@code unknown}
         * @param objectId logical object ID, or blank for {@code unknown}
         */
        public Finding {
            severity = severity == null ? Severity.WARNING : severity;
            code = fallback(code);
            objectId = fallback(objectId);
        }
    }

    /** Severity assigned to a stable diagnostic finding. */
    public enum Severity {
        /** A condition that makes the affected configuration invalid or unusable. */
        ERROR,
        /** A condition that deserves attention but does not necessarily prevent use. */
        WARNING,
        /** Informational context that does not reduce health by itself. */
        INFO
    }

    /**
     * Redacted status of a restart-journaled lifecycle operation.
     *
     * @param operationId opaque logical operation ID
     * @param operation stable operation name
     * @param state stable journal phase or state
     * @param createdAtEpochMillis creation time in milliseconds since the Unix epoch
     */
    public record PendingOperationStatus(
            String operationId, String operation, String state, long createdAtEpochMillis) {
        /**
         * Normalizes an operation status and validates its timestamp.
         *
         * @param operationId logical operation ID, or blank for {@code unknown}
         * @param operation operation name, or blank for {@code unknown}
         * @param state journal state, or blank for {@code unknown}
         * @param createdAtEpochMillis non-negative creation time in milliseconds since the Unix epoch
         * @throws IllegalArgumentException if the timestamp is negative
         */
        public PendingOperationStatus {
            operationId = fallback(operationId);
            operation = fallback(operation);
            state = fallback(state);
            if (createdAtEpochMillis < 0L) {
                throw new IllegalArgumentException("Pending-operation timestamp must not be negative");
            }
        }
    }

    /**
     * Aggregate backup-catalog and manifest-verification health.
     *
     * @param total total cataloged backups
     * @param verified backups with current successful verification receipts
     * @param invalid backups known to be invalid
     * @param legacy backups that still require legacy validation and manifest creation
     * @param pinned backups excluded from retention pruning
     * @param totalBytes aggregate backup size in bytes
     * @param problems immutable, deterministically sorted problem summaries
     */
    public record BackupHealth(
            int total,
            int verified,
            int invalid,
            int legacy,
            int pinned,
            long totalBytes,
            List<BackupProblem> problems) {
        /**
         * Validates aggregate counts and defensively sorts backup problems.
         *
         * @param total non-negative total backup count
         * @param verified non-negative verified count not greater than {@code total}
         * @param invalid non-negative invalid count not greater than {@code total}
         * @param legacy non-negative legacy count not greater than {@code total}
         * @param pinned non-negative pinned count not greater than {@code total}
         * @param totalBytes non-negative aggregate size in bytes
         * @param problems problem summaries, or {@code null} for an empty list
         * @throws IllegalArgumentException if counts are negative, exceed totals, overlap beyond the total, or byte
         *     size is negative
         */
        public BackupHealth {
            if (total < 0
                    || verified < 0
                    || invalid < 0
                    || legacy < 0
                    || pinned < 0
                    || verified > total
                    || invalid > total
                    || legacy > total
                    || pinned > total
                    || (long) verified + invalid + legacy > total
                    || totalBytes < 0L) {
                throw new IllegalArgumentException("Backup health counts are inconsistent");
            }
            problems = sortedCopy(
                    problems, Comparator.comparing(BackupProblem::backupId).thenComparing(BackupProblem::reasonCode));
        }

        /**
         * Creates the healthy summary for an empty backup catalog.
         *
         * @return a zero-count backup summary
         */
        public static BackupHealth empty() {
            return new BackupHealth(0, 0, 0, 0, 0, 0L, List.of());
        }

        /**
         * Reports whether no invalid backup or explicit backup problem is known.
         *
         * @return {@code true} when the invalid count and problem list are both empty
         */
        public boolean healthy() {
            return invalid == 0 && problems.isEmpty();
        }
    }

    /**
     * Redacted problem associated with one logical backup ID.
     *
     * @param backupId logical catalog ID, never an absolute path
     * @param state stable backup state
     * @param reasonCode stable diagnostic reason code
     */
    public record BackupProblem(String backupId, String state, String reasonCode) {
        /**
         * Normalizes a backup problem.
         *
         * @param backupId logical backup ID, or blank for {@code unknown}
         * @param state stable state, or blank for {@code unknown}
         * @param reasonCode stable reason code, or blank for {@code unknown}
         */
        public BackupProblem {
            backupId = fallback(backupId);
            state = fallback(state);
            reasonCode = fallback(reasonCode);
        }
    }

    /**
     * Read-only forecast of retention pruning plus the most recently recorded automatic run.
     *
     * @param enabled whether automatic retention is configured
     * @param beforeCount backup count before forecast pruning
     * @param afterCount estimated backup count after forecast pruning
     * @param beforeBytes aggregate bytes before forecast pruning
     * @param afterBytes estimated aggregate bytes after forecast pruning
     * @param constraintsSatisfied whether configured limits can be met without pruning protected backups
     * @param prunes immutable proposed-prune details
     * @param warnings immutable stable warning codes
     * @param lastRun most recently recorded automatic retention run
     */
    public record RetentionPreview(
            boolean enabled,
            int beforeCount,
            int afterCount,
            long beforeBytes,
            long afterBytes,
            boolean constraintsSatisfied,
            List<RetentionPrune> prunes,
            List<String> warnings,
            RetentionRun lastRun) {
        /**
         * Validates estimates and creates immutable, deterministic retention details.
         *
         * @param enabled whether automatic retention is configured
         * @param beforeCount non-negative pre-prune count
         * @param afterCount non-negative post-prune estimate not greater than {@code beforeCount}
         * @param beforeBytes non-negative pre-prune bytes
         * @param afterBytes non-negative post-prune estimate not greater than {@code beforeBytes}
         * @param constraintsSatisfied whether configured limits are forecast to be satisfied
         * @param prunes proposed prunes, or {@code null} for an empty list
         * @param warnings stable warning codes, or {@code null} for an empty list
         * @param lastRun last run summary, or {@code null} when none is available
         * @throws IllegalArgumentException if counts or bytes are inconsistent, or disabled retention proposes changes
         */
        public RetentionPreview {
            if (beforeCount < 0
                    || afterCount < 0
                    || afterCount > beforeCount
                    || beforeBytes < 0L
                    || afterBytes < 0L
                    || afterBytes > beforeBytes) {
                throw new IllegalArgumentException("Retention preview counts are inconsistent");
            }
            prunes = sortedCopy(
                    prunes,
                    Comparator.comparingLong(RetentionPrune::createdAtEpochMillis)
                            .thenComparing(RetentionPrune::backupId));
            warnings = warnings == null
                    ? List.of()
                    : warnings.stream().map(DoctorReport::fallback).sorted().toList();
            lastRun = lastRun == null ? RetentionRun.none() : lastRun;
            if (!enabled && (!prunes.isEmpty() || beforeCount != afterCount || beforeBytes != afterBytes)) {
                throw new IllegalArgumentException("Disabled retention cannot preview pruning");
            }
        }

        /**
         * Creates a preview with no automatic-run history for source compatibility with older callers.
         *
         * @param enabled whether automatic retention is configured
         * @param beforeCount backup count before forecast pruning
         * @param afterCount estimated backup count after forecast pruning
         * @param beforeBytes aggregate bytes before forecast pruning
         * @param afterBytes estimated aggregate bytes after forecast pruning
         * @param constraintsSatisfied whether configured limits can be met
         * @param prunes proposed prunes
         * @param warnings stable warning codes
         * @throws IllegalArgumentException if any delegated preview invariant is violated
         */
        public RetentionPreview(
                boolean enabled,
                int beforeCount,
                int afterCount,
                long beforeBytes,
                long afterBytes,
                boolean constraintsSatisfied,
                List<RetentionPrune> prunes,
                List<String> warnings) {
            this(
                    enabled,
                    beforeCount,
                    afterCount,
                    beforeBytes,
                    afterBytes,
                    constraintsSatisfied,
                    prunes,
                    warnings,
                    RetentionRun.none());
        }

        /**
         * Creates the neutral summary used when automatic retention is disabled.
         *
         * @return a disabled, zero-change, constraint-satisfied preview
         */
        public static RetentionPreview disabled() {
            return new RetentionPreview(false, 0, 0, 0L, 0L, true, List.of(), List.of(), RetentionRun.none());
        }

        /**
         * Returns the forecast space reclaimed by proposed pruning.
         *
         * @return {@code beforeBytes - afterBytes}, in bytes
         */
        public long reclaimableBytes() {
            return beforeBytes - afterBytes;
        }

        /**
         * Reports whether the forecast and recorded run satisfy constraints without recorded apply failures.
         *
         * @return {@code true} when retention requires no operational attention
         */
        public boolean healthy() {
            return constraintsSatisfied
                    && (!lastRun.available() || lastRun.constraintsSatisfied())
                    && (!lastRun.applyRecorded() || lastRun.failureCount() == 0);
        }
    }

    /**
     * Bounded summary of the most recent automatic retention evaluation and optional apply result.
     *
     * @param available whether a run has been recorded
     * @param evaluatedAtEpochMillis evaluation time in milliseconds since the Unix epoch
     * @param enabled whether retention was enabled for the evaluation
     * @param beforeCount backup count before evaluation
     * @param afterCount forecast backup count after proposed pruning
     * @param proposedCount total number of proposed prunes, which may exceed the bounded detail list
     * @param proposals immutable bounded proposal details
     * @param protectedCount number of backups protected from pruning
     * @param constraintsSatisfied whether the evaluated plan met configured constraints
     * @param applyRecorded whether an apply attempt was recorded for this evaluation
     * @param appliedCount number of backups successfully pruned
     * @param failureCount number of prune failures
     * @param warnings immutable stable warning codes
     */
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
            List<String> warnings) {
        /**
         * Validates counts and creates immutable, deterministic run details.
         *
         * @param available whether a run has been recorded
         * @param evaluatedAtEpochMillis non-negative evaluation time in milliseconds since the Unix epoch
         * @param enabled whether retention was enabled for the run
         * @param beforeCount non-negative count before evaluation
         * @param afterCount non-negative forecast count not greater than {@code beforeCount}
         * @param proposedCount non-negative total proposal count
         * @param proposals bounded proposal details, or {@code null} for an empty list
         * @param protectedCount non-negative protected-backup count
         * @param constraintsSatisfied whether the evaluated plan met configured constraints
         * @param applyRecorded whether an apply result accompanies the evaluation
         * @param appliedCount non-negative successful prune count
         * @param failureCount non-negative failure count
         * @param warnings stable warning codes, or {@code null} for an empty list
         * @throws IllegalArgumentException if counts are inconsistent, unavailable state contains results, or detailed
         *     proposals exceed {@code proposedCount}
         */
        public RetentionRun {
            if (evaluatedAtEpochMillis < 0L
                    || beforeCount < 0
                    || afterCount < 0
                    || afterCount > beforeCount
                    || proposedCount < 0
                    || protectedCount < 0
                    || appliedCount < 0
                    || failureCount < 0) {
                throw new IllegalArgumentException("Retention-run counts are inconsistent");
            }
            proposals = sortedCopy(
                    proposals,
                    Comparator.comparing(RetentionProposal::backupId)
                            .thenComparing(proposal -> String.join(",", proposal.reasons())));
            warnings = warnings == null
                    ? List.of()
                    : warnings.stream().map(DoctorReport::fallback).sorted().toList();
            if (!available
                    && (proposedCount != 0
                            || !proposals.isEmpty()
                            || appliedCount != 0
                            || failureCount != 0
                            || applyRecorded)) {
                throw new IllegalArgumentException("Unavailable retention run cannot contain results");
            }
            if (proposals.size() > proposedCount) {
                throw new IllegalArgumentException("Retention-run proposals exceed the reported count");
            }
        }

        /**
         * Creates the neutral summary used before an automatic retention evaluation has run.
         *
         * @return an unavailable, zero-count run summary
         */
        public static RetentionRun none() {
            return new RetentionRun(false, 0L, false, 0, 0, 0, List.of(), 0, true, false, 0, 0, List.of());
        }
    }

    /**
     * Bounded retention proposal detail for one logical backup.
     *
     * @param backupId logical backup ID, never an absolute path
     * @param reasons immutable stable prune-reason codes
     */
    public record RetentionProposal(String backupId, List<String> reasons) {
        /**
         * Normalizes and sorts a proposal.
         *
         * @param backupId logical backup ID, or blank for {@code unknown}
         * @param reasons stable reason codes, or {@code null} for an empty list
         */
        public RetentionProposal {
            backupId = fallback(backupId);
            reasons = reasons == null
                    ? List.of()
                    : reasons.stream().map(DoctorReport::fallback).sorted().toList();
        }
    }

    /**
     * Proposed retention prune with the estimated age and storage effect needed for operator review.
     *
     * @param backupId logical backup ID, never an absolute path
     * @param createdAtEpochMillis backup creation time in milliseconds since the Unix epoch
     * @param sizeBytes backup size in bytes
     * @param reasons immutable stable prune-reason codes
     */
    public record RetentionPrune(String backupId, long createdAtEpochMillis, long sizeBytes, List<String> reasons) {
        /**
         * Validates and normalizes a proposed prune.
         *
         * @param backupId logical backup ID, or blank for {@code unknown}
         * @param createdAtEpochMillis non-negative backup creation time in milliseconds since the Unix epoch
         * @param sizeBytes non-negative estimated backup size in bytes
         * @param reasons stable reason codes, or {@code null} for an empty list
         * @throws IllegalArgumentException if the timestamp or size is negative
         */
        public RetentionPrune {
            backupId = fallback(backupId);
            if (createdAtEpochMillis < 0L || sizeBytes < 0L) {
                throw new IllegalArgumentException("Retention prune values must not be negative");
            }
            reasons = reasons == null
                    ? List.of()
                    : reasons.stream().map(DoctorReport::fallback).sorted().toList();
        }
    }

    /**
     * Non-authoritative storage estimates used to warn operators before backup-intensive operations.
     *
     * @param usableBytes filesystem-reported usable bytes, or {@code -1} when unknown
     * @param backupBytes estimated bytes already occupied by backups, or {@code -1} when unknown
     * @param estimatedNextBackupBytes estimated bytes for the next backup, or {@code -1} when unknown
     * @param requiredHeadroomBytes estimated required free space in bytes, or {@code -1} when unknown
     */
    public record DiskEstimate(
            long usableBytes, long backupBytes, long estimatedNextBackupBytes, long requiredHeadroomBytes) {
        /**
         * Validates byte estimates while preserving {@code -1} as the unknown sentinel.
         *
         * @param usableBytes usable bytes, or {@code -1}
         * @param backupBytes current backup bytes, or {@code -1}
         * @param estimatedNextBackupBytes next-backup estimate in bytes, or {@code -1}
         * @param requiredHeadroomBytes estimated required free bytes, or {@code -1}
         * @throws IllegalArgumentException if any value is less than {@code -1}
         */
        public DiskEstimate {
            if (usableBytes < -1L
                    || backupBytes < -1L
                    || estimatedNextBackupBytes < -1L
                    || requiredHeadroomBytes < -1L) {
                throw new IllegalArgumentException("Disk estimates must be non-negative or -1 when unknown");
            }
        }

        /**
         * Creates a disk estimate with every value unknown.
         *
         * @return an estimate containing {@code -1} in every byte field
         */
        public static DiskEstimate unknown() {
            return new DiskEstimate(-1L, -1L, -1L, -1L);
        }

        /**
         * Conservatively reports whether known usable space meets known required headroom.
         *
         * <p>Unknown capacity or requirement does not alone mark the report unhealthy because no authoritative
         * insufficiency has been observed.
         *
         * @return {@code false} only when both values are known and usable bytes are below required headroom
         */
        public boolean sufficient() {
            return usableBytes < 0L || requiredHeadroomBytes < 0L || usableBytes >= requiredHeadroomBytes;
        }
    }

    private static String fallback(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
