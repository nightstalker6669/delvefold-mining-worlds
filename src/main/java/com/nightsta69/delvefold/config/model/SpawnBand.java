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
        double discardOnAirExposure
) {
    public SpawnBand {
        id = id == null ? "" : id.trim();
        distribution = distribution == null ? HeightDistribution.UNIFORM : distribution;
    }

    public static SpawnBand uniform(String id, int veinSize, double attempts, int minY, int maxY, double airDiscard) {
        return new SpawnBand(id, veinSize, attempts, HeightDistribution.UNIFORM, minY, maxY, null, null, null, airDiscard);
    }

    public static SpawnBand triangle(String id, int veinSize, double attempts, int minY, int maxY, int peakY, double airDiscard) {
        return new SpawnBand(id, veinSize, attempts, HeightDistribution.TRIANGLE, minY, maxY, peakY, null, null, airDiscard);
    }

    public SpawnBand withAttempts(double attempts) {
        return new SpawnBand(id, veinSize, attempts, distribution, minY, maxY, peakY, plateauMinY, plateauMaxY, discardOnAirExposure);
    }
}
