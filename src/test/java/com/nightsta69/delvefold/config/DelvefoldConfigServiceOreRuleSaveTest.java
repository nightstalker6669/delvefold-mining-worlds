package com.nightsta69.delvefold.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.model.OreRule;
import java.util.List;
import org.junit.jupiter.api.Test;

class DelvefoldConfigServiceOreRuleSaveTest {
    @Test
    void createOnlyRejectsAnExistingRuleWithoutChangingIt() {
        OreRule existing = OrePresets.balanced().rules().getFirst();
        OreRule replacement = existing.withEnabled(!existing.enabled());

        OreRuleSavePlanner.Plan plan = OreRuleSavePlanner.plan(List.of(existing), replacement, true);

        assertTrue(plan.collision());
        assertEquals(List.of(existing), plan.rules());
    }

    @Test
    void updateReplacesAnExistingRule() {
        OreRule existing = OrePresets.balanced().rules().getFirst();
        OreRule replacement = existing.withEnabled(!existing.enabled());

        OreRuleSavePlanner.Plan plan = OreRuleSavePlanner.plan(List.of(existing), replacement, false);

        assertFalse(plan.collision());
        assertEquals(List.of(replacement), plan.rules());
    }

    @Test
    void createOnlyAddsANewRuleWhenItsIdIsUnique() {
        OreRule existing = OrePresets.balanced().rules().getFirst();
        OreRule created = new OreRule(
                "custom_unique",
                existing.enabled(),
                existing.required(),
                existing.terrainModes(),
                existing.targets(),
                existing.biomes(),
                existing.bands());

        OreRuleSavePlanner.Plan plan = OreRuleSavePlanner.plan(List.of(existing), created, true);

        assertFalse(plan.collision());
        assertEquals(List.of(existing, created), plan.rules());
    }
}
