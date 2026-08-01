package com.nightsta69.delvefold.config.model;

import com.google.gson.annotations.SerializedName;

/** Selects the placement algorithm used by one schema-2 ore band. */
public enum OreBandPlacement {
    /** Places independently salted classic veins using attempts and vein size. */
    @SerializedName("vein")
    VEIN("vein"),
    /** Places deterministic regional ore provinces with a per-chunk work cap. */
    @SerializedName("province")
    PROVINCE("province");

    private final String serializedName;

    OreBandPlacement(String serializedName) {
        this.serializedName = serializedName;
    }

    /**
     * Returns the stable lowercase value written to schema-2 profiles.
     *
     * @return serialized placement name
     */
    public String serializedName() {
        return serializedName;
    }

    /**
     * Resolves a serialized placement name after trimming whitespace, without regard to letter case.
     *
     * @param value serialized placement name
     * @return matching placement algorithm
     * @throws IllegalArgumentException if the name is absent or unrecognized
     */
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
