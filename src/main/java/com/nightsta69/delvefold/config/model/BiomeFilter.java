package com.nightsta69.delvefold.config.model;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Immutable include/exclude selectors controlling the biomes in which an ore rule may run.
 *
 * <p>Selectors are registry IDs or block-style tag references prefixed with {@code #}. Empty lists are accepted and
 * normalized to immutable lists. Exclusions take precedence when the filter is evaluated.
 *
 * @param include immutable selectors that opt biomes in; an empty list imposes no positive restriction
 * @param exclude immutable selectors that opt biomes out
 */
public record BiomeFilter(List<String> include, List<String> exclude) {
    /** Default filter targeting every biome in Delvefold's mining-biome tag. */
    public static final BiomeFilter ALL_MINING_BIOMES = new BiomeFilter(List.of("#delvefold:mining_biomes"), List.of());

    /**
     * Normalizes nullable deserialization input and takes immutable snapshots of both selector lists.
     *
     * @param include selectors to include, or {@code null} to use an empty list
     * @param exclude selectors to exclude, or {@code null} to use an empty list
     */
    public BiomeFilter(@Nullable List<String> include, @Nullable List<String> exclude) {
        this.include = include == null ? List.of() : List.copyOf(include);
        this.exclude = exclude == null ? List.of() : List.copyOf(exclude);
    }
}
