package com.nightsta69.delvefold.client.gui;

import com.nightsta69.delvefold.config.model.GameplayPreset;
import com.nightsta69.delvefold.config.model.GameplaySettings;
import com.nightsta69.delvefold.config.model.GeologyTheme;
import com.nightsta69.delvefold.config.model.LandmarkPreset;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.RenewalSeedMode;
import com.nightsta69.delvefold.config.model.RenewalSettings;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.TerrainVariant;
import com.nightsta69.delvefold.config.model.WorldIdentitySettings;
import com.nightsta69.delvefold.network.model.AdminSnapshot;

/** Immutable local setup choices retained across resize-driven widget rebuilds and wizard steps. */
record SetupDraft(
        TerrainMode terrainMode,
        OrePreset orePreset,
        GameplayPreset gameplayPreset,
        TerrainVariant terrainVariant,
        GeologyTheme geologyTheme,
        LandmarkPreset landmarkPreset,
        RenewalSeedMode renewalSeedMode) {
    /** Copies the server-authoritative proposed defaults into an independent local draft. */
    static SetupDraft from(AdminSnapshot snapshot) {
        return new SetupDraft(
                snapshot.terrainMode(),
                snapshot.orePreset(),
                snapshot.gameplay().preset(),
                snapshot.identity().terrainVariant(),
                snapshot.identity().geologyTheme(),
                snapshot.identity().landmarkPreset(),
                snapshot.identity().renewal().seedMode());
    }

    SetupDraft withTerrainMode(TerrainMode value) {
        return new SetupDraft(
                value, orePreset, gameplayPreset, terrainVariant, geologyTheme, landmarkPreset, renewalSeedMode);
    }

    SetupDraft withOrePreset(OrePreset value) {
        return new SetupDraft(
                terrainMode, value, gameplayPreset, terrainVariant, geologyTheme, landmarkPreset, renewalSeedMode);
    }

    SetupDraft withGameplayPreset(GameplayPreset value) {
        return new SetupDraft(
                terrainMode, orePreset, value, terrainVariant, geologyTheme, landmarkPreset, renewalSeedMode);
    }

    SetupDraft withTerrainVariant(TerrainVariant value) {
        return new SetupDraft(
                terrainMode, orePreset, gameplayPreset, value, geologyTheme, landmarkPreset, renewalSeedMode);
    }

    SetupDraft withGeologyTheme(GeologyTheme value) {
        return new SetupDraft(
                terrainMode, orePreset, gameplayPreset, terrainVariant, value, landmarkPreset, renewalSeedMode);
    }

    SetupDraft withLandmarkPreset(LandmarkPreset value) {
        return new SetupDraft(
                terrainMode, orePreset, gameplayPreset, terrainVariant, geologyTheme, value, renewalSeedMode);
    }

    SetupDraft withRenewalSeedMode(RenewalSeedMode value) {
        return new SetupDraft(
                terrainMode, orePreset, gameplayPreset, terrainVariant, geologyTheme, landmarkPreset, value);
    }

    GameplaySettings gameplay() {
        return GameplaySettings.fromPreset(gameplayPreset);
    }

    /** Applies recreation-locked choices while retaining every unrelated identity and renewal field. */
    WorldIdentitySettings selectedIdentity(WorldIdentitySettings source) {
        boolean landmarksEnabled = landmarkPreset != LandmarkPreset.PURE_MINING;
        RenewalSettings renewal = source.renewal();
        return new WorldIdentitySettings(
                source.displayName(),
                terrainVariant,
                landmarkPreset,
                landmarksEnabled && source.surveyStations(),
                landmarksEnabled && source.motherlodes(),
                landmarksEnabled && source.faultLines(),
                geologyTheme,
                new RenewalSettings(
                        renewal.enabled(),
                        renewal.intervalDays(),
                        renewal.warningMinutes(),
                        renewal.nextRenewalAtEpochMillis(),
                        renewalSeedMode));
    }
}
