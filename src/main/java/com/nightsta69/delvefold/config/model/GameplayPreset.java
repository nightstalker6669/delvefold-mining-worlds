package com.nightsta69.delvefold.config.model;

import com.google.gson.annotations.SerializedName;

public enum GameplayPreset {
    @SerializedName("safe")
    SAFE("safe"),
    @SerializedName("hostile")
    HOSTILE("hostile"),
    @SerializedName("normal")
    NORMAL("normal");

    private final String serializedName;

    GameplayPreset(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static GameplayPreset parse(String value) {
        for (GameplayPreset preset : values()) {
            if (preset.serializedName.equalsIgnoreCase(value)) {
                return preset;
            }
        }
        throw new IllegalArgumentException("Unknown gameplay preset: " + value);
    }
}
