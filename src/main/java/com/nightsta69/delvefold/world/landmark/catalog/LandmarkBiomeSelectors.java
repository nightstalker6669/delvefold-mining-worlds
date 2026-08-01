package com.nightsta69.delvefold.world.landmark.catalog;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

/** Inclusive/exclusive biome IDs or {@code #tag} selectors. */
public record LandmarkBiomeSelectors(List<String> include, List<String> exclude) {
    public static final Codec<LandmarkBiomeSelectors> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.listOf().optionalFieldOf("include", List.of()).forGetter(LandmarkBiomeSelectors::include),
            Codec.STRING.listOf().optionalFieldOf("exclude", List.of()).forGetter(LandmarkBiomeSelectors::exclude)
    ).apply(instance, LandmarkBiomeSelectors::new));

    public LandmarkBiomeSelectors {
        include = include == null ? List.of() : List.copyOf(include);
        exclude = exclude == null ? List.of() : List.copyOf(exclude);
    }

    public static LandmarkBiomeSelectors miningBiomes() {
        return new LandmarkBiomeSelectors(List.of("#delvefold:mining_biomes"), List.of());
    }
}
