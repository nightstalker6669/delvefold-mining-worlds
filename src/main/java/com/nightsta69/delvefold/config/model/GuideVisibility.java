package com.nightsta69.delvefold.config.model;

import com.google.gson.annotations.SerializedName;

/** Controls which players or command sources may open the read-only Delvefold guide. */
public enum GuideVisibility {
    /** Allows every player to request a server-authorized guide snapshot. */
    @SerializedName("public")
    PUBLIC("public"),
    /** Restricts guide snapshots to operators and other authorized command sources. */
    @SerializedName("operators")
    OPERATORS("operators"),
    /** Disables guide snapshots for players and commands. */
    @SerializedName("disabled")
    DISABLED("disabled");

    private final String serializedName;

    GuideVisibility(String serializedName) {
        this.serializedName = serializedName;
    }

    /**
     * Returns the stable lowercase value written to schema-2 settings.
     *
     * @return serialized visibility name
     */
    public String serializedName() {
        return serializedName;
    }

    /**
     * Resolves a serialized visibility name without regard to letter case.
     *
     * @param value serialized visibility name
     * @return matching visibility policy
     * @throws IllegalArgumentException if the name is not recognized
     */
    public static GuideVisibility parse(String value) {
        for (GuideVisibility visibility : values()) {
            if (visibility.serializedName.equalsIgnoreCase(value)) {
                return visibility;
            }
        }
        throw new IllegalArgumentException("Unknown guide visibility: " + value);
    }
}
