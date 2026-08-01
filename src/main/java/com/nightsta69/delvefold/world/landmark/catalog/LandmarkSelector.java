package com.nightsta69.delvefold.world.landmark.catalog;

import com.nightsta69.delvefold.config.model.LandmarkPreset;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.WorldIdentitySettings;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** Pure deterministic preset filtering and weighted definition selection. */
public final class LandmarkSelector {
    private LandmarkSelector() {}

    public static boolean accepts(LandmarkPreset preset, long deterministicSeed) {
        if (preset == null || preset == LandmarkPreset.PURE_MINING) {
            return false;
        }
        int bucket = (int) Math.floorMod(mix64(deterministicSeed), 4L);
        return preset == LandmarkPreset.BALANCED ? bucket == 0 : bucket != 3;
    }

    public static boolean categoryEnabled(LandmarkCategory category, WorldIdentitySettings identity) {
        if (category == null || identity == null) {
            return false;
        }
        return switch (category) {
            case SURVEY_STATION -> identity.surveyStations();
            case MOTHERLODE -> identity.motherlodes();
            case FAULT_LINE -> identity.faultLines();
        };
    }

    public static Optional<LandmarkDefinition> select(
            LandmarkCatalogSnapshot catalog,
            TerrainMode terrain,
            WorldIdentitySettings identity,
            Predicate<LandmarkDefinition> extraFilter,
            long deterministicSeed) {
        if (catalog == null || terrain == null || identity == null) {
            return Optional.empty();
        }
        Predicate<LandmarkDefinition> filter = extraFilter == null ? ignored -> true : extraFilter;
        List<LandmarkDefinition> candidates = catalog.orderedDefinitions().stream()
                .filter(definition -> definition.terrainModes().contains(terrain))
                .filter(definition -> categoryEnabled(definition.category(), identity))
                .filter(filter)
                .toList();
        long totalWeight =
                candidates.stream().mapToLong(LandmarkDefinition::weight).sum();
        if (totalWeight <= 0L) {
            return Optional.empty();
        }
        long roll = Math.floorMod(mix64(deterministicSeed), totalWeight);
        for (LandmarkDefinition candidate : candidates) {
            roll -= candidate.weight();
            if (roll < 0L) {
                return Optional.of(candidate);
            }
        }
        return Optional.of(candidates.getLast());
    }

    static long mix64(long value) {
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }
}
