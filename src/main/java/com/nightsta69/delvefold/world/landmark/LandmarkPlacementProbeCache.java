package com.nightsta69.delvefold.world.landmark;

import com.nightsta69.delvefold.world.landmark.catalog.LandmarkDefinition;
import com.nightsta69.delvefold.world.landmark.catalog.LandmarkPlacementStyle;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntSupplier;

/** Per-structure-candidate cache for height probes whose result is definition-ID independent. */
final class LandmarkPlacementProbeCache {
    private final Map<ProbeKey, Integer> resolved = new HashMap<>();

    int resolve(LandmarkDefinition definition, IntSupplier resolver) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(resolver, "resolver");
        if (definition.placementStyle() == LandmarkPlacementStyle.BURIED) {
            // Buried placement deliberately consumes the definition's content stream.
            return resolver.getAsInt();
        }
        ProbeKey key = new ProbeKey(definition.placementStyle(), definition.minY(), definition.maxY());
        return resolved.computeIfAbsent(key, ignored -> resolver.getAsInt());
    }

    int cachedProbeCount() {
        return resolved.size();
    }

    record ProbeKey(LandmarkPlacementStyle style, int minY, int maxY) {}
}
