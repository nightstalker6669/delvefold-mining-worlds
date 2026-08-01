package com.nightsta69.delvefold.world.landmark;

import com.mojang.serialization.MapCodec;
import com.nightsta69.delvefold.config.DelvefoldConfigService;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.WorldIdentitySettings;
import com.nightsta69.delvefold.world.DelvefoldWorldgen;
import com.nightsta69.delvefold.world.landmark.catalog.LandmarkCatalogService;
import com.nightsta69.delvefold.world.landmark.catalog.LandmarkCatalogSnapshot;
import com.nightsta69.delvefold.world.landmark.catalog.LandmarkDefinition;
import com.nightsta69.delvefold.world.landmark.catalog.LandmarkPlacementStyle;
import com.nightsta69.delvefold.world.landmark.catalog.LandmarkSelector;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** One structure-system entry that selects a reloadable landmark definition per deterministic candidate. */
public final class DelvefoldLandmarkStructure extends Structure {
    public static final MapCodec<DelvefoldLandmarkStructure> CODEC =
            Structure.simpleCodec(DelvefoldLandmarkStructure::new);

    public DelvefoldLandmarkStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        var snapshot = currentConfig();
        if (snapshot == null
                || !snapshot.settings().initialized()
                || snapshot.settings().terrainMode() == null) {
            return Optional.empty();
        }
        TerrainMode terrain = snapshot.settings().terrainMode();
        ChunkPos chunk = context.chunkPos();
        Holder<Biome> generatorBiome = context.biomeSource()
                .getNoiseBiome(
                        QuartPos.fromBlock(chunk.getMiddleBlockX()),
                        0,
                        QuartPos.fromBlock(chunk.getMiddleBlockZ()),
                        context.randomState().sampler());
        TerrainMode generatedTerrain = DelvefoldWorldgen.terrainFor(generatorBiome);
        if (generatedTerrain == null || generatedTerrain != terrain) {
            return Optional.empty();
        }
        WorldIdentitySettings identity = snapshot.settings().identity();
        long generationSalt = snapshot.settings().generationSalt();
        if (!LandmarkSelector.accepts(
                identity.landmarkPreset(), LandmarkSeeds.acceptanceSeed(context.seed(), chunk, generationSalt))) {
            return Optional.empty();
        }

        // A datapack reload may publish while worker threads are finding structure starts. Capture one
        // immutable revision and never pair candidates from one catalog with definitions from another.
        LandmarkCatalogSnapshot catalog = LandmarkCatalogService.get().snapshot();
        LandmarkPlacementProbeCache probeCache = new LandmarkPlacementProbeCache();
        Optional<LandmarkGenerationPlanner.Selection<PlacementCandidate>> selected = LandmarkGenerationPlanner.select(
                catalog,
                terrain,
                identity,
                definition -> candidate(context, definition, generationSalt, probeCache),
                LandmarkSeeds.selectionSeed(context.seed(), chunk, generationSalt));
        if (selected.isEmpty()) {
            return Optional.empty();
        }
        LandmarkDefinition definition = selected.get().definition();
        PlacementCandidate candidate = selected.get().candidate();
        BlockPos biomeCheckPosition =
                new BlockPos(chunk.getMiddleBlockX(), candidate.position().getY(), chunk.getMiddleBlockZ());
        return Optional.of(new GenerationStub(
                biomeCheckPosition,
                pieces -> pieces.addPiece(new LandmarkTemplatePiece(
                        context.structureTemplateManager(),
                        context.registryAccess(),
                        definition,
                        candidate.position(),
                        candidate.rotation(),
                        candidate.contentSeed()))));
    }

    private static Optional<PlacementCandidate> candidate(
            GenerationContext context,
            LandmarkDefinition definition,
            long generationSalt,
            LandmarkPlacementProbeCache probeCache) {
        var template = context.structureTemplateManager().get(definition.template());
        if (template.isEmpty()) {
            return Optional.empty();
        }
        ChunkPos chunk = context.chunkPos();
        long contentSeed = LandmarkSeeds.definitionSeed(context.seed(), chunk, generationSalt, definition.id());
        RandomSource random = RandomSource.create(contentSeed);
        Rotation rotation = Rotation.getRandom(random);
        Vec3i size = template.get().getSize(rotation);
        if (size.getX() <= 0
                || size.getZ() <= 0
                || size.getX() > LandmarkDefinition.MAX_TEMPLATE_HORIZONTAL_SPAN
                || size.getZ() > LandmarkDefinition.MAX_TEMPLATE_HORIZONTAL_SPAN) {
            return Optional.empty();
        }
        int centerX = chunk.getMiddleBlockX();
        int centerZ = chunk.getMiddleBlockZ();
        int y = probeCache.resolve(definition, () -> resolveY(context, definition, random, centerX, centerZ));
        if (y < context.heightAccessor().getMinBuildHeight()
                || y + size.getY() >= context.heightAccessor().getMaxBuildHeight()) {
            return Optional.empty();
        }
        BlockPos position = centeredOrigin(template.get(), rotation, centerX, y, centerZ);
        Holder<Biome> biome = context.biomeSource()
                .getNoiseBiome(
                        QuartPos.fromBlock(centerX),
                        QuartPos.fromBlock(y),
                        QuartPos.fromBlock(centerZ),
                        context.randomState().sampler());
        if (!LandmarkBiomeMatcher.matches(definition.biomes(), biome)) {
            return Optional.empty();
        }
        return Optional.of(new PlacementCandidate(position, rotation, contentSeed));
    }

    static BlockPos centeredOrigin(
            StructureTemplate template, Rotation rotation, int centerX, int minimumY, int centerZ) {
        Vec3i rotatedSize = template.getSize(rotation);
        BlockPos desiredBoundsMinimum =
                new BlockPos(centerX - rotatedSize.getX() / 2, minimumY, centerZ - rotatedSize.getZ() / 2);
        return template.getZeroPositionWithTransform(desiredBoundsMinimum, Mirror.NONE, rotation);
    }

    private static int resolveY(
            GenerationContext context, LandmarkDefinition definition, RandomSource random, int x, int z) {
        int minimum = Math.max(definition.minY(), context.heightAccessor().getMinBuildHeight() + 1);
        int maximum = Math.min(definition.maxY(), context.heightAccessor().getMaxBuildHeight() - 2);
        if (minimum > maximum) {
            return Integer.MIN_VALUE;
        }
        if (definition.placementStyle() == LandmarkPlacementStyle.SURFACE) {
            int surface = context.chunkGenerator()
                    .getFirstOccupiedHeight(
                            x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
            return surface >= minimum && surface <= maximum ? surface : Integer.MIN_VALUE;
        }
        if (definition.placementStyle() == LandmarkPlacementStyle.BURIED) {
            return minimum + random.nextInt(maximum - minimum + 1);
        }
        NoiseColumn column =
                context.chunkGenerator().getBaseColumn(x, z, context.heightAccessor(), context.randomState());
        for (int y = maximum; y >= minimum; y--) {
            if (!column.getBlock(y).isAir()
                    && column.getBlock(y + 1).isAir()
                    && column.getBlock(y + 2).isAir()
                    && column.getBlock(y + 3).isAir()) {
                return y + 1;
            }
        }
        return Integer.MIN_VALUE;
    }

    private static com.nightsta69.delvefold.config.ConfigSnapshot currentConfig() {
        try {
            return DelvefoldConfigService.get().snapshot();
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    @Override
    public StructureType<?> type() {
        return LandmarkRegistries.LANDMARK_STRUCTURE.get();
    }

    private record PlacementCandidate(BlockPos position, Rotation rotation, long contentSeed) {}
}
