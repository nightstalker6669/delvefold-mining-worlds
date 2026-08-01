package com.nightsta69.delvefold.config.model;

import com.google.gson.annotations.SerializedName;

/** Selects how an incoming Delvefold portal chooses its mining-world destination. */
public enum PortalRoutingMode {
    /** Scales and links the player's source coordinates, preserving the established portal behavior. */
    @SerializedName("coordinate_linked")
    COORDINATE_LINKED("coordinate_linked"),
    /** Routes every incoming player to the configured protected mining-world hub. */
    @SerializedName("central_hub")
    CENTRAL_HUB("central_hub");

    private final String serializedName;

    PortalRoutingMode(String serializedName) {
        this.serializedName = serializedName;
    }

    /**
     * Returns the stable lowercase value written to schema-2 settings.
     *
     * @return serialized routing-mode name
     */
    public String serializedName() {
        return serializedName;
    }

    /**
     * Resolves a serialized routing-mode name without regard to letter case.
     *
     * @param value serialized routing-mode name
     * @return matching routing mode
     * @throws IllegalArgumentException if the name is not recognized
     */
    public static PortalRoutingMode parse(String value) {
        for (PortalRoutingMode mode : values()) {
            if (mode.serializedName.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown portal routing mode: " + value);
    }
}
