package com.nightsta69.delvefold.network;

import com.nightsta69.delvefold.config.importer.OreImportModels.DiffEntry;
import com.nightsta69.delvefold.config.importer.OreImportModels.DiscoveryResult;
import com.nightsta69.delvefold.config.importer.OreImportModels.Group;
import com.nightsta69.delvefold.config.importer.OreImportModels.Plan;
import com.nightsta69.delvefold.config.importer.OreImportSessionService.IssuedPreview;
import com.nightsta69.delvefold.config.importer.OreImportSessionService.IssuedScan;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.validation.ConfigIssue;
import com.nightsta69.delvefold.config.validation.ConfigIssueMessages;
import com.nightsta69.delvefold.network.model.OreImportViews.CandidateView;
import com.nightsta69.delvefold.network.model.OreImportViews.DiffView;
import com.nightsta69.delvefold.network.model.OreImportViews.GroupView;
import com.nightsta69.delvefold.network.model.OreImportViews.PreviewView;
import com.nightsta69.delvefold.network.model.OreImportViews.ScanView;
import com.nightsta69.delvefold.network.model.OreImportViews.TerrainDeltaView;
import com.nightsta69.delvefold.network.model.OreImportViews.ValidationIssueView;
import java.util.List;

/** Converts server-only discovery/plan sessions into bounded display pages. */
public final class OreImportNetworkViews {
    private OreImportNetworkViews() {}

    /**
     * Projects an issued server scan session into one bounded client page.
     *
     * @param issued immutable issued scan and opaque session token
     * @param revision non-negative ore revision against which the scan was created
     * @param baseProfileId profile used as the import base
     * @param requestedPage requested zero-based page, clamped to the available range
     * @return immutable display-only scan view
     */
    public static ScanView scan(IssuedScan issued, long revision, String baseProfileId, int requestedPage) {
        return scan(issued.scanToken(), issued.discovery(), revision, baseProfileId, requestedPage);
    }

    /**
     * Projects discovery output and its server-owned token into one bounded client page.
     *
     * @param token opaque scan token; never a filesystem path or confirmation secret
     * @param discovery immutable server discovery result
     * @param revision non-negative source ore revision
     * @param baseProfileId profile used as the import base
     * @param requestedPage requested zero-based page, clamped to the available range
     * @return immutable display-only scan view
     */
    public static ScanView scan(
            String token, DiscoveryResult discovery, long revision, String baseProfileId, int requestedPage) {
        int total = discovery.groups().size();
        int pages = pageCount(total, ProtocolLimits.MAX_IMPORT_GROUPS_PER_PAGE);
        int page = Math.clamp(requestedPage, 0, pages - 1);
        int start = page * ProtocolLimits.MAX_IMPORT_GROUPS_PER_PAGE;
        int end = Math.min(total, start + ProtocolLimits.MAX_IMPORT_GROUPS_PER_PAGE);
        List<GroupView> groups = discovery.groups().subList(start, end).stream()
                .map(OreImportNetworkViews::group)
                .toList();
        boolean detailTruncated = discovery.truncated()
                || discovery.groups().stream()
                        .anyMatch(group -> group.candidates().size() > ProtocolLimits.MAX_VARIANTS);
        return new ScanView(
                token, revision, baseProfileId, page, pages, total, discovery.scannedBlocks(), detailTruncated, groups);
    }

    /**
     * Projects an issued non-mutating preview session into one bounded client page.
     *
     * @param issued immutable plan and opaque commit token
     * @param revision non-negative source ore revision
     * @param requestedPage requested zero-based page, clamped to the available range
     * @return immutable display-only preview view
     */
    public static PreviewView preview(IssuedPreview issued, long revision, int requestedPage) {
        return preview(issued.commitToken(), issued.plan(), revision, requestedPage);
    }

    /**
     * Projects a validated plan into a bounded diff, workload, and issue page without activating a profile.
     *
     * @param token opaque server commit token binding a later create request to this plan
     * @param plan immutable import plan
     * @param revision non-negative source ore revision
     * @param requestedPage requested zero-based page, clamped to the available range
     * @return immutable display-only preview view
     */
    public static PreviewView preview(String token, Plan plan, long revision, int requestedPage) {
        int total = plan.diff().size();
        int pages = pageCount(total, ProtocolLimits.MAX_IMPORT_DIFF_PER_PAGE);
        int page = Math.clamp(requestedPage, 0, pages - 1);
        int start = page * ProtocolLimits.MAX_IMPORT_DIFF_PER_PAGE;
        int end = Math.min(total, start + ProtocolLimits.MAX_IMPORT_DIFF_PER_PAGE);
        List<DiffView> diff = plan.diff().subList(start, end).stream()
                .map(OreImportNetworkViews::diff)
                .toList();
        List<TerrainDeltaView> workloads = java.util.Arrays.stream(TerrainMode.values())
                .map(terrain -> new TerrainDeltaView(
                        terrain,
                        plan.beforeWorkload().forTerrain(terrain).attemptsPerChunk(),
                        plan.beforeWorkload().forTerrain(terrain).workUnits(),
                        plan.afterWorkload().forTerrain(terrain).attemptsPerChunk(),
                        plan.afterWorkload().forTerrain(terrain).workUnits()))
                .toList();
        List<ConfigIssue> sourceIssues = plan.validation().issues();
        int issueLimit = ProtocolLimits.MAX_IMPORT_ISSUES;
        List<ValidationIssueView> issues = sourceIssues.stream()
                .limit(issueLimit)
                .map(issue -> new ValidationIssueView(
                        issue.severity(),
                        bounded(issue.code(), ProtocolLimits.ID_LENGTH),
                        bounded(issue.path(), ProtocolLimits.SHORT_TEXT_LENGTH),
                        bounded(ConfigIssueMessages.encode(issue), ProtocolLimits.MAX_IMPORT_MESSAGE_LENGTH)))
                .toList();
        return new PreviewView(
                token,
                revision,
                plan.baseProfileId(),
                page,
                pages,
                total,
                plan.valid(),
                plan.addedRuleCount(),
                diff,
                workloads,
                issues,
                sourceIssues.size() > issueLimit);
    }

    private static GroupView group(Group group) {
        List<CandidateView> candidates = group.candidates().stream()
                .limit(ProtocolLimits.MAX_VARIANTS)
                .map(candidate -> new CandidateView(
                        candidate.blockId(), candidate.replaceTag(), candidate.hostKind(), candidate.evidence()))
                .toList();
        return new GroupView(
                group.id(), group.namespace(), group.material(), group.evidence(), group.reviewRequired(), candidates);
    }

    private static DiffView diff(DiffEntry entry) {
        return new DiffView(
                entry.groupId(),
                entry.status(),
                entry.ruleId(),
                entry.addedBlocks(),
                entry.skippedBlocks(),
                bounded(entry.message(), ProtocolLimits.MAX_IMPORT_MESSAGE_LENGTH));
    }

    private static int pageCount(int count, int pageSize) {
        return Math.max(1, (count + pageSize - 1) / pageSize);
    }

    private static String bounded(String value, int maximum) {
        String safe = value == null ? "" : value;
        if (safe.codePointCount(0, safe.length()) <= maximum) {
            return safe;
        }
        return safe.substring(0, safe.offsetByCodePoints(0, maximum));
    }
}
