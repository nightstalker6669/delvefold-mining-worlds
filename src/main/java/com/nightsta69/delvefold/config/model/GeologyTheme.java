package com.nightsta69.delvefold.config.model;

import com.google.gson.annotations.SerializedName;

/** Bounded vanilla-block geology applied when a mining world generation is created. */
public enum GeologyTheme {
    @SerializedName("classic")
    CLASSIC("classic"),
    @SerializedName("volcanic")
    VOLCANIC("volcanic"),
    @SerializedName("dripstone")
    DRIPSTONE("dripstone"),
    @SerializedName("lush")
    LUSH("lush"),
    @SerializedName("crystal")
    CRYSTAL("crystal");

    private final String serializedName;

    GeologyTheme(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

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
