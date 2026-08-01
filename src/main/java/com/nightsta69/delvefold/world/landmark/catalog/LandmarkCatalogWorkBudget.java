package com.nightsta69.delvefold.world.landmark.catalog;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Conservative bound for distinct vertical scans performed by one landmark structure candidate. */
final class LandmarkCatalogWorkBudget {
    static final int MAX_CAVE_SCAN_CELLS_PER_CANDIDATE = 8192;
    private static final int DELVEFOLD_DIMENSION_HEIGHT = 384;

    private LandmarkCatalogWorkBudget() {
    }

    static Analysis analyze(Collection<LandmarkDefinition> definitions) {
        Set<CaveProbe> probes = new HashSet<>();
        if (definitions != null) {
            definitions.stream()
                    .filter(definition -> definition != null
                            && definition.placementStyle() == LandmarkPlacementStyle.CAVE_FLOOR)
                    .map(definition -> new CaveProbe(definition.minY(), definition.maxY()))
                    .forEach(probes::add);
        }
        long cells = 0L;
        for (CaveProbe probe : probes) {
            long configuredSpan = (long) probe.maxY() - probe.minY() + 1L;
            cells += Math.min(DELVEFOLD_DIMENSION_HEIGHT, Math.max(0L, configuredSpan));
        }
        return new Analysis(probes.size(), (int) Math.min(Integer.MAX_VALUE, cells));
    }

    static List<String> validate(Collection<LandmarkDefinition> definitions) {
        Analysis analysis = analyze(definitions);
        if (analysis.caveScanCellsPerCandidate() <= MAX_CAVE_SCAN_CELLS_PER_CANDIDATE) {
            return List.of();
        }
        return List.of("catalog requires " + analysis.caveScanCellsPerCandidate()
                + " distinct cave-floor scan cells per structure candidate; maximum is "
                + MAX_CAVE_SCAN_CELLS_PER_CANDIDATE
                + " (reuse height bounds or reduce cave-floor definitions)");
    }

    record Analysis(int distinctCaveProbes, int caveScanCellsPerCandidate) {
    }

    private record CaveProbe(int minY, int maxY) {
    }
}
