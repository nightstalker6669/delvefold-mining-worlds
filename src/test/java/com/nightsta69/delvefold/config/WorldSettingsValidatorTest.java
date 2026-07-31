package com.nightsta69.delvefold.config;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.nightsta69.delvefold.config.model.GameplayPreset;
import com.nightsta69.delvefold.config.model.GameplaySettings;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.PortalSettings;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.WorldSettingsDocument;
import com.nightsta69.delvefold.config.validation.WorldSettingsValidator;
import org.junit.jupiter.api.Test;

class WorldSettingsValidatorTest {
    @Test
    void rejectsUnsafePortalAndUninitializedTerrainSettings() {
        WorldSettingsDocument settings = new WorldSettingsDocument(
                1,
                0,
                0,
                "",
                false,
                TerrainMode.FLAT,
                OrePreset.VANILLA_BALANCED,
                GameplaySettings.fromPreset(GameplayPreset.SAFE),
                new PortalSettings(true, true, false, 0, 1.0D));

        assertFalse(WorldSettingsValidator.validate(settings).valid());
    }
}
