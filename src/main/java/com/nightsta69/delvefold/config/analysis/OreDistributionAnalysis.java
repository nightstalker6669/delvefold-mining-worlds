package com.nightsta69.delvefold.config.analysis;

import com.nightsta69.delvefold.config.model.HeightDistribution;
import com.nightsta69.delvefold.config.model.SpawnBand;
import java.util.ArrayList;
import java.util.List;

/** Pure distribution math shared by validation, diagnostics, and the client preview. */
public final class OreDistributionAnalysis {
    private OreDistributionAnalysis() {}

    /**
     * Evaluates a band at every inclusive block height from its minimum through maximum Y values.
     *
     * <p>The returned samples are ordered by ascending Y. Their probabilities are normalized from the configured
     * distribution weights and their expected-attempt values sum to the band's configured attempts per eligible chunk,
     * subject to ordinary floating-point rounding. Invalid height ranges, attempt rates, or distribution shapes produce
     * an {@link Density#INVALID} summary instead of throwing.
     *
     * @param band band whose vertical distribution and per-chunk work should be evaluated
     * @return immutable, height-ordered distribution summary for the band
     */
    public static Summary analyze(SpawnBand band) {
        int minY = band.minY();
        int maxY = band.maxY();
        if (minY > maxY || !Double.isFinite(band.attemptsPerChunk()) || band.attemptsPerChunk() < 0.0D) {
            return new Summary(List.of(), 0.0D, 0.0D, Density.INVALID);
        }

        List<Double> weights = new ArrayList<>(maxY - minY + 1);
        double totalWeight = 0.0D;
        for (int y = minY; y <= maxY; y++) {
            double weight = weightAt(band, y);
            weights.add(weight);
            totalWeight += weight;
        }
        if (!(totalWeight > 0.0D)) {
            return new Summary(
                    List.of(),
                    band.attemptsPerChunk(),
                    band.attemptsPerChunk() * Math.max(0, band.veinSize()),
                    Density.INVALID);
        }

        List<Sample> samples = new ArrayList<>(weights.size());
        for (int index = 0; index < weights.size(); index++) {
            double probability = weights.get(index) / totalWeight;
            samples.add(new Sample(minY + index, probability, probability * band.attemptsPerChunk()));
        }
        double workUnits = band.attemptsPerChunk() * Math.max(0, band.veinSize());
        return new Summary(samples, band.attemptsPerChunk(), workUnits, density(workUnits));
    }

    private static double weightAt(SpawnBand band, int y) {
        HeightDistribution distribution = band.distribution();
        if (distribution == HeightDistribution.UNIFORM) {
            return 1.0D;
        }
        if (distribution == HeightDistribution.TRIANGLE) {
            Integer configuredPeak = band.peakY();
            int peak = configuredPeak == null ? (band.minY() + band.maxY()) / 2 : configuredPeak;
            if (peak < band.minY() || peak > band.maxY()) {
                return 0.0D;
            }
            return y <= peak
                    ? (double) (y - band.minY() + 1) / (peak - band.minY() + 1)
                    : (double) (band.maxY() - y + 1) / (band.maxY() - peak + 1);
        }
        Integer configuredPlateauMin = band.plateauMinY();
        Integer configuredPlateauMax = band.plateauMaxY();
        int plateauMin = configuredPlateauMin == null ? band.minY() : configuredPlateauMin;
        int plateauMax = configuredPlateauMax == null ? band.maxY() : configuredPlateauMax;
        if (plateauMin < band.minY() || plateauMax > band.maxY() || plateauMin > plateauMax) {
            return 0.0D;
        }
        if (y < plateauMin) {
            return (double) (y - band.minY() + 1) / (plateauMin - band.minY() + 1);
        }
        if (y > plateauMax) {
            return (double) (band.maxY() - y + 1) / (band.maxY() - plateauMax + 1);
        }
        return 1.0D;
    }

    private static Density density(double workUnits) {
        if (workUnits <= 16.0D) {
            return Density.SPARSE;
        }
        if (workUnits <= 128.0D) {
            return Density.MODERATE;
        }
        if (workUnits <= 512.0D) {
            return Density.DENSE;
        }
        return Density.EXTREME;
    }

    /**
     * One normalized sample in a band's vertical distribution.
     *
     * @param y absolute block Y represented by the sample
     * @param probability normalized share of the band's placement attempts assigned to {@code y}
     * @param expectedAttempts expected placement attempts at {@code y} per eligible chunk
     */
    public record Sample(int y, double probability, double expectedAttempts) {}

    /**
     * Immutable vertical distribution and conservative work summary for one spawn band.
     *
     * @param samples normalized samples ordered by ascending block Y
     * @param attemptsPerChunk configured placement attempts per eligible chunk
     * @param workUnits conservative block-placement work units per eligible chunk
     * @param density qualitative classification derived from {@code workUnits}
     */
    public record Summary(List<Sample> samples, double attemptsPerChunk, double workUnits, Density density) {
        /**
         * Creates an immutable summary by defensively copying the ordered samples.
         *
         * @param samples normalized samples ordered by ascending block Y
         * @param attemptsPerChunk configured placement attempts per eligible chunk
         * @param workUnits conservative block-placement work units per eligible chunk
         * @param density qualitative work-density classification
         */
        public Summary {
            samples = List.copyOf(samples);
        }

        /**
         * Returns the greatest normalized probability assigned to any represented block height.
         *
         * @return maximum sample probability, or {@code 0.0} when no samples are present
         */
        public double maximumProbability() {
            return samples.stream().mapToDouble(Sample::probability).max().orElse(0.0D);
        }
    }

    /** Qualitative bands for conservative block-placement work per eligible chunk. */
    public enum Density {
        /** The height range, attempt rate, or configured distribution cannot produce a valid distribution. */
        INVALID,
        /** At most 16 block-placement work units are configured per eligible chunk. */
        SPARSE,
        /** More than 16 and at most 128 work units are configured per eligible chunk. */
        MODERATE,
        /** More than 128 and at most 512 work units are configured per eligible chunk. */
        DENSE,
        /** More than 512 work units are configured per eligible chunk. */
        EXTREME
    }
}
