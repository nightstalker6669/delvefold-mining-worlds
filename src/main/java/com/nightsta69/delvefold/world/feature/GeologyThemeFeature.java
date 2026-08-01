package com.nightsta69.delvefold.world.feature;

import com.nightsta69.delvefold.config.ConfigSnapshot;
import com.nightsta69.delvefold.config.DelvefoldConfigService;
import com.nightsta69.delvefold.config.model.GeologyTheme;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.world.DelvefoldWorldgen;
import com.nightsta69.delvefold.world.feature.GeologyThemePlanner.Material;
import com.nightsta69.delvefold.world.feature.GeologyThemePlanner.Placement;
import com.nightsta69.delvefold.world.feature.GeologyThemePlanner.Position;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import org.jspecify.annotations.Nullable;

/**
 * Applies the pure geology plan after re-checking every target against live world-generation state.
 *
 * <p>Every mutation is constrained to the currently generating chunk, the configured block-Y bounds, and
 * {@link WorldGenLevel#ensureCanWrite(BlockPos)}. Neighbor-sensitive fluid placement also rejects any neighbor outside
 * that chunk.
 */
public final class GeologyThemeFeature extends Feature<GeologyThemeConfiguration> {
    private static final int UPDATE_NONE = 2;
    private static final int SURFACE_SEARCH_RADIUS = 48;

    /** Creates the feature with the shared phase-selecting datapack codec. */
    public GeologyThemeFeature() {
        super(GeologyThemeConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<GeologyThemeConfiguration> context) {
        ConfigSnapshot snapshot;
        try {
            snapshot = DelvefoldConfigService.get().snapshot();
        } catch (IllegalStateException ignored) {
            return false;
        }
        var settings = snapshot.settings();
        GeologyTheme theme = settings.identity().geologyTheme();
        @Nullable TerrainMode terrainMode = settings.terrainMode();
        if (!settings.initialized()
                || terrainMode == null
                || theme == GeologyTheme.CLASSIC
                || !context.level()
                        .getLevel()
                        .dimension()
                        .equals(DelvefoldWorldgen.levelFor(
                                terrainMode, settings.identity().terrainVariant()))) {
            return false;
        }

        ChunkPos chunk = new ChunkPos(context.origin());
        int minimumY = context.level().getMinBuildHeight() + 4;
        int maximumY = context.level().getMaxBuildHeight() - 5;
        return applyTheme(
                        context.level(),
                        chunk,
                        theme,
                        context.config().phase(),
                        terrainMode,
                        context.level().getSeed(),
                        settings.generationSalt(),
                        minimumY,
                        maximumY)
                > 0;
    }

    static int applyTheme(
            WorldGenLevel level,
            ChunkPos chunk,
            GeologyTheme theme,
            GeologyThemeConfiguration.Phase phase,
            TerrainMode terrainMode,
            long worldSeed,
            long generationSalt,
            int minimumY,
            int maximumY) {
        GeologyThemePlanner.Plan plan =
                GeologyThemePlanner.plan(theme, phase, worldSeed, chunk.x, chunk.z, generationSalt, minimumY, maximumY);
        return applyPlan(level, chunk, plan, terrainMode, minimumY, maximumY);
    }

    static int applyPlan(
            WorldGenLevel level,
            ChunkPos chunk,
            GeologyThemePlanner.Plan plan,
            TerrainMode terrainMode,
            int minimumY,
            int maximumY) {
        int writes = 0;
        for (Placement placement : plan.placements()) {
            if (apply(level, chunk, placement, terrainMode, minimumY, maximumY)) {
                writes++;
            }
        }
        return writes;
    }

    private static boolean apply(
            WorldGenLevel level,
            ChunkPos chunk,
            Placement placement,
            TerrainMode terrainMode,
            int minimumY,
            int maximumY) {
        Position position = placement.position();
        BlockPos target = new BlockPos(position.x(), position.y(), position.z());
        if (!inside(chunk, target)
                || target.getY() < minimumY
                || target.getY() > maximumY
                || !level.ensureCanWrite(target)) {
            return false;
        }
        return switch (placement.role()) {
            case STRATA -> replaceNatural(level, target, state(placement.material()));
            case FLUID -> sealedFluid(level, chunk, target, state(placement.material()));
            case DECORATION -> decorate(level, chunk, target, placement.material(), terrainMode, minimumY, maximumY);
        };
    }

    private static boolean decorate(
            WorldGenLevel level,
            ChunkPos chunk,
            BlockPos seed,
            Material material,
            TerrainMode terrainMode,
            int minimumY,
            int maximumY) {
        for (int offset = 0; offset <= SURFACE_SEARCH_RADIUS; offset++) {
            int above = seed.getY() + offset;
            if (above <= maximumY && replaceFloor(level, chunk, seed.getX(), above, seed.getZ(), material)) {
                return true;
            }
            int below = seed.getY() - offset;
            if (offset > 0
                    && below >= minimumY
                    && replaceFloor(level, chunk, seed.getX(), below, seed.getZ(), material)) {
                return true;
            }
        }
        if (terrainMode != TerrainMode.CAVERN) {
            int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, seed.getX(), seed.getZ()) - 1;
            if (surfaceY >= minimumY
                    && surfaceY <= maximumY
                    && replaceFloor(level, chunk, seed.getX(), surfaceY, seed.getZ(), material)) {
                return true;
            }
        }
        return false;
    }

