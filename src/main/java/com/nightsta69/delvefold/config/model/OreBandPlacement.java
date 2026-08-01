package com.nightsta69.delvefold.config.model;

import com.google.gson.annotations.SerializedName;

/** Selects the placement algorithm used by one schema-2 ore band. */
public enum OreBandPlacement {
    @SerializedName("vein")
    VEIN("vein"),
    @SerializedName("province")
    PROVINCE("province");

    private final String serializedName;

    OreBandPlacement(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static OreBandPlacement parse(String value) {
        String normalized = value == null ? "" : value.trim();
        for (OreBandPlacement placement : values()) {
            if (placement.serializedName.equalsIgnoreCase(normalized)) {
                return placement;
            }
        }
        throw new IllegalArgumentException("Unknown ore band placement: " + value);
    }
}
