package com.nightsta69.delvefold.config.model;

/**
 * Shape, density, and hard per-chunk work bound for a regional ore province.
 * Validation deliberately lives in {@code OreConfigValidator} so malformed JSON
 * can be reported with an exact configuration path rather than failing while it
 * is being decoded.
 */
public record ProvinceSettings(
        int regionSize,
        int radius,
        int verticalThickness,
        double density,
        int perChunkWorkCap) {

    public static ProvinceSettings defaults() {
        return new ProvinceSettings(512, 192, 48, 0.08D, 1024);
    }
}
