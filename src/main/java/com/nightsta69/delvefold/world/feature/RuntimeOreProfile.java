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
import java.util.LinkedHashMap;
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

            List<CompiledTargetGroup> targetGroups = compileTargetGroups(rule);
            if (targetGroups.isEmpty()) {
                continue;
            }

            List<CompiledBand> bands = new ArrayList<>(rule.bands().size());
            for (SpawnBand band : rule.bands()) {
                if (band.attemptsPerChunk() <= 0.0D || band.minY() > band.maxY()) {
                    continue;
                }
                bands.add(new CompiledBand(
                        rule.id() + '/' + band.id(),
                        targetGroups,
                        band.veinSize(),
                        (float) band.discardOnAirExposure(),
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

    private static List<CompiledTargetGroup> compileTargetGroups(OreRule rule) {
        Map<TagKey<Block>, MutableTargetGroup> grouped = new LinkedHashMap<>();
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
            if (target.weight() < OreTarget.MIN_WEIGHT || target.weight() > OreTarget.MAX_WEIGHT) {
                warnOnce(
                        rule.id() + '|' + target.sourceId() + "|weight=" + target.weight(),
                        "Skipping ore rule {} target {} because its weight {} is outside {}..{}",
                        rule.id(),
                        target.sourceId(),
                        target.weight(),
                        OreTarget.MIN_WEIGHT,
                        OreTarget.MAX_WEIGHT);
                continue;
            }
            TagKey<Block> replaceable = TagKey.create(Registries.BLOCK, tagId);
            List<Map.Entry<ResourceLocation, Block>> outputs = outputBlocks(target);
            if (outputs.isEmpty()) {
                warnOnce(
                        rule.id() + '|' + target.sourceId() + "|empty",
                        "Skipping ore rule {} target {} because it resolves to no installed output blocks",
                        rule.id(),
                        target.sourceId());
                continue;
            }
            MutableTargetGroup group = grouped.computeIfAbsent(replaceable, ignored -> new MutableTargetGroup());
            double memberWeight = target.tagDriven()
                    ? (double) target.weight() / outputs.size()
                    : target.weight();
            int distinctOutputs = 0;
            for (Map.Entry<ResourceLocation, Block> output : outputs) {
                BlockState state = applyProperties(
                        rule.id(), output.getKey(), output.getValue().defaultBlockState(), target.state());
                if (group.add(state, memberWeight, target.weight() == OreTarget.DEFAULT_WEIGHT)) {
                    distinctOutputs++;
                } else {
                    warnOnce(
                            rule.id() + '|' + replaceable.location() + '|' + state,
                            "Deduplicating overlapping output {} for host tag {} in ore rule {}",
                            output.getKey(),
                            replaceable.location(),
                            rule.id());
                }
            }
            if (distinctOutputs == 0) {
                warnOnce(
                        rule.id() + '|' + replaceable.location() + '|' + target.sourceId() + "|shadowed",
                        "Ore rule {} target {} is ineffective because every resolved state overlaps an earlier target for host tag {}",
                        rule.id(),
                        target.sourceId(),
                        replaceable.location());
            }
        }
        return grouped.entrySet().stream()
                .filter(entry -> !entry.getValue().candidates.isEmpty())
                .map(entry -> entry.getValue().compile(entry.getKey()))
                .toList();
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

    record CompiledBand(
            String salt,
            List<CompiledTargetGroup> targetGroups,
            int veinSize,
            float discardOnAirExposure,
            double attemptsPerChunk,
            HeightSampler height) {
        CompiledBand {
            targetGroups = List.copyOf(targetGroups);
        }

        OreConfiguration ore(RandomSource random) {
            List<OreConfiguration.TargetBlockState> targets = new ArrayList<>(targetGroups.size());
            for (CompiledTargetGroup group : targetGroups) {
                BlockState selected = group.select(random);
                targets.add(OreConfiguration.target(new TagMatchTest(group.replaceable()), selected));
            }
            return new OreConfiguration(targets, veinSize, discardOnAirExposure);
        }
    }

    record CompiledTargetGroup(
            TagKey<Block> replaceable,
            List<CompiledOutput> outputs,
            boolean legacyUniform,
            double totalWeight) {
        CompiledTargetGroup {
            outputs = List.copyOf(outputs);
            if (outputs.isEmpty() || !(totalWeight > 0.0D) || !Double.isFinite(totalWeight)) {
                throw new IllegalArgumentException("Compiled ore target group requires finite positive output weight");
            }
        }

        BlockState select(RandomSource random) {
            if (outputs.size() == 1) {
                return outputs.getFirst().state();
            }
            if (legacyUniform) {
                // Compatibility path: this is the exact pre-weight random call and candidate ordering.
                return outputs.get(random.nextInt(outputs.size())).state();
            }
            double selected = random.nextDouble() * totalWeight;
            int low = 0;
            int high = outputs.size() - 1;
            while (low < high) {
                int middle = (low + high) >>> 1;
                if (selected < outputs.get(middle).cumulativeWeight()) {
                    high = middle;
                } else {
                    low = middle + 1;
                }
            }
            return outputs.get(low).state();
        }
    }

    record CompiledOutput(BlockState state, double selectionWeight, double cumulativeWeight) {
    }

    private static final class MutableTargetGroup {
        private final LinkedHashMap<BlockState, Double> candidates = new LinkedHashMap<>();
        private boolean legacyUniform = true;

        boolean add(BlockState state, double selectionWeight, boolean defaultWeight) {
            if (candidates.containsKey(state)) {
                return false;
            }
            candidates.put(state, selectionWeight);
            legacyUniform &= defaultWeight;
            return true;
        }

        CompiledTargetGroup compile(TagKey<Block> replaceable) {
            List<CompiledOutput> outputs = new ArrayList<>(candidates.size());
            double cumulative = 0.0D;
            for (Map.Entry<BlockState, Double> candidate : candidates.entrySet()) {
                cumulative += candidate.getValue();
                outputs.add(new CompiledOutput(candidate.getKey(), candidate.getValue(), cumulative));
            }
            return new CompiledTargetGroup(replaceable, outputs, legacyUniform, cumulative);
        }
    }

    private record SelectionKey(TerrainMode terrainMode, ResourceKey<Biome> biome) {
    }

    @FunctionalInterface
    interface HeightSampler {
        int sample(RandomSource random);
    }
}
