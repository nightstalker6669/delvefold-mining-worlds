package com.nightsta69.delvefold.world.feature;

import com.mojang.logging.LogUtils;
import com.nightsta69.delvefold.config.DelvefoldConfigService;
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
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;
import org.slf4j.Logger;

/** Places every enabled entry in the mining ore table with an ID-stable random stream. */
public final class MiningOreFeature extends Feature<MiningOreConfiguration> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<ResourceLocation> WARNED_MISSING_BLOCKS = ConcurrentHashMap.newKeySet();
    private static final long CHUNK_SALT = 0x9E3779B97F4A7C15L;
    private static final long ORE_SALT = 0xD1B54A32D192ED03L;
    private static final long COUNT_SALT = 0x94D049BB133111EBL;
    private static final long ATTEMPT_SALT = 0xBF58476D1CE4E5B9L;
    private volatile RuntimeOreProfile runtimeProfile;

    public MiningOreFeature() {
        super(MiningOreConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<MiningOreConfiguration> context) {
        ChunkPos chunkPos = new ChunkPos(context.origin());
        OreProfileDocument document = currentOreDocument();
        TerrainMode terrainMode = terrainMode(context);
        if (document != null && terrainMode != null) {
            return placeRuntimeProfile(context, chunkPos, document, terrainMode);
        }
        return placeResourceFallback(context, chunkPos);
    }

    private boolean placeRuntimeProfile(
            FeaturePlaceContext<MiningOreConfiguration> context,
            ChunkPos chunkPos,
            OreProfileDocument document,
            TerrainMode terrainMode) {
        RuntimeOreProfile profile = profileFor(document);
        Holder<Biome> biome = context.level().getBiome(context.origin());
        boolean placedAny = false;

        for (CompiledBand band : profile.bands(terrainMode, biome)) {
            long bandSeed = seedFor(context.level().getSeed(), chunkPos, band.salt());
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
                        chunkPos.getMinBlockX() + random.nextInt(16),
                        y,
                        chunkPos.getMinBlockZ() + random.nextInt(16));
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

    private static boolean placeResourceFallback(
            FeaturePlaceContext<MiningOreConfiguration> context, ChunkPos chunkPos) {
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

            long definitionSeed = seedFor(context.level().getSeed(), chunkPos, definition.id());
            OreConfiguration ore = new OreConfiguration(
                    targets, definition.veinSize(), definition.discardChanceOnAirExposure());

            for (int attempt = 0; attempt < definition.veinsPerChunk(); attempt++) {
                RandomSource random = randomForAttempt(definitionSeed, attempt);
                BlockPos origin = new BlockPos(
                        chunkPos.getMinBlockX() + random.nextInt(16),
                        definition.pickY(random, minY, maxY),
                        chunkPos.getMinBlockZ() + random.nextInt(16));
                placedAny |= Feature.ORE.place(new FeaturePlaceContext<>(
                        java.util.Optional.empty(),
                        context.level(),
                        context.chunkGenerator(),
                        random,
                        origin,
                        ore));
            }
        }

        return placedAny;
    }

    private RuntimeOreProfile profileFor(OreProfileDocument document) {
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

    private static OreProfileDocument currentOreDocument() {
        try {
            return DelvefoldConfigService.get().snapshot().ores();
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    private static TerrainMode terrainMode(FeaturePlaceContext<MiningOreConfiguration> context) {
        return DelvefoldWorldgen.terrainFor(context.level().getLevel().dimension());
    }

    private static List<OreConfiguration.TargetBlockState> resolveTargets(OreDefinition definition) {
        List<OreConfiguration.TargetBlockState> resolved = new ArrayList<>(definition.targets().size());
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
            resolved.add(OreConfiguration.target(
                    new TagMatchTest(target.replaceable()), output.defaultBlockState()));
        }
        return resolved;
    }

    private static long seedFor(long worldSeed, ChunkPos chunkPos, ResourceLocation oreId) {
        return seedFor(worldSeed, chunkPos, oreId.toString());
    }

    private static long seedFor(long worldSeed, ChunkPos chunkPos, String salt) {
        long seed = worldSeed ^ chunkPos.toLong() * CHUNK_SALT;
        seed ^= stableHash64(salt) * ORE_SALT;
        return mix64(seed);
    }

    private static RandomSource randomForAttempt(long definitionSeed, int attempt) {
        return RandomSource.create(mix64(definitionSeed ^ (attempt + 1L) * ATTEMPT_SALT));
    }

    private static long stableHash64(String value) {
        long hash = 0xCBF29CE484222325L;
        for (int index = 0; index < value.length(); index++) {
            hash ^= value.charAt(index);
            hash *= 0x100000001B3L;
        }
        return hash;
    }

    private static long mix64(long value) {
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }
}
