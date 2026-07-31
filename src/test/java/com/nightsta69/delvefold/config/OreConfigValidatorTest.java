package com.nightsta69.delvefold.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.model.HeightDistribution;
import com.nightsta69.delvefold.config.model.BiomeFilter;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.model.SpawnBand;
import com.nightsta69.delvefold.config.validation.OreConfigValidator;
import com.nightsta69.delvefold.config.validation.RegistryLookup;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OreConfigValidatorTest {
    @Test
    void rejectsInvalidBandBoundsAndRates() {
        var base = OrePresets.balanced();
        var rule = base.rules().getFirst().withBands(List.of(new SpawnBand(
                "broken",
                65,
                -1.0D,
                HeightDistribution.TRIANGLE,
                100,
                -20,
                999,
                null,
                null,
                2.0D
        )));
        var document = new OreProfileDocument(1, 0, "test", List.of(rule));
        var report = OreConfigValidator.validate(document, RegistryLookup.SKIP);
        assertFalse(report.valid());
        assertTrue(report.errorCount() >= 4, () -> report.issues().toString());
    }

    @Test
    void optionalMissingModBlockWarnsButDoesNotReject() {
        var base = OrePresets.balanced();
        var original = base.rules().getFirst();
        var optional = new com.nightsta69.delvefold.config.model.OreRule(
                original.id(), original.enabled(), false, original.terrainModes(), original.targets(), original.biomes(), original.bands());
        RegistryLookup missingBlocks = new RegistryLookup() {
            @Override
            public boolean blockExists(String id) {
                return false;
            }

            @Override
            public boolean blockTagExists(String id) {
                return true;
            }
        };
        var report = OreConfigValidator.validate(
                new OreProfileDocument(1, 0, "test", List.of(optional)), missingBlocks);
        assertTrue(report.valid(), () -> report.issues().toString());
        assertTrue(report.warningCount() > 0);
    }

    @Test
    void rejectsEmptyTerrainSelectionAndMalformedBiomeSelectors() {
        var base = OrePresets.balanced();
        var original = base.rules().getFirst();
        var invalid = new com.nightsta69.delvefold.config.model.OreRule(
                original.id(), original.enabled(), original.required(), Set.of(), original.targets(),
                new BiomeFilter(List.of("not a biome"), List.of()), original.bands());
        var report = OreConfigValidator.validate(
                new OreProfileDocument(1, 0, "test", List.of(invalid)), RegistryLookup.SKIP);
        assertFalse(report.valid());
        assertTrue(report.issues().stream().anyMatch(issue -> "rule.no_terrain".equals(issue.code())));
        assertTrue(report.issues().stream().anyMatch(issue -> "biomes.invalid_selector".equals(issue.code())));
    }

    @Test
    void rejectsAggregateWorldgenBudgetsThatCouldStallChunkGeneration() {
        var original = OrePresets.balanced().rules().getFirst();
        List<com.nightsta69.delvefold.config.model.OreRule> rules = new ArrayList<>();
        for (int index = 0; index < 17; index++) {
            rules.add(new com.nightsta69.delvefold.config.model.OreRule(
                    "stress_" + index,
                    true,
                    original.required(),
                    original.terrainModes(),
                    original.targets(),
                    original.biomes(),
                    List.of(SpawnBand.uniform("main", 1, 256.0D, -64, 64, 0.0D))));
        }
        var report = OreConfigValidator.validate(new OreProfileDocument(1, 0, "stress", rules), RegistryLookup.SKIP);
        assertFalse(report.valid());
        assertTrue(report.issues().stream().anyMatch(issue -> "budget.too_many_attempts".equals(issue.code())));
    }
}
