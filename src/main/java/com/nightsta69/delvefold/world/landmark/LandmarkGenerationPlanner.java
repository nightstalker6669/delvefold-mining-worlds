package com.nightsta69.delvefold.world.landmark;

import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.WorldIdentitySettings;
import com.nightsta69.delvefold.world.landmark.catalog.LandmarkCatalogSnapshot;
import com.nightsta69.delvefold.world.landmark.catalog.LandmarkDefinition;
import com.nightsta69.delvefold.world.landmark.catalog.LandmarkSelector;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** Pure definition/candidate selection against exactly one immutable catalog revision. */
final class LandmarkGenerationPlanner {
    private LandmarkGenerationPlanner() {
    }

    static <T> Optional<Selection<T>> select(
            LandmarkCatalogSnapshot catalog,
            TerrainMode terrain,
            WorldIdentitySettings identity,
            Function<LandmarkDefinition, Optional<T>> candidateResolver,
            long deterministicSeed) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(candidateResolver, "candidateResolver");

        Map<net.minecraft.resources.ResourceLocation, T> placeable = new LinkedHashMap<>();
        for (LandmarkDefinition definition : catalog.orderedDefinitions()) {
            if (!definition.terrainModes().contains(terrain)
                    || !LandmarkSelector.categoryEnabled(definition.category(), identity)) {
                continue;
            }
            candidateResolver.apply(definition).ifPresent(candidate -> placeable.put(definition.id(), candidate));
        }
        return LandmarkSelector.select(
                        catalog,
                        terrain,
                        identity,
                        definition -> placeable.containsKey(definition.id()),
                        deterministicSeed)
                .map(definition -> new Selection<>(definition, placeable.get(definition.id())));
    }

    record Selection<T>(LandmarkDefinition definition, T candidate) {
        Selection {
            Objects.requireNonNull(definition, "definition");
            Objects.requireNonNull(candidate, "candidate");
        }
    }
}
