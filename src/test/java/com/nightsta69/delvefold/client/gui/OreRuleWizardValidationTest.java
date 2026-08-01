package com.nightsta69.delvefold.client.gui;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.model.HeightDistribution;
import com.nightsta69.delvefold.config.model.OreBandPlacement;
import com.nightsta69.delvefold.config.model.ProvinceSettings;
import com.nightsta69.delvefold.network.ProtocolLimits;
import com.nightsta69.delvefold.network.model.AdminSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Exhaustively characterizes the ore wizard's pure parsing and band semantics. */
class OreRuleWizardValidationTest {
    @Test
    void stateAndSelectorParsingRetainsOrderTrimsValuesAndDeduplicatesSelectors() {
        Map<String, String> state = OreRuleWizardValidation.parseState("axis=x, waterlogged= false ");
        assertAll(
                () -> assertEquals(Map.of("axis", "x", "waterlogged", "false"), state),
                () -> assertEquals(List.of("axis", "waterlogged"), new ArrayList<>(state.keySet())),
                () -> assertEquals(
                        List.of("#delvefold:mining_biomes", "minecraft:badlands"),
                        OreRuleWizardValidation.parseSelectors(
                                "#delvefold:mining_biomes, minecraft:badlands #delvefold:mining_biomes")),
                () -> assertTrue(OreRuleWizardValidation.parseState("  ").isEmpty()),
                () -> assertTrue(OreRuleWizardValidation.parseSelectors(null).isEmpty()),
                () -> assertEquals(1, OreRuleWizardValidation.parseWeight(" 1 ")),
                () -> assertEquals(1000, OreRuleWizardValidation.parseWeight("1000")));
    }

    @Test
    void invalidStateSelectorsAndWeightsKeepTheirLocalizedFailureTypes() {
        OreRuleWizardValidation.LocalizedValidationException format = assertThrows(
                OreRuleWizardValidation.LocalizedValidationException.class,
                () -> OreRuleWizardValidation.parseState("facing"));
        OreRuleWizardValidation.LocalizedValidationException duplicate = assertThrows(
                OreRuleWizardValidation.LocalizedValidationException.class,
                () -> OreRuleWizardValidation.parseState("axis=x,axis=z"));
        OreRuleWizardValidation.LocalizedValidationException selector = assertThrows(
                OreRuleWizardValidation.LocalizedValidationException.class,
                () -> OreRuleWizardValidation.parseSelectors("not a valid selector!"));

        assertAll(
                () -> assertEquals("screen.delvefold.ore_wizard.validation.state_format", format.translationKey()),
                () -> assertEquals(
                        "screen.delvefold.ore_wizard.validation.state_duplicate", duplicate.translationKey()),
                () -> assertEquals("screen.delvefold.ore_wizard.validation.biome_selector", selector.translationKey()),
                () -> assertThrows(NumberFormatException.class, () -> OreRuleWizardValidation.parseWeight("0")),
                () -> assertThrows(NumberFormatException.class, () -> OreRuleWizardValidation.parseWeight("1001")),
                () -> assertThrows(NumberFormatException.class, () -> OreRuleWizardValidation.parseWeight("1.5")));
    }

