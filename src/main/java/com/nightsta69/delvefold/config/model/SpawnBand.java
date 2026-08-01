package com.nightsta69.delvefold.config.model;

import org.jspecify.annotations.Nullable;

/**
 * One independently-salted placement band. Nullable peak and plateau values are ignored unless the selected
 * distribution needs them.
 *
 * <p>Every rule, band, chunk, province region, and attempt receives an independent deterministic salt. Profile
 * validation enforces the documented bounds and the aggregate 4096-attempt/65536-work-unit per-chunk safety budget.
 *
 * @param id stable band ID unique within its rule and included in deterministic salt derivation
 * @param veinSize attempted output blocks per classic vein, 1–64; retained but ignored for province placement
 * @param attemptsPerChunk average classic-vein attempts per chunk, 0–256; retained but ignored for provinces
 * @param distribution vertical sampling distribution
 * @param minY inclusive minimum generation height, validated within -64 through 320
 * @param maxY inclusive maximum generation height, validated within -64 through 320
 * @param peakY required triangle peak height, otherwise ignored
 * @param plateauMinY required trapezoid plateau minimum height, otherwise ignored
 * @param plateauMaxY required trapezoid plateau maximum height, otherwise ignored
 * @param discardOnAirExposure probability from 0 through 1 of discarding an exposed candidate
 * @param placement classic-vein or regional-province placement; omitted schema-2 values default to vein
 * @param province province shape and work cap, required only for province placement
 */
