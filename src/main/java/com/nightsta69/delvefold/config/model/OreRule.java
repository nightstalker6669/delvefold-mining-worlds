package com.nightsta69.delvefold.config.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable ore-placement rule published to world generation after full-profile validation.
 *
 * <p>Rule, target, and band order contributes to deterministic generation. Collection inputs are defensively copied;
 * profile changes affect newly generated chunks only.
 *
 * @param id stable lowercase rule ID used in deterministic salts and administrative commands
 * @param enabled whether the rule participates in placement and aggregate work-budget accounting
 * @param required whether unresolved output blocks or tags reject the profile instead of warning and skipping
 * @param terrainModes immutable set of terrain modes in which the rule may run
 * @param targets immutable ordered output/host candidates, limited to 16 after validation
 * @param biomes immutable include/exclude biome filter
 * @param bands immutable ordered placement bands, limited to 16 after validation
 */
public record OreRule(
        String id,
        boolean enabled,
        boolean required,
        Set<TerrainMode> terrainModes,
        List<OreTarget> targets,
        BiomeFilter biomes,
        List<SpawnBand> bands) {
    /**
     * Normalizes deserialization input and takes immutable snapshots of every collection.
     *
     * <p>A missing biome filter uses {@link BiomeFilter#ALL_MINING_BIOMES}. Empty or missing terrain modes, targets,
     * and bands remain empty and are reported later by profile validation.
     *
     * @param id stable rule ID
     * @param enabled whether placement is enabled
     * @param required whether missing output registry entries are fatal
     * @param terrainModes applicable terrain modes
     * @param targets ordered output/host candidates
     * @param biomes include/exclude biome filter
     * @param bands ordered placement bands
     */
    public OreRule {
        id = id == null ? "" : id.trim();
        terrainModes = terrainModes == null || terrainModes.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(terrainModes));
        targets = targets == null ? List.of() : List.copyOf(targets);
        biomes = biomes == null ? BiomeFilter.ALL_MINING_BIOMES : biomes;
        bands = bands == null ? List.of() : List.copyOf(bands);
    }

    /**
     * Copies this rule with a replacement enabled state.
     *
     * @param value replacement enabled state
     * @return immutable rule copy retaining all other fields
     */
    public OreRule withEnabled(boolean value) {
        return new OreRule(id, value, required, terrainModes, targets, biomes, bands);
    }

    /**
     * Copies this rule with a defensively copied replacement band list.
     *
     * @param replacementBands complete ordered replacement band list
     * @return immutable rule copy retaining all other fields
     */
    public OreRule withBands(List<SpawnBand> replacementBands) {
        return new OreRule(id, enabled, required, terrainModes, targets, biomes, replacementBands);
    }
}
