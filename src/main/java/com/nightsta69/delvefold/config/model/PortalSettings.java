package com.nightsta69.delvefold.config.model;

public record PortalSettings(
        boolean enabled,
        boolean allowFromOverworldOnly,
        int cooldownSeconds,
        double coordinateScale,
        PortalRoutingMode routingMode,
        PortalHubSettings hub) {
    public PortalSettings {
        routingMode = routingMode == null ? PortalRoutingMode.COORDINATE_LINKED : routingMode;
        hub = hub == null ? PortalHubSettings.defaults() : hub;
    }

    /** Source- and binary-compatible constructor for schema-2 callers predating central-hub routing. */
    public PortalSettings(
            boolean enabled, boolean allowFromOverworldOnly, int cooldownSeconds, double coordinateScale) {
        this(
                enabled,
                allowFromOverworldOnly,
                cooldownSeconds,
                coordinateScale,
                PortalRoutingMode.COORDINATE_LINKED,
                PortalHubSettings.defaults());
    }

    public static PortalSettings defaults() {
        return new PortalSettings(
                true, true, 5, 1.0D, PortalRoutingMode.COORDINATE_LINKED, PortalHubSettings.defaults());
    }
}
