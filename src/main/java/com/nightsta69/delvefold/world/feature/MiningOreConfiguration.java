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
 * <p>Output blocks are stored as resource locations rather than registry holders. This is
 * intentional: a datapack can mention an optional mod's block and Delvefold will skip that entry
 * when the mod is absent instead of making the entire worldgen registry fail to load.</p>
 */
public record MiningOreConfiguration(List<OreDefinition> ores) implements FeatureConfiguration {
    public static final Codec<MiningOreConfiguration> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    OreDefinition.CODEC.listOf().fieldOf("ores").forGetter(MiningOreConfiguration::ores))
            .apply(instance, MiningOreConfiguration::new));

    public MiningOreConfiguration {
        ores = List.copyOf(ores);
    }

    public record OreDefinition(
            ResourceLocation id,
            List<OreTarget> targets,
            int veinSize,
            int veinsPerChunk,
            int minY,
            int maxY,
            HeightDistribution distribution,
            float discardChanceOnAirExposure) {
        public static final Codec<OreDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        ResourceLocation.CODEC.fieldOf("id").forGetter(OreDefinition::id),
                        OreTarget.CODEC.listOf().fieldOf("targets").forGetter(OreDefinition::targets),
                        Codec.intRange(1, 64).fieldOf("vein_size").forGetter(OreDefinition::veinSize),
                        Codec.intRange(0, 128).fieldOf("veins_per_chunk").forGetter(OreDefinition::veinsPerChunk),
                        Codec.INT.fieldOf("min_y").forGetter(OreDefinition::minY),
                        Codec.INT.fieldOf("max_y").forGetter(OreDefinition::maxY),
                        HeightDistribution.CODEC.optionalFieldOf("distribution", HeightDistribution.UNIFORM)
                                .forGetter(OreDefinition::distribution),
                        Codec.floatRange(0.0F, 1.0F)
                                .optionalFieldOf("discard_chance_on_air_exposure", 0.0F)
                                .forGetter(OreDefinition::discardChanceOnAirExposure))
                .apply(instance, OreDefinition::new));

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

    public record OreTarget(ResourceLocation block, TagKey<Block> replaceable) {
        public static final Codec<OreTarget> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        ResourceLocation.CODEC.fieldOf("block").forGetter(OreTarget::block),
                        TagKey.codec(Registries.BLOCK).fieldOf("replaceable").forGetter(OreTarget::replaceable))
                .apply(instance, OreTarget::new));
    }

    public enum HeightDistribution implements StringRepresentable {
        UNIFORM("uniform"),
        TRIANGLE("triangle");

        public static final Codec<HeightDistribution> CODEC =
                StringRepresentable.fromEnum(HeightDistribution::values);

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
