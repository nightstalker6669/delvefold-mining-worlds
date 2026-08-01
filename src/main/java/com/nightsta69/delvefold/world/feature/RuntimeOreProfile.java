package com.nightsta69.delvefold.world.feature;

import com.mojang.logging.LogUtils;
import com.nightsta69.delvefold.config.model.BiomeFilter;
import com.nightsta69.delvefold.config.model.HeightDistribution;
import com.nightsta69.delvefold.config.model.OreBandPlacement;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.model.OreRule;
import com.nightsta69.delvefold.config.model.ProvinceSettings;
import com.nightsta69.delvefold.config.model.SpawnBand;
import com.nightsta69.delvefold.config.model.TerrainMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntToDoubleFunction;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;
import org.slf4j.Logger;

/** Immutable, worldgen-thread-safe compilation of the editable ore profile. */
final class RuntimeOreProfile {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> WARNED_TARGETS = ConcurrentHashMap.newKeySet();

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

            OreTargetResolution.Result targetResolution = OreTargetResolution.resolve(rule);
            logTargetIssues(targetResolution);
            List<CompiledTargetGroup> targetGroups = targetResolution.groups().stream()
                    .map(group -> new CompiledTargetGroup(
                            group.replaceable(),
                            group.outputs().stream()
                                    .map(output -> new CompiledOutput(
                                            output.state(), output.selectionWeight(), output.cumulativeWeight()))
                                    .toList(),
                            group.legacyUniform(),
                            group.totalWeight()))
                    .toList();
            if (targetGroups.isEmpty()) {
                continue;
            }

            List<CompiledBand> bands = new ArrayList<>(rule.bands().size());
            for (SpawnBand band : rule.bands()) {
                if (band.minY() > band.maxY()) {
                    continue;
                }
                ProvinceSettings province = band.province();
                boolean effectiveProvince = band.placement() == OreBandPlacement.PROVINCE
                        && province != null
                        && province.regionSize() > 0
                        && province.radius() > 0
                        && province.radius() <= province.regionSize()
                        && province.verticalThickness() > 0
                        && Double.isFinite(province.density())
                        && province.density() > 0.0D
                        && province.density() <= 1.0D
                        && province.perChunkWorkCap() > 0;
                if ((band.placement() == OreBandPlacement.VEIN && band.attemptsPerChunk() <= 0.0D)
                        || (band.placement() == OreBandPlacement.PROVINCE && !effectiveProvince)) {
                    continue;
                }
                bands.add(new CompiledBand(
                        rule.id() + '/' + band.id(),
                        targetGroups,
                        band.veinSize(),
                        (float) band.discardOnAirExposure(),
                        band.attemptsPerChunk(),
                        compileHeight(band),
                        band.placement(),
                        province,
                        band));
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
            if (rule.terrainModes().contains(terrainMode) && OreBiomeMatcher.matches(rule.biomes(), biome)) {
                result.addAll(rule.bands());
            }
        }
        return List.copyOf(result);
    }

    private static void logTargetIssues(OreTargetResolution.Result resolution) {
        for (OreTargetResolution.Issue issue : resolution.issues()) {
            String key = issue.ruleId() + '|' + issue.targetIndex() + '|' + issue.kind() + '|' + issue.referenceId();
            switch (issue.kind()) {
                case INVALID_HOST_TAG ->
                    warnOnce(
                            key,
                            "Skipping ore rule {} target {} because its replacement tag ID is unavailable",
                            issue.ruleId(),
                            issue.sourceId());
                case INVALID_WEIGHT ->
                    warnOnce(
                            key,
                            "Skipping ore rule {} target {} because its configured weight is invalid",
                            issue.ruleId(),
                            issue.sourceId());
                case MISSING_BLOCK, MISSING_OUTPUT_TAG ->
                    warnOnce(
                            key,
                            "Skipping ore rule {} target {} because it resolves to no installed output blocks",
                            issue.ruleId(),
                            issue.sourceId());
                case INVALID_STATE_PROPERTY, INVALID_STATE_VALUE ->
                    warnOnce(
                            key,
                            "Ignoring invalid block-state setting {} for target {} in ore rule {}",
                            issue.referenceId(),
                            issue.sourceId(),
                            issue.ruleId());
                case SHADOWED_OUTPUT ->
                    warnOnce(
                            key,
                            "Deduplicating overlapping output {} for target {} in ore rule {}",
                            issue.referenceId(),
                            issue.sourceId(),
                            issue.ruleId());
                case SHADOWED_TARGET ->
                    warnOnce(
                            key,
                            "Ore rule {} target {} is ineffective because every resolved state overlaps an earlier target",
                            issue.ruleId(),
                            issue.sourceId());
                case MISSING_HOST_TAG -> {
                    // Validation already reports this; runtime retains its prior empty-host behavior.
                }
            }
        }
    }

    private static HeightSampler compileHeight(SpawnBand band) {
        if (band.distribution() == HeightDistribution.TRIANGLE && band.peakY() != null) {
            int peak = band.peakY();
            return weighted(
                    band.minY(),
                    band.maxY(),
                    y -> y <= peak
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
            HeightSampler height,
            OreBandPlacement placement,
            ProvinceSettings province,
            SpawnBand sourceBand) {
        CompiledBand {
            targetGroups = List.copyOf(targetGroups);
            placement = placement == null ? OreBandPlacement.VEIN : placement;
            sourceBand = java.util.Objects.requireNonNull(sourceBand, "sourceBand");
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
            TagKey<Block> replaceable, List<CompiledOutput> outputs, boolean legacyUniform, double totalWeight) {
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

    record CompiledOutput(BlockState state, double selectionWeight, double cumulativeWeight) {}

    private record SelectionKey(TerrainMode terrainMode, ResourceKey<Biome> biome) {}

    @FunctionalInterface
    interface HeightSampler {
        int sample(RandomSource random);
    }
}
