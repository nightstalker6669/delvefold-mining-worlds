package com.nightsta69.delvefold.config;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.nightsta69.delvefold.config.model.GameplayPreset;
import com.nightsta69.delvefold.config.model.GameplaySettings;
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
}
