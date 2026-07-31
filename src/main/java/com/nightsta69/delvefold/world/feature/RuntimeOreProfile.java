package com.nightsta69.delvefold.world.feature;

import com.mojang.logging.LogUtils;
import com.nightsta69.delvefold.config.model.BiomeFilter;
import com.nightsta69.delvefold.config.model.HeightDistribution;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.model.OreRule;
import com.nightsta69.delvefold.config.model.OreTarget;
import com.nightsta69.delvefold.config.model.SpawnBand;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.world.DelvefoldWorldgen;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntToDoubleFunction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;
import org.slf4j.Logger;

/** Immutable, worldgen-thread-safe compilation of the editable ore profile. */
final class RuntimeOreProfile {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> WARNED_TARGETS = ConcurrentHashMap.newKeySet();
    private static final String MINING_BIOMES_SELECTOR = "#delvefold:mining_biomes";

    private final OreProfileDocument source;
    private final List<CompiledRule> rules;
    private final Map<SelectionKey, List<CompiledBand>> selectionCache = new ConcurrentHashMap<>();

    private RuntimeOreProfile(OreProfileDocument source, List<CompiledRule> rules) {
        this.source = source;
        this.rules = List.copyOf(rules);
    }

    static RuntimeOreProfile compile(OreProfileDocument source) {
        List<CompiledRule> rules = new ArrayList<>();
        for (OreRule rule : source.rules()) {
            if (!rule.enabled()) {
                continue;
            }

            List<OreConfiguration.TargetBlockState> targets = compileTargets(rule);
            if (targets.isEmpty()) {
                continue;
            }

            List<CompiledBand> bands = new ArrayList<>(rule.bands().size());
            for (SpawnBand band : rule.bands()) {
                if (band.attemptsPerChunk() <= 0.0D || band.minY() > band.maxY()) {
                    continue;
                }
                OreConfiguration ore = new OreConfiguration(
                        targets, band.veinSize(), (float) band.discardOnAirExposure());
                bands.add(new CompiledBand(
                        rule.id() + '/' + band.id(),
                        ore,
                        band.attemptsPerChunk(),
                        compileHeight(band)));
            }
            if (!bands.isEmpty()) {
                rules.add(new CompiledRule(rule.terrainModes(), rule.biomes(), bands));
            }
        }
        return new RuntimeOreProfile(source, rules);
    }

    OreProfileDocument source() {
        return source;
    }

    List<CompiledBand> bands(TerrainMode terrainMode, Holder<Biome> biome) {
        Optional<ResourceKey<Biome>> biomeKey = biome.unwrapKey();
        if (biomeKey.isPresent()) {
            SelectionKey key = new SelectionKey(terrainMode, biomeKey.get());
            return selectionCache.computeIfAbsent(key, ignored -> selectBands(terrainMode, biome));
        }
        return selectBands(terrainMode, biome);
    }

    private List<CompiledBand> selectBands(TerrainMode terrainMode, Holder<Biome> biome) {
        List<CompiledBand> result = new ArrayList<>();
        for (CompiledRule rule : rules) {
            if (rule.terrainModes().contains(terrainMode) && matches(rule.biomes(), biome)) {
                result.addAll(rule.bands());
            }
        }
        return List.copyOf(result);
    }

    private static List<OreConfiguration.TargetBlockState> compileTargets(OreRule rule) {
        List<OreConfiguration.TargetBlockState> targets = new ArrayList<>();
        for (OreTarget target : rule.targets()) {
            ResourceLocation tagId = ResourceLocation.tryParse(stripHash(target.replaceTag()));
            if (tagId == null) {
                warnOnce(
                        rule.id() + '|' + target.sourceId() + '|' + target.replaceTag(),
                        "Skipping ore rule {} target {} because its replacement tag ID is unavailable",
                        rule.id(),
                        target.sourceId());
                continue;
            }
            TagKey<Block> replaceable = TagKey.create(Registries.BLOCK, tagId);
            for (Map.Entry<ResourceLocation, Block> output : outputBlocks(target)) {
                BlockState state = applyProperties(
                        rule.id(), output.getKey(), output.getValue().defaultBlockState(), target.state());
                targets.add(OreConfiguration.target(new TagMatchTest(replaceable), state));
            }
        }
        return targets;
    }

    private static List<Map.Entry<ResourceLocation, Block>> outputBlocks(OreTarget target) {
        if (!target.tagDriven()) {
            ResourceLocation id = ResourceLocation.tryParse(target.block());
            Block block = id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
            if (block == null) {
                return List.of();
            }
            return List.of(Map.entry(id, block));
        }
        ResourceLocation id = ResourceLocation.tryParse(target.blockTag());
        if (id == null) {
            return List.of();
        }
        TagKey<Block> tag = TagKey.create(Registries.BLOCK, id);
        return BuiltInRegistries.BLOCK.getTag(tag).stream()
                .flatMap(holders -> holders.stream())
                .map(holder -> Map.entry(BuiltInRegistries.BLOCK.getKey(holder.value()), holder.value()))
                .sorted(Map.Entry.comparingByKey())
                .toList();
    }

