package com.nightsta69.delvefold.world.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * A complete, data-driven ore table executed once per mining-dimension chunk.
 *
 * <p>Output blocks are stored as resource locations rather than registry holders. This is intentional: a datapack can
 * mention an optional mod's block and Delvefold will skip that entry when the mod is absent instead of making the
 * entire worldgen registry fail to load.
 *
 * @param ores immutable fallback ore definitions evaluated once per currently generating chunk
 */
public record MiningOreConfiguration(List<OreDefinition> ores) implements FeatureConfiguration {
    /** Configured-feature codec used by Minecraft's datapack registry loader. */
    public static final Codec<MiningOreConfiguration> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    OreDefinition.CODEC.listOf().fieldOf("ores").forGetter(MiningOreConfiguration::ores))
            .apply(instance, MiningOreConfiguration::new));

    /**
     * Defensively copies the fallback ore table.
     *
     * @param ores fallback ore definitions in deterministic declaration order
     */
    public MiningOreConfiguration {
        ores = List.copyOf(ores);
    }

    /**
     * One legacy resource-backed ore definition used when no server profile snapshot is available.
     *
     * @param id stable salt ID for deterministic per-chunk randomness
     * @param targets ordered host-tag/output-block pairs
     * @param veinSize maximum vein size in blocks, from 1 through 64
     * @param veinsPerChunk placement attempts per chunk, from 0 through 128
     * @param minY inclusive minimum block Y before build-height clipping
     * @param maxY inclusive maximum block Y before build-height clipping
     * @param distribution uniform or triangular height sampling
     * @param discardChanceOnAirExposure probability from 0 through 1 used by Minecraft's ore feature
     */
    public record OreDefinition(
            ResourceLocation id,
            List<OreTarget> targets,
            int veinSize,
            int veinsPerChunk,
            int minY,
            int maxY,
            HeightDistribution distribution,
            float discardChanceOnAirExposure) {
        /** Datapack codec enforcing vein-size, attempt-count, and air-discard bounds. */
        public static final Codec<OreDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        ResourceLocation.CODEC.fieldOf("id").forGetter(OreDefinition::id),
                        OreTarget.CODEC.listOf().fieldOf("targets").forGetter(OreDefinition::targets),
                        Codec.intRange(1, 64).fieldOf("vein_size").forGetter(OreDefinition::veinSize),
                        Codec.intRange(0, 128).fieldOf("veins_per_chunk").forGetter(OreDefinition::veinsPerChunk),
                        Codec.INT.fieldOf("min_y").forGetter(OreDefinition::minY),
                        Codec.INT.fieldOf("max_y").forGetter(OreDefinition::maxY),
                        HeightDistribution.CODEC
                                .optionalFieldOf("distribution", HeightDistribution.UNIFORM)
                                .forGetter(OreDefinition::distribution),
                        Codec.floatRange(0.0F, 1.0F)
                                .optionalFieldOf("discard_chance_on_air_exposure", 0.0F)
                                .forGetter(OreDefinition::discardChanceOnAirExposure))
                .apply(instance, OreDefinition::new));

        /**
         * Defensively copies target ordering, which is part of deterministic output selection.
         *
         * @param id stable definition ID
         * @param targets ordered target list
         * @param veinSize maximum vein size in blocks
         * @param veinsPerChunk placement attempts per chunk
         * @param minY inclusive minimum block Y
         * @param maxY inclusive maximum block Y
         * @param distribution height distribution
         * @param discardChanceOnAirExposure air-exposure discard probability
         */
        public OreDefinition {
            targets = List.copyOf(targets);
        }

        int pickY(net.minecraft.util.RandomSource random, int effectiveMinY, int effectiveMaxY) {
            int span = effectiveMaxY - effectiveMinY + 1;
            if (distribution == HeightDistribution.TRIANGLE) {
                return effectiveMinY + (random.nextInt(span) + random.nextInt(span)) / 2;
            }
            return effectiveMinY + random.nextInt(span);
        }
    }

    /**
     * One optional output block and the block tag it may replace.
     *
     * @param block output block registry ID; absence at runtime skips this target
     * @param replaceable host block tag evaluated by Minecraft's ore feature
     */
    public record OreTarget(ResourceLocation block, TagKey<Block> replaceable) {
        /** Datapack codec for a fallback output block and host block tag. */
        public static final Codec<OreTarget> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        ResourceLocation.CODEC.fieldOf("block").forGetter(OreTarget::block),
                        TagKey.codec(Registries.BLOCK).fieldOf("replaceable").forGetter(OreTarget::replaceable))
                .apply(instance, OreTarget::new));
    }

    /** Height sampler used by legacy resource-backed ore definitions. */
    public enum HeightDistribution implements StringRepresentable {
        /** Samples every block Y in the inclusive range with equal probability. */
        UNIFORM("uniform"),
        /** Averages two uniform samples to concentrate attempts near the range center. */
        TRIANGLE("triangle");

        /** Stable string codec used by configured-feature JSON. */
        public static final Codec<HeightDistribution> CODEC = StringRepresentable.fromEnum(HeightDistribution::values);

        private final String serializedName;

        HeightDistribution(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }
}
