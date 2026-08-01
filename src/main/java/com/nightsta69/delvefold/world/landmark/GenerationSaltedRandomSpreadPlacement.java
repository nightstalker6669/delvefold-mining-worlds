package com.nightsta69.delvefold.world.landmark;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.nightsta69.delvefold.config.DelvefoldConfigService;
import java.util.Optional;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

/** Vanilla random-spread spacing whose candidate grid rotates with Delvefold's persisted generation salt. */
public final class GenerationSaltedRandomSpreadPlacement extends RandomSpreadStructurePlacement {
    public static final MapCodec<GenerationSaltedRandomSpreadPlacement> CODEC =
            RecordCodecBuilder.<GenerationSaltedRandomSpreadPlacement>mapCodec(instance ->
                    placementCodec(instance).and(instance.group(
                            Codec.intRange(1, 4096).fieldOf("spacing")
                                    .forGetter(GenerationSaltedRandomSpreadPlacement::spacing),
                            Codec.intRange(0, 4096).fieldOf("separation")
                                    .forGetter(GenerationSaltedRandomSpreadPlacement::separation),
                            RandomSpreadType.CODEC.optionalFieldOf("spread_type", RandomSpreadType.LINEAR)
                                    .forGetter(GenerationSaltedRandomSpreadPlacement::spreadType)
                    )).apply(instance, GenerationSaltedRandomSpreadPlacement::new))
                    .validate(GenerationSaltedRandomSpreadPlacement::validate);

    public GenerationSaltedRandomSpreadPlacement(
            Vec3i locateOffset,
            StructurePlacement.FrequencyReductionMethod frequencyReductionMethod,
            float frequency,
            int salt,
            Optional<StructurePlacement.ExclusionZone> exclusionZone,
            int spacing,
            int separation,
            RandomSpreadType spreadType) {
        super(locateOffset, frequencyReductionMethod, frequency, salt, exclusionZone,
                spacing, separation, spreadType);
    }

    private static DataResult<GenerationSaltedRandomSpreadPlacement> validate(
            GenerationSaltedRandomSpreadPlacement placement) {
        return placement.spacing() <= placement.separation()
                ? DataResult.error(() -> "Spacing must be larger than separation")
                : DataResult.success(placement);
    }

    @Override
    protected boolean isPlacementChunk(ChunkGeneratorStructureState structureState, int x, int z) {
        ChunkPos candidate = potentialStructureChunk(
                structureState.getLevelSeed(), currentGenerationSalt(), x, z);
        return candidate.x == x && candidate.z == z;
    }

    /** Deterministic seam used to verify spacing without generating a brittle area of chunks. */
    ChunkPos potentialStructureChunk(long worldSeed, long generationSalt, int x, int z) {
        return getPotentialStructureChunk(effectiveWorldSeed(worldSeed, generationSalt), x, z);
    }

    public static long effectiveWorldSeed(long worldSeed, long generationSalt) {
        return LandmarkSeeds.placementWorldSeed(worldSeed, generationSalt);
    }

    private static long currentGenerationSalt() {
        try {
            return DelvefoldConfigService.get().snapshot().settings().generationSalt();
        } catch (IllegalStateException ignored) {
            return 0L;
        }
    }

    @Override
    public StructurePlacementType<?> type() {
        return LandmarkRegistries.GENERATION_SALTED_RANDOM_SPREAD.get();
    }
}
