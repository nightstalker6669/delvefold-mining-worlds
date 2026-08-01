package com.nightsta69.delvefold.config.model;

import com.google.gson.annotations.SerializedName;

/** Predefined natural-spawning policies stored by their stable schema-2 names. */
public enum GameplayPreset {
    /** Disables every natural-spawning category managed by Delvefold. */
    @SerializedName("safe")
    SAFE("safe"),
    /** Enables hostile, ambient, patrol, and phantom spawning while suppressing passive categories. */
    @SerializedName("hostile")
    HOSTILE("hostile"),
    /** Enables all natural-spawning categories managed by Delvefold. */
    @SerializedName("normal")
    NORMAL("normal");

    private final String serializedName;

    GameplayPreset(String serializedName) {
        this.serializedName = serializedName;
    }

    /**
     * Returns the stable lowercase value written to schema-2 settings.
     *
     * @return serialized preset name
     */
    public String serializedName() {
        return serializedName;
    }

    /**
     * Resolves a serialized preset name without regard to letter case.
     *
     * @param value serialized preset name
     * @return matching gameplay preset
     * @throws IllegalArgumentException if the name is not recognized
     */
    public static GameplayPreset parse(String value) {
        for (GameplayPreset preset : values()) {
            if (preset.serializedName.equalsIgnoreCase(value)) {
                return preset;
            }
        }
        throw new IllegalArgumentException("Unknown gameplay preset: " + value);
    }
}
