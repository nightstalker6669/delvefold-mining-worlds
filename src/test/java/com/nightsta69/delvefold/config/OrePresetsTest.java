package com.nightsta69.delvefold.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.validation.OreConfigValidator;
import com.nightsta69.delvefold.config.validation.RegistryLookup;
import org.junit.jupiter.api.Test;

class OrePresetsTest {
    @Test
    void everyBuiltInPresetPassesStructuralValidation() {
        for (OrePreset preset : OrePreset.values()) {
            var report = OreConfigValidator.validate(OrePresets.create(preset), RegistryLookup.SKIP);
            assertTrue(report.valid(), () -> preset + " failed validation: " + report.issues());
        }
    }

    @Test
    void richDoublesEveryBalancedAttemptRateWithoutMovingBands() {
        var balanced = OrePresets.balanced();
        var rich = OrePresets.rich();
        assertEquals(balanced.rules().size(), rich.rules().size());
        for (int ruleIndex = 0; ruleIndex < balanced.rules().size(); ruleIndex++) {
            var baseRule = balanced.rules().get(ruleIndex);
            var richRule = rich.rules().get(ruleIndex);
            assertEquals(baseRule.id(), richRule.id());
            assertEquals(baseRule.bands().size(), richRule.bands().size());
            for (int bandIndex = 0; bandIndex < baseRule.bands().size(); bandIndex++) {
                var baseBand = baseRule.bands().get(bandIndex);
                var richBand = richRule.bands().get(bandIndex);
                assertEquals(baseBand.attemptsPerChunk() * 2.0D, richBand.attemptsPerChunk());
                assertEquals(baseBand.minY(), richBand.minY());
                assertEquals(baseBand.maxY(), richBand.maxY());
                assertEquals(baseBand.veinSize(), richBand.veinSize());
            }
        }
    }

    @Test
    void emptyPresetContainsNoOreRules() {
        assertTrue(OrePresets.empty().rules().isEmpty());
    }
}
