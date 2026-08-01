package com.nightsta69.delvefold.guide;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.guide.GuideSnapshot.Applicability;
import com.nightsta69.delvefold.guide.GuideSnapshot.HeightBand;
import com.nightsta69.delvefold.guide.GuideSnapshot.OreEntry;
import com.nightsta69.delvefold.guide.GuideSnapshot.Output;
import com.nightsta69.delvefold.guide.GuideSnapshot.OutputKind;
import com.nightsta69.delvefold.guide.GuideSnapshot.PortalStatus;
import com.nightsta69.delvefold.guide.GuideSnapshot.RelativeFrequency;
import com.nightsta69.delvefold.guide.GuideSnapshot.Renewal;
import java.util.List;
import org.junit.jupiter.api.Test;

class GuideTextSummaryTest {
    @Test
    void consoleSummaryIncludesUsefulPublicInformationOnly() {
        GuideSnapshot snapshot = new GuideSnapshot(GuideSnapshot.CURRENT_FORMAT_VERSION,
                "Public Mine", "cavern", "classic", "dripstone", "pack:metals", PortalStatus.AVAILABLE,
                new Renewal(true, true, false, 90_000L),
                List.of(new OreEntry("tin",
                        List.of(new Output(OutputKind.BLOCK_TAG, "c:ores/tin", "example:tin_ore")),
                        new Applicability(List.of("cavern"), true, true,
                                List.of("#delvefold:mining_biomes"), List.of()),
                        List.of(new HeightBand("main", "triangle", -32, 80, 12, 12, 6)),
                        RelativeFrequency.UNCOMMON, false)), false);

        String text = String.join("\n", GuideTextSummary.lines(snapshot));
        assertTrue(text.contains("Public Mine"));
        assertTrue(text.contains("pack:metals"));
        assertTrue(text.contains("geology: dripstone"));
        assertTrue(text.contains("#c:ores/tin"));
        assertTrue(text.contains("#delvefold:mining_biomes"));
        assertTrue(text.contains("Y 12"));
        assertFalse(text.toLowerCase().contains("seed"));
        assertFalse(text.toLowerCase().contains("token"));
        assertFalse(text.toLowerCase().contains("path"));
    }
}
