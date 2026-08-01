package com.nightsta69.delvefold.config.model;

/**
 * Immutable horizontal location and protection boundary for the central mining hub. The service chooses a safe vertical
 * position for the active terrain at these coordinates.
 *
 * @param x hub center X coordinate in blocks within the safe world boundary
 * @param z hub center Z coordinate in blocks within the safe world boundary
 * @param protectionRadius protected horizontal radius in blocks, inclusive of the full vertical column
 */
public record PortalHubSettings(int x, int z, int protectionRadius) {
    /** Maximum absolute X or Z coordinate accepted for safe hub creation. */
    public static final int MAX_ABSOLUTE_COORDINATE = 29_999_936;
    /** Minimum protected horizontal radius in blocks. */
    public static final int MIN_PROTECTION_RADIUS = 8;
    /** Maximum protected horizontal radius in blocks. */
    public static final int MAX_PROTECTION_RADIUS = 256;
    /** Compatibility-default protected horizontal radius in blocks. */
    public static final int DEFAULT_PROTECTION_RADIUS = 16;

    /**
     * Validates and creates a central-hub location.
     *
     * @param x hub center X coordinate in blocks
     * @param z hub center Z coordinate in blocks
     * @param protectionRadius protected horizontal radius in blocks
     * @throws IllegalArgumentException if either coordinate exceeds the safe boundary or the radius is outside 8–256
     */
    public PortalHubSettings {
        if (Math.abs((long) x) > MAX_ABSOLUTE_COORDINATE || Math.abs((long) z) > MAX_ABSOLUTE_COORDINATE) {
            throw new IllegalArgumentException("Central hub coordinates exceed the safe world boundary");
        }
        if (protectionRadius < MIN_PROTECTION_RADIUS || protectionRadius > MAX_PROTECTION_RADIUS) {
            throw new IllegalArgumentException("Central hub protection radius must be between " + MIN_PROTECTION_RADIUS
                    + " and " + MAX_PROTECTION_RADIUS);
        }
    }

    /**
     * Returns the schema-2 compatibility hub used when central-hub fields are absent.
     *
     * @return hub centered at X/Z zero with a 16-block protection radius
     */
    public static PortalHubSettings defaults() {
        return new PortalHubSettings(0, 0, DEFAULT_PROTECTION_RADIUS);
    }
}
