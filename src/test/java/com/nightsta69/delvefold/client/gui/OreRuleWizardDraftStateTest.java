package com.nightsta69.delvefold.client.gui;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.model.HeightDistribution;
import com.nightsta69.delvefold.config.model.OreBandPlacement;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.network.model.AdminSnapshot;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Characterizes local ore-rule editing independently of Minecraft screen and widget lifecycle. */
class OreRuleWizardDraftStateTest {
    @Test
    void emptyDraftReceivesTheEstablishedVariantTerrainAndBandDefaults() {
        AdminSnapshot.OreRuleDraft empty = new AdminSnapshot.OreRuleDraft(
                "empty",
                true,
                false,
                "example:deepslate_tin_ore",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        OreRuleWizardDraftState state = new OreRuleWizardDraftState(empty, null, false);

        assertAll(
                () -> assertEquals(
                        Map.of("example:deepslate_tin_ore", "minecraft:deepslate_ore_replaceables"),
                        state.selectedVariants),
                () -> assertEquals(Map.of(), state.variantStates.get("example:deepslate_tin_ore")),
                () -> assertEquals(
                        AdminSnapshot.OreVariantDraft.MIN_WEIGHT,
                        Objects.requireNonNull(state.variantWeights.get("example:deepslate_tin_ore"))
                                .intValue()),
                () -> assertEquals(List.of(TerrainMode.values()), state.terrainModes),
                () -> assertEquals(List.of(AdminSnapshot.OreBandDraft.defaultBand()), state.bands),
                () -> assertEquals("example:deepslate_tin_ore", state.focusedVariant),
                () -> assertFalse(state.unsupportedDuplicateSources));
    }

    @Test
    void duplicateSourcesRetainTheFirstVariantAndBlockUnsafeSaving() {
        AdminSnapshot.OreVariantDraft first = new AdminSnapshot.OreVariantDraft(
                "example:tin_ore", "", "minecraft:stone_ore_replaceables", Map.of("facing", "north"), 3);
        AdminSnapshot.OreVariantDraft duplicate = new AdminSnapshot.OreVariantDraft(
                "example:tin_ore", "", "minecraft:deepslate_ore_replaceables", Map.of("facing", "south"), 9);
        AdminSnapshot.OreRuleDraft draft = rule(List.of(first, duplicate));

        OreRuleWizardDraftState state = new OreRuleWizardDraftState(draft, "example:tin_ore", false);
        AdminSnapshot.OreRuleDraft payload = state.toDraft();

        assertAll(
                () -> assertTrue(OreRuleWizardDraftState.hasDuplicateSources(draft)),
                () -> assertTrue(state.unsupportedDuplicateSources),
                () -> assertEquals(1, payload.variants().size()),
                () -> assertEquals(first, payload.variants().getFirst()));
    }

    @Test
    void snapshotRefreshPreservesInvalidRawTextWhilePageReopenUsesCommittedValues() {
        AdminSnapshot.OreRuleDraft draft = rule(List.of(new AdminSnapshot.OreVariantDraft(
                "example:tin_ore", "", "minecraft:stone_ore_replaceables", Map.of("lit", "false"), 5)));
        OreRuleWizardDraftState active = new OreRuleWizardDraftState(draft, "example:tin_ore", false);
        active.variantStates.put(active.focusedVariant, Map.of("lit", "true"));
        active.variantWeights.put(active.focusedVariant, 13);
        active.biomeIncludes.clear();
        active.biomeIncludes.add("minecraft:badlands");
        active.biomeExcludes.add("minecraft:deep_dark");
        active.bands.set(0, band("edited", -32, 48));
        active.bandInputs.prime(0, active.bands.getFirst());
        active.bandInputs.setVeinSize("not-a-number");
        active.bandInputs.setPeakY("unfinished-");
        active.rawStateProperties = "lit=maybe,";
        active.rawWeight = "1001";
        active.rawBiomeIncludes = "#example:unfinished,";
        active.rawBiomeExcludes = "invalid selector";

        OreRuleWizardDraftState reopened = new OreRuleWizardDraftState(active.toDraft(), active.focusedVariant, false);
        reopened.bandInputs.prime(0, reopened.bands.getFirst());
        OreRuleWizardDraftState refreshed = new OreRuleWizardDraftState(active.toDraft(), active.focusedVariant, false);
        refreshed.copyRawInputsFrom(active);
        refreshed.bandInputs.prime(0, AdminSnapshot.OreBandDraft.defaultBand());

        assertAll(
                () -> assertEquals("edited", reopened.bandInputs.bandId()),
                () -> assertEquals("6", reopened.bandInputs.veinSize()),
                () -> assertEquals("0", reopened.bandInputs.peakY()),
                () -> assertEquals("lit=true", reopened.rawStateProperties),
                () -> assertEquals("13", reopened.rawWeight),
                () -> assertEquals("minecraft:badlands", reopened.rawBiomeIncludes),
                () -> assertEquals("minecraft:deep_dark", reopened.rawBiomeExcludes),
                () -> assertEquals(active.bandInputs.values(), refreshed.bandInputs.values()),
                () -> assertEquals("lit=maybe,", refreshed.rawStateProperties),
                () -> assertEquals("1001", refreshed.rawWeight),
                () -> assertEquals("#example:unfinished,", refreshed.rawBiomeIncludes),
                () -> assertEquals("invalid selector", refreshed.rawBiomeExcludes));
    }

    @Test
    void payloadConstructionPreservesExactSelectorsOrderingStateWeightsAndBands() {
        AdminSnapshot.OreBandDraft low = band("low", -64, 0);
        AdminSnapshot.OreBandDraft high = band("high", 1, 96);
        AdminSnapshot.OreRuleDraft input = new AdminSnapshot.OreRuleDraft(
                "example.tin",
                false,
                true,
                "example:tin_ore",
                List.of(
                        new AdminSnapshot.OreVariantDraft(
                                "example:tin_ore",
                                "",
                                "minecraft:stone_ore_replaceables",
                                Map.of("waterlogged", "false", "axis", "x"),
                                7),
                        new AdminSnapshot.OreVariantDraft(
                                "", "c:ores/tin", "minecraft:deepslate_ore_replaceables", Map.of(), 11)),
                List.of(TerrainMode.WILD, TerrainMode.FLAT),
                List.of("#delvefold:mining_biomes", "minecraft:badlands"),
                List.of("minecraft:deep_dark"),
                List.of(low, high));
        OreRuleWizardDraftState state = new OreRuleWizardDraftState(input, "#c:ores/tin", false);

        AdminSnapshot.OreRuleDraft payload = state.toDraft();

        assertAll(
                () -> assertEquals(input, payload),
                () -> assertEquals(
                        List.of("example:tin_ore", "#c:ores/tin"),
                        payload.variants().stream()
                                .map(AdminSnapshot.OreVariantDraft::sourceId)
                                .toList()),
                () -> assertEquals(7, payload.variants().get(0).weight()),
                () -> assertEquals(11, payload.variants().get(1).weight()),
                () -> assertEquals(List.of(low, high), payload.bands()));
    }

    private static AdminSnapshot.OreRuleDraft rule(List<AdminSnapshot.OreVariantDraft> variants) {
        return new AdminSnapshot.OreRuleDraft(
                "example.tin",
                true,
                false,
                "example:tin_ore",
                variants,
                List.of(TerrainMode.FLAT),
                List.of("#delvefold:mining_biomes"),
                List.of(),
                List.of(AdminSnapshot.OreBandDraft.defaultBand()));
    }

    private static AdminSnapshot.OreBandDraft band(String id, int minY, int maxY) {
        return new AdminSnapshot.OreBandDraft(
                id, 6, 2.5D, HeightDistribution.UNIFORM, minY, maxY, 0, minY, maxY, 0.25D, OreBandPlacement.VEIN, null);
    }
}
