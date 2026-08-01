package com.nightsta69.delvefold.config.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.analysis.OreProfileForecast.IssueKind;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast.IssueSeverity;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast.ReferenceIssue;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast.RuleStatus;
import com.nightsta69.delvefold.config.analysis.OreProfileForecastBuilder.TargetAnalysis;
import com.nightsta69.delvefold.config.analysis.OreProfileForecastBuilder.TargetIssue;
import com.nightsta69.delvefold.config.model.BiomeFilter;
import com.nightsta69.delvefold.config.model.HeightDistribution;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.model.OreRule;
import com.nightsta69.delvefold.config.model.OreTarget;
import com.nightsta69.delvefold.config.model.SpawnBand;
import com.nightsta69.delvefold.config.model.TerrainMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class OreForecastRuleAnalysisTest {
    private static final OreTarget TARGET = OreTarget.of("minecraft:diamond_ore", "minecraft:stone_ore_replaceables");

    @Test
    void statusPrecedenceCoversDisabledInvalidTerrainBiomeOutputsAttemptsAndEffectiveRules() {
        BiomeFilter blocked = new BiomeFilter(List.of("minecraft:desert"), List.of());
        List<OreRule> rules = List.of(
                new OreRule("", false, false, Set.of(), List.of(), blocked, List.of()),
                new OreRule("invalid", true, false, Set.of(TerrainMode.FLAT), List.of(), blocked, List.of(validBand())),
                rule("terrain", true, Set.of(TerrainMode.CAVERN), BiomeFilter.ALL_MINING_BIOMES, validBand()),
                rule("biome", true, Set.of(TerrainMode.FLAT), blocked, validBand()),
                rule("outputs", true, Set.of(TerrainMode.FLAT), BiomeFilter.ALL_MINING_BIOMES, validBand()),
                rule(
                        "attempts",
                        true,
                        Set.of(TerrainMode.FLAT),
                        BiomeFilter.ALL_MINING_BIOMES,
                        SpawnBand.uniform("zero", 4, 0.0D, 0, 0, 0.0D)),
                rule("effective", true, Set.of(TerrainMode.FLAT), BiomeFilter.ALL_MINING_BIOMES, validBand()));
        OreProfileDocument profile = profile("statuses", rules);

        OreProfileForecast forecast = OreProfileForecastBuilder.build(
                profile.profile(),
                profile,
                TerrainMode.FLAT,
                (terrain, filter) -> !filter.equals(blocked),
                rule -> rule.id().equals("outputs") ? TargetAnalysis.empty() : resolved(),
                0,
                16);

        assertEquals(
                List.of(
                        RuleStatus.DISABLED,
                        RuleStatus.INVALID,
                        RuleStatus.TERRAIN_MISMATCH,
                        RuleStatus.BIOME_MISMATCH,
                        RuleStatus.NO_EFFECTIVE_OUTPUTS,
                        RuleStatus.NO_ATTEMPTS,
                        RuleStatus.EFFECTIVE),
                forecast.rules().stream()
                        .map(OreProfileForecast.RuleForecast::status)
                        .toList());
        assertEquals(1.0D, forecast.rules().getLast().effectiveAttempts(), 1.0E-9);
        assertEquals(4.0D, forecast.rules().getLast().effectiveWorkUnits(), 1.0E-9);
    }

    @Test
    void referenceAggregationPreservesFirstOccurrenceAndMergesSeverityAndAffectedCounts() {
        OreRule first = rule("first", true, Set.of(TerrainMode.FLAT), BiomeFilter.ALL_MINING_BIOMES, validBand());
        OreRule second = rule("second", true, Set.of(TerrainMode.FLAT), BiomeFilter.ALL_MINING_BIOMES, validBand());
        OreProfileDocument profile = profile("references", List.of(first, second));

        OreProfileForecast forecast = OreProfileForecastBuilder.build(
                profile.profile(),
                profile,
                TerrainMode.FLAT,
                (terrain, filter) -> true,
                rule -> rule.id().equals("first")
                        ? new TargetAnalysis(
                                1,
                                2,
                                List.of(
                                        targetIssue(IssueKind.MISSING_BLOCK, IssueSeverity.WARNING, "shared", 0),
                                        targetIssue(IssueKind.SHADOWED_OUTPUT, IssueSeverity.WARNING, "shadow", 2),
                                        targetIssue(
                                                IssueKind.INVALID_STATE_PROPERTY, IssueSeverity.WARNING, "state", 0)),
                                false)
                        : new TargetAnalysis(
                                1,
                                4,
                                List.of(
                                        targetIssue(IssueKind.MISSING_BLOCK, IssueSeverity.ERROR, "shared", 3),
                                        targetIssue(IssueKind.MISSING_HOST_TAG, IssueSeverity.ERROR, "host", 0),
                                        targetIssue(IssueKind.SHADOWED_OUTPUT, IssueSeverity.WARNING, "shadow", 4)),
                                false),
                0,
                16);

        assertEquals(1, forecast.references().missingBlocks());
        assertEquals(1, forecast.references().missingHostTags());
        assertEquals(1, forecast.references().invalidStates());
        assertEquals(6, forecast.references().shadowedOutputs());
        assertEquals(6, forecast.references().totalIssues());
        assertEquals(
                List.of("shared", "shadow", "state", "host"),
                forecast.references().details().stream()
                        .map(ReferenceIssue::referenceId)
                        .toList());
        ReferenceIssue shared = forecast.references().details().getFirst();
        assertEquals(IssueSeverity.ERROR, shared.severity());
        assertEquals(4, shared.affectedOutputs());
        ReferenceIssue shadow = forecast.references().details().get(1);
        assertEquals(6, shadow.affectedOutputs());
        assertEquals(RuleStatus.EFFECTIVE, forecast.rules().getFirst().status());
        assertEquals(RuleStatus.INVALID, forecast.rules().getLast().status());
    }

    @Test
    void targetIssueAndCounterBoundsRetainStablePrefixesAndReportTruncation() {
        List<OreTarget> targets = new ArrayList<>();
        for (int index = 0; index < OreProfileForecast.MAX_TARGETS_PER_RULE + 1; index++) {
            targets.add(OreTarget.of("minecraft:diamond_ore", "minecraft:stone_ore_replaceables"));
        }
        OreRule rule = new OreRule(
                "bounded",
                true,
                false,
                Set.of(TerrainMode.FLAT),
                targets,
                BiomeFilter.ALL_MINING_BIOMES,
                List.of(validBand()));
        List<TargetIssue> sourceIssues = new ArrayList<>();
        for (int index = 0; index < 300; index++) {
            sourceIssues.add(new TargetIssue(
                    IssueKind.MISSING_BLOCK, IssueSeverity.WARNING, index, "source_" + index, "reference_" + index, 0));
        }
        OreProfileDocument profile = profile("bounded", List.of(rule));

        OreProfileForecast forecast = OreProfileForecastBuilder.build(
                profile.profile(),
                profile,
                TerrainMode.FLAT,
                (terrain, filter) -> true,
                ignored -> new TargetAnalysis(5_000, 5_001, sourceIssues, false),
                0,
                16);

        OreProfileForecast.RuleForecast bounded = forecast.rules().getFirst();
        assertEquals(OreProfileForecast.MAX_TARGETS_PER_RULE, bounded.targetCount());
        assertEquals(OreProfileForecast.MAX_RULE_COUNTER, bounded.effectiveOutputCount());
        assertEquals(OreProfileForecast.MAX_RULE_COUNTER, bounded.shadowedOutputCount());
        assertEquals(OreProfileForecast.MAX_RULE_ISSUES, bounded.issues().size());
        assertEquals("reference_0", bounded.issues().getFirst().referenceId());
        assertEquals("reference_15", bounded.issues().getLast().referenceId());
        assertEquals(15, bounded.issues().getLast().targetIndex());
        assertTrue(bounded.truncated());
        assertEquals(
                OreProfileForecast.MAX_REFERENCE_DETAILS,
                forecast.references().details().size());
        assertEquals("reference_0", forecast.references().details().getFirst().referenceId());
        assertEquals("reference_63", forecast.references().details().getLast().referenceId());
        assertTrue(forecast.references().truncated());
        assertTrue(forecast.truncated());
    }

    @Test
    void uniformTriangleAndTrapezoidBandsUseCanonicalSequentialDistributionMath() {
        SpawnBand uniform = SpawnBand.uniform("uniform", 2, 4.0D, 0, 3, 0.0D);
        SpawnBand triangle = SpawnBand.triangle("triangle", 3, 6.0D, 0, 2, 1, 0.0D);
        SpawnBand trapezoid = new SpawnBand("trapezoid", 4, 8.0D, HeightDistribution.TRAPEZOID, 0, 3, null, 1, 2, 0.0D);
        OreRule rule = new OreRule(
                "distributions",
                true,
                false,
                Set.of(TerrainMode.FLAT),
                List.of(TARGET),
                BiomeFilter.ALL_MINING_BIOMES,
                List.of(uniform, triangle, trapezoid));
        OreProfileDocument profile = profile("distributions", List.of(rule));

        OreProfileForecast forecast = OreProfileForecastBuilder.build(
                profile.profile(), profile, TerrainMode.FLAT, (terrain, filter) -> true, ignored -> resolved(), 0, 16);

        OreProfileForecast.RuleForecast result = forecast.rules().getFirst();
        assertEquals(18.0D, result.effectiveAttempts(), 1.0E-9);
        assertEquals(58.0D, result.effectiveWorkUnits(), 1.0E-9);
        double[] expectedAttempts = {
            1.0D + 1.5D + 4.0D / 3.0D, 1.0D + 3.0D + 8.0D / 3.0D, 1.0D + 1.5D + 8.0D / 3.0D, 1.0D + 4.0D / 3.0D
        };
        double[] expectedWork = {
            2.0D + 4.5D + 16.0D / 3.0D, 2.0D + 9.0D + 32.0D / 3.0D, 2.0D + 4.5D + 32.0D / 3.0D, 2.0D + 16.0D / 3.0D
        };
        for (int y = 0; y <= 3; y++) {
            OreProfileForecast.HeightSample sample =
                    forecast.activeTerrainHeightOverlay().get(y + 64);
            assertEquals(expectedAttempts[y], sample.expectedAttempts(), 1.0E-9);
            assertEquals(expectedWork[y], sample.expectedWorkUnits(), 1.0E-9);
        }
        assertEquals(
                18.0D,
                forecast.activeTerrainHeightOverlay().stream()
                        .mapToDouble(OreProfileForecast.HeightSample::expectedAttempts)
                        .sum(),
                1.0E-9);
        assertEquals(
                58.0D,
                forecast.activeTerrainHeightOverlay().stream()
                        .mapToDouble(OreProfileForecast.HeightSample::expectedWorkUnits)
                        .sum(),
                1.0E-9);
    }

    @Test
    void invalidOutOfRangeBandCannotLeakOrTruncateHeightSamples() {
        SpawnBand outside = SpawnBand.uniform("outside", 4, 1.0D, -65, 320, 0.0D);
        OreProfileDocument profile = profile(
                "outside",
                List.of(rule("outside", true, Set.of(TerrainMode.FLAT), BiomeFilter.ALL_MINING_BIOMES, outside)));

        OreProfileForecast forecast = OreProfileForecastBuilder.build(
                profile.profile(), profile, TerrainMode.FLAT, (terrain, filter) -> true, ignored -> resolved(), 0, 16);

        assertEquals(RuleStatus.INVALID, forecast.rules().getFirst().status());
        assertEquals(
                OreProfileForecast.MAX_HEIGHT_SAMPLES,
                forecast.activeTerrainHeightOverlay().size());
        assertEquals(
                OreProfileForecast.MIN_HEIGHT,
                forecast.activeTerrainHeightOverlay().getFirst().y());
        assertEquals(
                OreProfileForecast.MAX_HEIGHT,
                forecast.activeTerrainHeightOverlay().getLast().y());
        assertTrue(forecast.activeTerrainHeightOverlay().stream()
                .allMatch(sample -> sample.expectedAttempts() == 0.0D && sample.expectedWorkUnits() == 0.0D));
    }

    @Test
    void boundedProfilePrefixAndAllOutputOrderingRemainDeterministic() {
        List<OreRule> rules = new ArrayList<>();
        for (int index = 0; index <= OreProfileForecast.MAX_TOTAL_RULES; index++) {
            rules.add(
                    rule("rule_" + index, true, Set.of(TerrainMode.FLAT), BiomeFilter.ALL_MINING_BIOMES, validBand()));
        }
        OreProfileDocument profile = profile("deterministic", rules);
        AtomicInteger analyses = new AtomicInteger();

        OreProfileForecast first = OreProfileForecastBuilder.build(
                null,
                profile,
                null,
                (terrain, filter) -> true,
                ignored -> {
                    analyses.incrementAndGet();
                    return resolved();
                },
                Integer.MAX_VALUE,
                OreProfileForecast.MAX_RULES_PER_PAGE);
        OreProfileForecast second = OreProfileForecastBuilder.build(
                "",
                profile,
                null,
                (terrain, filter) -> true,
                ignored -> resolved(),
                Integer.MAX_VALUE,
                OreProfileForecast.MAX_RULES_PER_PAGE);

        assertEquals(OreProfileForecast.MAX_TOTAL_RULES, analyses.get());
        assertEquals(first, second);
        assertEquals("deterministic", first.profileId());
        assertEquals(OreProfileForecast.MAX_TOTAL_RULES, first.totalRuleCount());
        assertEquals(31, first.page());
        assertEquals(
                List.of(TerrainMode.values()),
                first.terrainTotals().stream()
                        .map(OreProfileForecast.TerrainTotals::terrain)
                        .toList());
        assertEquals(
                List.of(496, 497, 498, 499, 500, 501, 502, 503, 504, 505, 506, 507, 508, 509, 510, 511),
                first.rules().stream()
                        .map(OreProfileForecast.RuleForecast::ruleIndex)
                        .toList());
        assertTrue(first.truncated());
    }

    private static OreProfileDocument profile(String id, List<OreRule> rules) {
        return new OreProfileDocument(OreProfileDocument.CURRENT_SCHEMA_VERSION, 0L, id, rules);
    }

    private static OreRule rule(
            String id, boolean enabled, Set<TerrainMode> terrains, BiomeFilter biomes, SpawnBand band) {
        return new OreRule(id, enabled, false, terrains, List.of(TARGET), biomes, List.of(band));
    }

    private static SpawnBand validBand() {
        return SpawnBand.uniform("main", 4, 1.0D, 0, 0, 0.0D);
    }

    private static TargetAnalysis resolved() {
        return new TargetAnalysis(1, 0, List.of(), false);
    }

    private static TargetIssue targetIssue(
            IssueKind kind, IssueSeverity severity, String referenceId, int affectedOutputs) {
        return new TargetIssue(kind, severity, 0, "source", referenceId, affectedOutputs);
    }
}
