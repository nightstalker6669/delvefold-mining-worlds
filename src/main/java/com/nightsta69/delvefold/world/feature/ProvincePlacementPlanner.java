package com.nightsta69.delvefold.world.feature;

import com.nightsta69.delvefold.config.model.HeightDistribution;
import com.nightsta69.delvefold.config.model.OreBandPlacement;
import com.nightsta69.delvefold.config.model.ProvinceSettings;
import com.nightsta69.delvefold.config.model.SpawnBand;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure deterministic planner for regional ore provinces.
 *
 * <p>Every returned candidate is inside the requested chunk. Regional centers depend only on their region coordinate,
 * world seed, persisted generation salt, and band ID, so neighboring chunks independently agree on the same province.
 * The hard work cap is shared by all province slices touching the chunk.
 */
public final class ProvincePlacementPlanner {
    private ProvincePlacementPlanner() {}

    public static Plan plan(
            long worldSeed,
            long generationSalt,
            String bandSalt,
            SpawnBand band,
            int chunkX,
            int chunkZ,
            int minBuildHeight,
            int maxBuildHeightExclusive) {
        ProvinceSettings settings = band == null ? null : band.province();
        if (band == null
                || band.placement() != OreBandPlacement.PROVINCE
                || settings == null
                || settings.regionSize() <= 0
                || settings.radius() <= 0
                || settings.radius() > settings.regionSize()
                || settings.verticalThickness() <= 0
                || !Double.isFinite(settings.density())
                || settings.density() <= 0.0D
                || settings.density() > 1.0D
                || settings.perChunkWorkCap() <= 0
                || minBuildHeight >= maxBuildHeightExclusive) {
            return Plan.EMPTY;
        }

        long chunkMinX = (long) chunkX * 16L;
        long chunkMinZ = (long) chunkZ * 16L;
        long chunkMaxX = chunkMinX + 15L;
        long chunkMaxZ = chunkMinZ + 15L;
        int radius = settings.radius();
        int regionSize = settings.regionSize();
        long minRegionX = Math.floorDiv(chunkMinX - radius, regionSize);
        long maxRegionX = Math.floorDiv(chunkMaxX + radius, regionSize);
        long minRegionZ = Math.floorDiv(chunkMinZ - radius, regionSize);
        long maxRegionZ = Math.floorDiv(chunkMaxZ + radius, regionSize);

        List<Slice> slices = new ArrayList<>();
        long chunkPosition = packChunk(chunkX, chunkZ);
        for (long regionX = minRegionX; regionX <= maxRegionX; regionX++) {
            for (long regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
                long centerSeed = GenerationSeedMixer.oreProvinceCenterSeed(
                        worldSeed, regionX, regionZ, bandSalt, generationSalt);
                DeterministicRandom centerRandom = new DeterministicRandom(centerSeed);
                long centerXLong = regionX * (long) regionSize + centerRandom.nextInt(regionSize);
                long centerZLong = regionZ * (long) regionSize + centerRandom.nextInt(regionSize);
                if (centerXLong < Integer.MIN_VALUE
                        || centerXLong > Integer.MAX_VALUE
                        || centerZLong < Integer.MIN_VALUE
                        || centerZLong > Integer.MAX_VALUE) {
                    continue;
                }
                int centerX = (int) centerXLong;
                int centerZ = (int) centerZLong;
                if (!circleIntersectsRectangle(centerX, centerZ, radius, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ)) {
                    continue;
                }
                int centerY = sampleHeight(band, centerRandom);
                int lowerHalf = (settings.verticalThickness() - 1) / 2;
                int upperHalf = settings.verticalThickness() / 2;
                int minY = Math.max(minBuildHeight, centerY - lowerHalf);
                int maxY = Math.min(maxBuildHeightExclusive - 1, centerY + upperHalf);
                if (minY > maxY) {
                    continue;
                }

                int minX = (int) Math.max(chunkMinX, centerXLong - radius);
                int maxX = (int) Math.min(chunkMaxX, centerXLong + radius);
                int minZ = (int) Math.max(chunkMinZ, centerZLong - radius);
                int maxZ = (int) Math.min(chunkMaxZ, centerZLong + radius);
                int volume = (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
                long chunkSeed = GenerationSeedMixer.oreProvinceChunkSeed(
                        worldSeed, regionX, regionZ, chunkPosition, bandSalt, generationSalt);
                DeterministicRandom countRandom = new DeterministicRandom(chunkSeed);
                double expected = volume * settings.density();
                int desired = Math.min(volume, stochasticRound(expected, countRandom));
                if (desired <= 0) {
                    continue;
                }
                slices.add(new Slice(
                        regionX,
                        regionZ,
                        centerX,
                        centerY,
                        centerZ,
                        minX,
                        maxX,
                        minY,
                        maxY,
                        minZ,
                        maxZ,
                        desired,
                        chunkSeed,
                        GenerationSeedMixer.oreProvinceOutputSeed(
                                worldSeed, regionX, regionZ, bandSalt, generationSalt)));
            }
        }
        if (slices.isEmpty()) {
            return Plan.EMPTY;
        }

        int[] allocations = allocate(slices, settings.perChunkWorkCap());
        List<ProvinceSlice> planned = new ArrayList<>(slices.size());
        int workUnits = 0;
        for (int sliceIndex = 0; sliceIndex < slices.size(); sliceIndex++) {
            Slice slice = slices.get(sliceIndex);
            int allocation = allocations[sliceIndex];
            if (allocation <= 0) {
                continue;
            }
            workUnits += allocation;
            List<Candidate> candidates = sampleCandidates(slice, allocation, settings);
            planned.add(new ProvinceSlice(
                    slice.regionX(),
                    slice.regionZ(),
                    slice.centerX(),
                    slice.centerY(),
                    slice.centerZ(),
                    slice.outputSeed(),
                    candidates));
        }
        return new Plan(planned, workUnits);
    }

    private static int stochasticRound(double expected, DeterministicRandom random) {
        if (!(expected > 0.0D)) {
            return 0;
        }
        if (expected >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        int whole = (int) Math.floor(expected);
        return whole + (random.nextDouble() < expected - whole ? 1 : 0);
    }

    private static int[] allocate(List<Slice> slices, int cap) {
        int[] result = new int[slices.size()];
        long totalDesired = slices.stream().mapToLong(Slice::desired).sum();
        int boundedCap = (int) Math.min(Math.max(0L, cap), totalDesired);
        if (boundedCap == 0) {
            return result;
        }
        if (totalDesired <= boundedCap) {
            for (int index = 0; index < slices.size(); index++) {
                result[index] = slices.get(index).desired();
            }
            return result;
        }

        List<AllocationRemainder> remainders = new ArrayList<>(slices.size());
        int assigned = 0;
        for (int index = 0; index < slices.size(); index++) {
            long scaled = (long) slices.get(index).desired() * boundedCap;
            result[index] = (int) (scaled / totalDesired);
            assigned += result[index];
            remainders.add(new AllocationRemainder(
                    index,
                    scaled % totalDesired,
                    slices.get(index).regionX(),
                    slices.get(index).regionZ()));
        }
        remainders.sort(Comparator.comparingLong(AllocationRemainder::remainder)
                .reversed()
                .thenComparingLong(AllocationRemainder::regionX)
                .thenComparingLong(AllocationRemainder::regionZ));
        for (int index = 0; assigned < boundedCap; index++, assigned++) {
            result[remainders.get(index).index()]++;
        }
        return result;
    }

    private static List<Candidate> sampleCandidates(Slice slice, int count, ProvinceSettings settings) {
        int width = slice.maxX() - slice.minX() + 1;
        int height = slice.maxY() - slice.minY() + 1;
        int depth = slice.maxZ() - slice.minZ() + 1;
        int volume = width * height * depth;
        int boundedCount = Math.min(count, volume);
        DeterministicRandom random = new DeterministicRandom(slice.chunkSeed());
        // Keep the stochastic-round draw aligned with the count-planning stream.
        random.nextDouble();
        Set<Integer> selected = new HashSet<>(Math.max(16, boundedCount * 2));
        for (int cursor = volume - boundedCount; cursor < volume; cursor++) {
            int draw = random.nextInt(cursor + 1);
            if (!selected.add(draw)) {
                selected.add(cursor);
            }
        }
        List<Integer> ordered = selected.stream().sorted().toList();
        List<Candidate> result = new ArrayList<>(ordered.size());
        double verticalRadius = Math.max(0.5D, settings.verticalThickness() / 2.0D);
        double radiusSquared = (double) settings.radius() * settings.radius();
        for (int linearIndex : ordered) {
            int localX = linearIndex % width;
            int remaining = linearIndex / width;
            int localY = remaining % height;
            int localZ = remaining / height;
            int x = slice.minX() + localX;
            int y = slice.minY() + localY;
            int z = slice.minZ() + localZ;
            double dx = x - slice.centerX();
            double dy = y - slice.centerY();
            double dz = z - slice.centerZ();
            double normalized = (dx * dx + dz * dz) / radiusSquared + (dy * dy) / (verticalRadius * verticalRadius);
            if (normalized <= 1.0D) {
                result.add(new Candidate(
                        x, y, z, GenerationSeedMixer.oreProvincePositionSeed(slice.chunkSeed(), linearIndex)));
            }
        }
        return List.copyOf(result);
    }

    private static boolean circleIntersectsRectangle(
            int centerX, int centerZ, int radius, long minX, long maxX, long minZ, long maxZ) {
        long nearestX = Math.clamp((long) centerX, minX, maxX);
        long nearestZ = Math.clamp((long) centerZ, minZ, maxZ);
        long dx = centerX - nearestX;
        long dz = centerZ - nearestZ;
        return dx * dx + dz * dz <= (long) radius * radius;
    }

    private static int sampleHeight(SpawnBand band, DeterministicRandom random) {
        int minY = band.minY();
        int maxY = band.maxY();
        if (minY >= maxY) {
            return minY;
        }
        if (band.distribution() == HeightDistribution.UNIFORM) {
            return minY + random.nextInt(maxY - minY + 1);
        }
        double[] cumulative = new double[maxY - minY + 1];
        double total = 0.0D;
        for (int index = 0; index < cumulative.length; index++) {
            int y = minY + index;
            total += Math.max(0.0D, heightWeight(band, y));
            cumulative[index] = total;
        }
        if (!(total > 0.0D)) {
            return minY;
        }
        double selected = random.nextDouble() * total;
        for (int index = 0; index < cumulative.length; index++) {
            if (selected < cumulative[index]) {
                return minY + index;
            }
        }
        return maxY;
    }

    private static double heightWeight(SpawnBand band, int y) {
        if (band.distribution() == HeightDistribution.TRIANGLE) {
            Integer peak = band.peakY();
            if (peak == null || peak < band.minY() || peak > band.maxY()) {
                return 0.0D;
            }
            return y <= peak
                    ? (double) (y - band.minY() + 1) / (peak - band.minY() + 1)
                    : (double) (band.maxY() - y + 1) / (band.maxY() - peak + 1);
        }
        Integer plateauMin = band.plateauMinY();
        Integer plateauMax = band.plateauMaxY();
        if (plateauMin == null
                || plateauMax == null
                || plateauMin < band.minY()
                || plateauMax > band.maxY()
                || plateauMin > plateauMax) {
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

    private static long packChunk(int chunkX, int chunkZ) {
        return Integer.toUnsignedLong(chunkX) | (Integer.toUnsignedLong(chunkZ) << 32);
    }

    public record Plan(List<ProvinceSlice> provinces, int workUnits) {
        private static final Plan EMPTY = new Plan(List.of(), 0);

        public Plan {
            provinces = provinces == null ? List.of() : List.copyOf(provinces);
            if (workUnits < 0) {
                throw new IllegalArgumentException("Province work cannot be negative");
            }
        }
    }

    public record ProvinceSlice(
            long regionX,
            long regionZ,
            int centerX,
            int centerY,
            int centerZ,
            long outputSeed,
            List<Candidate> candidates) {
        public ProvinceSlice {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }
    }

    public record Candidate(int x, int y, int z, long placementSeed) {}

    private record Slice(
            long regionX,
            long regionZ,
            int centerX,
            int centerY,
            int centerZ,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int minZ,
            int maxZ,
            int desired,
            long chunkSeed,
            long outputSeed) {}

    private record AllocationRemainder(int index, long remainder, long regionX, long regionZ) {}

    /** Fixed SplitMix64 stream so planning never depends on a client/server runtime class. */
    private static final class DeterministicRandom {
        private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;
        private long state;

        private DeterministicRandom(long seed) {
            state = seed;
        }

        private long nextLong() {
            state += GOLDEN_GAMMA;
            return GenerationSeedMixer.mix64(state);
        }

        private int nextInt(int bound) {
            if (bound <= 0) {
                throw new IllegalArgumentException("Bound must be positive");
            }
            long bits;
            long value;
            do {
                bits = nextLong() >>> 1;
                value = bits % bound;
            } while (bits - value + (bound - 1L) < 0L);
            return (int) value;
        }

        private double nextDouble() {
            return (nextLong() >>> 11) * 0x1.0p-53;
        }
    }
}
