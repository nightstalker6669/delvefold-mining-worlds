package com.nightsta69.delvefold.config.analysis;

import com.nightsta69.delvefold.config.analysis.OreProfileForecast.HeightSample;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast.IssueKind;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast.IssueSeverity;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast.ReferenceIssue;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast.ReferenceSummary;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast.RuleForecast;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast.RuleStatus;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast.TerrainTotals;
import com.nightsta69.delvefold.config.model.BiomeFilter;
import com.nightsta69.delvefold.config.model.HeightDistribution;
import com.nightsta69.delvefold.config.model.OreBandPlacement;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.model.OreRule;
import com.nightsta69.delvefold.config.model.OreTarget;
import com.nightsta69.delvefold.config.model.ProvinceSettings;
import com.nightsta69.delvefold.config.model.SpawnBand;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.validation.OreConfigValidator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Pure builder for bounded forecast pages from immutable profile and registry-analysis inputs. */
public final class OreProfileForecastBuilder {
    private OreProfileForecastBuilder() {}

    /** Pure entry point used by the Minecraft adapter and deterministic unit tests. */
    public static OreProfileForecast build(
            String profileId,
            OreProfileDocument profile,
            TerrainMode activeTerrain,
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

        OreWorkBudgetAnalysis.ProfileBudget configuredBudget = OreWorkBudgetAnalysis.analyze(profile);
        List<AnalyzedRule> analyzedRules = new ArrayList<>(totalRuleCount);
        for (int ruleIndex = 0; ruleIndex < totalRuleCount; ruleIndex++) {
            OreRule rule = profile.rules().get(ruleIndex);
            analyzedRules.add(new AnalyzedRule(
                    ruleIndex, rule, Objects.requireNonNull(targetAnalyzer.analyze(rule), "target analysis")));
        }

        List<TerrainTotals> terrainTotals = new ArrayList<>(TerrainMode.values().length);
        for (TerrainMode terrain : TerrainMode.values()) {
            OreWorkBudgetAnalysis.Budget configured = configuredBudget.terrain(terrain);
            OreWorkBudgetAnalysis.Budget effective = effectiveBudget(analyzedRules, terrain, biomeApplicability);
            terrainTotals.add(new TerrainTotals(
                    terrain,
                    terrain == activeTerrain,
                    configured.attemptsPerChunk(),
                    configured.workUnitsPerChunk(),
                    effective.attemptsPerChunk(),
                    effective.workUnitsPerChunk()));
        }

        List<HeightSample> overlay =
                activeTerrain == null ? List.of() : heightOverlay(analyzedRules, activeTerrain, biomeApplicability);

        int fromIndex = page * pageSize;
        int toIndex = Math.min(totalRuleCount, fromIndex + pageSize);
        List<RuleForecast> rules = new ArrayList<>(toIndex - fromIndex);
        boolean truncated = profile.rules().size() > totalRuleCount;
        for (int ruleIndex = fromIndex; ruleIndex < toIndex; ruleIndex++) {
            RuleForecast forecast = ruleForecast(analyzedRules.get(ruleIndex), activeTerrain, biomeApplicability);
            rules.add(forecast);
            truncated |= forecast.truncated();
        }

        ReferenceSummary references = referenceSummary(analyzedRules);
        truncated |= references.truncated();
        return fitNetworkBudget(
                boundedIdentifier(profileId == null || profileId.isBlank() ? profile.profile() : profileId),
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

    private static OreProfileForecast fitNetworkBudget(
            String profileId,
            long profileRevision,
            TerrainMode activeTerrain,
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

    private static OreWorkBudgetAnalysis.Budget effectiveBudget(
            List<AnalyzedRule> rules, TerrainMode terrain, BiomeApplicability biomes) {
        double attempts = 0.0D;
        double workUnits = 0.0D;
        for (AnalyzedRule analyzed : rules) {
            OreRule rule = analyzed.rule();
            if (!rule.enabled()
                    || !rule.terrainModes().contains(terrain)
                    || !biomes.matches(terrain, rule.biomes())
                    || analyzed.targets().effectiveOutputCount() == 0
                    || invalidRule(rule, analyzed.targets())) {
                continue;
            }
            OreWorkBudgetAnalysis.Budget ruleBudget = effectiveBandBudget(rule);
            attempts += ruleBudget.attemptsPerChunk();
            workUnits += ruleBudget.workUnitsPerChunk();
        }
        return new OreWorkBudgetAnalysis.Budget(attempts, workUnits);
    }

    private static OreWorkBudgetAnalysis.Budget effectiveBandBudget(OreRule rule) {
        OreWorkBudgetAnalysis.Budget result = OreWorkBudgetAnalysis.Budget.ZERO;
        for (SpawnBand band : rule.bands()) {
            if (!runtimeBand(band)) {
                continue;
            }
            result = result.plus(OreWorkBudgetAnalysis.analyze(band));
        }
        return result;
    }

    private static List<HeightSample> heightOverlay(
            List<AnalyzedRule> rules, TerrainMode terrain, BiomeApplicability biomes) {
        int minimumY = OreConfigValidator.MIN_WORLD_Y;
        int maximumY = OreConfigValidator.MAX_WORLD_Y;
        double[] attempts = new double[maximumY - minimumY + 1];
        double[] workUnits = new double[attempts.length];
        for (AnalyzedRule analyzed : rules) {
            OreRule rule = analyzed.rule();
            if (!rule.enabled()
                    || !rule.terrainModes().contains(terrain)
                    || !biomes.matches(terrain, rule.biomes())
                    || analyzed.targets().effectiveOutputCount() == 0
                    || invalidRule(rule, analyzed.targets())) {
                continue;
            }
            for (SpawnBand band : rule.bands()) {
                if (!runtimeBand(band)) {
                    continue;
                }
                if (band.placement() == OreBandPlacement.PROVINCE) {
                    addProvinceHeightOverlay(band, attempts, workUnits, minimumY, maximumY);
                    continue;
                }
                OreDistributionAnalysis.Summary distribution = OreDistributionAnalysis.analyze(band);
                for (OreDistributionAnalysis.Sample sample : distribution.samples()) {
                    if (sample.y() < minimumY || sample.y() > maximumY) {
                        continue;
                    }
                    int index = sample.y() - minimumY;
                    attempts[index] += sample.expectedAttempts();
                    workUnits[index] += sample.expectedAttempts() * Math.max(1, band.veinSize());
                }
            }
        }
        List<HeightSample> result = new ArrayList<>(attempts.length);
        for (int index = 0; index < attempts.length; index++) {
            result.add(new HeightSample(minimumY + index, attempts[index], workUnits[index]));
        }
        return List.copyOf(result);
    }

    private static void addProvinceHeightOverlay(
            SpawnBand band, double[] attempts, double[] workUnits, int minimumY, int maximumY) {
        ProvinceSettings province = band.province();
        if (province == null) {
            return;
        }
        OreDistributionAnalysis.Summary centers = OreDistributionAnalysis.analyze(band.withAttempts(1.0D));
        if (centers.samples().isEmpty()) {
            return;
        }
        double[] weights = new double[attempts.length];
        double verticalRadius = Math.max(0.5D, province.verticalThickness() / 2.0D);
        int lowerHalf = (province.verticalThickness() - 1) / 2;
        int upperHalf = province.verticalThickness() / 2;
        double totalWeight = 0.0D;
        for (OreDistributionAnalysis.Sample center : centers.samples()) {
            int low = Math.max(minimumY, center.y() - lowerHalf);
            int high = Math.min(maximumY, center.y() + upperHalf);
            for (int y = low; y <= high; y++) {
                double normalized = (y - center.y()) / verticalRadius;
                double crossSection = Math.max(0.0D, 1.0D - normalized * normalized);
                double contribution = center.probability() * crossSection;
                weights[y - minimumY] += contribution;
                totalWeight += contribution;
            }
        }
        if (!(totalWeight > 0.0D)) {
            return;
        }
        double cappedWork = OreWorkBudgetAnalysis.analyze(band).workUnitsPerChunk();
        double scale = cappedWork / totalWeight;
        for (int index = 0; index < weights.length; index++) {
            double expected = weights[index] * scale;
            attempts[index] += expected;
            workUnits[index] += expected;
        }
    }

    private static RuleForecast ruleForecast(
            AnalyzedRule analyzed, TerrainMode activeTerrain, BiomeApplicability biomes) {
        OreRule rule = analyzed.rule();
        TargetAnalysis targets = analyzed.targets();
        OreWorkBudgetAnalysis.Budget configured = activeTerrain == null
                ? OreWorkBudgetAnalysis.Budget.ZERO
                : OreWorkBudgetAnalysis.analyze(rule, activeTerrain);
        OreWorkBudgetAnalysis.Budget effective = OreWorkBudgetAnalysis.Budget.ZERO;
        boolean invalid = invalidRule(rule, targets);
        RuleStatus status;
        if (!rule.enabled()) {
            status = RuleStatus.DISABLED;
        } else if (invalid) {
            status = RuleStatus.INVALID;
        } else if (activeTerrain == null) {
            status = RuleStatus.UNINITIALIZED;
        } else if (!rule.terrainModes().contains(activeTerrain)) {
            status = RuleStatus.TERRAIN_MISMATCH;
        } else if (!biomes.matches(activeTerrain, rule.biomes())) {
            status = RuleStatus.BIOME_MISMATCH;
        } else if (targets.effectiveOutputCount() == 0) {
            status = RuleStatus.NO_EFFECTIVE_OUTPUTS;
        } else {
            effective = effectiveBandBudget(rule);
            status = effective.attemptsPerChunk() > 0.0D ? RuleStatus.EFFECTIVE : RuleStatus.NO_ATTEMPTS;
        }

        List<ReferenceIssue> allIssues = referenceIssues(analyzed);
        List<ReferenceIssue> visibleIssues =
                allIssues.stream().limit(OreProfileForecast.MAX_RULE_ISSUES).toList();
        int rawMissingReferences =
                (int) allIssues.stream().filter(issue -> missing(issue.kind())).count();
        int effectiveOutputCount = Math.min(OreProfileForecast.MAX_RULE_COUNTER, targets.effectiveOutputCount());
        int shadowedOutputCount = Math.min(OreProfileForecast.MAX_RULE_COUNTER, targets.shadowedOutputCount());
        int missingReferences = Math.min(OreProfileForecast.MAX_RULE_COUNTER, rawMissingReferences);
        int targetCount =
                Math.min(OreProfileForecast.MAX_TARGETS_PER_RULE, rule.targets().size());
        boolean truncated = targets.issuesTruncated()
                || visibleIssues.size() < allIssues.size()
                || effectiveOutputCount < targets.effectiveOutputCount()
                || shadowedOutputCount < targets.shadowedOutputCount()
                || missingReferences < rawMissingReferences
                || targetCount < rule.targets().size();
        return new RuleForecast(
                analyzed.ruleIndex(),
                boundedIdentifier(rule.id()),
                rule.enabled(),
                rule.required(),
                status,
                configured.attemptsPerChunk(),
                configured.workUnitsPerChunk(),
                effective.attemptsPerChunk(),
                effective.workUnitsPerChunk(),
                targetCount,
                effectiveOutputCount,
                missingReferences,
                shadowedOutputCount,
                visibleIssues,
                truncated);
    }

    private static ReferenceSummary referenceSummary(List<AnalyzedRule> analyzedRules) {
        List<ReferenceIssue> allIssues = analyzedRules.stream()
                .flatMap(rule -> referenceIssues(rule).stream())
                .toList();
        Map<ReferenceKey, ReferenceAccumulator> details = new LinkedHashMap<>();
        for (ReferenceIssue issue : allIssues) {
            ReferenceKey key = new ReferenceKey(issue.kind(), issue.referenceId());
            details.computeIfAbsent(key, ignored -> new ReferenceAccumulator(issue))
                    .include(issue);
        }
        List<ReferenceIssue> boundedDetails = details.values().stream()
                .limit(OreProfileForecast.MAX_REFERENCE_DETAILS)
                .map(ReferenceAccumulator::result)
                .toList();
        int missingBlocks = distinctCount(allIssues, IssueKind.MISSING_BLOCK);
        int missingOutputTags = distinctCount(allIssues, IssueKind.MISSING_OUTPUT_TAG);
        int missingHostTags = distinctCount(allIssues, IssueKind.MISSING_HOST_TAG);
        int invalidStates = (int) allIssues.stream()
                .filter(issue -> issue.kind() == IssueKind.INVALID_STATE_PROPERTY
                        || issue.kind() == IssueKind.INVALID_STATE_VALUE)
                .count();
        int shadowedOutputs = allIssues.stream()
                .filter(issue -> issue.kind() == IssueKind.SHADOWED_OUTPUT)
                .mapToInt(issue -> Math.max(1, issue.affectedOutputs()))
                .sum();
        boolean analysisTruncated =
                analyzedRules.stream().anyMatch(rule -> rule.targets().issuesTruncated());
        return new ReferenceSummary(
                Math.min(OreProfileForecast.MAX_SUMMARY_COUNTER, missingBlocks),
                Math.min(OreProfileForecast.MAX_SUMMARY_COUNTER, missingOutputTags),
                Math.min(OreProfileForecast.MAX_SUMMARY_COUNTER, missingHostTags),
                Math.min(OreProfileForecast.MAX_SUMMARY_COUNTER, invalidStates),
                Math.min(OreProfileForecast.MAX_SUMMARY_COUNTER, shadowedOutputs),
                Math.min(OreProfileForecast.MAX_SUMMARY_COUNTER, allIssues.size()),
                boundedDetails,
                analysisTruncated
                        || boundedDetails.size() < details.size()
                        || allIssues.size() > OreProfileForecast.MAX_SUMMARY_COUNTER);
    }

    private static int distinctCount(List<ReferenceIssue> issues, IssueKind kind) {
        return (int) issues.stream()
                .filter(issue -> issue.kind() == kind)
                .map(ReferenceIssue::referenceId)
                .distinct()
                .count();
    }

    private static List<ReferenceIssue> referenceIssues(AnalyzedRule analyzed) {
        return analyzed.targets().issues().stream()
                .map(issue -> new ReferenceIssue(
                        issue.kind(),
                        issue.severity(),
                        analyzed.ruleIndex(),
                        Math.clamp(issue.targetIndex(), -1, OreProfileForecast.MAX_TARGETS_PER_RULE - 1),
                        boundedIdentifier(analyzed.rule().id()),
                        boundedIdentifier(issue.sourceId()),
                        boundedIdentifier(issue.referenceId()),
                        Math.min(OreProfileForecast.MAX_RULE_COUNTER, issue.affectedOutputs())))
                .toList();
    }

    private static boolean invalidRule(OreRule rule, TargetAnalysis resolution) {
        if (rule.id().isBlank()
                || rule.terrainModes().isEmpty()
                || rule.targets().isEmpty()
                || rule.bands().isEmpty()) {
            return true;
        }
        for (OreTarget target : rule.targets()) {
            boolean exact = !target.block().isBlank();
            boolean tagged = !target.blockTag().isBlank();
            if (exact == tagged || target.weight() < OreTarget.MIN_WEIGHT || target.weight() > OreTarget.MAX_WEIGHT) {
                return true;
            }
        }
        if (rule.bands().stream().anyMatch(band -> !validConfiguredBand(band))) {
            return true;
        }
        return resolution.issues().stream().anyMatch(issue -> issue.severity() == IssueSeverity.ERROR);
    }

    private static boolean validConfiguredBand(SpawnBand band) {
        if (band.minY() < OreConfigValidator.MIN_WORLD_Y
                || band.maxY() > OreConfigValidator.MAX_WORLD_Y
                || band.minY() > band.maxY()
                || !Double.isFinite(band.discardOnAirExposure())
                || band.discardOnAirExposure() < 0.0D
                || band.discardOnAirExposure() > 1.0D) {
            return false;
        }
        if (band.placement() == OreBandPlacement.VEIN) {
            if (band.province() != null
                    || band.veinSize() < 1
                    || band.veinSize() > 64
                    || !Double.isFinite(band.attemptsPerChunk())
                    || band.attemptsPerChunk() < 0.0D
                    || band.attemptsPerChunk() > 256.0D) {
                return false;
            }
        } else {
            ProvinceSettings province = band.province();
            if (province == null
                    || province.regionSize() < OreConfigValidator.MIN_PROVINCE_REGION_SIZE
                    || province.regionSize() > OreConfigValidator.MAX_PROVINCE_REGION_SIZE
                    || province.regionSize() % 16 != 0
                    || province.radius() < 1
                    || province.radius() > province.regionSize()
                    || province.verticalThickness() < 1
                    || province.verticalThickness()
                            > OreConfigValidator.MAX_WORLD_Y - OreConfigValidator.MIN_WORLD_Y + 1
                    || !Double.isFinite(province.density())
                    || province.density() <= 0.0D
                    || province.density() > 1.0D
                    || province.perChunkWorkCap() < 1
                    || province.perChunkWorkCap() > OreConfigValidator.MAX_PROVINCE_WORK_PER_CHUNK) {
                return false;
            }
        }
        if (band.distribution() == HeightDistribution.TRIANGLE) {
            return band.peakY() != null && band.peakY() >= band.minY() && band.peakY() <= band.maxY();
        }
        if (band.distribution() == HeightDistribution.TRAPEZOID) {
            return band.plateauMinY() != null
                    && band.plateauMaxY() != null
                    && band.plateauMinY() >= band.minY()
                    && band.plateauMaxY() <= band.maxY()
                    && band.plateauMinY() <= band.plateauMaxY();
        }
        return true;
    }

    private static boolean runtimeBand(SpawnBand band) {
        return validConfiguredBand(band)
                && (band.placement() == OreBandPlacement.PROVINCE || band.attemptsPerChunk() > 0.0D);
    }

    private static boolean missing(IssueKind kind) {
        return kind == IssueKind.MISSING_BLOCK
                || kind == IssueKind.MISSING_OUTPUT_TAG
                || kind == IssueKind.MISSING_HOST_TAG;
    }

    private static String boundedIdentifier(String value) {
        String safe = value == null ? "" : value;
        int codePoints = safe.codePointCount(0, safe.length());
        if (codePoints <= OreProfileForecast.MAX_IDENTIFIER_LENGTH) {
            return safe;
        }
        int end = safe.offsetByCodePoints(0, OreProfileForecast.MAX_IDENTIFIER_LENGTH);
        return safe.substring(0, end);
    }

    @FunctionalInterface
    public interface BiomeApplicability {
        boolean matches(TerrainMode terrain, BiomeFilter filter);
    }

    @FunctionalInterface
    public interface TargetAnalyzer {
        TargetAnalysis analyze(OreRule rule);
    }

    public record TargetAnalysis(
            int effectiveOutputCount, int shadowedOutputCount, List<TargetIssue> issues, boolean issuesTruncated) {
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

        public static TargetAnalysis empty() {
            return new TargetAnalysis(0, 0, List.of(), false);
        }
    }

    public record TargetIssue(
            IssueKind kind,
            IssueSeverity severity,
            int targetIndex,
            String sourceId,
            String referenceId,
            int affectedOutputs) {
        public TargetIssue {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(severity, "severity");
            sourceId = sourceId == null ? "" : sourceId;
            referenceId = referenceId == null ? "" : referenceId;
            affectedOutputs = Math.max(0, affectedOutputs);
        }
    }

    private record AnalyzedRule(int ruleIndex, OreRule rule, TargetAnalysis targets) {}

    private record ReferenceKey(IssueKind kind, String referenceId) {}

    private static final class ReferenceAccumulator {
        private final ReferenceIssue first;
        private IssueSeverity severity;
        private int affected;

        private ReferenceAccumulator(ReferenceIssue first) {
            this.first = first;
            this.severity = first.severity();
        }

        private void include(ReferenceIssue issue) {
            if (issue.severity() == IssueSeverity.ERROR) {
                severity = IssueSeverity.ERROR;
            }
            affected = Math.min(OreProfileForecast.MAX_RULE_COUNTER, affected + Math.max(1, issue.affectedOutputs()));
        }

        private ReferenceIssue result() {
            return new ReferenceIssue(
                    first.kind(),
                    severity,
                    first.ruleIndex(),
                    first.targetIndex(),
                    first.ruleId(),
                    first.sourceId(),
                    first.referenceId(),
                    affected);
        }
    }
}