    private static boolean replaceFloor(WorldGenLevel level, ChunkPos chunk, int x, int y, int z, Material material) {
        BlockPos floor = new BlockPos(x, y, z);
        if (!inside(chunk, floor) || !level.ensureCanWrite(floor) || !decoratableFloor(level.getBlockState(floor))) {
            return false;
        }
        BlockState above = level.getBlockState(floor.above());
        if (!above.isAir() && !above.getFluidState().isEmpty()) {
            return false;
        }
        level.setBlock(floor, state(material), UPDATE_NONE);
        return true;
    }

    private static boolean sealedFluid(WorldGenLevel level, ChunkPos chunk, BlockPos target, BlockState fluid) {
        if (!natural(level.getBlockState(target))) {
            return false;
        }
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = target.relative(direction);
            if (!inside(chunk, neighbor)
                    || !level.ensureCanWrite(neighbor)
                    || !natural(level.getBlockState(neighbor))) {
                return false;
            }
        }
        level.setBlock(target, fluid, UPDATE_NONE);
        return true;
    }

    private static boolean replaceNatural(WorldGenLevel level, BlockPos target, BlockState replacement) {
        if (!natural(level.getBlockState(target))) {
            return false;
        }
        level.setBlock(target, replacement, UPDATE_NONE);
        return true;
    }

    private static boolean natural(BlockState state) {
        return state.is(Blocks.STONE)
                || state.is(Blocks.DEEPSLATE)
                || state.is(Blocks.GRANITE)
                || state.is(Blocks.DIORITE)
                || state.is(Blocks.ANDESITE)
                || state.is(Blocks.TUFF);
    }

    private static boolean decoratableFloor(BlockState state) {
        return natural(state)
                || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.MUD)
                || state.is(Blocks.CLAY);
    }

    private static boolean inside(ChunkPos chunk, BlockPos position) {
        return position.getX() >= chunk.getMinBlockX()
                && position.getX() <= chunk.getMaxBlockX()
                && position.getZ() >= chunk.getMinBlockZ()
                && position.getZ() <= chunk.getMaxBlockZ();
    }

    private static BlockState state(Material material) {
        return switch (material) {
            case TUFF -> Blocks.TUFF.defaultBlockState();
            case BASALT -> Blocks.BASALT.defaultBlockState();
            case BLACKSTONE -> Blocks.BLACKSTONE.defaultBlockState();
            case MAGMA_BLOCK -> Blocks.MAGMA_BLOCK.defaultBlockState();
            case DRIPSTONE_BLOCK -> Blocks.DRIPSTONE_BLOCK.defaultBlockState();
            case CALCITE -> Blocks.CALCITE.defaultBlockState();
            case CLAY -> Blocks.CLAY.defaultBlockState();
            case MUD -> Blocks.MUD.defaultBlockState();
            case ROOTED_DIRT -> Blocks.ROOTED_DIRT.defaultBlockState();
            case MOSS_BLOCK -> Blocks.MOSS_BLOCK.defaultBlockState();
            case SMOOTH_BASALT -> Blocks.SMOOTH_BASALT.defaultBlockState();
            case AMETHYST_BLOCK -> Blocks.AMETHYST_BLOCK.defaultBlockState();
            case WATER -> Blocks.WATER.defaultBlockState();
            case LAVA -> Blocks.LAVA.defaultBlockState();
        };
    }
}
