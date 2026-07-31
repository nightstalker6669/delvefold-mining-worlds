package com.nightsta69.delvefold.config.model;

import com.google.gson.annotations.SerializedName;

public enum OrePreset {
    @SerializedName("vanilla_balanced")
    VANILLA_BALANCED("vanilla_balanced"),
    @SerializedName("rich")
    RICH("rich"),
    @SerializedName("empty")
    EMPTY("empty");

    private final String serializedName;

    OrePreset(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

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
