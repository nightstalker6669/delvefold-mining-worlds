package com.nightsta69.delvefold.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.model.HeightDistribution;
import com.nightsta69.delvefold.config.model.OreTarget;
import com.nightsta69.delvefold.config.model.TerrainMode;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class OreRuleTemplatesTest {
    @Test
    void uncommonTemplateIsTheEstablishedCommandPreset() {
        var band = OreRuleTemplates.uncommonBand();

        assertEquals("main", band.id());
        assertEquals(8, band.veinSize());
        assertEquals(8.0D, band.attemptsPerChunk());
        assertEquals(HeightDistribution.TRIANGLE, band.distribution());
        assertEquals(-64, band.minY());
        assertEquals(128, band.maxY());
        assertEquals(16, band.peakY());
        assertEquals(0.0D, band.discardOnAirExposure());

        var rule = OreRuleTemplates.uncommon(
                "example_tin", List.of(OreTarget.of("example:tin_ore", "minecraft:stone_ore_replaceables")));
        assertTrue(rule.enabled());
        assertFalse(rule.required());
        assertEquals(EnumSet.allOf(TerrainMode.class), rule.terrainModes());
        assertEquals(List.of(band), rule.bands());
    }
}
