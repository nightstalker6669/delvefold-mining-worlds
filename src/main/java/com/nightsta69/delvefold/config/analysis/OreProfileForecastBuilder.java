package com.nightsta69.delvefold.config.analysis;

import com.nightsta69.delvefold.config.analysis.OreProfileForecast.IssueKind;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast.IssueSeverity;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast.ReferenceSummary;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast.RuleForecast;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast.TerrainTotals;
import com.nightsta69.delvefold.config.model.BiomeFilter;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.model.OreRule;
import com.nightsta69.delvefold.config.model.TerrainMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Pure facade for bounded forecast pages from immutable profile and registry-analysis inputs. */
public final class OreProfileForecastBuilder {
    private OreProfileForecastBuilder() {}

    /**
     * Builds one server-authoritative, network-bounded forecast page.
     *
     * <p>Rules retain profile order, terrain totals retain {@link TerrainMode#values()} order, and height samples
     * retain ascending block-Y order. Floating-point totals are accumulated sequentially in rule, band, and height
     * order. Page inputs are clamped to the public forecast limits. Network fitting first reserves one diagnostic per
     * visible rule, then admits whole-profile reference details, then admits remaining diagnostics rule by rule; each
     * source retains its original order. Omitted data is reported through truncation flags. The supplied callbacks must
     * themselves be deterministic for identical registry and biome inputs.
     *
     * @param profileId profile identifier to expose, or {@code null}/blank to use the document identifier
     * @param profile immutable profile to analyze
     * @param activeTerrain initialized terrain, or {@code null} when no mining world has been initialized
     * @param biomeApplicability callback that determines whether a biome filter can match each terrain
     * @param targetAnalyzer callback that resolves output targets and reference issues for each rule
     * @param requestedPage zero-based requested page, clamped to the available page range
     * @param requestedPageSize requested rules per page, clamped to the supported network range
     * @return immutable forecast containing configured and effective attempts/work units per eligible chunk
     */
    public static OreProfileForecast build(
            @Nullable String profileId,
            OreProfileDocument profile,
            @Nullable TerrainMode activeTerrain,
            BiomeApplicability biomeApplicability,
            TargetAnalyzer targetAnalyzer,
            int requestedPage,
            int requestedPageSize) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(biomeApplicability, "biomeApplicability");
        Objects.requireNonNull(targetAnalyzer, "targetAnalyzer");
        int pageSize = Math.clamp(requestedPageSize, 1, OreProfileForecast.MAX_RULES_PER_PAGE);
        int totalRuleCount = Math.min(profile.rules().size(), OreProfileForecast.MAX_TOTAL_RULES);
        int pageCount = Math.max(1, (totalRuleCount + pageSize - 1) / pageSize);
        int page = Math.clamp(requestedPage, 0, pageCount - 1);

        List<OreForecastRuleAnalysis.AnalyzedRule> analyzedRules =
                OreForecastRuleAnalysis.analyzeRules(profile, totalRuleCount, targetAnalyzer);
        List<TerrainTotals> terrainTotals =
                OreForecastRuleAnalysis.terrainTotals(profile, analyzedRules, activeTerrain, biomeApplicability);
        List<OreProfileForecast.HeightSample> overlay = activeTerrain == null
                ? List.of()
                : OreForecastRuleAnalysis.heightOverlay(analyzedRules, activeTerrain, biomeApplicability);

        int fromIndex = page * pageSize;
        int toIndex = Math.min(totalRuleCount, fromIndex + pageSize);
        List<RuleForecast> rules = new ArrayList<>(toIndex - fromIndex);
        boolean truncated = profile.rules().size() > totalRuleCount;
        for (int ruleIndex = fromIndex; ruleIndex < toIndex; ruleIndex++) {
            RuleForecast forecast = OreForecastRuleAnalysis.ruleForecast(
                    analyzedRules.get(ruleIndex), activeTerrain, biomeApplicability);
            rules.add(forecast);
            truncated |= forecast.truncated();
        }

