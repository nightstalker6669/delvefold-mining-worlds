package com.nightsta69.delvefold.network.model;

import com.nightsta69.delvefold.config.importer.OreImportModels.DiffStatus;
import com.nightsta69.delvefold.config.importer.OreImportModels.Evidence;
import com.nightsta69.delvefold.config.importer.OreImportModels.HostKind;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.validation.IssueSeverity;
import com.nightsta69.delvefold.network.ProtocolLimits;
import java.util.List;
import java.util.Objects;

/** Bounded, display-only views for the server-authoritative ore importer. */
public final class OreImportViews {
    private OreImportViews() {}

    public record ScanView(
            String scanToken,
            long expectedOreRevision,
            String baseProfileId,
            int page,
            int pageCount,
            int totalGroups,
            int scannedBlocks,
            boolean truncated,
            List<GroupView> groups) {
        public ScanView {
            scanToken = OreImportViews.token(scanToken);
            expectedOreRevision = OreImportViews.nonNegative(expectedOreRevision, "ore revision");
            baseProfileId = OreImportViews.id(baseProfileId, "base profile ID");
            page = OreImportViews.bounded(page, 0, ProtocolLimits.MAX_IMPORT_GROUPS, "scan page");
            pageCount = OreImportViews.bounded(pageCount, 1, ProtocolLimits.MAX_IMPORT_GROUPS, "scan page count");
            totalGroups = OreImportViews.bounded(totalGroups, 0, ProtocolLimits.MAX_IMPORT_GROUPS, "scan group count");
            scannedBlocks = OreImportViews.bounded(scannedBlocks, 0, 1_000_000, "scanned block count");
            groups = OreImportViews.limited(groups, ProtocolLimits.MAX_IMPORT_GROUPS_PER_PAGE, "scan page groups");
            int expectedPageCount = Math.max(
                    1,
                    (totalGroups + ProtocolLimits.MAX_IMPORT_GROUPS_PER_PAGE - 1)
                            / ProtocolLimits.MAX_IMPORT_GROUPS_PER_PAGE);
            if (pageCount != expectedPageCount || page >= pageCount) {
                throw new IllegalArgumentException("Invalid scan paging metadata");
            }
            int expectedPageSize = Math.min(
                    ProtocolLimits.MAX_IMPORT_GROUPS_PER_PAGE,
                    Math.max(0, totalGroups - page * ProtocolLimits.MAX_IMPORT_GROUPS_PER_PAGE));
            if (groups.size() != expectedPageSize) {
                throw new IllegalArgumentException("Scan page contents do not match its bounded total");
            }
            if (groups.stream().map(GroupView::id).distinct().count() != groups.size()) {
                throw new IllegalArgumentException("Scan page contains duplicate group IDs");
            }
        }
    }

    public record GroupView(
            String id,
            String namespace,
            String material,
            Evidence evidence,
            boolean reviewRequired,
            List<CandidateView> candidates) {
        public GroupView {
            id = OreImportViews.id(id, "group ID");
            namespace = OreImportViews.id(namespace, "group namespace");
            material = OreImportViews.id(material, "group material");
            evidence = Objects.requireNonNull(evidence, "evidence");
            candidates = OreImportViews.limited(candidates, ProtocolLimits.MAX_VARIANTS, "group candidates");
            if (candidates.isEmpty()
                    || candidates.stream()
                                    .map(CandidateView::blockId)
                                    .distinct()
                                    .count()
                            != candidates.size()) {
                throw new IllegalArgumentException("Import groups require distinct candidate blocks");
            }
        }
    }

    public record CandidateView(String blockId, String replaceTag, HostKind hostKind, Evidence evidence) {
        public CandidateView {
            blockId = OreImportViews.id(blockId, "candidate block ID");
            replaceTag = OreImportViews.optionalId(replaceTag, "replacement tag");
            hostKind = Objects.requireNonNull(hostKind, "hostKind");
            evidence = Objects.requireNonNull(evidence, "evidence");
        }
    }