    private static BlockState applyProperties(
            String ruleId,
            ResourceLocation blockId,
            BlockState state,
            Map<String, String> properties) {
        BlockState result = state;
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            Property<?> property = result.getBlock().getStateDefinition().getProperty(entry.getKey());
            if (property == null) {
                warnOnce(
                        ruleId + '|' + blockId + '|' + entry.getKey(),
                        "Ignoring unknown block-state property {} for {} in ore rule {}",
                        entry.getKey(),
                        blockId,
                        ruleId);
                continue;
            }
            Optional<BlockState> updated = setProperty(result, property, entry.getValue());
            if (updated.isEmpty()) {
                warnOnce(
                        ruleId + '|' + blockId + '|' + entry.getKey() + '=' + entry.getValue(),
                        "Ignoring invalid value {} for property {} on {} in ore rule {}",
                        entry.getValue(),
                        entry.getKey(),
                        blockId,
                        ruleId);
            } else {
                result = updated.get();
            }
        }
        return result;
    }

    private static <T extends Comparable<T>> Optional<BlockState> setProperty(
            BlockState state, Property<T> property, String serializedValue) {
        return property.getValue(serializedValue)
                .map(value -> state.setValue(property, value));
    }

    private static HeightSampler compileHeight(SpawnBand band) {
        if (band.distribution() == HeightDistribution.TRIANGLE && band.peakY() != null) {
            int peak = band.peakY();
            return weighted(band.minY(), band.maxY(), y -> y <= peak
                    ? (double) (y - band.minY() + 1) / (peak - band.minY() + 1)
                    : (double) (band.maxY() - y + 1) / (band.maxY() - peak + 1));
        }
        if (band.distribution() == HeightDistribution.TRAPEZOID
                && band.plateauMinY() != null
                && band.plateauMaxY() != null) {
            int plateauMin = band.plateauMinY();
            int plateauMax = band.plateauMaxY();
            return weighted(band.minY(), band.maxY(), y -> {
                if (y < plateauMin) {
                    return (double) (y - band.minY() + 1) / (plateauMin - band.minY() + 1);
                }
                if (y > plateauMax) {
                    return (double) (band.maxY() - y + 1) / (band.maxY() - plateauMax + 1);
                }
                return 1.0D;
            });
        }
        return random -> random.nextIntBetweenInclusive(band.minY(), band.maxY());
    }

    private static HeightSampler weighted(int minY, int maxY, IntToDoubleFunction weightFunction) {
        double[] cumulativeWeights = new double[maxY - minY + 1];
        double total = 0.0D;
        for (int i = 0; i < cumulativeWeights.length; i++) {
            total += Math.max(0.0D, weightFunction.applyAsDouble(minY + i));
            cumulativeWeights[i] = total;
        }
        double totalWeight = total;
        return random -> {
            double selected = random.nextDouble() * totalWeight;
            int index = Arrays.binarySearch(cumulativeWeights, selected);
            if (index < 0) {
                index = -index - 1;
            }
            return minY + Math.min(index, cumulativeWeights.length - 1);
        };
    }

    private static boolean matches(BiomeFilter filter, Holder<Biome> biome) {
        boolean included = filter.include().isEmpty()
                || filter.include().stream().anyMatch(selector -> matches(selector, biome));
        boolean excluded = filter.exclude().stream().anyMatch(selector -> matches(selector, biome));
        return included && !excluded;
    }

    private static boolean matches(String selector, Holder<Biome> biome) {
        if (MINING_BIOMES_SELECTOR.equals(selector) && isDelvefoldMiningBiome(biome)) {
            return true;
        }

        boolean tag = selector.startsWith("#");
        ResourceLocation id = ResourceLocation.tryParse(tag ? selector.substring(1) : selector);
        if (id == null) {
            return false;
        }
        if (tag) {
            return biome.is(TagKey.create(Registries.BIOME, id));
        }
        return biome.is(ResourceKey.create(Registries.BIOME, id));
    }

    private static boolean isDelvefoldMiningBiome(Holder<Biome> biome) {
        return biome.is(DelvefoldWorldgen.MINING_FLAT_BIOME)
                || biome.is(DelvefoldWorldgen.MINING_CAVERN_BIOME)
                || biome.is(DelvefoldWorldgen.MINING_WILD_BIOME);
    }

    private static String stripHash(String value) {
        return value.startsWith("#") ? value.substring(1) : value;
    }

    private static void warnOnce(String key, String message, Object... arguments) {
        if (WARNED_TARGETS.add(key)) {
            LOGGER.warn(message, arguments);
        }
    }

    record CompiledRule(Set<TerrainMode> terrainModes, BiomeFilter biomes, List<CompiledBand> bands) {
        CompiledRule {
            terrainModes = Set.copyOf(terrainModes);
            bands = List.copyOf(bands);
        }
    }

    record CompiledBand(String salt, OreConfiguration ore, double attemptsPerChunk, HeightSampler height) {
    }

    private record SelectionKey(TerrainMode terrainMode, ResourceKey<Biome> biome) {
    }

    @FunctionalInterface
    interface HeightSampler {
        int sample(RandomSource random);
    }
}
