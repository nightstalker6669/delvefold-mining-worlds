package com.nightsta69.delvefold.world.feature;

import com.mojang.logging.LogUtils;
import com.nightsta69.delvefold.config.ConfigSnapshot;
import com.nightsta69.delvefold.config.DelvefoldConfigService;
import com.nightsta69.delvefold.config.model.OreBandPlacement;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.world.DelvefoldWorldgen;
import com.nightsta69.delvefold.world.feature.MiningOreConfiguration.OreDefinition;
import com.nightsta69.delvefold.world.feature.MiningOreConfiguration.OreTarget;
import com.nightsta69.delvefold.world.feature.RuntimeOreProfile.CompiledBand;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;
import org.slf4j.Logger;

/** Places every enabled entry in the mining ore table with an ID-stable random stream. */
public final class MiningOreFeature extends Feature<MiningOreConfiguration> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<ResourceLocation> WARNED_MISSING_BLOCKS = ConcurrentHashMap.newKeySet();
    private static final long COUNT_SALT = 0x94D049BB133111EBL;
    private static final long ATTEMPT_SALT = 0xBF58476D1CE4E5B9L;
    private volatile RuntimeOreProfile runtimeProfile;

    public MiningOreFeature() {
        super(MiningOreConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<MiningOreConfiguration> context) {
        ChunkPos chunkPos = new ChunkPos(context.origin());
        ConfigSnapshot snapshot = currentSnapshot();
        OreProfileDocument document = snapshot == null ? null : snapshot.ores();
        long generationSalt = snapshot == null ? 0L : snapshot.settings().generationSalt();
        TerrainMode terrainMode = terrainMode(context);
        if (document != null && terrainMode != null) {
            return placeRuntimeProfile(context, chunkPos, document, terrainMode, generationSalt);
        }
        return placeResourceFallback(context, chunkPos, generationSalt);
    }

    private boolean placeRuntimeProfile(
            FeaturePlaceContext<MiningOreConfiguration> context,
            ChunkPos chunkPos,
            OreProfileDocument document,
            TerrainMode terrainMode,
            long generationSalt) {
        RuntimeOreProfile profile = profileFor(document);
        Holder<Biome> biome = context.level().getBiome(context.origin());
        boolean placedAny = false;

        for (CompiledBand band : profile.bands(terrainMode, biome)) {
            if (band.placement() == OreBandPlacement.PROVINCE) {
                placedAny |= placeProvinceBand(
                        context.level(), chunkPos, band, context.level().getSeed(), generationSalt);
                continue;
            }
            long bandSeed = seedFor(context.level().getSeed(), chunkPos, band.salt(), generationSalt);
            RandomSource countRandom = RandomSource.create(mix64(bandSeed ^ COUNT_SALT));
            int attempts = (int) Math.floor(band.attemptsPerChunk());
            double fractionalAttempt = band.attemptsPerChunk() - attempts;
            if (fractionalAttempt > 0.0D && countRandom.nextDouble() < fractionalAttempt) {
                attempts++;
            }

            for (int attempt = 0; attempt < attempts; attempt++) {
                RandomSource random = randomForAttempt(bandSeed, attempt);
                int y = band.height().sample(random);
                if (context.level().isOutsideBuildHeight(y)) {
                    continue;
                }
                BlockPos origin = new BlockPos(
                        chunkPos.getMinBlockX() + random.nextInt(16), y, chunkPos.getMinBlockZ() + random.nextInt(16));
                placedAny |= Feature.ORE.place(new FeaturePlaceContext<>(
                        java.util.Optional.empty(),
                        context.level(),
                        context.chunkGenerator(),
                        random,
                        origin,
                        band.ore(random)));
            }
        }

        return placedAny;
    }

    static boolean placeProvinceBand(
            WorldGenLevel level, ChunkPos chunkPos, CompiledBand band, long worldSeed, long generationSalt) {
        ProvincePlacementPlanner.Plan plan = ProvincePlacementPlanner.plan(
                worldSeed,
                generationSalt,
                band.salt(),
                band.sourceBand(),
                chunkPos.x,
                chunkPos.z,
                level.getMinBuildHeight(),
                level.getMaxBuildHeight());
        return applyProvincePlan(level, chunkPos, band, plan);
    }

    static boolean applyProvincePlan(
            WorldGenLevel level, ChunkPos chunkPos, CompiledBand band, ProvincePlacementPlanner.Plan plan) {
        boolean placedAny = false;
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        int chunkMinX = chunkPos.getMinBlockX();
        int chunkMinZ = chunkPos.getMinBlockZ();
        int chunkMaxX = chunkPos.getMaxBlockX();
        int chunkMaxZ = chunkPos.getMaxBlockZ();

        for (ProvincePlacementPlanner.ProvinceSlice province : plan.provinces()) {
            OreConfiguration ore = band.ore(RandomSource.create(province.outputSeed()));
            for (ProvincePlacementPlanner.Candidate candidate : province.candidates()) {
                // The planner owns this invariant; retain the guard at the mutation boundary too.
                if (candidate.x() < chunkMinX
                        || candidate.x() > chunkMaxX
                        || candidate.z() < chunkMinZ
                        || candidate.z() > chunkMaxZ
                        || level.isOutsideBuildHeight(candidate.y())) {
                    continue;
                }
                position.set(candidate.x(), candidate.y(), candidate.z());
                if (!level.ensureCanWrite(position)) {
                    continue;
                }
                var existing = level.getBlockState(position);
                RandomSource random = RandomSource.create(candidate.placementSeed());
                for (OreConfiguration.TargetBlockState target : ore.targetStates) {
                    if (OreFeature.canPlaceOre(existing, level::getBlockState, random, ore, target, position)) {
                        placedAny |= level.setBlock(position, target.state, 2);
                        break;
                    }
                }
            }
        }
        return placedAny;
    }

    private static boolean placeResourceFallback(
            FeaturePlaceContext<MiningOreConfiguration> context, ChunkPos chunkPos, long generationSalt) {
        boolean placedAny = false;

        for (OreDefinition definition : context.config().ores()) {
            if (definition.veinsPerChunk() == 0 || definition.minY() > definition.maxY()) {
                continue;
            }

            List<OreConfiguration.TargetBlockState> targets = resolveTargets(definition);
            if (targets.isEmpty()) {
                continue;
            }

            int minY = Math.max(definition.minY(), context.level().getMinBuildHeight());
            int maxY = Math.min(definition.maxY(), context.level().getMaxBuildHeight() - 1);
            if (minY > maxY) {
                continue;
            }

            long definitionSeed = seedFor(context.level().getSeed(), chunkPos, definition.id(), generationSalt);
            OreConfiguration ore =
                    new OreConfiguration(targets, definition.veinSize(), definition.discardChanceOnAirExposure());

            for (int attempt = 0; attempt < definition.veinsPerChunk(); attempt++) {
                RandomSource random = randomForAttempt(definitionSeed, attempt);
                BlockPos origin = new BlockPos(
                        chunkPos.getMinBlockX() + random.nextInt(16),
                        definition.pickY(random, minY, maxY),
                        chunkPos.getMinBlockZ() + random.nextInt(16));
                placedAny |= Feature.ORE.place(new FeaturePlaceContext<>(
                        java.util.Optional.empty(), context.level(), context.chunkGenerator(), random, origin, ore));
            }
        }

        return placedAny;
    }

    RuntimeOreProfile profileFor(OreProfileDocument document) {
        RuntimeOreProfile current = runtimeProfile;
        if (current != null && current.source().equals(document)) {
            return current;
        }

        synchronized (this) {
            current = runtimeProfile;
            if (current != null && current.source().equals(document)) {
                return current;
            }

            RuntimeOreProfile compiled = RuntimeOreProfile.compile(document);
            OreProfileDocument latest = currentOreDocument();
            if (latest != null && latest.equals(document)) {
                runtimeProfile = compiled;
            }
            return compiled;
        }
    }

    /** Clears tag- and registry-derived output choices atomically with profile compilation. */
    public synchronized void invalidateRuntimeProfile() {
        runtimeProfile = null;
    }

    private static OreProfileDocument currentOreDocument() {
        ConfigSnapshot snapshot = currentSnapshot();
        return snapshot == null ? null : snapshot.ores();
    }

    private static ConfigSnapshot currentSnapshot() {
        try {
            return DelvefoldConfigService.get().snapshot();
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    private static TerrainMode terrainMode(FeaturePlaceContext<MiningOreConfiguration> context) {
        return DelvefoldWorldgen.terrainFor(context.level().getLevel().dimension());
    }

    private static List<OreConfiguration.TargetBlockState> resolveTargets(OreDefinition definition) {
        List<OreConfiguration.TargetBlockState> resolved =
                new ArrayList<>(definition.targets().size());
        for (OreTarget target : definition.targets()) {
            Block output = BuiltInRegistries.BLOCK.getOptional(target.block()).orElse(null);
            if (output == null) {
                if (WARNED_MISSING_BLOCKS.add(target.block())) {
                    LOGGER.warn(
                            "Skipping Delvefold ore {} because output block {} is not registered",
                            definition.id(),
                            target.block());
                }
                continue;
            }
            resolved.add(OreConfiguration.target(new TagMatchTest(target.replaceable()), output.defaultBlockState()));
        }
        return resolved;
    }

    static long seedFor(long worldSeed, ChunkPos chunkPos, ResourceLocation oreId) {
        return seedFor(worldSeed, chunkPos, oreId.toString());
    }

    static long seedFor(long worldSeed, ChunkPos chunkPos, String salt) {
        return GenerationSeedMixer.oreSeed(worldSeed, chunkPos.toLong(), salt, 0L);
    }

    static long seedFor(long worldSeed, ChunkPos chunkPos, ResourceLocation oreId, long generationSalt) {
        return seedFor(worldSeed, chunkPos, oreId.toString(), generationSalt);
    }

    static long seedFor(long worldSeed, ChunkPos chunkPos, String salt, long generationSalt) {
        if (generationSalt == 0L) {
            // Compatibility path: retain the exact pre-renewal arithmetic and call path.
            return seedFor(worldSeed, chunkPos, salt);
        }
        return GenerationSeedMixer.oreSeed(worldSeed, chunkPos.toLong(), salt, generationSalt);
    }

    private static RandomSource randomForAttempt(long definitionSeed, int attempt) {
        return RandomSource.create(mix64(definitionSeed ^ (attempt + 1L) * ATTEMPT_SALT));
    }

    private static long mix64(long value) {
        return GenerationSeedMixer.mix64(value);
    }
}
