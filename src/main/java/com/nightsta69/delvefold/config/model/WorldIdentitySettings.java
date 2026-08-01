package com.nightsta69.delvefold.config.model;

public record WorldIdentitySettings(
        String displayName,
        TerrainVariant terrainVariant,
        LandmarkPreset landmarkPreset,
        boolean surveyStations,
        boolean motherlodes,
        boolean faultLines,
        GeologyTheme geologyTheme,
        RenewalSettings renewal) {
    public WorldIdentitySettings {
        displayName = displayName == null || displayName.isBlank() ? "Delvefold Mining World" : displayName.trim();
        terrainVariant = terrainVariant == null ? TerrainVariant.CLASSIC : terrainVariant;
        landmarkPreset = landmarkPreset == null ? LandmarkPreset.BALANCED : landmarkPreset;
        renewal = renewal == null ? RenewalSettings.disabled() : renewal;
        geologyTheme = geologyTheme == null ? GeologyTheme.CLASSIC : geologyTheme;
    }

    /** Source- and binary-compatible constructor for schema-2 callers predating geology themes. */
    public WorldIdentitySettings(
            String displayName,
            TerrainVariant terrainVariant,
            LandmarkPreset landmarkPreset,
            boolean surveyStations,
            boolean motherlodes,
            boolean faultLines,
            RenewalSettings renewal) {
        this(
                displayName,
                terrainVariant,
                landmarkPreset,
                surveyStations,
                motherlodes,
                faultLines,
                GeologyTheme.CLASSIC,
                renewal);
    }

    public static WorldIdentitySettings defaults() {
        return new WorldIdentitySettings(
                "Delvefold Mining World",
                TerrainVariant.CLASSIC,
                LandmarkPreset.BALANCED,
                true,
                true,
                true,
                GeologyTheme.CLASSIC,
                RenewalSettings.disabled());
    }

    public WorldIdentitySettings withRenewal(RenewalSettings replacement) {
        return new WorldIdentitySettings(
                displayName,
                terrainVariant,
                landmarkPreset,
                surveyStations,
                motherlodes,
                faultLines,
                geologyTheme,
                replacement);
    }

    public WorldIdentitySettings withTerrainVariant(TerrainVariant replacement) {
        return new WorldIdentitySettings(
                displayName,
                replacement,
                landmarkPreset,
                surveyStations,
                motherlodes,
                faultLines,
                geologyTheme,
                renewal);
    }

    public WorldIdentitySettings withGeologyTheme(GeologyTheme replacement) {
        return new WorldIdentitySettings(
                displayName,
                terrainVariant,
                landmarkPreset,
                surveyStations,
                motherlodes,
                faultLines,
                replacement,
                renewal);
    }

    public WorldIdentitySettings withTerrainAndGeology(TerrainVariant variant, GeologyTheme theme) {
        return new WorldIdentitySettings(
                displayName, variant, landmarkPreset, surveyStations, motherlodes, faultLines, theme, renewal);
    }

    public WorldIdentitySettings withDisplayName(String replacement) {
        return new WorldIdentitySettings(
                replacement,
                terrainVariant,
                landmarkPreset,
                surveyStations,
                motherlodes,
                faultLines,
                geologyTheme,
                renewal);
    }

    public WorldIdentitySettings withLandmarks(
            LandmarkPreset preset, boolean survey, boolean motherlode, boolean faults) {
        return new WorldIdentitySettings(
                displayName, terrainVariant, preset, survey, motherlode, faults, geologyTheme, renewal);
    }
}
