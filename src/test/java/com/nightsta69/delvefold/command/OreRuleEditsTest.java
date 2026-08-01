package com.nightsta69.delvefold.command;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nightsta69.delvefold.config.model.BiomeFilter;
import com.nightsta69.delvefold.config.model.HeightDistribution;
import com.nightsta69.delvefold.config.model.OreBandPlacement;
import com.nightsta69.delvefold.config.model.OreRule;
import com.nightsta69.delvefold.config.model.OreTarget;
import com.nightsta69.delvefold.config.model.ProvinceSettings;
import com.nightsta69.delvefold.config.model.SpawnBand;
import com.nightsta69.delvefold.config.model.TerrainMode;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Characterizes the pure ore-rule transformations used by Brigadier command handlers. */
class OreRuleEditsTest {
    private static final String STONE_HOST = "minecraft:stone_ore_replaceables";
    private static final String DEEPSLATE_HOST = "minecraft:deepslate_ore_replaceables";

    @Test
    void targetEditsPreserveOrderAndDistinguishExactBlocksFromOutputTags() {
        OreTarget exact = OreTarget.of("example:copper_ore", STONE_HOST, 2);
        OreTarget tag = OreTarget.ofTag("example:copper_ore", DEEPSLATE_HOST, 3);
        OreRule original = rule(List.of(exact, tag, exact), List.of());

        OreRule withExact = OreRuleEdits.addExactTarget(original, "example:silver_ore", '#' + STONE_HOST, 7);
        OreRule withTag = OreRuleEdits.addTagTarget(original, "#c:ores/silver", DEEPSLATE_HOST, 11);
        OreRule withoutExact = OreRuleEdits.removeExactTargets(original, "example:copper_ore");
        OreRule withoutTag = OreRuleEdits.removeTagTargets(original, "example:copper_ore");

        assertAll(
                () -> assertEquals(List.of(exact, tag, exact), original.targets()),
                () -> assertEquals(
                        OreTarget.of("example:silver_ore", STONE_HOST, 7),
                        withExact.targets().getLast()),
                () -> assertEquals(
                        OreTarget.ofTag("c:ores/silver", DEEPSLATE_HOST, 11),
                        withTag.targets().getLast()),
                () -> assertEquals(List.of(tag), withoutExact.targets()),
                () -> assertEquals(List.of(exact, exact), withoutTag.targets()));
    }

    @Test
    void weightReplacementRequiresOneSourceOfTheRequestedKind() {
        OreTarget exact = OreTarget.of("example:tin_ore", STONE_HOST, 2);
        OreTarget tag = OreTarget.ofTag("example:tin_ore", DEEPSLATE_HOST, 3);
        OreRule original = rule(List.of(exact, tag), List.of());

        OreRule exactChanged = OreRuleEdits.replaceTargetWeight(original, "example:tin_ore", false, 19);
        OreRule tagChanged = OreRuleEdits.replaceTargetWeight(original, "example:tin_ore", true, 23);

        assertAll(
                () -> assertEquals(19, exactChanged.targets().get(0).weight().intValue()),
                () -> assertEquals(3, exactChanged.targets().get(1).weight().intValue()),
                () -> assertEquals(2, tagChanged.targets().get(0).weight().intValue()),
                () -> assertEquals(23, tagChanged.targets().get(1).weight().intValue()));

        IllegalArgumentException missingExact = assertThrows(
                IllegalArgumentException.class,
                () -> OreRuleEdits.replaceTargetWeight(original, "example:lead_ore", false, 5));
        IllegalArgumentException missingTag = assertThrows(
                IllegalArgumentException.class,
                () -> OreRuleEdits.replaceTargetWeight(original, "c:ores/lead", true, 5));
        IllegalArgumentException ambiguous = assertThrows(
                IllegalArgumentException.class,
                () -> OreRuleEdits.replaceTargetWeight(
                        rule(List.of(exact, exact), List.of()), "example:tin_ore", false, 5));

        assertAll(
                () -> assertEquals("Unknown target block: example:lead_ore", missingExact.getMessage()),
                () -> assertEquals("Unknown output tag: #c:ores/lead", missingTag.getMessage()),
                () -> assertEquals(
                        "Ambiguous target block: example:tin_ore; multiple targets use that source. "
                                + "Edit the specific target in canonical JSON.",
                        ambiguous.getMessage()));
    }

