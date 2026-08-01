package com.nightsta69.delvefold.config.model;

import com.google.gson.annotations.SerializedName;

/** Bounded vanilla-block geology applied when a mining world generation is created. */
public enum GeologyTheme {
    /** Compatibility theme retaining the original stone and deepslate geology. */
    @SerializedName("classic")
    CLASSIC("classic"),
    /** Volcanic strata and decorations assembled from vanilla blocks and fluids. */
    @SerializedName("volcanic")
    VOLCANIC("volcanic"),
    /** Dripstone-focused strata and cave decoration. */
    @SerializedName("dripstone")
    DRIPSTONE("dripstone"),
    /** Lush-cave vegetation and moisture decoration. */
    @SerializedName("lush")
    LUSH("lush"),
    /** Crystal and geode-inspired strata and decoration. */
    @SerializedName("crystal")
    CRYSTAL("crystal");

    private final String serializedName;

    GeologyTheme(String serializedName) {
        this.serializedName = serializedName;
    }

    /**
     * Returns the stable lowercase value written to schema-2 world settings.
     *
     * @return serialized theme name
     */
    public String serializedName() {
        return serializedName;
    }

    /**
     * Resolves a serialized theme name after trimming whitespace, without regard to letter case.
     *
     * @param value serialized theme name
     * @return matching geology theme
     * @throws IllegalArgumentException if the name is absent or unrecognized
     */
    public static GeologyTheme parse(String value) {
        String normalized = value == null ? "" : value.trim();
        for (GeologyTheme theme : values()) {
            if (theme.serializedName.equalsIgnoreCase(normalized)) {
                return theme;
            }
        }
        throw new IllegalArgumentException("Unknown geology theme: " + value);
    }
}
