package com.nightsta69.delvefold.world.feature;

import com.nightsta69.delvefold.config.model.BiomeFilter;
import com.nightsta69.delvefold.world.DelvefoldWorldgen;
import java.util.Objects;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

/** Canonical biome-selector matching shared by runtime placement and server diagnostics. */
public final class OreBiomeMatcher {
    private static final String MINING_BIOMES_SELECTOR = "#delvefold:mining_biomes";

    private OreBiomeMatcher() {}

    public static boolean matches(BiomeFilter filter, Holder<Biome> biome) {
        Objects.requireNonNull(filter, "filter");
        Objects.requireNonNull(biome, "biome");
        boolean included =
                filter.include().isEmpty() || filter.include().stream().anyMatch(selector -> matches(selector, biome));
        boolean excluded = filter.exclude().stream().anyMatch(selector -> matches(selector, biome));
        return included && !excluded;
    }

    public static boolean matches(String selector, Holder<Biome> biome) {
        if (selector == null) {
            return false;
        }
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
}
