package com.nightsta69.delvefold.config.model;

import com.google.gson.annotations.SerializedName;

/** Selects how an incoming Delvefold portal chooses its mining-world destination. */
public enum PortalRoutingMode {
    @SerializedName("coordinate_linked")
    COORDINATE_LINKED("coordinate_linked"),
    @SerializedName("central_hub")
    CENTRAL_HUB("central_hub");

    private final String serializedName;

    PortalRoutingMode(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static PortalRoutingMode parse(String value) {
        for (PortalRoutingMode mode : values()) {
            if (mode.serializedName.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown portal routing mode: " + value);
    }
}
