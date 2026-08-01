package com.nightsta69.delvefold.config.model;

import com.google.gson.annotations.SerializedName;

/** Controls which players or command sources may open the read-only Delvefold guide. */
public enum GuideVisibility {
    @SerializedName("public")
    PUBLIC("public"),
    @SerializedName("operators")
    OPERATORS("operators"),
    @SerializedName("disabled")
    DISABLED("disabled");

    private final String serializedName;

    GuideVisibility(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static GuideVisibility parse(String value) {
        for (GuideVisibility visibility : values()) {
            if (visibility.serializedName.equalsIgnoreCase(value)) {
                return visibility;
            }
        }
        throw new IllegalArgumentException("Unknown guide visibility: " + value);
    }
}
