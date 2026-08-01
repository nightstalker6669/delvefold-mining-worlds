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

    public DoctorReportBuilder(Clock clock) {
        this(clock.millis());
    }

    public DoctorReportBuilder(long generatedAtEpochMillis) {
        if (generatedAtEpochMillis < 0L) {
            throw new IllegalArgumentException("generatedAtEpochMillis must not be negative");
        }
        this.generatedAtEpochMillis = generatedAtEpochMillis;
    }

    public DoctorReportBuilder versions(String delvefold, String minecraft, String neoForge,
                                        int publicApi, int networkProtocol, int configSchema) {
        checkMutable();
        versions = new DoctorReport.VersionInfo(
                delvefold, minecraft, neoForge, publicApi, networkProtocol, configSchema);
        return this;
    }

    public DoctorReportBuilder addDimension(String dimensionId, String terrain,
                                            DoctorReport.DimensionState state) {
        checkMutable();
        dimensions.add(new DoctorReport.DimensionStatus(dimensionId, terrain, state));
        return this;
    }

    public DoctorReportBuilder profile(String activeProfileId, long revision,
                                       int enabledRules, int totalRules,
                                       long errorCount, long warningCount) {
        checkMutable();
        this.profileId = activeProfileId;
        this.profileRevision = revision;
        this.enabledRules = enabledRules;
        this.totalRules = totalRules;
        this.profileErrors = errorCount;
        this.profileWarnings = warningCount;
        return this;
    }

    public DoctorReportBuilder addIneffectiveTarget(String ruleId, String targetId, String reasonCode) {
        checkMutable();
        ineffectiveTargets.add(new DoctorReport.IneffectiveTarget(ruleId, targetId, reasonCode));
        return this;
    }

    public DoctorReportBuilder addProfileFinding(DoctorReport.Severity severity,
                                                 String code, String objectId) {
        checkMutable();
        profileFindings.add(new DoctorReport.Finding(severity, code, objectId));
        return this;
    }

    public DoctorReportBuilder addPendingOperation(String operationId, String operation,
                                                   String state, long createdAtEpochMillis) {
        checkMutable();
        pendingOperations.add(new DoctorReport.PendingOperationStatus(
                operationId, operation, state, createdAtEpochMillis));
        return this;
    }

    public DoctorReportBuilder backups(int total, int verified, int invalid, int legacy,
                                       int pinned, long totalBytes) {
        checkMutable();
        this.backupTotal = total;
        this.backupVerified = verified;
        this.backupInvalid = invalid;
        this.backupLegacy = legacy;
        this.backupPinned = pinned;
        this.backupBytes = totalBytes;
        return this;
    }

    public DoctorReportBuilder addBackupProblem(String backupId, String state, String reasonCode) {
        checkMutable();
        backupProblems.add(new DoctorReport.BackupProblem(backupId, state, reasonCode));
        return this;
    }

    public DoctorReportBuilder retention(DoctorReport.RetentionPreview preview) {
        checkMutable();
        retention = preview == null ? DoctorReport.RetentionPreview.disabled() : preview;
        return this;
    }

    public DoctorReportBuilder disk(long usableBytes, long backupBytes,
                                    long estimatedNextBackupBytes, long requiredHeadroomBytes) {
        checkMutable();
        disk = new DoctorReport.DiskEstimate(
                usableBytes, backupBytes, estimatedNextBackupBytes, requiredHeadroomBytes);
        return this;
    }

    public DoctorReportBuilder disk(DoctorReport.DiskEstimate estimate) {
        checkMutable();
        disk = estimate == null ? DoctorReport.DiskEstimate.unknown() : estimate;
        return this;
    }

    public DoctorReport build() {
        checkMutable();
        built = true;
        DoctorReport.ProfileHealth profile = new DoctorReport.ProfileHealth(
                profileId, profileRevision, enabledRules, totalRules, profileErrors, profileWarnings,
                ineffectiveTargets, profileFindings);
        DoctorReport.BackupHealth backups = new DoctorReport.BackupHealth(
                backupTotal, backupVerified, backupInvalid, backupLegacy, backupPinned,
                backupBytes, backupProblems);
        return new DoctorReport(DoctorReport.CURRENT_FORMAT_VERSION, generatedAtEpochMillis,
                versions, dimensions, profile, pendingOperations, backups, retention, disk);
    }

    private void checkMutable() {
        if (built) {
            throw new IllegalStateException("A DoctorReportBuilder may only build one report");
        }
    }
}
