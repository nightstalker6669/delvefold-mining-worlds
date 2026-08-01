package com.nightsta69.delvefold.world.feature;

import com.nightsta69.delvefold.config.model.GeologyTheme;
import com.nightsta69.delvefold.world.feature.GeologyThemeConfiguration.Phase;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Pure, bounded planner. Minecraft state checks are deliberately deferred to the feature adapter. */
public final class GeologyThemePlanner {
    /** Maximum number of strata replacement candidates returned for one chunk and phase. */
    public static final int MAX_STRATA_PLACEMENTS = 384;
    /** Maximum combined solid-decoration and fluid candidates returned for one chunk and phase. */
    public static final int MAX_DECORATION_PLACEMENTS = 12;
    /** Maximum fluid candidates within the decoration budget for one chunk. */
    public static final int MAX_FLUID_PLACEMENTS = 3;

    private static final int STRATA_NODES = 8;

    private GeologyThemePlanner() {}

    /**
     * Builds a deterministic, bounded geology plan whose horizontal positions stay in the requested chunk.
     *
     * <p>Seed inputs are mixed in the stable order world seed, packed chunk coordinates, persisted generation salt,
     * theme name, then phase domain. Heights are block coordinates sampled from the inclusive supplied bounds. This
     * method is pure and performs no world access; the feature adapter revalidates every write against the currently
     * generating chunk.
     *
     * @param theme recreation-locked geology theme; classic produces an empty plan
     * @param phase independent strata or decoration stream; null defaults to strata
     * @param worldSeed server world's 64-bit generation seed
     * @param chunkX target chunk X coordinate
     * @param chunkZ target chunk Z coordinate
     * @param generationSalt persisted mining-world generation salt
     * @param minimumY inclusive minimum block Y
     * @param maximumY inclusive maximum block Y
     * @return immutable bounded placement plan
     */
    public static Plan plan(
            @Nullable GeologyTheme theme,
            @Nullable Phase phase,
            long worldSeed,
            int chunkX,
            int chunkZ,
            long generationSalt,
            int minimumY,
            int maximumY) {
        if (theme == null || theme == GeologyTheme.CLASSIC || maximumY < minimumY) {
            return Plan.EMPTY;
        }
        Phase selectedPhase = phase == null ? Phase.STRATA : phase;
        long chunkPosition = ((long) chunkZ << 32) ^ (chunkX & 0xFFFFFFFFL);
        Random random = new Random(GenerationSeedMixer.geologySeed(
                worldSeed, chunkPosition, generationSalt, theme.serializedName(), selectedPhase == Phase.STRATA));
        return selectedPhase == Phase.STRATA
                ? strata(theme, random, chunkX, chunkZ, minimumY, maximumY)
                : decorations(theme, random, chunkX, chunkZ, minimumY, maximumY);
    }

    private static Plan strata(GeologyTheme theme, Random random, int chunkX, int chunkZ, int minimumY, int maximumY) {
        List<Placement> placements = new ArrayList<>(MAX_STRATA_PLACEMENTS);
        Set<Position> occupied = new HashSet<>();
        int originX = chunkX * 16;
        int originZ = chunkZ * 16;
        int height = maximumY - minimumY + 1;
        for (int node = 0; node < STRATA_NODES && placements.size() < MAX_STRATA_PLACEMENTS; node++) {
            int centerX = random.nextInt(16);
            int centerZ = random.nextInt(16);
            int centerY = minimumY + random.nextInt(height);
            int radiusX = 3 + random.nextInt(4);
            int radiusZ = 3 + random.nextInt(4);
            int radiusY = 1 + random.nextInt(2);
            Material material = strataMaterial(theme, random);
            for (int dy = -radiusY; dy <= radiusY && placements.size() < MAX_STRATA_PLACEMENTS; dy++) {
                for (int dz = -radiusZ; dz <= radiusZ && placements.size() < MAX_STRATA_PLACEMENTS; dz++) {
                    for (int dx = -radiusX; dx <= radiusX && placements.size() < MAX_STRATA_PLACEMENTS; dx++) {
                        int localX = centerX + dx;
                        int localZ = centerZ + dz;
                        int y = centerY + dy;
                        if (localX < 0 || localX > 15 || localZ < 0 || localZ > 15 || y < minimumY || y > maximumY) {
                            continue;
                        }
                        double distance = square(dx / (double) radiusX)
                                + square(dy / (double) radiusY)
                                + square(dz / (double) radiusZ);
                        Position position = new Position(originX + localX, y, originZ + localZ);
                        if (distance <= 1.0D && occupied.add(position)) {
                            placements.add(new Placement(position, material, Role.STRATA));
                        }
                    }
                }
            }
        }
        return new Plan(placements);
    }

    private static Plan decorations(
            GeologyTheme theme, Random random, int chunkX, int chunkZ, int minimumY, int maximumY) {
        List<Placement> placements = new ArrayList<>(MAX_DECORATION_PLACEMENTS);
        Set<Position> occupied = new HashSet<>();
        int originX = chunkX * 16;
        int originZ = chunkZ * 16;
        int height = maximumY - minimumY + 1;
        int fluids = 0;
        for (int candidate = 0; candidate < MAX_DECORATION_PLACEMENTS; candidate++) {
            Position position;
            do {
                position = new Position(
                        originX + random.nextInt(16), minimumY + random.nextInt(height), originZ + random.nextInt(16));
            } while (!occupied.add(position));
            boolean fluid = candidate % 4 == 3 && fluids < MAX_FLUID_PLACEMENTS;
            placements.add(new Placement(
                    position,
                    fluid ? fluidMaterial(theme) : decorationMaterial(theme),
                    fluid ? Role.FLUID : Role.DECORATION));
            if (fluid) fluids++;
        }
        return new Plan(placements);
    }

