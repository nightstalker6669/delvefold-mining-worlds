package com.nightsta69.delvefold.config.model;

public record WorldIdentitySettings(
        String displayName,
        TerrainVariant terrainVariant,
        LandmarkPreset landmarkPreset,
        boolean surveyStations,
        boolean motherlodes,
        boolean faultLines,
        RenewalSettings renewal
) {
    public WorldIdentitySettings {
        displayName = displayName == null || displayName.isBlank() ? "Delvefold Mining World" : displayName.trim();
        terrainVariant = terrainVariant == null ? TerrainVariant.CLASSIC : terrainVariant;
        landmarkPreset = landmarkPreset == null ? LandmarkPreset.BALANCED : landmarkPreset;
        renewal = renewal == null ? RenewalSettings.disabled() : renewal;
    }

    public static WorldIdentitySettings defaults() {
        return new WorldIdentitySettings("Delvefold Mining World", TerrainVariant.CLASSIC,
                LandmarkPreset.BALANCED, true, true, true, RenewalSettings.disabled());
    }

    public WorldIdentitySettings withRenewal(RenewalSettings replacement) {
        return new WorldIdentitySettings(displayName, terrainVariant, landmarkPreset,
                surveyStations, motherlodes, faultLines, replacement);
    }

    public WorldIdentitySettings withTerrainVariant(TerrainVariant replacement) {
        return new WorldIdentitySettings(displayName, replacement, landmarkPreset,
                surveyStations, motherlodes, faultLines, renewal);
    }
}
