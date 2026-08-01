package com.nightsta69.delvefold.config.analysis;

import com.nightsta69.delvefold.config.model.HeightDistribution;
import com.nightsta69.delvefold.config.model.SpawnBand;
import java.util.ArrayList;
import java.util.List;

/** Pure distribution math shared by validation, diagnostics, and the client preview. */
public final class OreDistributionAnalysis {
    private OreDistributionAnalysis() {}

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
            int peak = band.peakY() == null ? (band.minY() + band.maxY()) / 2 : band.peakY();
            if (peak < band.minY() || peak > band.maxY()) {
                return 0.0D;
            }
            return y <= peak
                    ? (double) (y - band.minY() + 1) / (peak - band.minY() + 1)
                    : (double) (band.maxY() - y + 1) / (band.maxY() - peak + 1);
        }
        int plateauMin = band.plateauMinY() == null ? band.minY() : band.plateauMinY();
        int plateauMax = band.plateauMaxY() == null ? band.maxY() : band.plateauMaxY();
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

    public record Sample(int y, double probability, double expectedAttempts) {}

    public record Summary(List<Sample> samples, double attemptsPerChunk, double workUnits, Density density) {
        public Summary {
            samples = List.copyOf(samples);
        }

        public double maximumProbability() {
            return samples.stream().mapToDouble(Sample::probability).max().orElse(0.0D);
        }
    }

    public enum Density {
        INVALID,
        SPARSE,
        MODERATE,
        DENSE,
        EXTREME
    }
}
