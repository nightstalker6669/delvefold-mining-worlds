package com.nightsta69.delvefold.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.analysis.OreDistributionAnalysis;
import com.nightsta69.delvefold.config.model.SpawnBand;
import org.junit.jupiter.api.Test;

class OreDistributionAnalysisTest {
    @Test
    void uniformDistributionNormalizesAcrossEveryHeight() {
        var result = OreDistributionAnalysis.analyze(SpawnBand.uniform("main", 8, 10, 0, 9, 0));

        assertEquals(10, result.samples().size());
        assertEquals(1.0D, result.samples().stream().mapToDouble(sample -> sample.probability()).sum(), 1.0E-9);
        assertEquals(1.0D, result.samples().getFirst().expectedAttempts(), 1.0E-9);
        assertEquals(80.0D, result.workUnits(), 1.0E-9);
    }

    @Test
    void trianglePeaksAtConfiguredHeight() {
        var result = OreDistributionAnalysis.analyze(SpawnBand.triangle("main", 4, 8, -4, 4, 2, 0));

        var peak = result.samples().stream().max(java.util.Comparator.comparingDouble(sample -> sample.probability())).orElseThrow();
        assertEquals(2, peak.y());
        assertTrue(peak.probability() > result.samples().getFirst().probability());
    }

    @Test
    void workloadProducesUsefulDensityBand() {
        var result = OreDistributionAnalysis.analyze(SpawnBand.uniform("main", 64, 16, -64, 64, 0));

        assertEquals(OreDistributionAnalysis.Density.EXTREME, result.density());
        assertEquals(1024.0D, result.workUnits(), 1.0E-9);
    }
}