public record SpawnBand(
        String id,
        int veinSize,
        double attemptsPerChunk,
        HeightDistribution distribution,
        int minY,
        int maxY,
        @Nullable Integer peakY,
        @Nullable Integer plateauMinY,
        @Nullable Integer plateauMaxY,
        double discardOnAirExposure,
        OreBandPlacement placement,
        @Nullable ProvinceSettings province) {
    /**
     * Normalizes compatibility defaults without hiding malformed numeric or distribution-specific values.
     *
     * <p>Full-profile validation reports range and shape failures with exact JSON paths before publication.
     *
     * @param id stable band ID, or {@code null} for an empty ID
     * @param veinSize attempted output blocks per vein
     * @param attemptsPerChunk average attempts per chunk
     * @param distribution vertical distribution, or {@code null} for uniform
     * @param minY inclusive minimum height
     * @param maxY inclusive maximum height
     * @param peakY triangle peak height
     * @param plateauMinY trapezoid plateau minimum height
     * @param plateauMaxY trapezoid plateau maximum height
     * @param discardOnAirExposure exposed-candidate discard probability
     * @param placement placement algorithm, or {@code null} for classic vein
     * @param province province settings when province placement is selected
     */
    public SpawnBand(
            @Nullable String id,
            int veinSize,
            double attemptsPerChunk,
            @Nullable HeightDistribution distribution,
            int minY,
            int maxY,
            @Nullable Integer peakY,
            @Nullable Integer plateauMinY,
            @Nullable Integer plateauMaxY,
            double discardOnAirExposure,
            @Nullable OreBandPlacement placement,
            @Nullable ProvinceSettings province) {
        this.id = id == null ? "" : id.trim();
        this.veinSize = veinSize;
        this.attemptsPerChunk = attemptsPerChunk;
        this.distribution = distribution == null ? HeightDistribution.UNIFORM : distribution;
        this.minY = minY;
        this.maxY = maxY;
        this.peakY = peakY;
        this.plateauMinY = plateauMinY;
        this.plateauMaxY = plateauMaxY;
        this.discardOnAirExposure = discardOnAirExposure;
        this.placement = placement == null ? OreBandPlacement.VEIN : placement;
        this.province = province;
    }

    /**
     * Creates a source- and binary-compatible classic-vein band for schema-2 callers predating provinces.
     *
     * @param id stable band ID
     * @param veinSize attempted output blocks per vein
     * @param attemptsPerChunk average attempts per chunk
     * @param distribution vertical distribution
     * @param minY inclusive minimum height
     * @param maxY inclusive maximum height
     * @param peakY triangle peak height
     * @param plateauMinY trapezoid plateau minimum height
     * @param plateauMaxY trapezoid plateau maximum height
     * @param discardOnAirExposure exposed-candidate discard probability
     */
    public SpawnBand(
            String id,
            int veinSize,
            double attemptsPerChunk,
            HeightDistribution distribution,
            int minY,
            int maxY,
            @Nullable Integer peakY,
            @Nullable Integer plateauMinY,
            @Nullable Integer plateauMaxY,
            double discardOnAirExposure) {
        this(
                id,
                veinSize,
                attemptsPerChunk,
                distribution,
                minY,
                maxY,
                peakY,
                plateauMinY,
                plateauMaxY,
                discardOnAirExposure,
                OreBandPlacement.VEIN,
                null);
    }

    /**
     * Creates a uniformly distributed classic-vein band.
     *
     * @param id stable band ID
     * @param veinSize attempted output blocks per vein, validated from 1 through 64
     * @param attempts average attempts per chunk, validated from 0 through 256
     * @param minY inclusive minimum height
     * @param maxY inclusive maximum height
     * @param airDiscard exposed-candidate discard probability from 0 through 1
     * @return unvalidated uniform vein band for inclusion in a complete profile
     */
    public static SpawnBand uniform(String id, int veinSize, double attempts, int minY, int maxY, double airDiscard) {
        return new SpawnBand(
                id, veinSize, attempts, HeightDistribution.UNIFORM, minY, maxY, null, null, null, airDiscard);
    }

    /**
     * Creates a triangularly distributed classic-vein band.
     *
     * @param id stable band ID
     * @param veinSize attempted output blocks per vein, validated from 1 through 64
     * @param attempts average attempts per chunk, validated from 0 through 256
     * @param minY inclusive minimum height
     * @param maxY inclusive maximum height
     * @param peakY triangle peak height within the inclusive band range
     * @param airDiscard exposed-candidate discard probability from 0 through 1
     * @return unvalidated triangular vein band for inclusion in a complete profile
     */
    public static SpawnBand triangle(
            String id, int veinSize, double attempts, int minY, int maxY, int peakY, double airDiscard) {
        return new SpawnBand(
                id, veinSize, attempts, HeightDistribution.TRIANGLE, minY, maxY, peakY, null, null, airDiscard);
    }

    /**
     * Copies this band with a replacement classic-vein attempt rate.
     *
     * @param attempts replacement average attempts per chunk, validated before profile publication
     * @return immutable band copy retaining every other field
     */
    public SpawnBand withAttempts(double attempts) {
        return new SpawnBand(
                id,
                veinSize,
                attempts,
                distribution,
                minY,
                maxY,
                peakY,
                plateauMinY,
                plateauMaxY,
                discardOnAirExposure,
                placement,
                province);
    }

    /**
     * Creates a regional-province band with neutral legacy vein fields.
     *
     * @param id stable band ID
     * @param distribution vertical center distribution
     * @param minY inclusive minimum center height
     * @param maxY inclusive maximum center height
     * @param peakY triangle peak height
     * @param plateauMinY trapezoid plateau minimum height
     * @param plateauMaxY trapezoid plateau maximum height
     * @param airDiscard exposed-candidate discard probability from 0 through 1
     * @param province deterministic province shape, density, and per-chunk work cap
     * @return unvalidated province band with vein size one and zero vein attempts
     */
    public static SpawnBand province(
            String id,
            HeightDistribution distribution,
            int minY,
            int maxY,
            @Nullable Integer peakY,
            @Nullable Integer plateauMinY,
            @Nullable Integer plateauMaxY,
            double airDiscard,
            ProvinceSettings province) {
        return new SpawnBand(
                id,
                1,
                0.0D,
                distribution,
                minY,
                maxY,
                peakY,
                plateauMinY,
                plateauMaxY,
                airDiscard,
                OreBandPlacement.PROVINCE,
                province);
    }

    /**
     * Reports whether this band selects regional-province placement.
     *
     * @return {@code true} only for {@link OreBandPlacement#PROVINCE}
     */
    public boolean provinceBand() {
        return placement == OreBandPlacement.PROVINCE;
    }
}
