package com.nightsta69.delvefold.world.landmark.catalog;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.resources.ResourceLocation;

/** Owns the atomic catalog and its all-or-nothing last-known-good reload policy. */
public final class LandmarkCatalogService {
    private static final LandmarkCatalogService INSTANCE = new LandmarkCatalogService();

    private final AtomicReference<LandmarkCatalogSnapshot> active =
            new AtomicReference<>(LandmarkCatalogSnapshot.empty());
    private final AtomicReference<LandmarkCatalogDiagnostics> diagnostics =
            new AtomicReference<>(LandmarkCatalogDiagnostics.initial());

    LandmarkCatalogService() {}

    public static LandmarkCatalogService get() {
        return INSTANCE;
    }

    public LandmarkCatalogSnapshot snapshot() {
        return active.get();
    }

    public LandmarkCatalogDiagnostics diagnostics() {
        return diagnostics.get();
    }

    public synchronized ReloadOutcome publish(
            Map<ResourceLocation, LandmarkDefinition> candidate, List<String> errors, Instant attemptedAt) {
        LandmarkCatalogSnapshot before = active.get();
        List<String> safeErrors = errors == null ? List.of() : List.copyOf(errors);
        Instant now = attemptedAt == null ? Instant.now() : attemptedAt;
        if (!safeErrors.isEmpty()) {
            LandmarkCatalogDiagnostics status = new LandmarkCatalogDiagnostics(
                    false,
                    !before.definitions().isEmpty(),
                    before.revision(),
                    before.definitions().size(),
                    now,
                    safeErrors);
            diagnostics.set(status);
            return new ReloadOutcome(false, before, status);
        }

        LandmarkCatalogSnapshot next =
                new LandmarkCatalogSnapshot(before.revision() + 1L, candidate == null ? Map.of() : candidate, now);
        active.set(next);
        LandmarkCatalogDiagnostics status = new LandmarkCatalogDiagnostics(
                true, false, next.revision(), next.definitions().size(), now, List.of());
        diagnostics.set(status);
        return new ReloadOutcome(true, next, status);
    }

    public synchronized void reset() {
        active.set(LandmarkCatalogSnapshot.empty());
        diagnostics.set(LandmarkCatalogDiagnostics.initial());
    }

    public record ReloadOutcome(
            boolean applied, LandmarkCatalogSnapshot activeSnapshot, LandmarkCatalogDiagnostics diagnostics) {}
}