    @Test
    void bandAdditionCopiesTheLegacyTemplateFieldsAndRemovalDeletesEveryMatchingId() {
        ProvinceSettings province = new ProvinceSettings(768, 224, 72, 0.125D, 1536);
        SpawnBand template =
                SpawnBand.province("template", HeightDistribution.TRAPEZOID, -32, 96, 17, -8, 24, 0.5D, province);
        SpawnBand retained = SpawnBand.uniform("retained", 4, 2.0D, -16, 64, 0.1D);
        OreRule original = rule(List.of(), List.of(retained));

        OreRule added = OreRuleEdits.addBand(original, "custom", template);
        SpawnBand copied = added.bands().getLast();
        OreRule removed = OreRuleEdits.removeBands(rule(List.of(), List.of(retained, retained, template)), "retained");

        assertAll(
                () -> assertEquals(List.of(retained), original.bands()),
                () -> assertEquals("custom", copied.id()),
                () -> assertEquals(template.veinSize(), copied.veinSize()),
                () -> assertEquals(template.attemptsPerChunk(), copied.attemptsPerChunk()),
                () -> assertEquals(template.distribution(), copied.distribution()),
                () -> assertEquals(template.minY(), copied.minY()),
                () -> assertEquals(template.maxY(), copied.maxY()),
                () -> assertEquals(template.peakY(), copied.peakY()),
                () -> assertEquals(template.plateauMinY(), copied.plateauMinY()),
                () -> assertEquals(template.plateauMaxY(), copied.plateauMaxY()),
                () -> assertEquals(template.discardOnAirExposure(), copied.discardOnAirExposure()),
                () -> assertEquals(OreBandPlacement.VEIN, copied.placement()),
                () -> assertNull(copied.province()),
                () -> assertEquals(List.of(template), removed.bands()));
    }

    @Test
    void classicBandFieldsRetainEveryUnselectedValueAndExactFailureMessages() {
        SpawnBand originalBand = new SpawnBand(
                "primary",
                7,
                3.5D,
                HeightDistribution.TRAPEZOID,
                -48,
                80,
                12,
                -5,
                25,
                0.25D,
                OreBandPlacement.VEIN,
                null);
        OreRule original = rule(List.of(), List.of(originalBand));

        SpawnBand vein = onlyBand(OreRuleEdits.setBandField(original, "primary", "vein_size", 9.0D));
        SpawnBand attempts = onlyBand(OreRuleEdits.setBandField(original, "primary", "attempts", 4.75D));
        SpawnBand min = onlyBand(OreRuleEdits.setBandField(original, "primary", "min_y", -32.0D));
        SpawnBand max = onlyBand(OreRuleEdits.setBandField(original, "primary", "max_y", 112.0D));
        SpawnBand peak = onlyBand(OreRuleEdits.setBandField(original, "primary", "peak_y", 18.0D));
        SpawnBand plateauMin = onlyBand(OreRuleEdits.setBandField(original, "primary", "plateau_min_y", -3.0D));
        SpawnBand plateauMax = onlyBand(OreRuleEdits.setBandField(original, "primary", "plateau_max_y", 31.0D));
        SpawnBand discard = onlyBand(OreRuleEdits.setBandField(original, "primary", "discard", 0.75D));

        assertAll(
                () -> assertEquals(9, vein.veinSize()),
                () -> assertEquals(4.75D, attempts.attemptsPerChunk()),
                () -> assertEquals(-32, min.minY()),
                () -> assertEquals(112, max.maxY()),
                () -> assertEquals(Integer.valueOf(18), peak.peakY()),
                () -> assertEquals(Integer.valueOf(-3), plateauMin.plateauMinY()),
                () -> assertEquals(Integer.valueOf(31), plateauMax.plateauMaxY()),
                () -> assertEquals(0.75D, discard.discardOnAirExposure()),
                () -> assertEquals(originalBand.distribution(), vein.distribution()),
                () -> assertEquals(originalBand.province(), vein.province()));

        IllegalArgumentException fractional = assertThrows(
                IllegalArgumentException.class,
                () -> OreRuleEdits.setBandField(original, "primary", "vein_size", 4.5D));
        IllegalArgumentException nonFinite = assertThrows(
                IllegalArgumentException.class,
                () -> OreRuleEdits.setBandField(original, "primary", "min_y", Double.NaN));
        IllegalArgumentException unknown = assertThrows(
                IllegalArgumentException.class, () -> OreRuleEdits.setBandField(original, "primary", "unknown", 1.0D));

        assertAll(
                () -> assertEquals("vein_size requires a whole number", fractional.getMessage()),
                () -> assertEquals("min_y requires a whole number", nonFinite.getMessage()),
                () -> assertEquals("Unknown band field: unknown", unknown.getMessage()),
                () -> assertEquals(
                        original,
                        OreRuleEdits.setBandField(original, "missing", "unknown", Double.NaN),
                        "An invalid edit for a missing band remains a no-op because no transformer is invoked"));
    }

