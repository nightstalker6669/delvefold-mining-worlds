package com.nightsta69.delvefold.config.model;

/**
 * Shape, density, and hard per-chunk work bound for a regional ore province. Validation deliberately lives in
 * {@code OreConfigValidator} so malformed JSON can be reported with an exact configuration path rather than failing
 * while it is being decoded.
 *
 * <p>Province centers are derived deterministically from the world seed, persisted generation salt, regional cell, and
 * band ID. Every generating chunk computes compatible centers but writes only within its own chunk boundaries.
 *
 * @param regionSize regional cell width and depth in blocks, 16–8192 and divisible by 16
 * @param radius horizontal province radius in blocks, 1 through {@code regionSize}
 * @param verticalThickness vertical thickness in blocks, 1–385
 * @param density candidate-sampling fraction greater than zero through 1
 * @param perChunkWorkCap hard attempt/work-unit cap, 1–4096, shared by all province slices touching one chunk
 */
public record ProvinceSettings(int regionSize, int radius, int verticalThickness, double density, int perChunkWorkCap) {

    /**
     * Returns the conservative province policy installed when a vein band is explicitly switched to province mode.
     *
     * @return 512-block regions, 192-block radius, 48-block thickness, 8-percent density, and 1024 work units per chunk
     */
    public static ProvinceSettings defaults() {
        return new ProvinceSettings(512, 192, 48, 0.08D, 1024);
    }
}
