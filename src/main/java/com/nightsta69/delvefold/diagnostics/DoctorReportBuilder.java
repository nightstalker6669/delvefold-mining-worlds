package com.nightsta69.delvefold.diagnostics;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/** Mutable, single-use assembler for a transport-neutral {@link DoctorReport}. */
public final class DoctorReportBuilder {
    private final long generatedAtEpochMillis;
    private DoctorReport.VersionInfo versions = DoctorReport.VersionInfo.unknown();
    private final List<DoctorReport.DimensionStatus> dimensions = new ArrayList<>();
    private String profileId = "unknown";
    private long profileRevision;
    private int enabledRules;
    private int totalRules;
    private long profileErrors;
    private long profileWarnings;
    private final List<DoctorReport.IneffectiveTarget> ineffectiveTargets = new ArrayList<>();
    private final List<DoctorReport.Finding> profileFindings = new ArrayList<>();
    private final List<DoctorReport.PendingOperationStatus> pendingOperations = new ArrayList<>();
    private int backupTotal;
    private int backupVerified;
    private int backupInvalid;
    private int backupLegacy;
    private int backupPinned;
    private long backupBytes;
    private final List<DoctorReport.BackupProblem> backupProblems = new ArrayList<>();
    private DoctorReport.RetentionPreview retention = DoctorReport.RetentionPreview.disabled();
    private DoctorReport.DiskEstimate disk = DoctorReport.DiskEstimate.unknown();
    private boolean built;

    /**
     * Creates a single-use builder timestamped from the supplied clock.
     *
     * @param clock clock used to obtain milliseconds since the Unix epoch
     * @throws NullPointerException if {@code clock} is {@code null}
     * @throws IllegalArgumentException if the clock returns a negative timestamp
     */
    public DoctorReportBuilder(Clock clock) {
        this(clock.millis());
    }

    /**
     * Creates a single-use builder with an explicit generation timestamp.
     *
     * @param generatedAtEpochMillis report generation time in milliseconds since the Unix epoch
     * @throws IllegalArgumentException if {@code generatedAtEpochMillis} is negative
     */
    public DoctorReportBuilder(long generatedAtEpochMillis) {
        if (generatedAtEpochMillis < 0L) {
            throw new IllegalArgumentException("generatedAtEpochMillis must not be negative");
        }
        this.generatedAtEpochMillis = generatedAtEpochMillis;
    }

    /**
     * Replaces the software and compatibility version summary.
     *
     * @param delvefold Delvefold version string
     * @param minecraft Minecraft version string
     * @param neoForge NeoForge version string
     * @param publicApi non-negative public API version
     * @param networkProtocol non-negative network protocol version
     * @param configSchema non-negative configuration schema version
     * @return this builder
     * @throws IllegalArgumentException if a numeric version is negative
     * @throws IllegalStateException if {@link #build()} has already been called
     */
    public DoctorReportBuilder versions(
            String delvefold, String minecraft, String neoForge, int publicApi, int networkProtocol, int configSchema) {
        checkMutable();
        versions =
                new DoctorReport.VersionInfo(delvefold, minecraft, neoForge, publicApi, networkProtocol, configSchema);
        return this;
    }

    /**
     * Adds one managed-dimension status.
     *
     * @param dimensionId namespaced dimension identifier
     * @param terrain configured terrain description
     * @param state current loading state
     * @return this builder
     * @throws IllegalStateException if {@link #build()} has already been called
     */
    public DoctorReportBuilder addDimension(String dimensionId, String terrain, DoctorReport.DimensionState state) {
        checkMutable();
        dimensions.add(new DoctorReport.DimensionStatus(dimensionId, terrain, state));
        return this;
    }

    /**
     * Replaces aggregate active-profile counts.
     *
     * @param activeProfileId logical profile ID, never a configuration path
     * @param revision non-negative configuration revision
     * @param enabledRules enabled rule count
     * @param totalRules total rule count
     * @param errorCount error-level validation count
     * @param warningCount warning-level validation count
     * @return this builder
     * @throws IllegalStateException if {@link #build()} has already been called
     */
    public DoctorReportBuilder profile(
            String activeProfileId,
            long revision,
            int enabledRules,
            int totalRules,
            long errorCount,
            long warningCount) {
        checkMutable();
        this.profileId = activeProfileId;
        this.profileRevision = revision;
        this.enabledRules = enabledRules;
        this.totalRules = totalRules;
        this.profileErrors = errorCount;
        this.profileWarnings = warningCount;
        return this;
    }

    /**
     * Adds a stable-code explanation for an ineffective ore target.
     *
     * @param ruleId logical ore-rule ID
     * @param targetId target block or tag resource identifier
     * @param reasonCode stable diagnostic reason code
     * @return this builder
     * @throws IllegalStateException if {@link #build()} has already been called
     */
    public DoctorReportBuilder addIneffectiveTarget(String ruleId, String targetId, String reasonCode) {
        checkMutable();
        ineffectiveTargets.add(new DoctorReport.IneffectiveTarget(ruleId, targetId, reasonCode));
        return this;
    }