    @Test
    void placementTransitionsPreserveExistingModesAndUseHistoricalRestoreDefaults() {
        SpawnBand vein = SpawnBand.triangle("primary", 13, 2.5D, -48, 80, 4, 0.2D);
        ProvinceSettings custom = new ProvinceSettings(1024, 320, 96, 0.2D, 2048);
        SpawnBand province =
                SpawnBand.province("primary", HeightDistribution.TRIANGLE, -48, 80, 4, null, null, 0.2D, custom);

        SpawnBand convertedToProvince = onlyBand(
                OreRuleEdits.setBandPlacement(rule(List.of(), List.of(vein)), "primary", OreBandPlacement.PROVINCE));
        SpawnBand unchangedProvince = onlyBand(OreRuleEdits.setBandPlacement(
                rule(List.of(), List.of(province)), "primary", OreBandPlacement.PROVINCE));
        SpawnBand convertedToVein = onlyBand(
                OreRuleEdits.setBandPlacement(rule(List.of(), List.of(province)), "primary", OreBandPlacement.VEIN));
        SpawnBand unchangedVein = onlyBand(
                OreRuleEdits.setBandPlacement(rule(List.of(), List.of(vein)), "primary", OreBandPlacement.VEIN));

        assertAll(
                () -> assertEquals(OreBandPlacement.PROVINCE, convertedToProvince.placement()),
                () -> assertEquals(1, convertedToProvince.veinSize()),
                () -> assertEquals(0.0D, convertedToProvince.attemptsPerChunk()),
                () -> assertEquals(ProvinceSettings.defaults(), convertedToProvince.province()),
                () -> assertEquals(custom, unchangedProvince.province()),
                () -> assertEquals(OreBandPlacement.VEIN, convertedToVein.placement()),
                () -> assertEquals(8, convertedToVein.veinSize()),
                () -> assertEquals(8.0D, convertedToVein.attemptsPerChunk()),
                () -> assertNull(convertedToVein.province()),
                () -> assertEquals(13, unchangedVein.veinSize()),
                () -> assertEquals(2.5D, unchangedVein.attemptsPerChunk()));
    }

    @Test
    void provinceFieldsPreserveUnselectedValuesAndRejectClassicBands() {
        ProvinceSettings settings = new ProvinceSettings(768, 224, 72, 0.125D, 1536);
        SpawnBand province =
                SpawnBand.province("primary", HeightDistribution.UNIFORM, -16, 128, null, null, null, 0.0D, settings);
        OreRule original = rule(List.of(), List.of(province));

        ProvinceSettings region =
                province(onlyBand(OreRuleEdits.setProvinceField(original, "primary", "region_size", 1024.0D)));
        ProvinceSettings radius =
                province(onlyBand(OreRuleEdits.setProvinceField(original, "primary", "radius", 320.0D)));
        ProvinceSettings thickness =
                province(onlyBand(OreRuleEdits.setProvinceField(original, "primary", "vertical_thickness", 96.0D)));
        ProvinceSettings density =
                province(onlyBand(OreRuleEdits.setProvinceField(original, "primary", "density", 0.2D)));
        ProvinceSettings workCap =
                province(onlyBand(OreRuleEdits.setProvinceField(original, "primary", "work_cap", 2048.0D)));

        assertAll(
                () -> assertEquals(1024, region.regionSize()),
                () -> assertEquals(settings.radius(), region.radius()),
                () -> assertEquals(320, radius.radius()),
                () -> assertEquals(96, thickness.verticalThickness()),
                () -> assertEquals(0.2D, density.density()),
                () -> assertEquals(2048, workCap.perChunkWorkCap()));

        SpawnBand classic = SpawnBand.uniform("primary", 8, 8.0D, -64, 64, 0.0D);
        IllegalArgumentException wrongMode = assertThrows(
                IllegalArgumentException.class,
                () -> OreRuleEdits.setProvinceField(rule(List.of(), List.of(classic)), "primary", "radius", 64.0D));
        IllegalArgumentException fractional = assertThrows(
                IllegalArgumentException.class,
                () -> OreRuleEdits.setProvinceField(original, "primary", "work_cap", 4.5D));
        IllegalArgumentException unknown = assertThrows(
                IllegalArgumentException.class,
                () -> OreRuleEdits.setProvinceField(original, "primary", "unknown", 1.0D));

        assertAll(
                () -> assertEquals(
                        "Band primary is not a province; set its placement to province first", wrongMode.getMessage()),
                () -> assertEquals("work_cap requires a whole number", fractional.getMessage()),
                () -> assertEquals("Unknown province field: unknown", unknown.getMessage()),
                () -> assertEquals(
                        original,
                        OreRuleEdits.setProvinceField(original, "missing", "unknown", Double.NaN),
                        "A missing band does not evaluate a province edit"));
    }

    private static OreRule rule(List<OreTarget> targets, List<SpawnBand> bands) {
        return new OreRule(
                "example.rule",
                true,
                false,
                Set.of(TerrainMode.FLAT, TerrainMode.WILD),
                targets,
                BiomeFilter.ALL_MINING_BIOMES,
                bands);
    }

    private static SpawnBand onlyBand(OreRule rule) {
        return rule.bands().getFirst();
    }

    private static ProvinceSettings province(SpawnBand band) {
        return Objects.requireNonNull(band.province());
    }
}
