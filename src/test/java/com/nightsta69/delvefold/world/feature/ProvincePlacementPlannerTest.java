package com.nightsta69.delvefold.world.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.model.HeightDistribution;
import com.nightsta69.delvefold.config.model.ProvinceSettings;
import com.nightsta69.delvefold.config.model.SpawnBand;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProvincePlacementPlannerTest {
    @Test
    void plannerIsRestartStableBoundedAndNeverReturnsAnotherChunksPosition() {
        SpawnBand band = province(new ProvinceSettings(64, 48, 24, 0.75D, 173));

        ProvincePlacementPlanner.Plan first =
                ProvincePlacementPlanner.plan(0x5EEDL, 71L, "test/province", band, -3, 5, -64, 321);
        ProvincePlacementPlanner.Plan repeated =
                ProvincePlacementPlanner.plan(0x5EEDL, 71L, "test/province", band, -3, 5, -64, 321);

        assertEquals(first, repeated);
        assertTrue(first.workUnits() <= 173);
        assertFalse(first.provinces().isEmpty());
        int minX = -3 * 16;
        int minZ = 5 * 16;
        assertTrue(first.provinces().stream()
                .flatMap(slice -> slice.candidates().stream())
                .allMatch(candidate -> candidate.x() >= minX
                        && candidate.x() <= minX + 15
                        && candidate.z() >= minZ
                        && candidate.z() <= minZ + 15
                        && candidate.y() >= -64
                        && candidate.y() < 321));
    }

    @Test
    void persistedGenerationSaltRotatesCentersSamplesAndOutputStreams() {
        SpawnBand band = province(new ProvinceSettings(32, 32, 17, 1.0D, 256));

        ProvincePlacementPlanner.Plan stable =
                ProvincePlacementPlanner.plan(42L, 100L, "rotating", band, 7, -4, -64, 321);
        ProvincePlacementPlanner.Plan repeated =
                ProvincePlacementPlanner.plan(42L, 100L, "rotating", band, 7, -4, -64, 321);
        ProvincePlacementPlanner.Plan rotated =
                ProvincePlacementPlanner.plan(42L, 101L, "rotating", band, 7, -4, -64, 321);

        assertEquals(stable, repeated);
        assertFalse(stable.equals(rotated));
    }

    @Test
    void adjacentChunksAgreeOnEverySharedRegionalCenter() {
        SpawnBand band = province(new ProvinceSettings(16, 16, 9, 1.0D, 512));
        ProvincePlacementPlanner.Plan west = ProvincePlacementPlanner.plan(99L, 0L, "border", band, 0, 0, -64, 321);
        ProvincePlacementPlanner.Plan east = ProvincePlacementPlanner.plan(99L, 0L, "border", band, 1, 0, -64, 321);
        Map<String, ProvincePlacementPlanner.ProvinceSlice> westByRegion = byRegion(west);
        Map<String, ProvincePlacementPlanner.ProvinceSlice> eastByRegion = byRegion(east);

        westByRegion.keySet().retainAll(eastByRegion.keySet());
        assertFalse(westByRegion.isEmpty(), "Expected at least one province to cross the chunk border");
        for (String region : westByRegion.keySet()) {
            var left = westByRegion.get(region);
            var right = eastByRegion.get(region);
            assertEquals(left.centerX(), right.centerX());
            assertEquals(left.centerY(), right.centerY());
            assertEquals(left.centerZ(), right.centerZ());
            assertEquals(left.outputSeed(), right.outputSeed());
            assertTrue(left.candidates().stream().allMatch(candidate -> candidate.x() <= 15));
            assertTrue(right.candidates().stream().allMatch(candidate -> candidate.x() >= 16));
        }
    }

    @Test
    void workCapIsSharedAcrossAllOverlappingRegions() {
        SpawnBand band = province(new ProvinceSettings(16, 16, 385, 1.0D, 37));

        ProvincePlacementPlanner.Plan plan = ProvincePlacementPlanner.plan(7L, 0L, "capped", band, 0, 0, -64, 321);

        assertTrue(plan.provinces().size() > 1);
        assertEquals(37, plan.workUnits());
        assertTrue(
                plan.provinces().stream()
                                .mapToInt(slice -> slice.candidates().size())
                                .sum()
                        <= plan.workUnits(),
                "Ellipsoid filtering cannot consume more than the capped samples");
    }

    private static SpawnBand province(ProvinceSettings settings) {
        return SpawnBand.province("main", HeightDistribution.TRIANGLE, -32, 64, 0, null, null, 0.0D, settings);
    }

    private static Map<String, ProvincePlacementPlanner.ProvinceSlice> byRegion(ProvincePlacementPlanner.Plan plan) {
        Map<String, ProvincePlacementPlanner.ProvinceSlice> result = new HashMap<>();
        for (var province : plan.provinces()) {
            result.put(province.regionX() + ":" + province.regionZ(), province);
        }
        return result;
    }
}
