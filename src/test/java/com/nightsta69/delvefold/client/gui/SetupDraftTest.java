package com.nightsta69.delvefold.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.model.GameplayPreset;
import com.nightsta69.delvefold.config.model.GeologyTheme;
import com.nightsta69.delvefold.config.model.LandmarkPreset;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.RenewalSeedMode;
import com.nightsta69.delvefold.config.model.RenewalSettings;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.TerrainVariant;
import com.nightsta69.delvefold.config.model.WorldIdentitySettings;
import org.junit.jupiter.api.Test;

class SetupDraftTest {
    private static final SetupDraft BASE = new SetupDraft(
            TerrainMode.FLAT,
            OrePreset.VANILLA_BALANCED,
            GameplayPreset.SAFE,
            TerrainVariant.CLASSIC,
            GeologyTheme.CLASSIC,
            LandmarkPreset.BALANCED,
            RenewalSeedMode.STABLE);

    @Test
    void immutableChoiceUpdatesRetainEveryUnrelatedUnsavedSelection() {
        SetupDraft changed = BASE.withTerrainMode(TerrainMode.WILD)
                .withOrePreset(OrePreset.RICH)
                .withGameplayPreset(GameplayPreset.NORMAL)
                .withTerrainVariant(TerrainVariant.EXPANSIVE)
                .withGeologyTheme(GeologyTheme.VOLCANIC)
                .withLandmarkPreset(LandmarkPreset.ABUNDANT)
                .withRenewalSeedMode(RenewalSeedMode.ROTATE_ON_RECREATE);

        assertEquals(TerrainMode.WILD, changed.terrainMode());
        assertEquals(OrePreset.RICH, changed.orePreset());
        assertEquals(GameplayPreset.NORMAL, changed.gameplayPreset());
        assertEquals(TerrainVariant.EXPANSIVE, changed.terrainVariant());
        assertEquals(GeologyTheme.VOLCANIC, changed.geologyTheme());
        assertEquals(LandmarkPreset.ABUNDANT, changed.landmarkPreset());
        assertEquals(RenewalSeedMode.ROTATE_ON_RECREATE, changed.renewalSeedMode());
        assertEquals(GameplayPreset.NORMAL, changed.gameplay().preset());
        assertTrue(changed.gameplay().monsters());
        assertTrue(changed.gameplay().creatures());
    }

    @Test
    void selectedIdentityChangesOnlySetupOwnedFieldsAndRetainsRenewalTiming() {
        RenewalSettings renewal = new RenewalSettings(true, 45, 90, 123_456_789L, RenewalSeedMode.STABLE);
        WorldIdentitySettings source = new WorldIdentitySettings(
                "Deep Works",
                TerrainVariant.CLASSIC,
                LandmarkPreset.BALANCED,
                true,
                false,
                true,
                GeologyTheme.CLASSIC,
                renewal);
        SetupDraft changed = BASE.withTerrainVariant(TerrainVariant.EXPANSIVE)
                .withGeologyTheme(GeologyTheme.CRYSTAL)
                .withLandmarkPreset(LandmarkPreset.ABUNDANT)
                .withRenewalSeedMode(RenewalSeedMode.ROTATE_ON_RECREATE);

        WorldIdentitySettings selected = changed.selectedIdentity(source);

        assertEquals("Deep Works", selected.displayName());
        assertEquals(TerrainVariant.EXPANSIVE, selected.terrainVariant());
        assertEquals(GeologyTheme.CRYSTAL, selected.geologyTheme());
        assertEquals(LandmarkPreset.ABUNDANT, selected.landmarkPreset());
        assertTrue(selected.surveyStations());
        assertFalse(selected.motherlodes());
        assertTrue(selected.faultLines());
        assertTrue(selected.renewal().enabled());
        assertEquals(45, selected.renewal().intervalDays());
        assertEquals(90, selected.renewal().warningMinutes());
        assertEquals(123_456_789L, selected.renewal().nextRenewalAtEpochMillis());
        assertEquals(RenewalSeedMode.ROTATE_ON_RECREATE, selected.renewal().seedMode());
    }

    @Test
    void pureMiningNarrowsEveryLandmarkToggleWithoutChangingOtherIdentityFields() {
        WorldIdentitySettings source = new WorldIdentitySettings(
                "Mine",
                TerrainVariant.CLASSIC,
                LandmarkPreset.BALANCED,
                true,
                true,
                true,
                GeologyTheme.DRIPSTONE,
                RenewalSettings.disabled());

        WorldIdentitySettings selected =
                BASE.withLandmarkPreset(LandmarkPreset.PURE_MINING).selectedIdentity(source);

        assertEquals(LandmarkPreset.PURE_MINING, selected.landmarkPreset());
        assertFalse(selected.surveyStations());
        assertFalse(selected.motherlodes());
        assertFalse(selected.faultLines());
        assertEquals("Mine", selected.displayName());
    }
}