    @Test
    void completeFilterParsingReturnsImmutableValuesAndEnforcesProtocolCounts() {
        OreRuleWizardValidation.FilterValues filters = OreRuleWizardValidation.parseFilters(
                "axis=x,waterlogged=false", "#delvefold:mining_biomes minecraft:badlands", "minecraft:deep_dark");
        String oversizedState = IntStream.rangeClosed(0, ProtocolLimits.MAX_STATE_PROPERTIES)
                .mapToObj(index -> "property" + index + "=value")
                .collect(java.util.stream.Collectors.joining(","));
        OreRuleWizardValidation.LocalizedValidationException limit = assertThrows(
                OreRuleWizardValidation.LocalizedValidationException.class,
                () -> OreRuleWizardValidation.parseFilters(oversizedState, "", ""));

        assertAll(
                () -> assertEquals(Map.of("axis", "x", "waterlogged", "false"), filters.state()),
                () -> assertEquals(List.of("#delvefold:mining_biomes", "minecraft:badlands"), filters.biomeIncludes()),
                () -> assertEquals(List.of("minecraft:deep_dark"), filters.biomeExcludes()),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> filters.state().put("axis", "z")),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> filters.biomeIncludes().add("x:y")),
                () -> assertEquals("screen.delvefold.ore_wizard.validation.filter_limit", limit.translationKey()));
    }

    @ParameterizedTest
    @MethodSource("validDistributionBands")
    void allHeightDistributionsAcceptTheirCompleteValidShapes(
            HeightDistribution distribution, int peakY, int plateauMin, int plateauMax) {
        OreRuleWizardValidation.BandParseResult result = OreRuleWizardValidation.parseBand(
                values(
                        "main",
                        "8",
                        "4.5",
                        "-32",
                        "64",
                        Integer.toString(peakY),
                        Integer.toString(plateauMin),
                        Integer.toString(plateauMax),
                        "0.25"),
                band(distribution, OreBandPlacement.VEIN, null));

        assertTrue(result.accepted());
        AdminSnapshot.OreBandDraft accepted = Objects.requireNonNull(result.band());
        assertAll(
                () -> assertEquals("", result.messageKey()),
                () -> assertEquals(distribution, accepted.distribution()),
                () -> assertEquals(peakY, accepted.peakY()),
                () -> assertEquals(plateauMin, accepted.plateauMinY()),
                () -> assertEquals(plateauMax, accepted.plateauMaxY()));
    }

    @Test
    void distributionSpecificShapeErrorsAreRejectedWithoutConstrainingUnusedUniformFields() {
        OreRuleWizardValidation.BandParseResult uniform = OreRuleWizardValidation.parseBand(
                values("main", "8", "4", "-32", "64", "320", "100", "-100", "0"),
                band(HeightDistribution.UNIFORM, OreBandPlacement.VEIN, null));
        OreRuleWizardValidation.BandParseResult triangle = OreRuleWizardValidation.parseBand(
                values("main", "8", "4", "-32", "64", "65", "-8", "8", "0"),
                band(HeightDistribution.TRIANGLE, OreBandPlacement.VEIN, null));
        OreRuleWizardValidation.BandParseResult trapezoid = OreRuleWizardValidation.parseBand(
                values("main", "8", "4", "-32", "64", "0", "12", "-12", "0"),
                band(HeightDistribution.TRAPEZOID, OreBandPlacement.VEIN, null));

        assertAll(
                () -> assertTrue(uniform.accepted()),
                () -> assertBandValuesError(triangle),
                () -> assertBandValuesError(trapezoid));
    }

    @Test
    void provinceBandsParseAllRawFieldsButCoerceVeinWorkAndPreserveProvinceSettings() {
        ProvinceSettings province = new ProvinceSettings(1024, 256, 72, 0.15D, 2048);
        OreRuleWizardValidation.BandParseResult configured = OreRuleWizardValidation.parseBand(
                values("province", "0", "NaN", "-16", "96", "0", "-8", "8", "0.1"),
                band(HeightDistribution.UNIFORM, OreBandPlacement.PROVINCE, province));
        OreRuleWizardValidation.BandParseResult defaults = OreRuleWizardValidation.parseBand(
                values("province", "999", "Infinity", "-16", "96", "0", "-8", "8", "0.1"),
                band(HeightDistribution.UNIFORM, OreBandPlacement.PROVINCE, null));
        OreRuleWizardValidation.BandParseResult invalidRaw = OreRuleWizardValidation.parseBand(
                values("province", "unused", "0", "-16", "96", "0", "-8", "8", "0.1"),
                band(HeightDistribution.UNIFORM, OreBandPlacement.PROVINCE, province));
        AdminSnapshot.OreBandDraft configuredBand = Objects.requireNonNull(configured.band());
        AdminSnapshot.OreBandDraft defaultBand = Objects.requireNonNull(defaults.band());

        assertAll(
                () -> assertTrue(configured.accepted()),
                () -> assertEquals(1, configuredBand.veinSize()),
                () -> assertEquals(0.0D, configuredBand.attemptsPerChunk()),
                () -> assertEquals(province, configuredBand.province()),
                () -> assertTrue(defaults.accepted()),
                () -> assertEquals(ProvinceSettings.defaults(), defaultBand.province()),
                () -> assertNumberError(invalidRaw));
    }

    @ParameterizedTest
    @MethodSource("invalidRawBands")
    void invalidRawAndSemanticValuesReturnTheEstablishedMessage(
            OreRuleWizardBandInputs.Values raw, String expectedMessage) {
        OreRuleWizardValidation.BandParseResult result =
                OreRuleWizardValidation.parseBand(raw, band(HeightDistribution.UNIFORM, OreBandPlacement.VEIN, null));

        assertAll(() -> assertFalse(result.accepted()), () -> assertEquals(expectedMessage, result.messageKey()));
    }

    private static Stream<Arguments> validDistributionBands() {
        return Stream.of(
                Arguments.of(HeightDistribution.UNIFORM, 320, 100, -100),
                Arguments.of(HeightDistribution.TRIANGLE, -32, -8, 8),
                Arguments.of(HeightDistribution.TRIANGLE, 64, -8, 8),
                Arguments.of(HeightDistribution.TRAPEZOID, 0, -32, 64),
                Arguments.of(HeightDistribution.TRAPEZOID, 0, -8, 8));
    }

    private static Stream<Arguments> invalidRawBands() {
        String number = "screen.delvefold.ore_wizard.validation.number";
        String bandValues = "screen.delvefold.ore_wizard.validation.band_values";
        return Stream.of(
                Arguments.of(values("main", "eight", "4", "-32", "64", "0", "-8", "8", "0"), number),
                Arguments.of(values("Bad ID", "8", "4", "-32", "64", "0", "-8", "8", "0"), bandValues),
                Arguments.of(values("main", "0", "4", "-32", "64", "0", "-8", "8", "0"), bandValues),
                Arguments.of(values("main", "65", "4", "-32", "64", "0", "-8", "8", "0"), bandValues),
                Arguments.of(values("main", "8", "NaN", "-32", "64", "0", "-8", "8", "0"), bandValues),
                Arguments.of(values("main", "8", "257", "-32", "64", "0", "-8", "8", "0"), bandValues),
                Arguments.of(values("main", "8", "4", "-65", "64", "0", "-8", "8", "0"), bandValues),
                Arguments.of(values("main", "8", "4", "-32", "321", "0", "-8", "8", "0"), bandValues),
                Arguments.of(values("main", "8", "4", "64", "-32", "0", "-8", "8", "0"), bandValues),
                Arguments.of(values("main", "8", "4", "-32", "64", "0", "-8", "8", "NaN"), bandValues),
                Arguments.of(values("main", "8", "4", "-32", "64", "0", "-8", "8", "1.01"), bandValues));
    }

    private static void assertBandValuesError(OreRuleWizardValidation.BandParseResult result) {
        assertAll(
                () -> assertFalse(result.accepted()),
                () -> assertEquals("screen.delvefold.ore_wizard.validation.band_values", result.messageKey()));
    }

    private static void assertNumberError(OreRuleWizardValidation.BandParseResult result) {
        assertAll(
                () -> assertFalse(result.accepted()),
                () -> assertEquals("screen.delvefold.ore_wizard.validation.number", result.messageKey()));
    }

    private static AdminSnapshot.OreBandDraft band(
            HeightDistribution distribution, OreBandPlacement placement, @Nullable ProvinceSettings province) {
        return new AdminSnapshot.OreBandDraft(
                "main", 8, 4.0D, distribution, -32, 64, 0, -8, 8, 0.0D, placement, province);
    }

    private static OreRuleWizardBandInputs.Values values(
            String id,
            String veinSize,
            String attempts,
            String minY,
            String maxY,
            String peakY,
            String plateauMin,
            String plateauMax,
            String airDiscard) {
        return new OreRuleWizardBandInputs.Values(
                id, veinSize, attempts, minY, maxY, peakY, plateauMin, plateauMax, airDiscard);
    }
}
