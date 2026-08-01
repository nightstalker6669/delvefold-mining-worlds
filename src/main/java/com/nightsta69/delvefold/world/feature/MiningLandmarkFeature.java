package com.nightsta69.delvefold.world.feature;

import com.mojang.serialization.Codec;
import com.nightsta69.delvefold.config.ConfigSnapshot;
import com.nightsta69.delvefold.config.DelvefoldConfigService;
import com.nightsta69.delvefold.config.model.LandmarkPreset;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.WorldIdentitySettings;
import com.nightsta69.delvefold.world.DelvefoldWorldgen;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.jspecify.annotations.Nullable;

/**
 * Legacy bounded single-chunk landmarks selected from active world identity settings.
 *
 * <p>The reloadable structure catalog supersedes this adapter for multi-chunk templates. Legacy mutations remain
 * bounded around the currently generating feature origin.
 */
public final class MiningLandmarkFeature extends Feature<NoneFeatureConfiguration> {
    private static final int UPDATE_NONE = 2;

    /**
     * Creates the legacy feature with Minecraft's no-configuration codec.
     *
     * @param codec platform codec for {@link NoneFeatureConfiguration}
     */
    public MiningLandmarkFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        ConfigSnapshot snapshot;
        try {
            snapshot = DelvefoldConfigService.get().snapshot();
        } catch (IllegalStateException ignored) {
            return false;
        }
        WorldIdentitySettings identity = snapshot.settings().identity();
        RandomSource random = randomForContent(
                context.random(),
                context.level().getSeed(),
                new ChunkPos(context.origin()),
                snapshot.settings().generationSalt());
        if (identity.landmarkPreset() == LandmarkPreset.PURE_MINING) {
            return false;
        }
        if (identity.landmarkPreset() == LandmarkPreset.BALANCED && random.nextBoolean()) {
            return false;
        }
        List<Landmark> choices = new ArrayList<>(3);
        if (identity.surveyStations()) choices.add(Landmark.SURVEY_STATION);
        if (identity.motherlodes()) choices.add(Landmark.MOTHERLODE);
        if (identity.faultLines()) choices.add(Landmark.FAULT_LINE);
        if (choices.isEmpty()) {
            return false;
        }
        @Nullable TerrainMode terrain =
                DelvefoldWorldgen.terrainFor(context.level().getLevel().dimension());
        if (terrain == null) {
            return false;
        }
        BlockPos origin = context.origin().offset(random.nextInt(16), 0, random.nextInt(16));
        return switch (choices.get(random.nextInt(choices.size()))) {
            case SURVEY_STATION -> surveyStation(context.level(), origin, terrain);
            case MOTHERLODE -> motherlode(context.level(), origin, random);
            case FAULT_LINE -> faultLine(context.level(), origin, random);
        };
    }

    static RandomSource randomForContent(
            RandomSource legacyRandom, long worldSeed, ChunkPos chunkPos, long generationSalt) {
        if (generationSalt == 0L) {
            // The placement modifier already consumed the legacy rarity and in-square draws.
            return legacyRandom;
        }
        return RandomSource.create(
                GenerationSeedMixer.landmarkContentSeed(worldSeed, chunkPos.toLong(), generationSalt));
    }

    private static boolean surveyStation(WorldGenLevel level, BlockPos origin, TerrainMode terrain) {
        BlockPos floor = terrain == TerrainMode.CAVERN
                ? cavernFloor(level, origin)
                : new BlockPos(
                        origin.getX(),
                        level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, origin.getX(), origin.getZ()) - 1,
                        origin.getZ());
        if (floor == null
                || floor.getY() <= level.getMinBuildHeight() + 2
                || floor.getY() >= level.getMaxBuildHeight() - 6) {
            return false;
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos at = floor.offset(dx, 0, dz);
                set(
                        level,
                        at,
                        (Math.abs(dx) == 2 || Math.abs(dz) == 2)
                                ? Blocks.COBBLED_DEEPSLATE.defaultBlockState()
                                : Blocks.POLISHED_DEEPSLATE.defaultBlockState());
                for (int dy = 1; dy <= 3; dy++) {
                    set(level, at.above(dy), Blocks.AIR.defaultBlockState());
                }
            }
        }
        set(level, floor.above(), Blocks.CRAFTING_TABLE.defaultBlockState());
        set(level, floor.offset(2, 1, 2), Blocks.OAK_FENCE.defaultBlockState());
        set(level, floor.offset(2, 2, 2), Blocks.TORCH.defaultBlockState());
        set(level, floor.offset(-2, 1, -2), Blocks.OAK_FENCE.defaultBlockState());
        set(level, floor.offset(-2, 2, -2), Blocks.TORCH.defaultBlockState());
        return true;
    }

    private static @Nullable BlockPos cavernFloor(WorldGenLevel level, BlockPos origin) {
        int top = Math.min(level.getMaxBuildHeight() - 5, 120);
        int bottom = Math.max(level.getMinBuildHeight() + 3, -48);
        for (int y = top; y >= bottom; y--) {
            BlockPos candidate = new BlockPos(origin.getX(), y, origin.getZ());
            if (!level.getBlockState(candidate).isAir()
                    && level.getBlockState(candidate.above()).isAir()
                    && level.getBlockState(candidate.above(2)).isAir()) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean motherlode(WorldGenLevel level, BlockPos origin, RandomSource random) {
        int minimum = Math.max(level.getMinBuildHeight() + 8, -48);
        int maximum = Math.min(level.getMaxBuildHeight() - 8, 48);
        int y = minimum + random.nextInt(Math.max(1, maximum - minimum + 1));
        BlockPos center = new BlockPos(origin.getX(), y, origin.getZ());
        BlockState accent =
                random.nextBoolean() ? Blocks.RAW_IRON_BLOCK.defaultBlockState() : Blocks.CALCITE.defaultBlockState();
        boolean placed = false;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx * dx + dy * dy + dz * dz > 6 || random.nextFloat() < 0.28F) continue;
                    BlockPos at = center.offset(dx, dy, dz);
                    if (replaceable(level.getBlockState(at))) {
                        set(
                                level,
                                at,
                                (dx == 0 && dy == 0 && dz == 0) ? Blocks.RAW_GOLD_BLOCK.defaultBlockState() : accent);
                        placed = true;
                    }
                }
            }
        }
        return placed;
    }

    private static boolean faultLine(WorldGenLevel level, BlockPos origin, RandomSource random) {
        int minimum = Math.max(level.getMinBuildHeight() + 4, -56);
        int surface = Math.min(
                level.getMaxBuildHeight() - 4,
                level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, origin.getX(), origin.getZ()));
        boolean placed = false;
        for (int y = minimum; y < surface && y < minimum + 96; y++) {
            int drift = (y - minimum) / 12;
            BlockPos at = new BlockPos(origin.getX() + drift, y, origin.getZ() + random.nextInt(3) - 1);
            if (replaceable(level.getBlockState(at))) {
                set(level, at, (y & 7) == 0 ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.TUFF.defaultBlockState());
                placed = true;
            }
        }
        return placed;
    }

    private static boolean replaceable(BlockState state) {
        return state.is(Blocks.STONE)
                || state.is(Blocks.DEEPSLATE)
                || state.is(Blocks.TUFF)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.GRASS_BLOCK);
    }

    private static void set(WorldGenLevel level, BlockPos position, BlockState state) {
        level.setBlock(position, state, UPDATE_NONE);
    }

    private enum Landmark {
        SURVEY_STATION,
        MOTHERLODE,
        FAULT_LINE
    }
}
