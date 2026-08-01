package com.nightsta69.delvefold.diagnostics;

import com.nightsta69.delvefold.admin.AdminLocalizedMessage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Renders a report as bounded, transport-neutral diagnostic lines in stable order.
 *
 * <p>The defaults fit the existing administration snapshot limits: at most 64 lines, at most 1,024 characters per line,
 * and at most 24 KiB of UTF-8 text across the complete result.
 */
public final class DoctorReportRenderer {
    /** Creates a stateless renderer for bounded Doctor report snapshots. */
    public DoctorReportRenderer() {}

    /** Maximum number of rendered lines, including a truncation summary when present. */
    public static final int MAX_LINES = 64;

    /** Maximum intended character count for each transport-neutral line. */
    public static final int MAX_LINE_CHARACTERS = 1024;

    /** Maximum aggregate UTF-8 payload size in bytes. */
    public static final int MAX_TOTAL_UTF8_BYTES = 24 * 1024;

    private static final int SUMMARY_RESERVE_BYTES = 128;

    /**
     * Renders a report as immutable localized-message envelopes in deterministic section order.
     *
     * <p>Free text is sanitized through the export redactor before transport. When line-count or aggregate UTF-8 byte
     * limits would be exceeded, details are omitted and one bounded truncation summary is appended. Rendering performs
     * no filesystem access and does not expose paths or unredacted configuration values.
     *
     * @param report immutable report to render
     * @return immutable lines bounded by {@link #MAX_LINES} and {@link #MAX_TOTAL_UTF8_BYTES}
     * @throws IllegalArgumentException if {@code report} is {@code null}
     */
    public List<String> render(DoctorReport report) {
        if (report == null) {
            throw new IllegalArgumentException("report is required");
        }
        LineCollector lines = new LineCollector();
        lines.add("message.delvefold.doctor.line.header", report.formatVersion(), report.generatedAtEpochMillis());
        lines.add("message.delvefold.doctor.line.status", report.healthy() ? "healthy" : "attention_required");
        lines.add(
                "message.delvefold.doctor.line.versions",
                safe(report.versions().delvefold()),
                safe(report.versions().minecraft()),
                safe(report.versions().neoForge()));
        lines.add(
                "message.delvefold.doctor.line.protocols",
                report.versions().publicApi(),
                report.versions().networkProtocol(),
                report.versions().configSchema());

        lines.add(
                "message.delvefold.doctor.line.dimensions", report.dimensions().size());
        for (DoctorReport.DimensionStatus dimension : report.dimensions()) {
            lines.add(
                    "message.delvefold.doctor.line.dimension",
                    safe(dimension.dimensionId()),
                    safe(dimension.terrain()),
                    lower(dimension.state().name()));
        }

        DoctorReport.ProfileHealth profile = report.profile();
        lines.add(
                "message.delvefold.doctor.line.profile",
                safe(profile.activeProfileId()),
                profile.revision(),
                profile.enabledRules(),
                profile.totalRules(),
                profile.errorCount(),
                profile.warningCount(),
                profile.healthy());
        for (DoctorReport.IneffectiveTarget target : profile.ineffectiveTargets()) {
            lines.add(
                    "message.delvefold.doctor.line.ineffective_target",
                    safe(target.ruleId()),
                    safe(target.targetId()),
                    safe(target.reasonCode()));
        }
        for (DoctorReport.Finding finding : profile.findings()) {
            lines.add(
                    "message.delvefold.doctor.line.profile_finding",
                    lower(finding.severity().name()),
                    safe(finding.code()),
                    safe(finding.objectId()));
        }

        lines.add(
                "message.delvefold.doctor.line.pending_operations",
                report.pendingOperations().size());
        for (DoctorReport.PendingOperationStatus operation : report.pendingOperations()) {
            lines.add(
                    "message.delvefold.doctor.line.pending_operation",
                    safe(operation.operationId()),
                    safe(operation.operation()),
                    safe(operation.state()),
                    operation.createdAtEpochMillis());
        }

        DoctorReport.BackupHealth backups = report.backups();
        lines.add(
                "message.delvefold.doctor.line.backups",
                backups.total(),
                backups.verified(),
                backups.invalid(),
                backups.legacy(),
                backups.pinned(),
                backups.totalBytes(),
                backups.healthy());
        for (DoctorReport.BackupProblem problem : backups.problems()) {
            lines.add(
                    "message.delvefold.doctor.line.backup_problem",
                    safe(problem.backupId()),
                    safe(problem.state()),
                    safe(problem.reasonCode()));
        }

        DoctorReport.RetentionPreview retention = report.retention();
        lines.add(
                "message.delvefold.doctor.line.retention",
                retention.enabled(),
                retention.beforeCount(),
                retention.afterCount(),
                retention.reclaimableBytes(),
                retention.constraintsSatisfied());
        for (DoctorReport.RetentionPrune prune : retention.prunes()) {
            lines.add(
                    "message.delvefold.doctor.line.retention_prune",
                    safe(prune.backupId()),
                    prune.createdAtEpochMillis(),
                    prune.sizeBytes(),
                    safe(String.join(",", prune.reasons())));
        }
        for (String warning : retention.warnings()) {
            lines.add("message.delvefold.doctor.line.retention_warning", safe(warning));
        }
        DoctorReport.RetentionRun run = retention.lastRun();
        lines.add(
                "message.delvefold.doctor.line.retention_last_run",
                run.available(),
                run.evaluatedAtEpochMillis(),
                run.enabled(),
                run.beforeCount(),
                run.afterCount(),
                run.proposedCount(),
                run.protectedCount(),
                run.constraintsSatisfied(),
                run.applyRecorded(),
                run.appliedCount(),
                run.failureCount());
        for (DoctorReport.RetentionProposal proposal : run.proposals()) {
            lines.add(
                    "message.delvefold.doctor.line.retention_last_proposal",
                    safe(proposal.backupId()),
                    safe(String.join(",", proposal.reasons())));
        }
        for (String warning : run.warnings()) {
            lines.add("message.delvefold.doctor.line.retention_last_warning", safe(warning));
        }

        DoctorReport.DiskEstimate disk = report.disk();
        lines.add(
                "message.delvefold.doctor.line.disk",
                disk.usableBytes(),
                disk.backupBytes(),
                disk.estimatedNextBackupBytes(),
                disk.requiredHeadroomBytes(),
                disk.sufficient());
        return lines.finish();
    }

    private static String safe(String value) {
        return DoctorReportExporter.safeText(value).replace(' ', '_');
    }

    private static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private static final class LineCollector {
        private final List<String> lines = new ArrayList<>();
        private int utf8Bytes;
        private int omitted;

        void add(String translationKey, Object... arguments) {
            String bounded = AdminLocalizedMessage.encode(translationKey, arguments);
            int bytes = bounded.getBytes(StandardCharsets.UTF_8).length;
            if (lines.size() >= MAX_LINES - 1 || utf8Bytes + bytes + SUMMARY_RESERVE_BYTES > MAX_TOTAL_UTF8_BYTES) {
                omitted++;
                return;
            }
            lines.add(bounded);
            utf8Bytes += bytes;
        }

        List<String> finish() {
            if (omitted > 0) {
                lines.add(AdminLocalizedMessage.encode("message.delvefold.doctor.line.truncated", omitted));
            }
            return List.copyOf(lines);
        }
    }
}