    public record PreviewView(
            String commitToken,
            long expectedOreRevision,
            String baseProfileId,
            int page,
            int pageCount,
            int totalDiffEntries,
            boolean valid,
            long addedRuleCount,
            List<DiffView> diff,
            List<TerrainDeltaView> workloads,
            List<ValidationIssueView> issues,
            boolean truncated) {
        public PreviewView {
            commitToken = OreImportViews.token(commitToken);
            expectedOreRevision = OreImportViews.nonNegative(expectedOreRevision, "ore revision");
            baseProfileId = OreImportViews.id(baseProfileId, "base profile ID");
            page = OreImportViews.bounded(page, 0, ProtocolLimits.MAX_IMPORT_GROUPS, "preview page");
            pageCount = OreImportViews.bounded(pageCount, 1, ProtocolLimits.MAX_IMPORT_GROUPS, "preview page count");
            totalDiffEntries = OreImportViews.bounded(
                    totalDiffEntries, 0, ProtocolLimits.MAX_IMPORT_SELECTED_GROUPS, "preview diff count");
            addedRuleCount = OreImportViews.nonNegative(addedRuleCount, "added rule count");
            diff = OreImportViews.limited(diff, ProtocolLimits.MAX_IMPORT_DIFF_PER_PAGE, "preview diff page");
            workloads = OreImportViews.limited(workloads, TerrainMode.values().length, "preview workloads");
            issues = OreImportViews.limited(issues, ProtocolLimits.MAX_IMPORT_ISSUES, "preview issues");
            int expectedPageCount = Math.max(
                    1,
                    (totalDiffEntries + ProtocolLimits.MAX_IMPORT_DIFF_PER_PAGE - 1)
                            / ProtocolLimits.MAX_IMPORT_DIFF_PER_PAGE);
            if (pageCount != expectedPageCount || page >= pageCount) {
                throw new IllegalArgumentException("Invalid preview paging metadata");
            }
            int expectedPageSize = Math.min(
                    ProtocolLimits.MAX_IMPORT_DIFF_PER_PAGE,
                    Math.max(0, totalDiffEntries - page * ProtocolLimits.MAX_IMPORT_DIFF_PER_PAGE));
            if (diff.size() != expectedPageSize) {
                throw new IllegalArgumentException("Preview page contents do not match its bounded total");
            }
            if (diff.stream().map(DiffView::groupId).distinct().count() != diff.size()) {
                throw new IllegalArgumentException("Preview page contains duplicate group IDs");
            }
        }
    }

    public record DiffView(
            String groupId,
            DiffStatus status,
            String ruleId,
            List<String> addedBlocks,
            List<String> skippedBlocks,
            String message) {
        public DiffView {
            groupId = OreImportViews.id(groupId, "diff group ID");
            status = Objects.requireNonNull(status, "status");
            ruleId = OreImportViews.optionalId(ruleId, "diff rule ID");
            addedBlocks = OreImportViews.ids(addedBlocks, ProtocolLimits.MAX_VARIANTS, "added blocks");
            skippedBlocks = OreImportViews.ids(skippedBlocks, ProtocolLimits.MAX_VARIANTS, "skipped blocks");
            message = OreImportViews.text(message, ProtocolLimits.MAX_IMPORT_MESSAGE_LENGTH);
        }
    }

    public record TerrainDeltaView(
            TerrainMode terrain,
            double beforeAttempts,
            double beforeWorkUnits,
            double afterAttempts,
            double afterWorkUnits) {
        public TerrainDeltaView {
            terrain = Objects.requireNonNull(terrain, "terrain");
            beforeAttempts = OreImportViews.metric(beforeAttempts);
            beforeWorkUnits = OreImportViews.metric(beforeWorkUnits);
            afterAttempts = OreImportViews.metric(afterAttempts);
            afterWorkUnits = OreImportViews.metric(afterWorkUnits);
        }

        public double addedAttempts() {
            return Math.max(0.0D, afterAttempts - beforeAttempts);
        }

        public double addedWorkUnits() {
            return Math.max(0.0D, afterWorkUnits - beforeWorkUnits);
        }
    }

    public record ValidationIssueView(IssueSeverity severity, String code, String path, String message) {
        public ValidationIssueView {
            severity = Objects.requireNonNull(severity, "severity");
            code = OreImportViews.id(code, "validation code");
            path = OreImportViews.text(path, ProtocolLimits.SHORT_TEXT_LENGTH);
            message = OreImportViews.text(message, ProtocolLimits.MAX_IMPORT_MESSAGE_LENGTH);
        }
    }

    private static List<String> ids(List<String> values, int maximum, String label) {
        return limited(values, maximum, label).stream()
                .map(value -> id(value, label))
                .toList();
    }

    private static <T> List<T> limited(List<T> values, int maximum, String label) {
        List<T> safe = values == null ? List.of() : List.copyOf(values);
        if (safe.size() > maximum) {
            throw new IllegalArgumentException(label + " exceed " + maximum);
        }
        return safe;
    }

    private static String token(String value) {
        String safe = text(value, ProtocolLimits.MAX_IMPORT_TOKEN_LENGTH);
        if (safe.isBlank()) {
            throw new IllegalArgumentException("Import token cannot be blank");
        }
        return safe;
    }

    private static String id(String value, String label) {
        String safe = text(value, ProtocolLimits.ID_LENGTH).trim();
        if (safe.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return safe;
    }

    private static String optionalId(String value, String label) {
        String safe = text(value, ProtocolLimits.ID_LENGTH).trim();
        if (safe.length() > ProtocolLimits.ID_LENGTH) {
            throw new IllegalArgumentException(label + " is too long");
        }
        return safe;
    }

    private static String text(String value, int maximum) {
        String safe = value == null ? "" : value;
        if (safe.codePointCount(0, safe.length()) > maximum) {
            throw new IllegalArgumentException("Text exceeds " + maximum + " characters");
        }
        return safe;
    }

    private static int bounded(int value, int minimum, int maximum, String label) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("Invalid " + label + ": " + value);
        }
        return value;
    }

    private static long nonNegative(long value, String label) {
        if (value < 0L) {
            throw new IllegalArgumentException("Invalid " + label + ": " + value);
        }
        return value;
    }

    private static double metric(double value) {
        if (!Double.isFinite(value) || value < 0.0D) {
            throw new IllegalArgumentException("Import workload metrics must be finite and non-negative");
        }
        return value;
    }
}
