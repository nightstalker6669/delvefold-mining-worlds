package com.nightsta69.delvefold.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nightsta69.delvefold.config.model.HeightDistribution;
import com.nightsta69.delvefold.config.model.OreBandPlacement;
import com.nightsta69.delvefold.config.model.ProvinceSettings;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.network.model.AdminSnapshot;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OreRuleDraftConversionTest {
    @Test
    void advancedGuiFieldsBecomeCanonicalOreRuleFields() {
        AdminSnapshot.OreRuleDraft draft = new AdminSnapshot.OreRuleDraft(
                "example.tin",
                true,
                false,
                "example:tin_ore",
                List.of(new AdminSnapshot.OreVariantDraft(
                        "example:tin_ore", "", "minecraft:stone_ore_replaceables", Map.of("lit", "true"), 37)),
                List.of(TerrainMode.CAVERN),
                List.of("#delvefold:mining_biomes", "example:deep_caves"),
                List.of("minecraft:plains"),
                List.of(AdminSnapshot.OreBandDraft.defaultBand()));

        var rule = DefaultDelvefoldAdminService.fromDraft(draft);

        assertEquals(Map.of("lit", "true"), rule.targets().getFirst().state());
        assertEquals(37, rule.targets().getFirst().weight());
        assertEquals(
                List.of("#delvefold:mining_biomes", "example:deep_caves"),
                rule.biomes().include());
        assertEquals(List.of("minecraft:plains"), rule.biomes().exclude());
        assertEquals(java.util.Set.of(TerrainMode.CAVERN), rule.terrainModes());

        AdminSnapshot.OreRuleDraft encodedForGui = DefaultDelvefoldAdminService.toDraft(rule);
        assertEquals(37, encodedForGui.variants().getFirst().weight());
    }

    @Test
    void mapperPreservesActiveDistributionProvinceAndTagTargetSemantics() {
        ProvinceSettings province = new ProvinceSettings(768, 224, 72, 0.125D, 1536);
        AdminSnapshot.OreRuleDraft draft = new AdminSnapshot.OreRuleDraft(
                "example.silver",
                false,
                true,
                "minecraft:air",
                List.of(new AdminSnapshot.OreVariantDraft(
                        "", "#c:ores/silver", "#minecraft:deepslate_ore_replaceables", Map.of(), 211)),
                List.of(TerrainMode.FLAT, TerrainMode.WILD),
                List.of(),
                List.of("minecraft:desert"),
                List.of(
                        new AdminSnapshot.OreBandDraft(
                                "triangle",
                                7,
                                3.5D,
                                HeightDistribution.TRIANGLE,
                                -48,
                                80,
                                12,
                                -5,
                                5,
                                0.25D,
                                OreBandPlacement.VEIN,
                                null),
                        new AdminSnapshot.OreBandDraft(
                                "trapezoid",
                                5,
                                2.25D,
                                HeightDistribution.TRAPEZOID,
                                -32,
                                96,
                                17,
                                -8,
                                24,
                                0.5D,
                                OreBandPlacement.VEIN,
                                null),
                        new AdminSnapshot.OreBandDraft(
                                "province",
                                1,
                                0.0D,
                                HeightDistribution.UNIFORM,
                                -16,
                                128,
                                0,
                                -16,
                                128,
                                0.0D,
                                OreBandPlacement.PROVINCE,
                                province)));

        var rule = DefaultDelvefoldAdminService.fromDraft(draft);

        assertEquals("c:ores/silver", rule.targets().getFirst().blockTag());
        assertEquals(
                "minecraft:deepslate_ore_replaceables",
                rule.targets().getFirst().replaceTag());
        assertEquals(12, rule.bands().get(0).peakY());
        assertEquals(-8, rule.bands().get(1).plateauMinY());
        assertEquals(24, rule.bands().get(1).plateauMaxY());
        assertEquals(province, rule.bands().get(2).province());

        AdminSnapshot.OreRuleDraft encoded = DefaultDelvefoldAdminService.toDraft(rule);
        assertEquals("minecraft:air", encoded.primaryBlockId());
        assertEquals("c:ores/silver", encoded.variants().getFirst().blockTag());
        assertEquals(12, encoded.bands().get(0).peakY());
        assertEquals(-8, encoded.bands().get(1).plateauMinY());
        assertEquals(24, encoded.bands().get(1).plateauMaxY());
        assertEquals(OreBandPlacement.PROVINCE, encoded.bands().get(2).placement());
        assertEquals(province, encoded.bands().get(2).province());
    }
}