        ReferenceSummary references = OreForecastRuleAnalysis.referenceSummary(analyzedRules);
        truncated |= references.truncated();
        return OreForecastNetworkBudgetFitter.fit(
                OreForecastRuleAnalysis.boundedIdentifier(
                        profileId == null || profileId.isBlank() ? profile.profile() : profileId),
                profile.revision(),
                activeTerrain,
                terrainTotals,
                overlay,
                totalRuleCount,
                page,
                pageSize,
                pageCount,
                rules,
                references,
                truncated);
    }

    /** Determines whether a biome filter is effective for a terrain without exposing registry state in the result. */
    @FunctionalInterface
    public interface BiomeApplicability {
        /**
         * Tests whether the filter can match at least one biome relevant to the terrain.
         *
         * @param terrain terrain being forecast
         * @param filter rule biome filter to test
         * @return {@code true} when the rule may run in the terrain
         */
        boolean matches(TerrainMode terrain, BiomeFilter filter);
    }

    /** Resolves one ore rule's effective outputs and ordered registry-reference findings. */
    @FunctionalInterface
    public interface TargetAnalyzer {
        /**
         * Analyzes output targets without mutating the rule or registry.
         *
         * @param rule rule whose exact blocks, tags, host tags, and state constraints should be resolved
         * @return non-null bounded analysis for the rule
         */
        TargetAnalysis analyze(OreRule rule);
    }

    /**
     * Bounded target-resolution result supplied to the pure forecast builder.
     *
     * @param effectiveOutputCount resolved output block states that can participate in placement
     * @param shadowedOutputCount resolved outputs made ineffective by earlier target precedence
     * @param issues ordered target/reference findings; the constructor defensively copies and bounds this list
     * @param issuesTruncated whether upstream analysis already omitted findings
     */
    public record TargetAnalysis(
            int effectiveOutputCount, int shadowedOutputCount, List<TargetIssue> issues, boolean issuesTruncated) {
        /**
         * Creates a finite analysis and limits retained issues to four times the public summary-detail bound.
         *
         * @param effectiveOutputCount non-negative effective output count
         * @param shadowedOutputCount non-negative shadowed output count
         * @param issues ordered findings, or {@code null} for none
         * @param issuesTruncated whether findings were already omitted upstream
         */
        public TargetAnalysis {
            if (effectiveOutputCount < 0 || shadowedOutputCount < 0) {
                throw new IllegalArgumentException("Target analysis counts cannot be negative");
            }
            issues = issues == null ? List.of() : List.copyOf(issues);
            if (issues.size() > OreProfileForecast.MAX_REFERENCE_DETAILS * 4) {
                issues = List.copyOf(issues.subList(0, OreProfileForecast.MAX_REFERENCE_DETAILS * 4));
                issuesTruncated = true;
            }
        }

        /**
         * Returns an analysis with no effective outputs, shadowing, or findings.
         *
         * @return reusable semantic empty value
         */
        public static TargetAnalysis empty() {
            return new TargetAnalysis(0, 0, List.of(), false);
        }
    }

    /**
     * One registry or target-precedence finding before it is converted to the bounded network model.
     *
     * @param kind finding category
     * @param severity warning or error severity
     * @param targetIndex zero-based target index, or a negative value for a rule-level finding
     * @param sourceId exact block or block-tag source associated with the target
     * @param referenceId missing, invalid, or shadowed registry reference
     * @param affectedOutputs number of resolved outputs affected by the finding
     */
    public record TargetIssue(
            IssueKind kind,
            IssueSeverity severity,
            int targetIndex,
            String sourceId,
            String referenceId,
            int affectedOutputs) {
        /**
         * Normalizes nullable identifiers to empty strings and clamps affected outputs to a non-negative value.
         *
         * @param kind finding category
         * @param severity warning or error severity
         * @param targetIndex zero-based target index, or a negative rule-level marker
         * @param sourceId associated block or tag identifier, or {@code null}
         * @param referenceId affected registry reference, or {@code null}
         * @param affectedOutputs number of outputs affected by the finding
         */
        public TargetIssue {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(severity, "severity");
            sourceId = sourceId == null ? "" : sourceId;
            referenceId = referenceId == null ? "" : referenceId;
            affectedOutputs = Math.max(0, affectedOutputs);
        }
    }
}
