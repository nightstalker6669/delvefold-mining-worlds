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

    /**
     * Applies the configured deterministic landmark-density gate.
     *
     * <p>Pure Mining accepts no candidates, Balanced accepts one of four seed buckets (25%), and Abundant accepts three
     * of four buckets (75%).
     *
     * @param preset active landmark-density preset
     * @param deterministicSeed independent acceptance-domain seed
     * @return whether this candidate proceeds to definition selection
     */
    public static boolean accepts(LandmarkPreset preset, long deterministicSeed) {
        if (preset == null || preset == LandmarkPreset.PURE_MINING) {
            return false;
        }
        int bucket = (int) Math.floorMod(mix64(deterministicSeed), 4L);
        return preset == LandmarkPreset.BALANCED ? bucket == 0 : bucket != 3;
    }

    /**
     * Tests the legacy per-category world-identity toggle for a definition.
     *
     * @param category definition category
     * @param identity active recreation-locked identity settings
     * @return {@code true} when that category remains enabled
     */
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

    /**
     * Selects one definition by weight from deterministic registry-ID ordering.
     *
     * <p>Filtering order is terrain, category toggle, then the caller's placement/dependency filter. The supplied seed
     * is mixed once and reduced modulo the sum of remaining weights; catalog iteration order is stable by ID.
     *
     * @param catalog single immutable catalog revision
     * @param terrain active mining terrain
     * @param identity active recreation-locked identity settings
     * @param extraFilter additional eligibility predicate, or null to accept all otherwise eligible definitions
     * @param deterministicSeed independent selection-domain seed
     * @return selected definition, or empty when no positive-weight candidate remains
     */
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
