package com.nightsta69.delvefold.config.model;

import org.jspecify.annotations.Nullable;

/**
 * Live portal-access and destination policy stored additively in schema 2.
 *
 * <p>Changing these settings affects later player portal use without recreating terrain. Portal transport remains
 * player-only. Validation enforces a 1–3600-second cooldown and a finite coordinate scale from 0.01 through 100.
 *
 * @param enabled whether Delvefold portal ignition and travel are available
 * @param allowFromOverworldOnly whether entry portals are restricted to the Overworld
 * @param cooldownSeconds per-player anti-bounce cooldown in seconds
 * @param coordinateScale multiplier applied by coordinate-linked routing
 * @param routingMode destination routing policy; absent schema-2 values default to coordinate-linked
 * @param hub immutable central-hub coordinates and protection boundary
 */
public record PortalSettings(
        boolean enabled,
        boolean allowFromOverworldOnly,
        int cooldownSeconds,
        double coordinateScale,
        PortalRoutingMode routingMode,
        PortalHubSettings hub) {
    /**
     * Creates portal settings with compatibility defaults for omitted routing fields.
     *
     * <p>Malformed numeric values are retained so the settings validator can report their exact JSON paths.
     *
     * @param enabled whether portals are enabled
     * @param allowFromOverworldOnly whether entry is restricted to the Overworld
     * @param cooldownSeconds anti-bounce cooldown in seconds
     * @param coordinateScale coordinate-linked routing multiplier
     * @param routingMode routing policy, or {@code null} for coordinate-linked
     * @param hub central-hub settings, or {@code null} for the default hub
     */
    public PortalSettings(
            boolean enabled,
            boolean allowFromOverworldOnly,
            int cooldownSeconds,
            double coordinateScale,
            @Nullable PortalRoutingMode routingMode,
            @Nullable PortalHubSettings hub) {
        this.enabled = enabled;
        this.allowFromOverworldOnly = allowFromOverworldOnly;
        this.cooldownSeconds = cooldownSeconds;
        this.coordinateScale = coordinateScale;
        this.routingMode = routingMode == null ? PortalRoutingMode.COORDINATE_LINKED : routingMode;
        this.hub = hub == null ? PortalHubSettings.defaults() : hub;
    }

    /**
     * Creates source- and binary-compatible settings for schema-2 callers predating central-hub routing.
     *
     * @param enabled whether portals are enabled
     * @param allowFromOverworldOnly whether entry is restricted to the Overworld
     * @param cooldownSeconds anti-bounce cooldown in seconds
     * @param coordinateScale coordinate-linked routing multiplier
     */
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

    /**
     * Returns the compatibility-preserving default portal policy.
     *
     * @return enabled, Overworld-only, coordinate-linked routing with a five-second cooldown and 1:1 scale
     */
    public static PortalSettings defaults() {
        return new PortalSettings(
                true, true, 5, 1.0D, PortalRoutingMode.COORDINATE_LINKED, PortalHubSettings.defaults());
    }
}
