package com.nightsta69.delvefold.config.model;

import org.jspecify.annotations.Nullable;

/**
 * Natural-spawning policy applied live to the active mining world.
 *
 * <p>The booleans control natural spawning only; commands, spawn eggs, breeding, and spawners remain available. A
 * missing preset is normalized to {@link GameplayPreset#SAFE}, preserving schema-2 compatibility.
 *
 * @param preset named baseline represented by the category flags
 * @param monsters whether hostile monsters may spawn naturally
 * @param creatures whether passive land creatures may spawn naturally
 * @param ambient whether ambient creatures may spawn naturally
 * @param waterCreatures whether aquatic creatures may spawn naturally
 * @param patrols whether patrol spawning is enabled
 * @param phantoms whether phantom spawning is enabled
 */
public record GameplaySettings(
        GameplayPreset preset,
        boolean monsters,
        boolean creatures,
        boolean ambient,
        boolean waterCreatures,
        boolean patrols,
        boolean phantoms) {
    /**
     * Creates gameplay settings, substituting the safe preset when deserialization supplies no preset.
     *
     * @param preset named baseline, or {@code null} for {@link GameplayPreset#SAFE}
     * @param monsters whether hostile monsters may spawn naturally
     * @param creatures whether passive land creatures may spawn naturally
     * @param ambient whether ambient creatures may spawn naturally
     * @param waterCreatures whether aquatic creatures may spawn naturally
     * @param patrols whether patrol spawning is enabled
     * @param phantoms whether phantom spawning is enabled
     */
    public GameplaySettings(
            @Nullable GameplayPreset preset,
            boolean monsters,
            boolean creatures,
            boolean ambient,
            boolean waterCreatures,
            boolean patrols,
            boolean phantoms) {
        this.preset = preset == null ? GameplayPreset.SAFE : preset;
        this.monsters = monsters;
        this.creatures = creatures;
        this.ambient = ambient;
        this.waterCreatures = waterCreatures;
        this.patrols = patrols;
        this.phantoms = phantoms;
    }

    /**
     * Expands a named preset into its complete category policy.
     *
     * @param preset preset to expand
     * @return immutable gameplay settings matching the preset
     */
    public static GameplaySettings fromPreset(GameplayPreset preset) {
        return switch (preset) {
            case SAFE -> new GameplaySettings(preset, false, false, false, false, false, false);
            case HOSTILE -> new GameplaySettings(preset, true, false, true, false, true, true);
            case NORMAL -> new GameplaySettings(preset, true, true, true, true, true, true);
        };
    }
}
