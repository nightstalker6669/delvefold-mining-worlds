package com.nightsta69.delvefold.config.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public record OreRule(
        String id,
        boolean enabled,
        boolean required,
        Set<TerrainMode> terrainModes,
        List<OreTarget> targets,
        BiomeFilter biomes,
        List<SpawnBand> bands) {
    public OreRule {
        id = id == null ? "" : id.trim();
        terrainModes = terrainModes == null || terrainModes.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(terrainModes));
        targets = targets == null ? List.of() : List.copyOf(targets);
        biomes = biomes == null ? BiomeFilter.ALL_MINING_BIOMES : biomes;
        bands = bands == null ? List.of() : List.copyOf(bands);
    }

    public OreRule withEnabled(boolean value) {
        return new OreRule(id, value, required, terrainModes, targets, biomes, bands);
    }

    public OreRule withBands(List<SpawnBand> replacementBands) {
        return new OreRule(id, enabled, required, terrainModes, targets, biomes, replacementBands);
    }
}
