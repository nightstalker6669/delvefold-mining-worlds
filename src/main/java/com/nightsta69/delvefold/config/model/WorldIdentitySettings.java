package com.nightsta69.delvefold.config.model;

import org.jspecify.annotations.Nullable;

/**
 * Immutable player-facing identity and generation policy for the mining world.
 *
 * <p>The display name and renewal schedule are live settings. Landmark policy and category toggles affect only newly
 * generated chunks. Terrain variant and geology theme are recreation-locked once the world is initialized.
 *
 * @param displayName player-facing mining-world name
 * @param terrainVariant recreation-locked terrain scale
 * @param landmarkPreset deterministic candidate-acceptance density for newly generated chunks
 * @param surveyStations whether survey-station catalog entries remain eligible
 * @param motherlodes whether motherlode catalog entries remain eligible
 * @param faultLines whether fault-line catalog entries remain eligible
 * @param geologyTheme recreation-locked bounded strata, decoration, fluid, and ambience theme
 * @param renewal opt-in restart-applied renewal schedule
 */
public record WorldIdentitySettings(
        String displayName,
        TerrainVariant terrainVariant,
        LandmarkPreset landmarkPreset,
        boolean surveyStations,
        boolean motherlodes,
        boolean faultLines,
        GeologyTheme geologyTheme,
        RenewalSettings renewal) {
    /**
     * Creates identity settings and applies additive schema-2 compatibility defaults.
     *
     * <p>A missing or blank display name becomes {@code Delvefold Mining World}; omitted variant, landmark preset,
     * geology theme, and renewal values become classic, balanced, classic, and disabled respectively.
     *
     * @param displayName player-facing mining-world name
     * @param terrainVariant terrain scale
     * @param landmarkPreset landmark candidate density
     * @param surveyStations whether survey stations are eligible
     * @param motherlodes whether motherlodes are eligible
     * @param faultLines whether fault-line landmarks are eligible
     * @param geologyTheme geology theme
     * @param renewal renewal schedule
     */
    public WorldIdentitySettings(
            @Nullable String displayName,
            @Nullable TerrainVariant terrainVariant,
            @Nullable LandmarkPreset landmarkPreset,
            boolean surveyStations,
            boolean motherlodes,
            boolean faultLines,
            @Nullable GeologyTheme geologyTheme,
            @Nullable RenewalSettings renewal) {
        displayName = displayName == null || displayName.isBlank() ? "Delvefold Mining World" : displayName.trim();
        terrainVariant = terrainVariant == null ? TerrainVariant.CLASSIC : terrainVariant;
        landmarkPreset = landmarkPreset == null ? LandmarkPreset.BALANCED : landmarkPreset;
        renewal = renewal == null ? RenewalSettings.disabled() : renewal;
        geologyTheme = geologyTheme == null ? GeologyTheme.CLASSIC : geologyTheme;
        this.displayName = displayName;
        this.terrainVariant = terrainVariant;
        this.landmarkPreset = landmarkPreset;
        this.surveyStations = surveyStations;
        this.motherlodes = motherlodes;
        this.faultLines = faultLines;
        this.geologyTheme = geologyTheme;
        this.renewal = renewal;
    }

    /**
     * Creates source- and binary-compatible identity settings for schema-2 callers predating geology themes.
     *
     * @param displayName player-facing mining-world name
     * @param terrainVariant terrain scale
     * @param landmarkPreset landmark candidate density
     * @param surveyStations whether survey stations are eligible
     * @param motherlodes whether motherlodes are eligible
     * @param faultLines whether fault-line landmarks are eligible
     * @param renewal renewal schedule
     */
    public WorldIdentitySettings(
            @Nullable String displayName,
            @Nullable TerrainVariant terrainVariant,
            @Nullable LandmarkPreset landmarkPreset,
            boolean surveyStations,
            boolean motherlodes,
            boolean faultLines,
            @Nullable RenewalSettings renewal) {
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

    /**
     * Returns the compatibility-preserving default identity.
     *
     * @return classic terrain and geology, balanced landmarks with every category enabled, and disabled renewal
     */
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

    /**
     * Copies this identity with a replacement restart-applied renewal schedule.
     *
     * @param replacement replacement renewal schedule
     * @return immutable identity copy retaining every other field
     */
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

    /**
     * Copies this identity with a replacement recreation-locked terrain variant.
     *
     * @param replacement replacement terrain variant
     * @return immutable identity copy retaining every other field
     */
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

    /**
     * Copies this identity with a replacement recreation-locked geology theme.
     *
     * @param replacement replacement geology theme
     * @return immutable identity copy retaining every other field
     */
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

    /**
     * Copies this identity with both recreation-locked terrain selections replaced atomically.
     *
     * @param variant replacement terrain variant
     * @param theme replacement geology theme
     * @return immutable identity copy retaining name, landmarks, and renewal
     */
    public WorldIdentitySettings withTerrainAndGeology(TerrainVariant variant, GeologyTheme theme) {
        return new WorldIdentitySettings(
                displayName, variant, landmarkPreset, surveyStations, motherlodes, faultLines, theme, renewal);
    }

    /**
     * Copies this identity with a replacement player-facing name.
     *
     * @param replacement replacement name; blank input is normalized to the default name
     * @return immutable identity copy retaining every generation setting
     */
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

    /**
     * Copies this identity with a complete replacement landmark policy for newly generated chunks.
     *
     * @param preset replacement deterministic landmark density
     * @param survey whether survey stations remain eligible
     * @param motherlode whether motherlodes remain eligible
     * @param faults whether fault-line landmarks remain eligible
     * @return immutable identity copy retaining name, terrain, geology, and renewal
     */
    public WorldIdentitySettings withLandmarks(
            LandmarkPreset preset, boolean survey, boolean motherlode, boolean faults) {
        return new WorldIdentitySettings(
                displayName, terrainVariant, preset, survey, motherlode, faults, geologyTheme, renewal);
    }
}
