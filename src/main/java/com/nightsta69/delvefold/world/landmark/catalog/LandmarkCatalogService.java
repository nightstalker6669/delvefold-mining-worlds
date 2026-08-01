package com.nightsta69.delvefold.world.landmark.catalog;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;

/**
 * Owns the atomic catalog and its all-or-nothing last-known-good reload policy.
 *
 * <p>Reload application publishes immutable snapshots through atomic references. World-generation workers may retain a
 * returned snapshot for one complete structure-candidate calculation; they never combine revisions.
 */
public final class LandmarkCatalogService {
    private static final LandmarkCatalogService INSTANCE = new LandmarkCatalogService();

    private final AtomicReference<LandmarkCatalogSnapshot> active =
            new AtomicReference<>(LandmarkCatalogSnapshot.empty());
    private final AtomicReference<LandmarkCatalogDiagnostics> diagnostics =
            new AtomicReference<>(LandmarkCatalogDiagnostics.initial());

    LandmarkCatalogService() {}

    /**
     * Returns the process-wide catalog owner.
     *
     * @return singleton service
     */
    public static LandmarkCatalogService get() {
        return INSTANCE;
    }

    /**
     * Captures the currently active immutable catalog revision.
     *
     * @return snapshot safe to retain for one complete generation operation
     */
    public LandmarkCatalogSnapshot snapshot() {
        return Objects.requireNonNull(active.get(), "active landmark catalog");
    }

    /**
     * Captures diagnostics for the most recently attempted reload.
     *
     * @return immutable bounded reload diagnostics
     */
    public LandmarkCatalogDiagnostics diagnostics() {
        return Objects.requireNonNull(diagnostics.get(), "landmark catalog diagnostics");
    }

    /**
     * Atomically accepts a complete candidate catalog or retains the last-known-good snapshot.
     *
     * <p>This method runs during the platform reload apply stage and serializes publications. Any error rejects the
     * entire candidate. A successful publication increments the revision exactly once.
     *
     * @param candidate complete decoded definitions keyed by registry ID; null is treated as empty
     * @param errors complete reload errors; any nonempty list rejects the candidate
     * @param attemptedAt preparation timestamp, or the current instant when absent
     * @return outcome containing the exact active snapshot and diagnostics published for this attempt
     */
    public synchronized ReloadOutcome publish(
            @Nullable Map<ResourceLocation, LandmarkDefinition> candidate,
            @Nullable List<String> errors,
            @Nullable Instant attemptedAt) {
        LandmarkCatalogSnapshot before = snapshot();
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

    /** Restores empty revision-zero state for isolated server lifecycle and GameTest cleanup. */
    public synchronized void reset() {
        active.set(LandmarkCatalogSnapshot.empty());
        diagnostics.set(LandmarkCatalogDiagnostics.initial());
    }

    /**
     * Result of one atomic catalog publication attempt.
     *
     * @param applied whether the candidate became active
     * @param activeSnapshot exact accepted or retained snapshot after the attempt
     * @param diagnostics bounded status associated with the attempt
     */
    public record ReloadOutcome(
            boolean applied, LandmarkCatalogSnapshot activeSnapshot, LandmarkCatalogDiagnostics diagnostics) {}
}
