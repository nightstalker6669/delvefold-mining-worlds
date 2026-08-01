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
     */
    public static ProfileBudget analyze(OreProfileDocument document) {
        Objects.requireNonNull(document, "document");
        EnumMap<TerrainMode, Budget> totals = emptyBudgets();
        for (OreRule rule : document.rules()) {
            if (!rule.enabled()) {
                continue;
            }
            for (TerrainMode terrain : rule.terrainModes()) {
                totals.put(terrain, totals.get(terrain).plus(analyze(rule, terrain)));
            }
        }
        return new ProfileBudget(totals);
    }

    /** Returns a rule's configured work for one terrain, regardless of whether the rule is enabled. */
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

    /** Conservative upper bound for one configured band in one eligible chunk. */
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

    public record ProfileBudget(Map<TerrainMode, Budget> terrains) {
        public ProfileBudget {
            Objects.requireNonNull(terrains, "terrains");
            EnumMap<TerrainMode, Budget> copy = emptyBudgets();
            copy.putAll(terrains);
            terrains = Collections.unmodifiableMap(copy);
        }

        public Budget terrain(TerrainMode terrain) {
            return terrains.getOrDefault(Objects.requireNonNull(terrain, "terrain"), Budget.ZERO);
        }
    }

    public record Budget(double attemptsPerChunk, double workUnitsPerChunk) {
        public static final Budget ZERO = new Budget(0.0D, 0.0D);

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