    private static Material strataMaterial(GeologyTheme theme, Random random) {
        int choice = random.nextInt(8);
        return switch (theme) {
            case VOLCANIC -> choice < 4 ? Material.TUFF : choice < 7 ? Material.BASALT : Material.BLACKSTONE;
            case DRIPSTONE -> choice < 4 ? Material.DRIPSTONE_BLOCK : choice < 7 ? Material.CALCITE : Material.TUFF;
            case LUSH ->
                choice < 3
                        ? Material.CLAY
                        : choice < 5 ? Material.MUD : choice < 7 ? Material.ROOTED_DIRT : Material.MOSS_BLOCK;
            case CRYSTAL ->
                choice < 4 ? Material.CALCITE : choice < 7 ? Material.SMOOTH_BASALT : Material.AMETHYST_BLOCK;
            case CLASSIC -> throw new IllegalStateException("Classic geology must not produce placements");
        };
    }

    private static Material decorationMaterial(GeologyTheme theme) {
        return switch (theme) {
            case VOLCANIC -> Material.MAGMA_BLOCK;
            case DRIPSTONE -> Material.DRIPSTONE_BLOCK;
            case LUSH -> Material.MOSS_BLOCK;
            case CRYSTAL -> Material.AMETHYST_BLOCK;
            case CLASSIC -> throw new IllegalStateException("Classic geology must not produce placements");
        };
    }

    private static Material fluidMaterial(GeologyTheme theme) {
        return theme == GeologyTheme.VOLCANIC ? Material.LAVA : Material.WATER;
    }

    private static double square(double value) {
        return value * value;
    }

    /**
     * Immutable per-chunk geology work plan.
     *
     * @param placements ordered candidates bounded by the public per-chunk safety constants
     */
    public record Plan(List<Placement> placements) {
        private static final Plan EMPTY = new Plan(List.of());

        /**
         * Copies placements and enforces per-role work budgets.
         *
         * @param placements ordered placement candidates
         * @throws IllegalArgumentException when the per-chunk strata, decoration, or fluid budget is exceeded
         */
        public Plan {
            placements = placements == null ? List.of() : List.copyOf(placements);
            long strata = placements.stream()
                    .filter(value -> value.role() == Role.STRATA)
                    .count();
            long decorations = placements.stream()
                    .filter(value -> value.role() == Role.DECORATION)
                    .count();
            long fluids = placements.stream()
                    .filter(value -> value.role() == Role.FLUID)
                    .count();
            if (strata > MAX_STRATA_PLACEMENTS
                    || decorations + fluids > MAX_DECORATION_PLACEMENTS
                    || fluids > MAX_FLUID_PLACEMENTS) {
                throw new IllegalArgumentException("Geology plan exceeds its per-chunk safety budget");
            }
        }
    }

    /**
     * One planned block mutation before live-state validation.
     *
     * @param position absolute block position inside the target chunk
     * @param material bounded vanilla material to place
     * @param role validation and placement behavior
     */
    public record Placement(Position position, Material material, Role role) {
        /**
         * Validates required placement fields.
         *
         * @param position absolute block position
         * @param material planned vanilla material
         * @param role placement role
         * @throws IllegalArgumentException when any field is null
         */
        public Placement {
            if (position == null || material == null || role == null) {
                throw new IllegalArgumentException("Geology placements require position, material, and role");
            }
        }
    }

    /**
     * Absolute block coordinates for one planned mutation.
     *
     * @param x block X inside the target chunk
     * @param y block Y inside the requested inclusive height range
     * @param z block Z inside the target chunk
     */
    public record Position(int x, int y, int z) {}

    /** Live-state validation strategy for a planned geology mutation. */
    public enum Role {
        /** Replaces an existing natural stone block. */
        STRATA,
        /** Searches for and replaces a valid exposed floor. */
        DECORATION,
        /** Replaces a natural block only when every neighbor is sealed within the current chunk. */
        FLUID
    }

    /** Closed vanilla-block palette available to geology themes. */
    public enum Material {
        /** Tuff strata. */
        TUFF,
        /** Basalt strata. */
        BASALT,
        /** Blackstone strata. */
        BLACKSTONE,
        /** Magma decoration. */
        MAGMA_BLOCK,
        /** Dripstone strata or decoration. */
        DRIPSTONE_BLOCK,
        /** Calcite strata. */
        CALCITE,
        /** Clay strata. */
        CLAY,
        /** Mud strata. */
        MUD,
        /** Rooted-dirt strata. */
        ROOTED_DIRT,
        /** Moss strata or decoration. */
        MOSS_BLOCK,
        /** Smooth-basalt strata. */
        SMOOTH_BASALT,
        /** Amethyst strata or decoration. */
        AMETHYST_BLOCK,
        /** Sealed water pocket. */
        WATER,
        /** Sealed lava pocket. */
        LAVA
    }

    private static final class Random {
        private long state;

        private Random(long seed) {
            this.state = seed;
        }

        private int nextInt(int bound) {
            if (bound <= 0) {
                throw new IllegalArgumentException("Random bound must be positive");
            }
            state += 0x9E3779B97F4A7C15L;
            return (int) Long.remainderUnsigned(GenerationSeedMixer.mix64(state), bound);
        }
    }
}
