package com.nightsta69.delvefold.world.feature;

import com.mojang.serialization.MapCodec;
import com.nightsta69.delvefold.config.DelvefoldConfigService;
import com.nightsta69.delvefold.world.DelvefoldWorldgen;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * Data-driven landmark rarity and in-square placement whose rotated layouts use
 * a deterministic stream independent from landmark contents.
 */
public final class GenerationSaltedLandmarkPlacement extends PlacementModifier {
    public static final MapCodec<GenerationSaltedLandmarkPlacement> CODEC =
            ExtraCodecs.POSITIVE_INT.fieldOf("chance")
                    .xmap(GenerationSaltedLandmarkPlacement::new,
                            GenerationSaltedLandmarkPlacement::chance);

    private final int chance;

    public GenerationSaltedLandmarkPlacement(int chance) {
        if (chance <= 0) {
            throw new IllegalArgumentException("Landmark placement chance must be positive");
        }
        this.chance = chance;
    }

    public int chance() {
        return chance;
    }

    @Override
    public Stream<BlockPos> getPositions(
            PlacementContext context, RandomSource random, BlockPos origin) {
        return candidateOrigin(
                random,
                context.getLevel().getSeed(),
                origin,
                currentGenerationSalt(),
                chance).stream();
    }

    static java.util.Optional<BlockPos> candidateOrigin(
            RandomSource legacyRandom,
            long worldSeed,
            BlockPos origin,
            long generationSalt,
            int chance) {
        RandomSource random = placementRandom(
                legacyRandom, worldSeed, new ChunkPos(origin), generationSalt);
        if (!(random.nextFloat() < 1.0F / (float) chance)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(origin.offset(random.nextInt(16), 0, random.nextInt(16)));
    }

    static RandomSource placementRandom(
            RandomSource legacyRandom, long worldSeed, ChunkPos chunkPos, long generationSalt) {
        if (generationSalt == 0L) {
            return legacyRandom;
        }
        return RandomSource.create(GenerationSeedMixer.landmarkPlacementSeed(
                worldSeed, chunkPos.toLong(), generationSalt));
    }

    private static long currentGenerationSalt() {
        try {
            return DelvefoldConfigService.get().snapshot().settings().generationSalt();
        } catch (IllegalStateException ignored) {
            return 0L;
        }
    }

    @Override
    public PlacementModifierType<?> type() {
        return DelvefoldWorldgen.GENERATION_SALTED_LANDMARK_PLACEMENT.get();
    }
}
