package com.nightsta69.delvefold.config.analysis;

import com.nightsta69.delvefold.config.model.OreBandPlacement;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.model.OreRule;
import com.nightsta69.delvefold.config.model.ProvinceSettings;
import com.nightsta69.delvefold.config.model.SpawnBand;
import com.nightsta69.delvefold.config.model.TerrainMode;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Canonical aggregate ore-placement work used by validation and diagnostics. */
public final class OreWorkBudgetAnalysis {
    private OreWorkBudgetAnalysis() {}

    /**
     * Computes the same conservative per-terrain totals enforced by the configuration validator. Disabled rules and
     * non-positive or non-finite attempt rates do not consume the budget. Biome filters are deliberately ignored
     * because validation must budget for the worst case.
     *
     * <p>Rules and bands are accumulated in profile order. Finite overflow is saturated to {@link Double#MAX_VALUE} so
     * the returned budget remains representable and cannot wrap to a non-finite value.
     *
     * @param document immutable profile whose enabled rules should be budgeted
     * @return immutable budget for every terrain mode, measured per eligible chunk
     */
    public static ProfileBudget analyze(OreProfileDocument document) {
        Objects.requireNonNull(document, "document");
        EnumMap<TerrainMode, Budget> totals = emptyBudgets();
        for (OreRule rule : document.rules()) {
            if (!rule.enabled()) {
                continue;
            }
            for (TerrainMode terrain : rule.terrainModes()) {
                Budget current = Objects.requireNonNull(totals.get(terrain), "complete terrain budget map");
                totals.put(terrain, current.plus(analyze(rule, terrain)));
            }
        }
        return new ProfileBudget(totals);
    }

    /**
     * Returns a rule's configured work for one terrain, regardless of whether the rule is enabled.
     *
     * @param rule rule whose spawn bands should be accumulated
     * @param terrain terrain for which applicability should be checked
     * @return zero when the rule excludes the terrain, otherwise its saturated per-chunk budget
     */
    public static Budget analyze(OreRule rule, TerrainMode terrain) {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(terrain, "terrain");
        if (!rule.terrainModes().contains(terrain)) {
            return Budget.ZERO;
        }
        double attempts = 0.0D;
        double workUnits = 0.0D;
        for (SpawnBand band : rule.bands()) {
            Budget bandBudget = analyze(band);
            attempts = saturatingAdd(attempts, bandBudget.attemptsPerChunk());
            workUnits = saturatingAdd(workUnits, bandBudget.workUnitsPerChunk());
        }
        return new Budget(attempts, workUnits);
    }

    /**
     * Computes a conservative upper bound for one configured band in one eligible chunk.
     *
     * <p>Vein work is attempts multiplied by at least one block per vein and saturates at {@link Double#MAX_VALUE}.
     * Province placement uses its hard per-chunk work cap for both attempts and work units because each sampled voxel
     * represents one placement attempt and one unit of work.
     *
     * @param band configured vein or province band
     * @return finite, non-negative per-chunk budget, or {@link Budget#ZERO} for ineffective input
     */
    public static Budget analyze(SpawnBand band) {
        Objects.requireNonNull(band, "band");
        if (band.placement() == OreBandPlacement.PROVINCE) {
            ProvinceSettings province = band.province();
            if (province == null
                    || !Double.isFinite(province.density())
                    || province.density() <= 0.0D
                    || province.perChunkWorkCap() <= 0) {
                return Budget.ZERO;
            }
            // A province sampler shares this hard cap across every regional center touching the chunk.
            // Each sampled voxel is both one attempt and one unit of work.
            double cappedWork = province.perChunkWorkCap();
            return new Budget(cappedWork, cappedWork);
        }
        double bandAttempts = band.attemptsPerChunk();
        if (!Double.isFinite(bandAttempts) || bandAttempts <= 0.0D) {
            return Budget.ZERO;
        }
        return new Budget(bandAttempts, saturatingMultiply(bandAttempts, Math.max(1, band.veinSize())));
    }

    private static EnumMap<TerrainMode, Budget> emptyBudgets() {
        EnumMap<TerrainMode, Budget> result = new EnumMap<>(TerrainMode.class);
        for (TerrainMode terrain : TerrainMode.values()) {
            result.put(terrain, Budget.ZERO);
        }
        return result;
    }

    private static double saturatingAdd(double left, double right) {
        double result = left + right;
        return Double.isFinite(result) ? result : Double.MAX_VALUE;
    }

    private static double saturatingMultiply(double value, int multiplier) {
        double result = value * multiplier;
        return Double.isFinite(result) ? result : Double.MAX_VALUE;
    }

    /**
     * Complete immutable mapping of terrain modes to conservative per-chunk budgets.
     *
     * @param terrains partial or complete terrain mapping; omitted modes are filled with {@link Budget#ZERO}
     */
    public record ProfileBudget(Map<TerrainMode, Budget> terrains) {
        /**
         * Normalizes the supplied mapping into an immutable enum map containing every terrain mode.
         *
         * @param terrains partial or complete mapping to copy
         */
        public ProfileBudget {
            Objects.requireNonNull(terrains, "terrains");
            EnumMap<TerrainMode, Budget> copy = emptyBudgets();
            copy.putAll(terrains);
            terrains = Collections.unmodifiableMap(copy);
        }

        /**
         * Looks up the budget for a terrain mode.
         *
         * @param terrain non-null terrain mode
         * @return terrain budget, falling back to {@link Budget#ZERO} for an absent entry
         */
        public Budget terrain(TerrainMode terrain) {
            return terrains.getOrDefault(Objects.requireNonNull(terrain, "terrain"), Budget.ZERO);
        }
    }

    /**
     * Finite, non-negative ore-generation workload for one eligible chunk.
     *
     * @param attemptsPerChunk expected or conservatively capped placement attempts per eligible chunk
     * @param workUnitsPerChunk conservative block-placement work units per eligible chunk
     */
    public record Budget(double attemptsPerChunk, double workUnitsPerChunk) {
        /** Reusable zero-attempt, zero-work budget. */
        public static final Budget ZERO = new Budget(0.0D, 0.0D);

        /**
         * Validates that both workload metrics are finite and non-negative.
         *
         * @param attemptsPerChunk placement attempts per eligible chunk
         * @param workUnitsPerChunk block-placement work units per eligible chunk
         */
        public Budget {
            if (!Double.isFinite(attemptsPerChunk)
                    || attemptsPerChunk < 0.0D
                    || !Double.isFinite(workUnitsPerChunk)
                    || workUnitsPerChunk < 0.0D) {
                throw new IllegalArgumentException("Ore work budgets must be finite and non-negative");
            }
        }

        Budget plus(Budget other) {
            return new Budget(
                    saturatingAdd(attemptsPerChunk, other.attemptsPerChunk),
                    saturatingAdd(workUnitsPerChunk, other.workUnitsPerChunk));
        }
    }
}
