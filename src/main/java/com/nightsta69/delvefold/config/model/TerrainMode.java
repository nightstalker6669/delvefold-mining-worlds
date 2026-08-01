package com.nightsta69.delvefold.config.model;

import com.google.gson.annotations.SerializedName;

/** Selects the recreation-locked mining-world terrain family and its registered dimension. */
public enum TerrainMode {
    /** Layered flat terrain with a mineable surface kept below the cloud layer. */
    @SerializedName("flat")
    FLAT("flat"),
    /** Enclosed subterranean terrain dominated by caves and solid host rock. */
    @SerializedName("cavern")
    CAVERN("cavern"),
    /** Overworld-like open terrain with ordinary surface features. */
    @SerializedName("wild")
    WILD("wild");

    private final String serializedName;

    TerrainMode(String serializedName) {
        this.serializedName = serializedName;
    }

    /**
     * Returns the stable lowercase value written to schema-2 settings and ore rules.
     *
     * @return serialized terrain-mode name
     */
    public String serializedName() {
        return serializedName;
    }

    /**
     * Resolves a serialized terrain-mode name without regard to letter case.
     *
     * @param value serialized terrain-mode name
     * @return matching terrain mode
     * @throws IllegalArgumentException if the name is not recognized
     */
    public static TerrainMode parse(String value) {
        for (TerrainMode mode : values()) {
            if (mode.serializedName.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown terrain mode: " + value);
    }
}
