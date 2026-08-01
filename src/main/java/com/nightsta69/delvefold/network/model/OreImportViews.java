package com.nightsta69.delvefold.network.model;

import com.nightsta69.delvefold.config.importer.OreImportModels.DiffStatus;
import com.nightsta69.delvefold.config.importer.OreImportModels.Evidence;
import com.nightsta69.delvefold.config.importer.OreImportModels.HostKind;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.validation.IssueSeverity;
import com.nightsta69.delvefold.network.ProtocolLimits;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Bounded, display-only views for the server-authoritative ore importer.
 *
 * <p>Every list container is defensively copied into an unmodifiable view. Session tokens remain player-bound server
 * capabilities: displaying or returning one never replaces permission, expiry, binding, or revision checks.
 */
public final class OreImportViews {
    private OreImportViews() {}

    /**
     * Immutable page of ore groups discovered by an authoritative scan.
     *
     * @param scanToken bounded server-issued token for the player-owned scan session
     * @param expectedOreRevision non-negative ore revision captured when the scan began
     * @param baseProfileId profile against which imports will be compared
     * @param page zero-based page represented by {@code groups}
     * @param pageCount positive total page count derived from {@code totalGroups}
     * @param totalGroups bounded number of groups across all pages
     * @param scannedBlocks bounded number of registry blocks examined
     * @param truncated whether discovery omitted entries because of a server-side bound
     * @param groups bounded, immutable group page with distinct identifiers
     */
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
        /**
         * Validates token, revision, paging metadata, counts, and group uniqueness and takes ownership of a list copy.
         *
         * @param scanToken server-issued scan token
         * @param expectedOreRevision ore revision captured when scanning began
         * @param baseProfileId comparison profile identifier
         * @param page zero-based page index
         * @param pageCount declared total page count
         * @param totalGroups declared total group count
         * @param scannedBlocks number of blocks examined
         * @param truncated whether discovery was truncated
         * @param groups group page to copy
         * @throws IllegalArgumentException if text, counts, page relationships, page size, or identifiers violate
         *     protocol invariants
         * @throws NullPointerException if the group list contains a null element
         */
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

