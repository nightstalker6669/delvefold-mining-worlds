package com.nightsta69.delvefold.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.model.GameplayPreset;
import com.nightsta69.delvefold.config.model.GameplaySettings;
import com.nightsta69.delvefold.config.model.GuideVisibility;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.PortalSettings;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.RenewalSettings;
import com.nightsta69.delvefold.config.model.WorldIdentitySettings;
import com.nightsta69.delvefold.config.model.WorldSettingsDocument;
import com.nightsta69.delvefold.config.validation.WorldSettingsValidator;
import org.junit.jupiter.api.Test;

class WorldSettingsValidatorTest {
    @Test
    void acceptsEveryGuideVisibilityMode() {
        for (GuideVisibility visibility : GuideVisibility.values()) {
            assertTrue(WorldSettingsValidator.validate(
                    WorldSettingsDocument.uninitialized().withGuideVisibility(visibility)).valid());
        }
    }

    @Test
    void rejectsUnsafePortalAndUninitializedTerrainSettings() {
        WorldSettingsDocument settings = new WorldSettingsDocument(
                WorldSettingsDocument.CURRENT_SCHEMA_VERSION,
                0,
                0,
                "",
                false,
                TerrainMode.FLAT,
                OrePreset.VANILLA_BALANCED,
                GameplaySettings.fromPreset(GameplayPreset.SAFE),
                new PortalSettings(true, true, 0, 1.0D),
                "vanilla_balanced",
                com.nightsta69.delvefold.config.model.WorldIdentitySettings.defaults());

        assertFalse(WorldSettingsValidator.validate(settings).valid());
    }

    @Test
    void rejectsOutOfRangeRenewalSchedule() {
        WorldSettingsDocument settings = WorldSettingsDocument.uninitialized().withIdentity(
                new WorldIdentitySettings("Test Mine",
                        com.nightsta69.delvefold.config.model.TerrainVariant.CLASSIC,
                        com.nightsta69.delvefold.config.model.LandmarkPreset.BALANCED,
                        true, true, true, new RenewalSettings(true, 0, 10081, -1L)));

        assertFalse(WorldSettingsValidator.validate(settings).valid());
    }

    @Test
    void rejectsNegativeGenerationSalt() {
        WorldSettingsDocument legacy = WorldSettingsDocument.uninitialized();
        WorldSettingsDocument settings = new WorldSettingsDocument(
                legacy.schemaVersion(), legacy.revision(), legacy.generationEpoch(), -1L,
                legacy.lastWorldOperationId(), legacy.initialized(), legacy.terrainMode(), legacy.orePreset(),
                legacy.gameplay(), legacy.portal(), legacy.activeProfileId(), legacy.identity(),
                legacy.guideVisibility());

        assertFalse(WorldSettingsValidator.validate(settings).valid());
    }

    @Test
    void rejectsActiveGenerationSaltWithoutAnInitializedWorld() {
        WorldSettingsDocument legacy = WorldSettingsDocument.uninitialized();
        WorldSettingsDocument settings = new WorldSettingsDocument(
                legacy.schemaVersion(), legacy.revision(), legacy.generationEpoch(), 42L,
                legacy.lastWorldOperationId(), false, null, legacy.orePreset(), legacy.gameplay(),
                legacy.portal(), legacy.activeProfileId(), legacy.identity(), legacy.guideVisibility());

        assertFalse(WorldSettingsValidator.validate(settings).valid());
    }

    @Test
    void acceptsNamespacedProfilesButEnforcesTotalLength() {
        WorldSettingsDocument valid = WorldSettingsDocument.uninitialized()
                .withActiveProfile("examplepack:metals/rich_tin");
        assertTrue(WorldSettingsValidator.validate(valid).valid());

        WorldSettingsDocument tooLong = WorldSettingsDocument.uninitialized()
                .withActiveProfile("pack:" + "a".repeat(124));
        assertFalse(WorldSettingsValidator.validate(tooLong).valid());
    }
}
