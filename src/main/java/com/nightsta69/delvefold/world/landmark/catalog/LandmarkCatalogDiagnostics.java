package com.nightsta69.delvefold.world.landmark.catalog;

import java.time.Instant;
import java.util.List;

/**
 * Bounded reload status exposed to administrator diagnostics.
 *
 * @param lastReloadAccepted whether the most recent complete reload replaced the active snapshot
 * @param retainingLastGood whether a rejected reload left a nonempty prior snapshot active
 * @param activeRevision monotonically increasing accepted-snapshot revision, never negative
 * @param activeDefinitions number of definitions in the active snapshot, never negative
 * @param attemptedAt wall-clock instant at which the reload attempt was prepared
 * @param errors at most 16 reload errors, each truncated to 512 characters
 */
public record LandmarkCatalogDiagnostics(
        boolean lastReloadAccepted,
        boolean retainingLastGood,
        long activeRevision,
        int activeDefinitions,
        Instant attemptedAt,
        List<String> errors) {

    /**
     * Bounds counters and error text before the diagnostics object is published.
     *
     * @param lastReloadAccepted whether the latest reload was accepted
     * @param retainingLastGood whether the prior nonempty snapshot remains active
     * @param activeRevision active accepted revision
     * @param activeDefinitions active definition count
     * @param attemptedAt reload-attempt instant, defaulting to the epoch when absent
     * @param errors reload errors to bound and copy
     */
    public LandmarkCatalogDiagnostics {
        activeRevision = Math.max(0L, activeRevision);
        activeDefinitions = Math.max(0, activeDefinitions);
        attemptedAt = attemptedAt == null ? Instant.EPOCH : attemptedAt;
        errors = errors == null
                ? List.of()
                : errors.stream()
                        .limit(16)
                        .map(value ->
                                value == null ? "unknown error" : value.substring(0, Math.min(512, value.length())))
                        .toList();
    }

    /**
     * Creates the pre-reload diagnostics state.
     *
     * @return rejected=false/retaining=false status for empty revision zero
     */
    public static LandmarkCatalogDiagnostics initial() {
        return new LandmarkCatalogDiagnostics(false, false, 0L, 0, Instant.EPOCH, List.of());
    }

    /**
     * Formats bounded, filesystem-free lines for administrator diagnostics.
     *
     * @return immutable summary containing active counts and any retained reload errors
     */
    public List<String> summaryLines() {
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        lines.add("Landmark catalog: " + activeDefinitions + " active definition(s), revision " + activeRevision);
        if (!lastReloadAccepted && !errors.isEmpty()) {
            lines.add(
                    retainingLastGood
                            ? "Landmark catalog reload rejected; retaining last-known-good data"
                            : "Landmark catalog reload rejected; no valid catalog is active");
            errors.forEach(error -> lines.add("Landmark catalog: " + error));
        }
        return List.copyOf(lines);
    }
}
