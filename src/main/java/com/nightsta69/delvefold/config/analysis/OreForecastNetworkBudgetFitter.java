package com.nightsta69.delvefold.config.analysis;

import com.nightsta69.delvefold.config.analysis.OreProfileForecast.HeightSample;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast.ReferenceIssue;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast.ReferenceSummary;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast.RuleForecast;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast.TerrainTotals;
import com.nightsta69.delvefold.config.model.TerrainMode;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Fits ordered forecast diagnostics into the immutable forecast network budget. */
final class OreForecastNetworkBudgetFitter {
    private OreForecastNetworkBudgetFitter() {}

    /**
     * Fits diagnostics without changing page contents or the documented admission order.
     *
     * <p>The first issue for each visible rule is considered before whole-profile details. Remaining rule issues are
     * then considered in rule and issue order. Every selected list is therefore a stable prefix of its source list.
     *
     * @param profileId bounded profile identifier
     * @param profileRevision profile revision
     * @param activeTerrain active terrain, or {@code null}
     * @param terrainTotals terrain totals in enum order
     * @param overlay ascending active-terrain height samples
     * @param totalRuleCount bounded total rule count
     * @param page selected zero-based page
     * @param pageSize selected page size
     * @param pageCount total page count
     * @param sourceRules complete ordered source page
     * @param sourceReferences whole-profile reference summary
     * @param sourceTruncated whether analysis was already truncated
     * @return network-bounded immutable forecast
     */
    static OreProfileForecast fit(
            String profileId,
            long profileRevision,
            @Nullable TerrainMode activeTerrain,
            List<TerrainTotals> terrainTotals,
            List<HeightSample> overlay,
            int totalRuleCount,
            int page,
            int pageSize,
            int pageCount,
            List<RuleForecast> sourceRules,
            ReferenceSummary sourceReferences,
            boolean sourceTruncated) {
        List<List<ReferenceIssue>> selectedRuleIssues = new ArrayList<>(sourceRules.size());
        List<RuleForecast> emptyRules = new ArrayList<>(sourceRules.size());
        for (RuleForecast rule : sourceRules) {
            selectedRuleIssues.add(new ArrayList<>());
            emptyRules.add(withIssues(
                    rule, List.of(), rule.truncated() || !rule.issues().isEmpty()));
        }
        ReferenceSummary emptyReferences = withDetails(
                sourceReferences,
                List.of(),
                sourceReferences.truncated() || !sourceReferences.details().isEmpty());
        OreProfileForecast base = new OreProfileForecast(
                OreProfileForecast.CURRENT_FORMAT_VERSION,
                profileId,
                profileRevision,
                activeTerrain,
                terrainTotals,
                overlay,
                totalRuleCount,
                page,
                pageSize,
                pageCount,
                emptyRules,
                emptyReferences,
                sourceTruncated
                        || sourceRules.stream().anyMatch(rule -> !rule.issues().isEmpty())
                        || !sourceReferences.details().isEmpty());
        int remaining = OreProfileForecast.MAX_ESTIMATED_NETWORK_BYTES - base.estimatedNetworkBytes();

        // Keep at least the first diagnostic for each visible rule before spending the rest globally.
        for (int index = 0; index < sourceRules.size(); index++) {
            List<ReferenceIssue> issues = sourceRules.get(index).issues();
            if (!issues.isEmpty() && issues.getFirst().estimatedNetworkBytes() <= remaining) {
                selectedRuleIssues.get(index).add(issues.getFirst());
                remaining -= issues.getFirst().estimatedNetworkBytes();
            }
        }

        List<ReferenceIssue> selectedDetails = new ArrayList<>();
        for (ReferenceIssue issue : sourceReferences.details()) {
            if (issue.estimatedNetworkBytes() > remaining) {
                break;
            }
            selectedDetails.add(issue);
            remaining -= issue.estimatedNetworkBytes();
        }

        for (int ruleIndex = 0; ruleIndex < sourceRules.size(); ruleIndex++) {
            List<ReferenceIssue> issues = sourceRules.get(ruleIndex).issues();
            int start = selectedRuleIssues.get(ruleIndex).isEmpty() ? 0 : 1;
            for (int issueIndex = start; issueIndex < issues.size(); issueIndex++) {
                ReferenceIssue issue = issues.get(issueIndex);
                if (issue.estimatedNetworkBytes() > remaining) {
                    break;
                }
                selectedRuleIssues.get(ruleIndex).add(issue);
                remaining -= issue.estimatedNetworkBytes();
            }
        }

        List<RuleForecast> boundedRules = new ArrayList<>(sourceRules.size());
        boolean omitted = false;
        for (int index = 0; index < sourceRules.size(); index++) {
            RuleForecast source = sourceRules.get(index);
            List<ReferenceIssue> selected = List.copyOf(selectedRuleIssues.get(index));
            boolean ruleTruncated =
                    source.truncated() || selected.size() < source.issues().size();
            omitted |= selected.size() < source.issues().size();
            boundedRules.add(withIssues(source, selected, ruleTruncated));
        }
        boolean referenceTruncated = sourceReferences.truncated()
                || selectedDetails.size() < sourceReferences.details().size();
        omitted |= selectedDetails.size() < sourceReferences.details().size();
        ReferenceSummary boundedReferences = withDetails(sourceReferences, selectedDetails, referenceTruncated);
        return new OreProfileForecast(
                OreProfileForecast.CURRENT_FORMAT_VERSION,
                profileId,
                profileRevision,
                activeTerrain,
                terrainTotals,
                overlay,
                totalRuleCount,
                page,
                pageSize,
                pageCount,
                boundedRules,
                boundedReferences,
                sourceTruncated || omitted);
    }

    private static RuleForecast withIssues(RuleForecast source, List<ReferenceIssue> issues, boolean truncated) {
        return new RuleForecast(
                source.ruleIndex(),
                source.ruleId(),
                source.enabled(),
                source.required(),
                source.status(),
                source.configuredAttempts(),
                source.configuredWorkUnits(),
                source.effectiveAttempts(),
                source.effectiveWorkUnits(),
                source.targetCount(),
                source.effectiveOutputCount(),
                source.missingReferenceCount(),
                source.shadowedOutputCount(),
                issues,
                truncated);
    }

    private static ReferenceSummary withDetails(
            ReferenceSummary source, List<ReferenceIssue> details, boolean truncated) {
        return new ReferenceSummary(
                source.missingBlocks(),
                source.missingOutputTags(),
                source.missingHostTags(),
                source.invalidStates(),
                source.shadowedOutputs(),
                source.totalIssues(),
                details,
                truncated);
    }
}
