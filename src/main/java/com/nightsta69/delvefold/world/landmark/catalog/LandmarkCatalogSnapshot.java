package com.nightsta69.delvefold.world.landmark.catalog;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;

/**
 * Immutable, registry-ID-ordered catalog safe for concurrent world-generation reads.
 *
 * @param revision monotonically increasing accepted-publication revision
 * @param definitions definitions copied into ascending registry-ID order
 * @param loadedAt wall-clock instant at which this revision was prepared
 */
public record LandmarkCatalogSnapshot(
        long revision, Map<ResourceLocation, LandmarkDefinition> definitions, Instant loadedAt) {

    /**
     * Normalizes revision/time values and creates an unmodifiable registry-ID-ordered definition map.
     *
     * @param revision accepted-publication revision, clamped to zero
     * @param definitions definitions to defensively copy
     * @param loadedAt preparation instant, defaulting to the epoch when absent
     */
    public LandmarkCatalogSnapshot {
        revision = Math.max(0L, revision);
        TreeMap<ResourceLocation, LandmarkDefinition> ordered = new TreeMap<>();
        if (definitions != null) {
            ordered.putAll(definitions);
        }
        definitions = java.util.Collections.unmodifiableMap(ordered);
        loadedAt = loadedAt == null ? Instant.EPOCH : loadedAt;
    }

    /**
     * Creates the initial catalog before any successful reload.
     *
     * @return empty revision-zero snapshot
     */
    public static LandmarkCatalogSnapshot empty() {
        return new LandmarkCatalogSnapshot(0L, Map.of(), Instant.EPOCH);
    }

    /**
     * Returns definitions in deterministic ascending registry-ID order.
     *
     * @return immutable ordered definition list
     */
    public List<LandmarkDefinition> orderedDefinitions() {
        return List.copyOf(definitions.values());
    }
}
