package com.nightsta69.delvefold.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.nightsta69.delvefold.config.model.GameplayPreset;
import com.nightsta69.delvefold.config.model.GeologyTheme;
import com.nightsta69.delvefold.config.model.LandmarkPreset;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.RenewalSettings;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.TerrainVariant;
import com.nightsta69.delvefold.config.model.WorldIdentitySettings;
import com.nightsta69.delvefold.config.model.WorldSettingsDocument;
import org.junit.jupiter.api.Test;

class GeologyThemeConfigTest {
    @Test
    void legacyIdentityConstructorAndMissingJsonFieldDefaultToClassic() {
        WorldIdentitySettings legacy = new WorldIdentitySettings(
                "Legacy Mine",
                TerrainVariant.EXPANSIVE,
                LandmarkPreset.ABUNDANT,
                true,
                true,
                true,
                RenewalSettings.disabled());
        assertEquals(GeologyTheme.CLASSIC, legacy.geologyTheme());

        var json = JsonParser.parseString(ConfigJson.GSON.toJson(legacy)).getAsJsonObject();
        json.remove("geology_theme");
        WorldIdentitySettings decoded = ConfigJson.GSON.fromJson(json, WorldIdentitySettings.class);
        assertEquals(GeologyTheme.CLASSIC, decoded.geologyTheme());
    }

    @Test
    void everyThemeUsesItsStableLowercaseJsonName() {
        for (GeologyTheme theme : GeologyTheme.values()) {
            assertEquals(theme, GeologyTheme.parse(theme.serializedName()));
            String json =
                    ConfigJson.GSON.toJson(WorldIdentitySettings.defaults().withGeologyTheme(theme));
            assertTrue(json.contains("\"geology_theme\": \"" + theme.serializedName() + "\""));
            assertEquals(
                    theme,
                    ConfigJson.GSON.fromJson(json, WorldIdentitySettings.class).geologyTheme());
        }
    }

    @Test
    void identityHelpersPreserveTheSelectedTheme() {
        WorldIdentitySettings crystal = WorldIdentitySettings.defaults().withGeologyTheme(GeologyTheme.CRYSTAL);

        assertEquals(GeologyTheme.CRYSTAL, crystal.withDisplayName("Renamed").geologyTheme());
        assertEquals(
                GeologyTheme.CRYSTAL,
                crystal.withTerrainVariant(TerrainVariant.EXPANSIVE).geologyTheme());
        assertEquals(
                GeologyTheme.CRYSTAL,
                crystal.withRenewal(RenewalSettings.disabled()).geologyTheme());
        assertEquals(
                GeologyTheme.CRYSTAL,
                crystal.withLandmarks(LandmarkPreset.PURE_MINING, false, false, false)
                        .geologyTheme());
    }

    @Test
    void legacyRecreatePreservesThemeAndExplicitRecreateCanChangeIt() {
        WorldIdentitySettings volcanic = WorldIdentitySettings.defaults().withGeologyTheme(GeologyTheme.VOLCANIC);
        WorldSettingsDocument initialized = WorldSettingsDocument.uninitialized()
                .initialize(TerrainMode.FLAT, OrePreset.VANILLA_BALANCED, GameplayPreset.SAFE, volcanic);

        WorldSettingsDocument preserved =
                initialized.recreate(TerrainMode.WILD, TerrainVariant.EXPANSIVE, null, null, "preserve");
        assertEquals(GeologyTheme.VOLCANIC, preserved.identity().geologyTheme());

        WorldSettingsDocument changed =
                preserved.recreate(TerrainMode.CAVERN, TerrainVariant.CLASSIC, GeologyTheme.LUSH, null, null, "change");
        assertEquals(GeologyTheme.LUSH, changed.identity().geologyTheme());
    }
}
