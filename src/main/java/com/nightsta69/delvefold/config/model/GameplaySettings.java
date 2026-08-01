package com.nightsta69.delvefold.config.model;

public record GameplaySettings(
        GameplayPreset preset,
        boolean monsters,
        boolean creatures,
        boolean ambient,
        boolean waterCreatures,
        boolean patrols,
        boolean phantoms) {
    public GameplaySettings {
        preset = preset == null ? GameplayPreset.SAFE : preset;
    }

    public static GameplaySettings fromPreset(GameplayPreset preset) {
        return switch (preset) {
            case SAFE -> new GameplaySettings(preset, false, false, false, false, false, false);
            case HOSTILE -> new GameplaySettings(preset, true, false, true, false, true, true);
            case NORMAL -> new GameplaySettings(preset, true, true, true, true, true, true);
        };
    }
}
