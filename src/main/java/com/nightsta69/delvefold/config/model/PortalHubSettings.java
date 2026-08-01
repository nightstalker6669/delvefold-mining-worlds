package com.nightsta69.delvefold.config.model;

/**
 * Immutable horizontal location and protection boundary for the central mining hub. The service chooses a safe vertical
 * position for the active terrain at these coordinates.
 */
public record PortalHubSettings(int x, int z, int protectionRadius) {
    public static final int MAX_ABSOLUTE_COORDINATE = 29_999_936;
    public static final int MIN_PROTECTION_RADIUS = 8;
    public static final int MAX_PROTECTION_RADIUS = 256;
    public static final int DEFAULT_PROTECTION_RADIUS = 16;

    public PortalHubSettings {
        if (Math.abs((long) x) > MAX_ABSOLUTE_COORDINATE || Math.abs((long) z) > MAX_ABSOLUTE_COORDINATE) {
            throw new IllegalArgumentException("Central hub coordinates exceed the safe world boundary");
        }
        if (protectionRadius < MIN_PROTECTION_RADIUS || protectionRadius > MAX_PROTECTION_RADIUS) {
            throw new IllegalArgumentException("Central hub protection radius must be between " + MIN_PROTECTION_RADIUS
                    + " and " + MAX_PROTECTION_RADIUS);
        }
    }

    public static PortalHubSettings defaults() {
        return new PortalHubSettings(0, 0, DEFAULT_PROTECTION_RADIUS);
    }
}
