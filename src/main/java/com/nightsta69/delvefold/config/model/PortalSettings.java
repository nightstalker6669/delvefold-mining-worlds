package com.nightsta69.delvefold.config.model;

public record PortalSettings(
        boolean enabled,
        boolean allowFromOverworldOnly,
        int cooldownSeconds,
        double coordinateScale
) {
    public static PortalSettings defaults() {
        return new PortalSettings(true, true, 5, 1.0D);
    }
}
