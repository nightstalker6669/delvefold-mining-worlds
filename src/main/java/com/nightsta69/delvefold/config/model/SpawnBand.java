package com.nightsta69.delvefold.config.model;

/**
 * One independently-salted placement band. Nullable peak and plateau values are
 * ignored unless the selected distribution needs them.
 */
public record SpawnBand(
        String id,
        int veinSize,
        double attemptsPerChunk,
        HeightDistribution distribution,
        int minY,
        int maxY,
        Integer peakY,
        Integer plateauMinY,
        Integer plateauMaxY,
        double discardOnAirExposure,
        OreBandPlacement placement,
        ProvinceSettings province
) {
    public SpawnBand {
        id = id == null ? "" : id.trim();
        distribution = distribution == null ? HeightDistribution.UNIFORM : distribution;
        placement = placement == null ? OreBandPlacement.VEIN : placement;
    }

    /** Source- and binary-compatible constructor for schema-2 vein bands. */
    public SpawnBand(
            String id,
            int veinSize,
            double attemptsPerChunk,
            HeightDistribution distribution,
            int minY,
            int maxY,
            Integer peakY,
            Integer plateauMinY,
            Integer plateauMaxY,
            double discardOnAirExposure) {
        this(id, veinSize, attemptsPerChunk, distribution, minY, maxY, peakY,
                plateauMinY, plateauMaxY, discardOnAirExposure, OreBandPlacement.VEIN, null);
    }

    public static SpawnBand uniform(String id, int veinSize, double attempts, int minY, int maxY, double airDiscard) {
        return new SpawnBand(id, veinSize, attempts, HeightDistribution.UNIFORM, minY, maxY, null, null, null, airDiscard);
    }

    public static SpawnBand triangle(String id, int veinSize, double attempts, int minY, int maxY, int peakY, double airDiscard) {
        return new SpawnBand(id, veinSize, attempts, HeightDistribution.TRIANGLE, minY, maxY, peakY, null, null, airDiscard);
    }

    public SpawnBand withAttempts(double attempts) {
        return new SpawnBand(id, veinSize, attempts, distribution, minY, maxY, peakY,
                plateauMinY, plateauMaxY, discardOnAirExposure, placement, province);
    }

    public static SpawnBand province(
            String id,
            HeightDistribution distribution,
            int minY,
            int maxY,
            Integer peakY,
            Integer plateauMinY,
            Integer plateauMaxY,
            double airDiscard,
            ProvinceSettings province) {
        return new SpawnBand(id, 1, 0.0D, distribution, minY, maxY, peakY,
                plateauMinY, plateauMaxY, airDiscard, OreBandPlacement.PROVINCE, province);
    }

    public boolean provinceBand() {
        return placement == OreBandPlacement.PROVINCE;
    }
}
