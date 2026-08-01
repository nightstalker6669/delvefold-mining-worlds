package com.nightsta69.delvefold.config.analysis;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nightsta69.delvefold.config.analysis.OreProfileForecast.ReferenceSummary;
import com.nightsta69.delvefold.config.analysis.OreProfileForecast.TerrainTotals;
import com.nightsta69.delvefold.config.model.TerrainMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class OreProfileForecastContractTest {
    private static final ReferenceSummary EMPTY_REFERENCES =
            new ReferenceSummary(0, 0, 0, 0, 0, 0, List.of(), false);

    @Test
    void constructorRejectsInvalidPagingMetadataAndOversizedIdentifiers() {
        assertThrows(IllegalArgumentException.class, () -> emptyForecast(1, 1, 12, 1));
        assertThrows(IllegalArgumentException.class, () -> emptyForecast(0, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> emptyForecast(
                OreProfileForecast.MAX_TOTAL_RULES + 1, 0, 12, 1));
        assertThrows(IllegalArgumentException.class, () -> new OreProfileForecast(
                OreProfileForecast.CURRENT_FORMAT_VERSION,
                "x".repeat(OreProfileForecast.MAX_IDENTIFIER_LENGTH + 1),
                0L, null, List.of(), List.of(), 0, 0, 12, 1,
                List.of(), EMPTY_REFERENCES, false));
    }

    @Test
    void constructorRejectsDuplicateTerrainsAndInvalidMetrics() {
        TerrainTotals first = new TerrainTotals(TerrainMode.FLAT, true, 0.0D, 0.0D, 0.0D, 0.0D);
        TerrainTotals duplicate = new TerrainTotals(TerrainMode.FLAT, false, 0.0D, 0.0D, 0.0D, 0.0D);

        assertThrows(IllegalArgumentException.class, () -> new OreProfileForecast(
                OreProfileForecast.CURRENT_FORMAT_VERSION, "profile", 0L, TerrainMode.FLAT,
                List.of(first, duplicate), List.of(), 0, 0, 12, 1,
                List.of(), EMPTY_REFERENCES, false));
        assertThrows(IllegalArgumentException.class,
                () -> new TerrainTotals(TerrainMode.FLAT, false, Double.NaN, 0.0D, 0.0D, 0.0D));
        assertThrows(IllegalArgumentException.class,
                () -> new OreProfileForecast.HeightSample(OreProfileForecast.MAX_HEIGHT + 1, 0.0D, 0.0D));
    }

    private static OreProfileForecast emptyForecast(int totalRules, int page, int pageSize, int pageCount) {
        return new OreProfileForecast(
                OreProfileForecast.CURRENT_FORMAT_VERSION, "profile", 0L, null,
                List.of(), List.of(), totalRules, page, pageSize, pageCount,
                List.of(), EMPTY_REFERENCES, false);
    }
}
