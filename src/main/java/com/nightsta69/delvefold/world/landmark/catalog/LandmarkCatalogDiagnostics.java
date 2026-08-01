package com.nightsta69.delvefold.world.landmark.catalog;

import java.time.Instant;
import java.util.List;

/** Bounded reload status exposed to administrator diagnostics. */
public record LandmarkCatalogDiagnostics(
        boolean lastReloadAccepted,
        boolean retainingLastGood,
        long activeRevision,
        int activeDefinitions,
        Instant attemptedAt,
        List<String> errors) {

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

    public static LandmarkCatalogDiagnostics initial() {
        return new LandmarkCatalogDiagnostics(false, false, 0L, 0, Instant.EPOCH, List.of());
    }

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
