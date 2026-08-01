package com.nightsta69.delvefold.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.model.GameplayPreset;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.RenewalSeedMode;
import com.nightsta69.delvefold.config.model.TerrainMode;
import org.junit.jupiter.api.Test;

class LiveSettingsTransitionTest {
    @Test
    void allowsLiveSeedPolicyChangesWithoutReplacingTheActiveSalt() {
        var active = com.nightsta69.delvefold.config.model.WorldSettingsDocument.uninitialized()
                .initialize(TerrainMode.FLAT, OrePreset.VANILLA_BALANCED, GameplayPreset.SAFE);
        var selected = active.withIdentity(active.identity().withRenewal(
                active.identity().renewal().withSeedMode(RenewalSeedMode.ROTATE_ON_RECREATE)));

        assertTrue(LiveSettingsTransition.validate(active, selected).isEmpty());
        assertEquals(active.generationSalt(), selected.generationSalt());
    }

    @Test
    void rejectsLiveEpochSaltTerrainAndOperationChanges() {
        var active = com.nightsta69.delvefold.config.model.WorldSettingsDocument.uninitialized()
                .initialize(TerrainMode.FLAT, OrePreset.VANILLA_BALANCED, GameplayPreset.SAFE);
        var recreated = active.recreate(
                TerrainMode.WILD,
                com.nightsta69.delvefold.config.model.TerrainVariant.EXPANSIVE,
                null,
                null,
                "manual-edit");
        var lifecycleMutation = new com.nightsta69.delvefold.config.model.WorldSettingsDocument(
                recreated.schemaVersion(), recreated.revision(), recreated.generationEpoch(), 42L,
                recreated.lastWorldOperationId(), recreated.initialized(), recreated.terrainMode(),
                recreated.orePreset(), recreated.gameplay(), recreated.portal(), recreated.activeProfileId(),
                recreated.identity(), recreated.guideVisibility());

        var issues = LiveSettingsTransition.validate(active, lifecycleMutation);

        assertTrue(issues.stream().anyMatch(issue -> "settings.lifecycle.epoch_locked".equals(issue.code())));
        assertTrue(issues.stream().anyMatch(issue -> "settings.lifecycle.salt_locked".equals(issue.code())));
        assertTrue(issues.stream().anyMatch(issue -> "settings.lifecycle.terrain_locked".equals(issue.code())));
        assertTrue(issues.stream().anyMatch(issue -> "settings.lifecycle.variant_locked".equals(issue.code())));
        assertTrue(issues.stream().anyMatch(issue -> "settings.lifecycle.operation_locked".equals(issue.code())));
    }
}
