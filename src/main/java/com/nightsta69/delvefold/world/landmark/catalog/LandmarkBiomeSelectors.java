package com.nightsta69.delvefold.world.landmark.catalog;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

/**
 * Inclusive/exclusive biome IDs or {@code #tag} selectors.
 *
 * @param include exact IDs or tags eligible for placement; empty means all biomes
 * @param exclude exact IDs or tags that override inclusion
 */
public record LandmarkBiomeSelectors(List<String> include, List<String> exclude) {
    /** Datapack codec with empty immutable lists as defaults for both selector groups. */
    public static final Codec<LandmarkBiomeSelectors> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING
                            .listOf()
                            .optionalFieldOf("include", List.of())
                            .forGetter(LandmarkBiomeSelectors::include),
                    Codec.STRING
                            .listOf()
                            .optionalFieldOf("exclude", List.of())
                            .forGetter(LandmarkBiomeSelectors::exclude))
            .apply(instance, LandmarkBiomeSelectors::new));

    /**
     * Copies selector lists and normalizes absent lists to empty.
     *
     * @param include inclusion selectors
     * @param exclude exclusion selectors
     */
    public LandmarkBiomeSelectors {
        include = include == null ? List.of() : List.copyOf(include);
        exclude = exclude == null ? List.of() : List.copyOf(exclude);
    }

    /**
     * Creates the default selector for the public {@code delvefold:mining_biomes} biome tag.
     *
     * @return selector that includes the mining-biome tag and excludes nothing
     */
    public static LandmarkBiomeSelectors miningBiomes() {
        return new LandmarkBiomeSelectors(List.of("#delvefold:mining_biomes"), List.of());
    }
}
