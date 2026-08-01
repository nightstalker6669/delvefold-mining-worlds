package com.nightsta69.delvefold.world.landmark;

import com.nightsta69.delvefold.world.landmark.catalog.LandmarkBiomeSelectors;
import java.util.Objects;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

/** Runtime matcher for exact biome IDs and biome tags in landmark definitions. */
public final class LandmarkBiomeMatcher {
    private LandmarkBiomeMatcher() {}

    public static boolean matches(LandmarkBiomeSelectors selectors, Holder<Biome> biome) {
        Objects.requireNonNull(selectors, "selectors");
        Objects.requireNonNull(biome, "biome");
        boolean included = selectors.include().isEmpty()
                || selectors.include().stream().anyMatch(selector -> matches(selector, biome));
        return included && selectors.exclude().stream().noneMatch(selector -> matches(selector, biome));
    }

    private static boolean matches(String selector, Holder<Biome> biome) {
        boolean tag = selector.startsWith("#");
        ResourceLocation id = ResourceLocation.tryParse(tag ? selector.substring(1) : selector);
        if (id == null) {
            return false;
        }
        return tag ? biome.is(TagKey.create(Registries.BIOME, id)) : biome.is(ResourceKey.create(Registries.BIOME, id));
    }
}
