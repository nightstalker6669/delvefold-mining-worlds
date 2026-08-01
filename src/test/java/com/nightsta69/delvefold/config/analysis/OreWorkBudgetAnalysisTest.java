package com.nightsta69.delvefold.config.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nightsta69.delvefold.config.model.BiomeFilter;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.model.OreRule;
import com.nightsta69.delvefold.config.model.ProvinceSettings;
import com.nightsta69.delvefold.config.model.SpawnBand;
import com.nightsta69.delvefold.config.model.TerrainMode;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OreWorkBudgetAnalysisTest {
    @Test
    void profileBudgetMatchesTheValidatorsConservativeTerrainMath() {
        OreRule enabled = new OreRule(
                "enabled",
                true,
                false,
                Set.of(TerrainMode.FLAT, TerrainMode.CAVERN),
                List.of(),
                BiomeFilter.ALL_MINING_BIOMES,
                List.of(
                        SpawnBand.uniform("main", 8, 2.5D, -32, 32, 0.0D),
                        SpawnBand.uniform("off", 64, 0.0D, -32, 32, 0.0D),
                        SpawnBand.uniform("invalid", 64, Double.NaN, -32, 32, 0.0D)));
        OreRule disabled = new OreRule(
                "disabled",
                false,
                false,
                Set.of(TerrainMode.FLAT),
                List.of(),
                BiomeFilter.ALL_MINING_BIOMES,
                List.of(SpawnBand.uniform("main", 64, 100.0D, -32, 32, 0.0D)));
        OreProfileDocument profile = new OreProfileDocument(
                OreProfileDocument.CURRENT_SCHEMA_VERSION, 0L, "budget", List.of(enabled, disabled));

        OreWorkBudgetAnalysis.ProfileBudget result = OreWorkBudgetAnalysis.analyze(profile);

        assertEquals(2.5D, result.terrain(TerrainMode.FLAT).attemptsPerChunk(), 1.0E-9);
        assertEquals(20.0D, result.terrain(TerrainMode.FLAT).workUnitsPerChunk(), 1.0E-9);
        assertEquals(result.terrain(TerrainMode.FLAT), result.terrain(TerrainMode.CAVERN));
        assertEquals(OreWorkBudgetAnalysis.Budget.ZERO, result.terrain(TerrainMode.WILD));
    }

    @Test
    void ruleAnalysisIsAvailableEvenWhenTheRuleIsDisabled() {
        OreRule rule = new OreRule(
                "disabled",
                false,
                false,
                Set.of(TerrainMode.WILD),
                List.of(),
                BiomeFilter.ALL_MINING_BIOMES,
                List.of(SpawnBand.uniform("main", 0, 3.0D, -32, 32, 0.0D)));

        OreWorkBudgetAnalysis.Budget result = OreWorkBudgetAnalysis.analyze(rule, TerrainMode.WILD);

        assertEquals(3.0D, result.attemptsPerChunk(), 1.0E-9);
        assertEquals(3.0D, result.workUnitsPerChunk(), 1.0E-9,
                "The safety analysis must retain the validator's max(1, vein size) behavior");
    }

    @Test
    void malformedExtremeNumbersSaturateInsteadOfCrashingValidationAnalysis() {
        OreRule rule = new OreRule(
                "extreme",
                true,
                false,
                Set.of(TerrainMode.FLAT),
                List.of(),
                BiomeFilter.ALL_MINING_BIOMES,
                List.of(SpawnBand.uniform("main", 64, Double.MAX_VALUE, -32, 32, 0.0D)));

        OreWorkBudgetAnalysis.Budget result = OreWorkBudgetAnalysis.analyze(rule, TerrainMode.FLAT);

        assertEquals(Double.MAX_VALUE, result.attemptsPerChunk());
        assertEquals(Double.MAX_VALUE, result.workUnitsPerChunk());
    }

    @Test
    void provinceCapIsCanonicalConservativeAttemptAndWorkBudget() {
        SpawnBand province = SpawnBand.province(
                "province", com.nightsta69.delvefold.config.model.HeightDistribution.UNIFORM,
                -32, 64, null, null, null, 0.0D,
                new ProvinceSettings(512, 192, 48, 0.01D, 777));
        OreRule rule = new OreRule(
                "mixed", true, false, Set.of(TerrainMode.FLAT), List.of(),
                BiomeFilter.ALL_MINING_BIOMES,
                List.of(SpawnBand.uniform("vein", 8, 2.0D, -32, 32, 0.0D), province));

        OreWorkBudgetAnalysis.Budget result = OreWorkBudgetAnalysis.analyze(rule, TerrainMode.FLAT);

        assertEquals(779.0D, result.attemptsPerChunk(), 1.0E-9);
        assertEquals(793.0D, result.workUnitsPerChunk(), 1.0E-9);
        assertEquals(777.0D, OreWorkBudgetAnalysis.analyze(province).workUnitsPerChunk(), 1.0E-9);
    }
}
