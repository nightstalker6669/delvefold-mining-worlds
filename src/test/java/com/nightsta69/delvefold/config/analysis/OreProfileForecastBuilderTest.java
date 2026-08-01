package com.nightsta69.delvefold.config.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.analysis.OreProfileForecast.IssueKind;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast.IssueSeverity;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast.RuleStatus;
import com.nightsta69.delvefold.config.analysis.OreProfileForecastBuilder.TargetAnalysis;
import com.nightsta69.delvefold.config.analysis.OreProfileForecastBuilder.TargetIssue;
import com.nightsta69.delvefold.config.model.BiomeFilter;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.model.OreRule;
import com.nightsta69.delvefold.config.model.OreTarget;
import com.nightsta69.delvefold.config.model.ProvinceSettings;
import com.nightsta69.delvefold.config.model.SpawnBand;
import com.nightsta69.delvefold.config.model.TerrainMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OreProfileForecastBuilderTest {
    @Test
    void emptyProfileProducesBoundedExactHeightOverlayAndOneEmptyPage() {
        OreProfileDocument profile =
                new OreProfileDocument(OreProfileDocument.CURRENT_SCHEMA_VERSION, 4L, "empty", List.of());

        OreProfileForecast forecast = OreProfileForecastBuilder.build(
                "named_empty",
                profile,
                TerrainMode.FLAT,
                (terrain, filter) -> true,
                rule -> TargetAnalysis.empty(),
                0,
                12);

        assertEquals("named_empty", forecast.profileId());
        assertEquals(4L, forecast.profileRevision());
        assertEquals(3, forecast.terrainTotals().size());
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
        assertEquals(0, forecast.totalRuleCount());
        assertEquals(1, forecast.pageCount());
        assertTrue(forecast.rules().isEmpty());
        assertFalse(forecast.truncated());
    }

    @Test
    void requestedRulePageIsClampedAndNeverContainsMoreThanSixteenRules() {
        List<OreRule> rules = new ArrayList<>();
        for (int index = 0; index < 17; index++) {
            rules.add(new OreRule(
                    "rule_" + index,
                    true,
                    false,
                    Set.of(TerrainMode.FLAT),
                    List.of(),
                    new BiomeFilter(List.of(), List.of()),
                    List.of(SpawnBand.uniform("main", 4, 1.0D, -8, 8, 0.0D))));
        }
        OreProfileDocument profile =
                new OreProfileDocument(OreProfileDocument.CURRENT_SCHEMA_VERSION, 0L, "paged", rules);

        OreProfileForecast forecast = OreProfileForecastBuilder.build(
                profile.profile(),
                profile,
                TerrainMode.FLAT,
                (terrain, filter) -> true,
                rule -> TargetAnalysis.empty(),
                99,
                99);

        assertEquals(OreProfileForecast.MAX_RULES_PER_PAGE, forecast.pageSize());
        assertEquals(2, forecast.pageCount());
        assertEquals(1, forecast.page());
        assertEquals(1, forecast.rules().size());
        assertEquals(16, forecast.rules().getFirst().ruleIndex());
        assertEquals(RuleStatus.INVALID, forecast.rules().getFirst().status());
    }

    @Test
    void missingOutputAndHostReferencesAreStructuredAndBounded() {
        OreRule missing = new OreRule(
                "missing",
                true,
                false,
                Set.of(TerrainMode.FLAT),
                List.of(OreTarget.of("missingmod:unobtainium_ore", "missingmod:host_blocks")),
                new BiomeFilter(List.of(), List.of()),
                List.of(SpawnBand.uniform("main", 4, 2.0D, -8, 8, 0.0D)));
        OreProfileDocument profile =
                new OreProfileDocument(OreProfileDocument.CURRENT_SCHEMA_VERSION, 0L, "missing", List.of(missing));

        OreProfileForecast forecast = OreProfileForecastBuilder.build(
                profile.profile(),
                profile,
                TerrainMode.FLAT,
                (terrain, filter) -> true,
                rule -> new TargetAnalysis(
                        0,
                        0,
                        List.of(
                                new TargetIssue(
                                        IssueKind.MISSING_HOST_TAG,
                                        IssueSeverity.ERROR,
                                        0,
                                        "missingmod:unobtainium_ore",
                                        "missingmod:host_blocks",
                                        0),
                                new TargetIssue(
                                        IssueKind.MISSING_BLOCK,
                                        IssueSeverity.WARNING,
                                        0,
                                        "missingmod:unobtainium_ore",
                                        "missingmod:unobtainium_ore",
                                        0)),
                        false),
                0,
                12);

        assertEquals(1, forecast.references().missingBlocks());
        assertEquals(1, forecast.references().missingHostTags());
        assertEquals(0, forecast.references().missingOutputTags());
        assertTrue(forecast.references().details().stream()
                .anyMatch(issue -> issue.kind() == IssueKind.MISSING_BLOCK
                        && issue.referenceId().equals("missingmod:unobtainium_ore")));
        assertTrue(forecast.references().details().stream()
                .anyMatch(issue -> issue.kind() == IssueKind.MISSING_HOST_TAG
                        && issue.referenceId().equals("missingmod:host_blocks")));
        assertEquals(2, forecast.rules().getFirst().missingReferenceCount());
        assertEquals(RuleStatus.INVALID, forecast.rules().getFirst().status());
    }

    @Test
    void uninitializedForecastReportsConfiguredTotalsWithoutEffectiveRuleWork() {
        OreRule rule = new OreRule(
                "configured",
                true,
                false,
                Set.of(TerrainMode.CAVERN),
                List.of(OreTarget.of("minecraft:diamond_ore", "minecraft:stone_ore_replaceables")),
                new BiomeFilter(List.of(), List.of()),
                List.of(SpawnBand.uniform("main", 8, 3.0D, -8, 8, 0.0D)));
        OreProfileDocument profile =
                new OreProfileDocument(OreProfileDocument.CURRENT_SCHEMA_VERSION, 0L, "uninitialized", List.of(rule));

        OreProfileForecast forecast = OreProfileForecastBuilder.build(
                profile.profile(),
                profile,
                null,
                (terrain, filter) -> true,
                ignored -> new TargetAnalysis(1, 0, List.of(), false),
                0,
                12);

        assertTrue(forecast.activeTerrainHeightOverlay().isEmpty());
        assertEquals(
                3.0D,
                forecast.terrainTotals().stream()
                        .filter(total -> total.terrain() == TerrainMode.CAVERN)
                        .findFirst()
                        .orElseThrow()
                        .configuredAttempts(),
                1.0E-9);
        assertEquals(0.0D, forecast.rules().getFirst().effectiveAttempts(), 1.0E-9);
        assertEquals(RuleStatus.UNINITIALIZED, forecast.rules().getFirst().status());
    }

    @Test
    void effectiveWorkAndHeightOverlayUseTheCanonicalDistributionMath() {
        OreRule rule = new OreRule(
                "diamond",
                true,
                true,
                Set.of(TerrainMode.FLAT),
                List.of(OreTarget.of("minecraft:diamond_ore", "minecraft:stone_ore_replaceables")),
                new BiomeFilter(List.of(), List.of()),
                List.of(SpawnBand.uniform("main", 8, 4.0D, 0, 3, 0.0D)));
        OreProfileDocument profile =
                new OreProfileDocument(OreProfileDocument.CURRENT_SCHEMA_VERSION, 0L, "effective", List.of(rule));

        OreProfileForecast forecast = OreProfileForecastBuilder.build(
                profile.profile(),
                profile,
                TerrainMode.FLAT,
                (terrain, filter) -> true,
                ignored -> new TargetAnalysis(1, 0, List.of(), false),
                0,
                12);

        assertEquals(RuleStatus.EFFECTIVE, forecast.rules().getFirst().status());
        assertEquals(4.0D, forecast.rules().getFirst().effectiveAttempts(), 1.0E-9);
        assertEquals(32.0D, forecast.rules().getFirst().effectiveWorkUnits(), 1.0E-9);
        for (int y = 0; y <= 3; y++) {
            OreProfileForecast.HeightSample sample =
                    forecast.activeTerrainHeightOverlay().get(y + 64);
            assertEquals(1.0D, sample.expectedAttempts(), 1.0E-9);
            assertEquals(8.0D, sample.expectedWorkUnits(), 1.0E-9);
        }
    }

    @Test
    void provinceForecastUsesHardCapAndThicknessAwareHeightOverlay() {
        SpawnBand province = SpawnBand.province(
                "regional",
                com.nightsta69.delvefold.config.model.HeightDistribution.UNIFORM,
                0,
                0,
                null,
                null,
                null,
                0.0D,
                new ProvinceSettings(512, 192, 5, 0.08D, 100));
        OreRule rule = new OreRule(
                "regional_diamond",
                true,
                true,
                Set.of(TerrainMode.FLAT),
                List.of(OreTarget.of("minecraft:diamond_ore", "minecraft:stone_ore_replaceables")),
                BiomeFilter.ALL_MINING_BIOMES,
                List.of(province));
        OreProfileDocument profile = new OreProfileDocument(2, 0L, "province", List.of(rule));

        OreProfileForecast forecast = OreProfileForecastBuilder.build(
                profile.profile(),
                profile,
                TerrainMode.FLAT,
                (terrain, filter) -> true,
                ignored -> new TargetAnalysis(1, 0, List.of(), false),
                0,
                12);

        var ruleForecast = forecast.rules().getFirst();
        assertEquals(RuleStatus.EFFECTIVE, ruleForecast.status());
        assertEquals(100.0D, ruleForecast.configuredAttempts(), 1.0E-9);
        assertEquals(100.0D, ruleForecast.configuredWorkUnits(), 1.0E-9);
        assertEquals(100.0D, ruleForecast.effectiveWorkUnits(), 1.0E-9);
        double overlayWork = forecast.activeTerrainHeightOverlay().stream()
                .mapToDouble(OreProfileForecast.HeightSample::expectedWorkUnits)
                .sum();
        assertEquals(100.0D, overlayWork, 1.0E-9);
        assertTrue(forecast.activeTerrainHeightOverlay().stream()
                .filter(sample -> sample.y() >= -2 && sample.y() <= 2)
                .allMatch(sample -> sample.expectedWorkUnits() > 0.0D));
        assertEquals(0.0D, forecast.activeTerrainHeightOverlay().get(-3 + 64).expectedWorkUnits(), 1.0E-9);
        assertEquals(0.0D, forecast.activeTerrainHeightOverlay().get(3 + 64).expectedWorkUnits(), 1.0E-9);
    }

    @Test
    void diagnosticDetailsAreReducedUntilTheForecastFitsItsNetworkBudget() {
        List<OreRule> rules = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            rules.add(new OreRule(
                    "rule_" + index,
                    true,
                    false,
                    Set.of(TerrainMode.FLAT),
                    List.of(OreTarget.of("minecraft:diamond_ore", "minecraft:stone_ore_replaceables")),
                    new BiomeFilter(List.of(), List.of()),
                    List.of(SpawnBand.uniform("main", 4, 1.0D, -8, 8, 0.0D))));
        }
        OreProfileDocument profile =
                new OreProfileDocument(OreProfileDocument.CURRENT_SCHEMA_VERSION, 0L, "bounded", rules);

        OreProfileForecast forecast = OreProfileForecastBuilder.build(
                profile.profile(),
                profile,
                TerrainMode.FLAT,
                (terrain, filter) -> true,
                rule -> {
                    List<TargetIssue> issues = new ArrayList<>();
                    for (int index = 0; index < 256; index++) {
                        String reference = "missingmod:" + rule.id() + '_' + index + "_xxxxxxxxxxxxxxxxxxxxxxxx";
                        issues.add(new TargetIssue(
                                IssueKind.MISSING_BLOCK,
                                IssueSeverity.WARNING,
                                0,
                                "missingmod:ore_xxxxxxxxxxxxxxxxxxxxxxxxx",
                                reference,
                                0));
                    }
                    return new TargetAnalysis(0, 0, issues, false);
                },
                0,
                12);

        assertTrue(forecast.truncated());
        assertTrue(forecast.estimatedNetworkBytes() <= OreProfileForecast.MAX_ESTIMATED_NETWORK_BYTES);
        assertTrue(
                forecast.rules().stream().allMatch(rule -> rule.issues().size() <= OreProfileForecast.MAX_RULE_ISSUES));
    }
}
