package com.nightsta69.delvefold.diagnostics;

import com.nightsta69.delvefold.config.ConfigJson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Writes deterministic, field-whitelisted, redacted doctor-report JSON.
 *
 * <p>Only explicitly modeled export fields are serialized. Free-text identifiers pass through a bounded sanitizer that
 * removes path-like, socket-like, structured, control-character, and likely secret-bearing values. Export never
 * serializes complete configuration documents, player records, server addresses, confirmation tokens, or source paths.
 */
public final class DoctorReportExporter {
    /** Creates a redacting exporter with no retained filesystem or report state. */
    public DoctorReportExporter() {}

    private static final int MAX_SAFE_TEXT_LENGTH = 192;
    private static final Pattern IPV4_SOCKET =
            Pattern.compile("(?i)(?:^|[^0-9])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}:[0-9]{1,5}(?:$|[^0-9])");
    private static final Pattern HOST_SOCKET =
            Pattern.compile("(?i)(?:^|[^a-z0-9_.-])(?:[a-z0-9-]+\\.)+[a-z]{2,63}:[0-9]{1,5}(?:$|[^0-9])");
    private static final Pattern IPV6_SOCKET = Pattern.compile("(?i)(?:[0-9a-f]{0,4}:){2,}[0-9a-f]{0,4}:?[0-9]{1,5}");
    private static final Pattern RESOURCE_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    /**
     * Atomically writes a redacted report into an existing exports directory.
     *
     * <p>The directory is never created implicitly. It and the final output must pass normalized containment and
     * symbolic-link checks. The generated filename contains the report timestamp in epoch milliseconds; an existing
     * file for the same timestamp is replaced only by the completed temporary file.
     *
     * @param exportsDirectory existing directory authorized for diagnostic exports
     * @param report immutable report to sanitize and serialize
     * @return the normalized absolute path of the completed JSON export
     * @throws IllegalArgumentException if either argument is {@code null}
     * @throws IOException if the directory or target fails safety checks, or temporary write or final move fails
     */
    public Path export(Path exportsDirectory, DoctorReport report) throws IOException {
        if (exportsDirectory == null || report == null) {
            throw new IllegalArgumentException("exportsDirectory and report are required");
        }
        Path directory = exportsDirectory.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory)) {
            throw new IOException("Doctor exports directory does not exist or failed safety checks");
        }
        String filename = "delvefold-doctor-%019d.json".formatted(report.generatedAtEpochMillis());
        Path target = directory.resolve(filename).normalize();
        @Nullable Path parent = target.getParent();
        if (parent == null || !parent.equals(directory) || Files.isSymbolicLink(target)) {
            throw new IOException("Doctor export target failed path-containment checks");
        }

        Path temporary = Files.createTempFile(directory, ".delvefold-doctor-", ".tmp");
        try {
            Files.writeString(temporary, toRedactedJson(report) + System.lineSeparator(), StandardCharsets.UTF_8);
            moveAtomically(temporary, target);
            return target;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * Produces the exact field-whitelisted, redacted JSON used by {@link #export(Path, DoctorReport)}.
     *
     * <p>This method performs no filesystem I/O. Byte counts remain byte estimates from the input report; sanitization
     * does not make them authoritative disk measurements.
     *
     * @param report immutable report to sanitize and serialize
     * @return compact JSON containing only the export model's approved fields
     * @throws IllegalArgumentException if {@code report} is {@code null}
     */
    public String toRedactedJson(DoctorReport report) {
        if (report == null) {
            throw new IllegalArgumentException("report is required");
        }
        ExportReport export = new ExportReport(
                report.formatVersion(),
                true,
                report.generatedAtEpochMillis(),
                redact(report.versions()),
                report.dimensions().stream().map(DoctorReportExporter::redact).toList(),
                redact(report.profile()),
                report.pendingOperations().stream()
                        .map(DoctorReportExporter::redact)
                        .toList(),
                redact(report.backups()),
                redact(report.retention()),
                new ExportDisk(
                        report.disk().usableBytes(),
                        report.disk().backupBytes(),
                        report.disk().estimatedNextBackupBytes(),
                        report.disk().requiredHeadroomBytes(),
                        report.disk().sufficient()),
                report.healthy());
        return ConfigJson.GSON.toJson(export);
    }

    private static ExportVersions redact(DoctorReport.VersionInfo versions) {
        return new ExportVersions(
                safeText(versions.delvefold()),
                safeText(versions.minecraft()),
                safeText(versions.neoForge()),
                versions.publicApi(),
                versions.networkProtocol(),
                versions.configSchema());
    }

    private static ExportDimension redact(DoctorReport.DimensionStatus dimension) {
        return new ExportDimension(
                safeText(dimension.dimensionId()),
                safeText(dimension.terrain()),
                lower(dimension.state().name()));
    }

    private static ExportProfile redact(DoctorReport.ProfileHealth profile) {
        return new ExportProfile(
                safeText(profile.activeProfileId()),
                profile.revision(),
                profile.enabledRules(),
                profile.totalRules(),
                profile.errorCount(),
                profile.warningCount(),
                profile.ineffectiveTargets().stream()
                        .map(target -> new ExportIneffectiveTarget(
                                safeText(target.ruleId()), safeText(target.targetId()), safeText(target.reasonCode())))
                        .toList(),
                profile.findings().stream()
                        .map(finding -> new ExportFinding(
                                lower(finding.severity().name()),
                                safeText(finding.code()),
                                safeText(finding.objectId())))
                        .toList(),
                profile.healthy());
    }

    private static ExportPendingOperation redact(DoctorReport.PendingOperationStatus operation) {
        return new ExportPendingOperation(
                safeText(operation.operationId()), safeText(operation.operation()),
                safeText(operation.state()), operation.createdAtEpochMillis());
    }

    private static ExportBackups redact(DoctorReport.BackupHealth backups) {
        return new ExportBackups(
                backups.total(),
                backups.verified(),
                backups.invalid(),
                backups.legacy(),
                backups.pinned(),
                backups.totalBytes(),
                backups.problems().stream()
                        .map(problem -> new ExportBackupProblem(
                                safeText(problem.backupId()),
                                safeText(problem.state()),
                                safeText(problem.reasonCode())))
                        .toList(),
                backups.healthy());
    }

    private static ExportRetention redact(DoctorReport.RetentionPreview retention) {
        return new ExportRetention(
                retention.enabled(),
                retention.beforeCount(),
                retention.afterCount(),
                retention.beforeBytes(),
                retention.afterBytes(),
                retention.reclaimableBytes(),
                retention.constraintsSatisfied(),
                retention.healthy(),
                retention.prunes().stream()
                        .map(prune -> new ExportRetentionPrune(
                                safeText(prune.backupId()),
                                prune.createdAtEpochMillis(),
                                prune.sizeBytes(),
                                prune.reasons().stream()
                                        .map(DoctorReportExporter::safeText)
                                        .toList()))
                        .toList(),
                retention.warnings().stream()
                        .map(DoctorReportExporter::safeText)
                        .toList(),
                redact(retention.lastRun()));
    }

    private static ExportRetentionRun redact(DoctorReport.RetentionRun run) {
        return new ExportRetentionRun(
                run.available(),
                run.evaluatedAtEpochMillis(),
                run.enabled(),
                run.beforeCount(),
                run.afterCount(),
                run.proposedCount(),
                run.proposals().stream()
                        .map(proposal -> new ExportRetentionProposal(
                                safeText(proposal.backupId()),
                                proposal.reasons().stream()
                                        .map(DoctorReportExporter::safeText)
                                        .toList()))
                        .toList(),
                run.protectedCount(),
                run.constraintsSatisfied(),
                run.applyRecorded(),
                run.appliedCount(),
                run.failureCount(),
                run.warnings().stream().map(DoctorReportExporter::safeText).toList());
    }

    static String safeText(@Nullable String input) {
        if (input == null || input.isBlank()) {
            return "unknown";
        }
        String value = input.strip();
        String lower = value.toLowerCase(Locale.ROOT);
        boolean filesystemLike = value.startsWith("/")
                || value.startsWith("\\")
                || value.startsWith("../")
                || value.contains("/../")
                || value.matches("(?i)^[a-z]:\\\\.*")
                || (!RESOURCE_ID.matcher(value).matches()
                        && (lower.contains("/home/")
                                || lower.contains("/users/")
                                || lower.contains("serverconfig/")
                                || lower.contains("world/dimensions/")));
        boolean secretLike = lower.contains("confirmation_token")
                || lower.contains("confirmation token")
                || lower.contains("token=")
                || lower.contains("password=")
                || lower.contains("secret=");
        boolean structured = value.indexOf('{') >= 0
                || value.indexOf('}') >= 0
                || value.indexOf('[') >= 0
                || value.indexOf(']') >= 0
                || value.contains("://");
        boolean control = value.chars().anyMatch(character -> Character.isISOControl(character));
        if (value.length() > MAX_SAFE_TEXT_LENGTH
                || filesystemLike
                || secretLike
                || structured
                || control
                || IPV4_SOCKET.matcher(value).find()
                || HOST_SOCKET.matcher(value).find()
                || IPV6_SOCKET.matcher(value).matches()) {
            return "[redacted]";
        }
        return value;
    }

    private static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private static void moveAtomically(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private record ExportReport(
            int formatVersion,
            boolean redacted,
            long generatedAtEpochMillis,
            ExportVersions versions,
            List<ExportDimension> dimensions,
            ExportProfile profile,
            List<ExportPendingOperation> pendingOperations,
            ExportBackups backups,
            ExportRetention retention,
            ExportDisk disk,
            boolean healthy) {}

    private record ExportVersions(
            String delvefold,
            String minecraft,
            String neoForge,
            int publicApi,
            int networkProtocol,
            int configSchema) {}

    private record ExportDimension(String dimensionId, String terrain, String state) {}

    private record ExportProfile(
            String activeProfileId,
            long revision,
            int enabledRules,
            int totalRules,
            long errorCount,
            long warningCount,
            List<ExportIneffectiveTarget> ineffectiveTargets,
            List<ExportFinding> findings,
            boolean healthy) {}

    private record ExportIneffectiveTarget(String ruleId, String targetId, String reasonCode) {}

    private record ExportFinding(String severity, String code, String objectId) {}

    private record ExportPendingOperation(
            String operationId, String operation, String state, long createdAtEpochMillis) {}

    private record ExportBackups(
            int total,
            int verified,
            int invalid,
            int legacy,
            int pinned,
            long totalBytes,
            List<ExportBackupProblem> problems,
            boolean healthy) {}

    private record ExportBackupProblem(String backupId, String state, String reasonCode) {}

    private record ExportRetention(
            boolean enabled,
            int beforeCount,
            int afterCount,
            long beforeBytes,
            long afterBytes,
            long reclaimableBytes,
            boolean constraintsSatisfied,
            boolean healthy,
            List<ExportRetentionPrune> prunes,
            List<String> warnings,
            ExportRetentionRun lastRun) {}

    private record ExportRetentionPrune(
            String backupId, long createdAtEpochMillis, long sizeBytes, List<String> reasons) {}

    private record ExportRetentionRun(
            boolean available,
            long evaluatedAtEpochMillis,
            boolean enabled,
            int beforeCount,
            int afterCount,
            int proposedCount,
            List<ExportRetentionProposal> proposals,
            int protectedCount,
            boolean constraintsSatisfied,
            boolean applyRecorded,
            int appliedCount,
            int failureCount,
            List<String> warnings) {}

    private record ExportRetentionProposal(String backupId, List<String> reasons) {}

    private record ExportDisk(
            long usableBytes,
            long backupBytes,
            long estimatedNextBackupBytes,
            long requiredHeadroomBytes,
            boolean sufficient) {}
}
