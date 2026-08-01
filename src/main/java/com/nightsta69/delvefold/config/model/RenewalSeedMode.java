package com.nightsta69.delvefold.config.model;

import com.google.gson.annotations.SerializedName;

/** Selects the layout policy applied when a new mining-world generation is created. */
public enum RenewalSeedMode {
    @SerializedName("stable")
    STABLE("stable"),
    @SerializedName("rotate_on_recreate")
    ROTATE_ON_RECREATE("rotate_on_recreate");

    private final String serializedName;

    RenewalSeedMode(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static RenewalSeedMode parse(String value) {
        for (RenewalSeedMode mode : values()) {
            if (mode.serializedName.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown renewal seed mode: " + value);
    }
}
