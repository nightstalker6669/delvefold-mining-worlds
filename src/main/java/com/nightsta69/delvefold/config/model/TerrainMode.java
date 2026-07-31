package com.nightsta69.delvefold.config.model;

import com.google.gson.annotations.SerializedName;

public enum TerrainMode {
    @SerializedName("flat")
    FLAT("flat"),
    @SerializedName("cavern")
    CAVERN("cavern"),
    @SerializedName("wild")
    WILD("wild");

    private final String serializedName;

    TerrainMode(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static TerrainMode parse(String value) {
        for (TerrainMode mode : values()) {
            if (mode.serializedName.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown terrain mode: " + value);
    }
}
