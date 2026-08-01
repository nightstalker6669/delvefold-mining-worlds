package com.nightsta69.delvefold.config.model;

import com.google.gson.annotations.SerializedName;

/** Identifies a bundled schema-2 ore-profile starting point. */
public enum OrePreset {
    /** Uses familiar Overworld ore families, heights, vein sizes, and attempt rates. */
    @SerializedName("vanilla_balanced")
    VANILLA_BALANCED("vanilla_balanced"),
    /** Doubles the balanced profile's placement attempts while retaining its heights and vein sizes. */
    @SerializedName("rich")
    RICH("rich"),
    /** Starts with no ore rules. */
    @SerializedName("empty")
    EMPTY("empty");

    private final String serializedName;

    OrePreset(String serializedName) {
        this.serializedName = serializedName;
    }

    /**
     * Returns the stable lowercase value written to schema-2 world settings.
     *
     * @return serialized preset name
     */
    public String serializedName() {
        return serializedName;
    }

    /**
     * Resolves a serialized preset name, accepting hyphens in place of underscores and {@code balanced} as the
     * compatibility alias for {@link #VANILLA_BALANCED}.
     *
     * @param value serialized preset name
     * @return matching bundled preset
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws IllegalArgumentException if the normalized name is not recognized
     */
    public static OrePreset parse(String value) {
        String normalized = value.replace('-', '_');
        if ("balanced".equalsIgnoreCase(normalized)) {
            return VANILLA_BALANCED;
        }
        for (OrePreset preset : values()) {
            if (preset.serializedName.equalsIgnoreCase(normalized)) {
                return preset;
            }
        }
        throw new IllegalArgumentException("Unknown ore preset: " + value);
    }
}
