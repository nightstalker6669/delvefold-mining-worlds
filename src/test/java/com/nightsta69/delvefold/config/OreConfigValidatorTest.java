package com.nightsta69.delvefold.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.model.BiomeFilter;
import com.nightsta69.delvefold.config.model.HeightDistribution;
import com.nightsta69.delvefold.config.model.OreBandPlacement;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.model.OreTarget;
import com.nightsta69.delvefold.config.model.ProvinceSettings;
import com.nightsta69.delvefold.config.model.SpawnBand;
import com.nightsta69.delvefold.config.validation.OreConfigValidator;
import com.nightsta69.delvefold.config.validation.RegistryLookup;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OreConfigValidatorTest {
    @Test
    void rejectsInvalidBandBoundsAndRates() {
        var base = OrePresets.balanced();
        var rule = base.rules()
                .getFirst()
                .withBands(List.of(new SpawnBand(
                        "broken", 65, -1.0D, HeightDistribution.TRIANGLE, 100, -20, 999, null, null, 2.0D)));
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
                original.id(),
                original.enabled(),
                false,
                original.terrainModes(),
                original.targets(),
                original.biomes(),
                original.bands());
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
                new OreProfileDocument(OreProfileDocument.CURRENT_SCHEMA_VERSION, 0, "test", List.of(optional)),
                missingBlocks);
        assertTrue(report.valid(), () -> report.issues().toString());
        assertTrue(report.warningCount() > 0);
    }

    @Test
    void rejectsEmptyTerrainSelectionAndMalformedBiomeSelectors() {
        var base = OrePresets.balanced();
        var original = base.rules().getFirst();
        var invalid = new com.nightsta69.delvefold.config.model.OreRule(
                original.id(),
                original.enabled(),
                original.required(),
                Set.of(),
                original.targets(),
                new BiomeFilter(List.of("not a biome"), List.of()),
                original.bands());
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

    @Test
    void acceptsTagDrivenOutputsAndRejectsAmbiguousTargets() {
        var original = OrePresets.balanced().rules().getFirst();
        var tagged = new com.nightsta69.delvefold.config.model.OreRule(
                "tagged_tin",
                true,
                false,
                original.terrainModes(),
                List.of(OreTarget.ofTag("c:ores/tin", "minecraft:stone_ore_replaceables")),
                original.biomes(),
                original.bands());
        var valid = OreConfigValidator.validate(
                new OreProfileDocument(2, 0, "tagged", List.of(tagged)), RegistryLookup.SKIP);
        assertTrue(valid.valid(), () -> valid.issues().toString());

        var ambiguous = new com.nightsta69.delvefold.config.model.OreRule(
                "ambiguous",
                true,
                false,
                original.terrainModes(),
                List.of(new OreTarget(
                        "minecraft:iron_ore", "c:ores/iron", java.util.Map.of(), "minecraft:stone_ore_replaceables")),
                original.biomes(),
                original.bands());
        var invalid = OreConfigValidator.validate(
                new OreProfileDocument(2, 0, "ambiguous", List.of(ambiguous)), RegistryLookup.SKIP);
        assertFalse(invalid.valid());
        assertTrue(invalid.issues().stream().anyMatch(issue -> "target.source".equals(issue.code())));
    }

    @Test
    void validatesDetectedNetherAndEndHostTagsAgainstTheLiveRegistryBoundary() {
        var original = OrePresets.balanced().rules().getFirst();
        var hostAware = new com.nightsta69.delvefold.config.model.OreRule(
                "host_aware_garnet",
                true,
                false,
                original.terrainModes(),
                List.of(
                        OreTarget.of("example:nether_garnet_ore", "c:netherracks"),
                        OreTarget.of("example:end_garnet_ore", "c:end_stones")),
                original.biomes(),
                original.bands());
        RegistryLookup complete = new RegistryLookup() {
            @Override
            public boolean blockExists(String id) {
                return true;
            }

            @Override
            public boolean blockTagExists(String id) {
                return Set.of("c:netherracks", "c:end_stones").contains(id);
            }
        };
        RegistryLookup missingEnd = new RegistryLookup() {
            @Override
            public boolean blockExists(String id) {
                return true;
            }

            @Override
            public boolean blockTagExists(String id) {
                return "c:netherracks".equals(id);
            }
        };

        var accepted =
                OreConfigValidator.validate(new OreProfileDocument(2, 0, "host_aware", List.of(hostAware)), complete);
        var rejected =
                OreConfigValidator.validate(new OreProfileDocument(2, 0, "host_aware", List.of(hostAware)), missingEnd);

        assertTrue(accepted.valid(), () -> accepted.issues().toString());
        assertFalse(rejected.valid());
        assertTrue(rejected.issues().stream()
                .anyMatch(issue -> "target.missing_replace_tag".equals(issue.code())
                        && issue.path().endsWith("targets[1].replace_tag")));
    }

    @Test
    void validatesTargetWeightBoundsAndWarnsAboutWeightedOverlaps() {
        var original = OrePresets.balanced().rules().getFirst();
        String host = "minecraft:stone_ore_replaceables";
        var invalidWeights = new com.nightsta69.delvefold.config.model.OreRule(
                "invalid_weights",
                true,
                false,
                original.terrainModes(),
                List.of(
                        new OreTarget("minecraft:diamond_ore", "", java.util.Map.of(), host, 0),
                        new OreTarget("minecraft:emerald_ore", "", java.util.Map.of(), host, 1001)),
                original.biomes(),
                original.bands());
        var invalid = OreConfigValidator.validate(
                new OreProfileDocument(2, 0, "invalid_weights", List.of(invalidWeights)), RegistryLookup.SKIP);
        assertFalse(invalid.valid());
        assertTrue(
                invalid.issues().stream()
                                .filter(issue -> "target.invalid_weight".equals(issue.code()))
                                .count()
                        == 2,
                () -> invalid.issues().toString());

        var overlapping = new com.nightsta69.delvefold.config.model.OreRule(
                "weighted_overlap",
                true,
                false,
                original.terrainModes(),
                List.of(OreTarget.of("minecraft:diamond_ore", host, 2), OreTarget.of("minecraft:diamond_ore", host, 9)),
                original.biomes(),
                original.bands());
        var warned = OreConfigValidator.validate(
                new OreProfileDocument(2, 0, "weighted_overlap", List.of(overlapping)), RegistryLookup.SKIP);
        assertTrue(warned.valid(), () -> warned.issues().toString());
        assertTrue(warned.issues().stream().anyMatch(issue -> "target.duplicate".equals(issue.code())));
    }

    @Test
    void validatesProvinceShapeDensityCapAndPlacementAssociation() {
        var original = OrePresets.balanced().rules().getFirst();
        var invalidProvince = new SpawnBand(
                "province",
                0,
                -1.0D,
                HeightDistribution.UNIFORM,
                -32,
                64,
                null,
                null,
                null,
                0.0D,
                OreBandPlacement.PROVINCE,
                new ProvinceSettings(30, 31, 386, Double.NaN, 4097));
        var missingProvince = new SpawnBand(
                "missing",
                1,
                0.0D,
                HeightDistribution.UNIFORM,
                -32,
                64,
                null,
                null,
                null,
                0.0D,
                OreBandPlacement.PROVINCE,
                null);
        var veinWithProvince = new SpawnBand(
                "vein",
                8,
                1.0D,
                HeightDistribution.UNIFORM,
                -32,
                64,
                null,
                null,
                null,
                0.0D,
                OreBandPlacement.VEIN,
                ProvinceSettings.defaults());
        var report = OreConfigValidator.validate(
                new OreProfileDocument(
                        2,
                        0L,
                        "invalid_provinces",
                        List.of(original.withBands(List.of(invalidProvince, missingProvince, veinWithProvince)))),
                RegistryLookup.SKIP);

        assertFalse(report.valid());
        for (String code : List.of(
                "province.invalid_region_size",
                "province.invalid_radius",
                "province.invalid_vertical_thickness",
                "province.invalid_density",
                "province.invalid_work_cap",
                "band.missing_province",
                "band.unexpected_province",
                "band.invalid_vein_size",
                "band.invalid_attempts")) {
            assertTrue(
                    report.issues().stream().anyMatch(issue -> code.equals(issue.code())),
                    () -> "Missing " + code + " in " + report.issues());
        }
    }

    @Test
    void aggregateSafetyBudgetCountsProvinceCapsAlongsideVeins() {
        var original = OrePresets.balanced().rules().getFirst();
        var first = SpawnBand.province(
                "first",
                HeightDistribution.UNIFORM,
                -32,
                64,
                null,
                null,
                null,
                0.0D,
                new ProvinceSettings(512, 192, 48, 0.08D, 3000));
        var second = SpawnBand.province(
                "second",
                HeightDistribution.UNIFORM,
                -32,
                64,
                null,
                null,
                null,
                0.0D,
                new ProvinceSettings(512, 192, 48, 0.08D, 3000));
        var report = OreConfigValidator.validate(
                new OreProfileDocument(2, 0L, "province_budget", List.of(original.withBands(List.of(first, second)))),
                RegistryLookup.SKIP);

        assertFalse(report.valid());
        assertTrue(report.issues().stream().anyMatch(issue -> "budget.too_many_attempts".equals(issue.code())));
    }
}