    /**
     * Immutable scan grouping of related registry blocks and server-produced classification evidence.
     *
     * @param id bounded scan-session group identifier
     * @param namespace bounded source namespace
     * @param material bounded inferred material name
     * @param evidence non-null evidence supporting the grouping
     * @param reviewRequired whether server heuristics require explicit user review
     * @param candidates bounded, immutable, non-empty candidates with distinct block identifiers
     */
    public record GroupView(
            String id,
            String namespace,
            String material,
            Evidence evidence,
            boolean reviewRequired,
            List<CandidateView> candidates) {
        /**
         * Validates identifiers and evidence and takes an immutable, bounded copy of the candidates.
         *
         * @param id group identifier
         * @param namespace source namespace
         * @param material inferred material name
         * @param evidence grouping evidence
         * @param reviewRequired whether explicit review is required
         * @param candidates candidate list to copy
         * @throws IllegalArgumentException if an identifier is invalid or candidates are empty or duplicate a block
         * @throws NullPointerException if evidence is null or the candidate list contains a null element
         */
        public GroupView {
            id = OreImportViews.id(id, "group ID");
            namespace = OreImportViews.id(namespace, "group namespace");
            material = OreImportViews.id(material, "group material");
            Objects.requireNonNull(evidence, "evidence");
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

    /**
     * Immutable candidate block within a discovered ore group.
     *
     * @param blockId bounded nonblank block identifier
     * @param replaceTag optional bounded replacement-target tag
     * @param hostKind non-null inferred host classification
     * @param evidence non-null evidence supporting the candidate
     */
    public record CandidateView(String blockId, String replaceTag, HostKind hostKind, Evidence evidence) {
        /**
         * Validates candidate identifiers and requires classification and evidence values.
         *
         * @param blockId candidate block identifier
         * @param replaceTag optional replacement-target tag; null becomes empty text
         * @param hostKind inferred host classification
         * @param evidence candidate evidence
         * @throws IllegalArgumentException if an identifier violates protocol bounds
         * @throws NullPointerException if host kind or evidence is null
         */
        public CandidateView(String blockId, @Nullable String replaceTag, HostKind hostKind, Evidence evidence) {
            blockId = OreImportViews.id(blockId, "candidate block ID");
            replaceTag = OreImportViews.optionalId(replaceTag, "replacement tag");
            Objects.requireNonNull(hostKind, "hostKind");
            Objects.requireNonNull(evidence, "evidence");
            this.blockId = blockId;
            this.replaceTag = replaceTag;
            this.hostKind = hostKind;
            this.evidence = evidence;
        }
    }

    /**
     * Immutable page of a server-validated ore-import preview.
     *
     * @param commitToken bounded server-issued token for the player-owned preview session
     * @param expectedOreRevision non-negative ore revision captured when the preview was computed
     * @param baseProfileId profile against which the import was compared
     * @param page zero-based page represented by {@code diff}
     * @param pageCount positive total page count derived from {@code totalDiffEntries}
     * @param totalDiffEntries bounded number of diff entries across all pages
     * @param valid whether authoritative validation permits committing the preview
     * @param addedRuleCount non-negative number of rules the plan would add
     * @param diff bounded, immutable diff page with distinct group identifiers
     * @param workloads bounded, immutable per-terrain workload deltas
     * @param issues bounded, immutable validation issue summaries
     * @param truncated whether server-side bounds omitted display detail
     */
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
        /**
         * Validates token, revision, paging metadata, counts, and uniqueness and takes immutable list copies.
         *
         * @param commitToken server-issued preview token
         * @param expectedOreRevision ore revision captured when previewing began
         * @param baseProfileId comparison profile identifier
         * @param page zero-based page index
         * @param pageCount declared total page count
         * @param totalDiffEntries declared total diff-entry count
         * @param valid whether the import plan passed validation
         * @param addedRuleCount number of rules the plan would add
         * @param diff diff page to copy
         * @param workloads workload deltas to copy
         * @param issues validation issues to copy
         * @param truncated whether display detail was truncated
         * @throws IllegalArgumentException if text, counts, metrics, paging relationships, page size, or identifiers
         *     violate protocol invariants
         * @throws NullPointerException if any copied list contains a null element
         */
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

    /**
     * Immutable per-group difference produced by an ore-import preview.
     *
     * @param groupId bounded nonblank scan group identifier
     * @param status non-null disposition of the group
     * @param ruleId optional bounded target rule identifier
     * @param addedBlocks bounded, immutable block identifiers that would be added
     * @param skippedBlocks bounded, immutable block identifiers omitted from the plan
     * @param message bounded display explanation
     */
    public record DiffView(
            String groupId,
            DiffStatus status,
            String ruleId,
            List<String> addedBlocks,
            List<String> skippedBlocks,
            String message) {
        /**
         * Validates identifiers and status and takes immutable copies of bounded block lists.
         *
         * @param groupId scan group identifier
         * @param status group disposition
         * @param ruleId optional target rule identifier; null becomes empty text
         * @param addedBlocks block identifiers to copy; null becomes empty
         * @param skippedBlocks skipped block identifiers to copy; null becomes empty
         * @param message display explanation; null becomes empty text
         * @throws IllegalArgumentException if text or identifier bounds are violated
         * @throws NullPointerException if status is null or a copied block list contains a null element
         */
        public DiffView(
                String groupId,
                DiffStatus status,
                @Nullable String ruleId,
                @Nullable List<String> addedBlocks,
                @Nullable List<String> skippedBlocks,
                @Nullable String message) {
            groupId = OreImportViews.id(groupId, "diff group ID");
            Objects.requireNonNull(status, "status");
            ruleId = OreImportViews.optionalId(ruleId, "diff rule ID");
            addedBlocks = OreImportViews.ids(addedBlocks, ProtocolLimits.MAX_VARIANTS, "added blocks");
            skippedBlocks = OreImportViews.ids(skippedBlocks, ProtocolLimits.MAX_VARIANTS, "skipped blocks");
            message = OreImportViews.text(message, ProtocolLimits.MAX_IMPORT_MESSAGE_LENGTH);
            this.groupId = groupId;
            this.status = status;
            this.ruleId = ruleId;
            this.addedBlocks = addedBlocks;
            this.skippedBlocks = skippedBlocks;
            this.message = message;
        }
    }

    /**
     * Immutable before-and-after workload metrics for one terrain mode.
     *
     * @param terrain non-null terrain mode
     * @param beforeAttempts finite, non-negative placement attempts per chunk before import
     * @param beforeWorkUnits finite, non-negative estimated work units per chunk before import
     * @param afterAttempts finite, non-negative placement attempts per chunk after import
     * @param afterWorkUnits finite, non-negative estimated work units per chunk after import
     */
    public record TerrainDeltaView(
            TerrainMode terrain,
            double beforeAttempts,
            double beforeWorkUnits,
            double afterAttempts,
            double afterWorkUnits) {
        /**
         * Requires a terrain mode and validates every metric as finite and non-negative.
         *
         * @param terrain terrain mode
         * @param beforeAttempts placement attempts per chunk before import
         * @param beforeWorkUnits estimated work units per chunk before import
         * @param afterAttempts placement attempts per chunk after import
         * @param afterWorkUnits estimated work units per chunk after import
         * @throws IllegalArgumentException if any metric is negative or non-finite
         * @throws NullPointerException if terrain is null
         */
        public TerrainDeltaView {
            Objects.requireNonNull(terrain, "terrain");
            beforeAttempts = OreImportViews.metric(beforeAttempts);
            beforeWorkUnits = OreImportViews.metric(beforeWorkUnits);
            afterAttempts = OreImportViews.metric(afterAttempts);
            afterWorkUnits = OreImportViews.metric(afterWorkUnits);
        }

        /**
         * Returns the non-negative increase in placement attempts.
         *
         * @return {@code max(0, afterAttempts - beforeAttempts)} attempts per chunk
         */
        public double addedAttempts() {
            return Math.max(0.0D, afterAttempts - beforeAttempts);
        }

        /**
         * Returns the non-negative increase in estimated work units.
         *
         * @return {@code max(0, afterWorkUnits - beforeWorkUnits)} estimated work units per chunk
         */
        public double addedWorkUnits() {
            return Math.max(0.0D, afterWorkUnits - beforeWorkUnits);
        }
    }

    /**
     * Immutable, bounded validation issue suitable for preview display.
     *
     * @param severity non-null issue severity
     * @param code bounded nonblank validation code
     * @param path bounded issue location, or empty text
     * @param message bounded display message, or empty text
     */
    public record ValidationIssueView(IssueSeverity severity, String code, String path, String message) {
        /**
         * Requires severity and validates all textual fields against their protocol bounds.
         *
         * @param severity issue severity
         * @param code validation code
         * @param path issue location; null becomes empty text
         * @param message display message; null becomes empty text
         * @throws IllegalArgumentException if textual bounds are violated or the code is blank
         * @throws NullPointerException if severity is null
         */
        public ValidationIssueView(
                IssueSeverity severity, String code, @Nullable String path, @Nullable String message) {
            Objects.requireNonNull(severity, "severity");
            code = OreImportViews.id(code, "validation code");
            path = OreImportViews.text(path, ProtocolLimits.SHORT_TEXT_LENGTH);
            message = OreImportViews.text(message, ProtocolLimits.MAX_IMPORT_MESSAGE_LENGTH);
            this.severity = severity;
            this.code = code;
            this.path = path;
            this.message = message;
        }
    }

    private static List<String> ids(@Nullable List<String> values, int maximum, String label) {
        return limited(values, maximum, label).stream()
                .map(value -> id(value, label))
                .toList();
    }

    private static <T> List<T> limited(@Nullable List<T> values, int maximum, String label) {
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

    private static String optionalId(@Nullable String value, String label) {
        String safe = text(value, ProtocolLimits.ID_LENGTH).trim();
        if (safe.length() > ProtocolLimits.ID_LENGTH) {
            throw new IllegalArgumentException(label + " is too long");
        }
        return safe;
    }

    private static String text(@Nullable String value, int maximum) {
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