    /**
     * Adds a stable profile-validation finding.
     *
     * @param severity finding severity
     * @param code stable diagnostic code
     * @param objectId logical affected object ID
     * @return this builder
     * @throws IllegalStateException if {@link #build()} has already been called
     */
    public DoctorReportBuilder addProfileFinding(DoctorReport.Severity severity, String code, String objectId) {
        checkMutable();
        profileFindings.add(new DoctorReport.Finding(severity, code, objectId));
        return this;
    }

    /**
     * Adds a redacted lifecycle-journal summary.
     *
     * @param operationId opaque operation ID
     * @param operation stable operation name
     * @param state stable journal phase or state
     * @param createdAtEpochMillis creation time in milliseconds since the Unix epoch
     * @return this builder
     * @throws IllegalArgumentException if the timestamp is negative
     * @throws IllegalStateException if {@link #build()} has already been called
     */
    public DoctorReportBuilder addPendingOperation(
            String operationId, String operation, String state, long createdAtEpochMillis) {
        checkMutable();
        pendingOperations.add(
                new DoctorReport.PendingOperationStatus(operationId, operation, state, createdAtEpochMillis));
        return this;
    }

    /**
     * Replaces aggregate backup-catalog counts.
     *
     * @param total total cataloged backups
     * @param verified backups with current successful verification receipts
     * @param invalid known-invalid backups
     * @param legacy backups requiring legacy validation and manifest creation
     * @param pinned retention-protected backups
     * @param totalBytes aggregate backup size in bytes
     * @return this builder
     * @throws IllegalStateException if {@link #build()} has already been called
     */
    public DoctorReportBuilder backups(int total, int verified, int invalid, int legacy, int pinned, long totalBytes) {
        checkMutable();
        this.backupTotal = total;
        this.backupVerified = verified;
        this.backupInvalid = invalid;
        this.backupLegacy = legacy;
        this.backupPinned = pinned;
        this.backupBytes = totalBytes;
        return this;
    }

    /**
     * Adds a redacted backup problem.
     *
     * @param backupId logical backup ID, never an absolute path
     * @param state stable backup state
     * @param reasonCode stable diagnostic reason code
     * @return this builder
     * @throws IllegalStateException if {@link #build()} has already been called
     */
    public DoctorReportBuilder addBackupProblem(String backupId, String state, String reasonCode) {
        checkMutable();
        backupProblems.add(new DoctorReport.BackupProblem(backupId, state, reasonCode));
        return this;
    }

    /**
     * Replaces retention diagnostics, using a disabled summary for {@code null}.
     *
     * @param preview retention forecast and last-run summary, or {@code null}
     * @return this builder
     * @throws IllegalStateException if {@link #build()} has already been called
     */
    public DoctorReportBuilder retention(DoctorReport.RetentionPreview preview) {
        checkMutable();
        retention = preview == null ? DoctorReport.RetentionPreview.disabled() : preview;
        return this;
    }

    /**
     * Replaces non-authoritative disk estimates.
     *
     * @param usableBytes usable filesystem bytes, or {@code -1} when unknown
     * @param backupBytes current backup bytes, or {@code -1} when unknown
     * @param estimatedNextBackupBytes estimated next-backup bytes, or {@code -1} when unknown
     * @param requiredHeadroomBytes estimated required free bytes, or {@code -1} when unknown
     * @return this builder
     * @throws IllegalArgumentException if any value is less than {@code -1}
     * @throws IllegalStateException if {@link #build()} has already been called
     */
    public DoctorReportBuilder disk(
            long usableBytes, long backupBytes, long estimatedNextBackupBytes, long requiredHeadroomBytes) {
        checkMutable();
        disk = new DoctorReport.DiskEstimate(usableBytes, backupBytes, estimatedNextBackupBytes, requiredHeadroomBytes);
        return this;
    }

    /**
     * Replaces disk estimates, using an unknown summary for {@code null}.
     *
     * @param estimate non-authoritative byte estimates, or {@code null}
     * @return this builder
     * @throws IllegalStateException if {@link #build()} has already been called
     */
    public DoctorReportBuilder disk(DoctorReport.DiskEstimate estimate) {
        checkMutable();
        disk = estimate == null ? DoctorReport.DiskEstimate.unknown() : estimate;
        return this;
    }

    /**
     * Builds the immutable, deterministically sorted report and permanently consumes this builder.
     *
     * @return the completed redacted diagnostic snapshot
     * @throws IllegalArgumentException if accumulated aggregate values violate report invariants
     * @throws IllegalStateException if this builder has already built a report
     */
    public DoctorReport build() {
        checkMutable();
        built = true;
        DoctorReport.ProfileHealth profile = new DoctorReport.ProfileHealth(
                profileId,
                profileRevision,
                enabledRules,
                totalRules,
                profileErrors,
                profileWarnings,
                ineffectiveTargets,
                profileFindings);
        DoctorReport.BackupHealth backups = new DoctorReport.BackupHealth(
                backupTotal, backupVerified, backupInvalid, backupLegacy, backupPinned, backupBytes, backupProblems);
        return new DoctorReport(
                DoctorReport.CURRENT_FORMAT_VERSION,
                generatedAtEpochMillis,
                versions,
                dimensions,
                profile,
                pendingOperations,
                backups,
                retention,
                disk);
    }

    private void checkMutable() {
        if (built) {
            throw new IllegalStateException("A DoctorReportBuilder may only build one report");
        }
    }
}
