package com.nightsta69.delvefold.config.model;

import java.util.List;

public record BiomeFilter(List<String> include, List<String> exclude) {
    public static final BiomeFilter ALL_MINING_BIOMES = new BiomeFilter(List.of("#delvefold:mining_biomes"), List.of());

    public BiomeFilter {
        include = include == null ? List.of() : List.copyOf(include);
        exclude = exclude == null ? List.of() : List.copyOf(exclude);
    }
}
