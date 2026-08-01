package com.nightsta69.delvefold.world.landmark.catalog;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;

/** Immutable, registry-ID-ordered catalog safe for concurrent world-generation reads. */
public record LandmarkCatalogSnapshot(
        long revision,
        Map<ResourceLocation, LandmarkDefinition> definitions,
        Instant loadedAt) {

    public LandmarkCatalogSnapshot {
        revision = Math.max(0L, revision);
        TreeMap<ResourceLocation, LandmarkDefinition> ordered = new TreeMap<>();
        if (definitions != null) {
            ordered.putAll(definitions);
        }
        definitions = java.util.Collections.unmodifiableMap(ordered);
        loadedAt = loadedAt == null ? Instant.EPOCH : loadedAt;
    }

    public static LandmarkCatalogSnapshot empty() {
        return new LandmarkCatalogSnapshot(0L, Map.of(), Instant.EPOCH);
    }

    public List<LandmarkDefinition> orderedDefinitions() {
        return List.copyOf(definitions.values());
    }
}
