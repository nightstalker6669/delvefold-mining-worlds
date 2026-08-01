package com.nightsta69.delvefold.config.model;

import com.google.gson.annotations.SerializedName;

/** Selects the layout policy applied when a new mining-world generation is created. */
public enum RenewalSeedMode {
    /** Reuses the compatibility salt so recreation reproduces the established deterministic layout. */
    @SerializedName("stable")
    STABLE("stable"),
    /** Incorporates the generation epoch when recreation commits, yielding a new deterministic layout. */
    @SerializedName("rotate_on_recreate")
    ROTATE_ON_RECREATE("rotate_on_recreate");

    private final String serializedName;

    RenewalSeedMode(String serializedName) {
        this.serializedName = serializedName;
    }

    /**
     * Returns the stable lowercase value written to schema-2 settings.
     *
     * @return serialized seed-mode name
     */
    public String serializedName() {
        return serializedName;
    }

    /**
     * Resolves a serialized seed-mode name without regard to letter case.
     *
     * @param value serialized seed-mode name
     * @return matching renewal seed mode
     * @throws IllegalArgumentException if the name is not recognized
     */
    public static RenewalSeedMode parse(String value) {
        for (RenewalSeedMode mode : values()) {
            if (mode.serializedName.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown renewal seed mode: " + value);
    }
}
