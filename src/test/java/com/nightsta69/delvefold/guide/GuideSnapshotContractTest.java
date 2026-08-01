package com.nightsta69.delvefold.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class GuideSnapshotContractTest {
    @Test
    void publicContractContainsOnlyWhitelistedReadOnlyFields() {
        assertEquals(List.of(
                        "formatVersion", "worldName", "terrain", "terrainVariant", "activeProfile",
                        "portalStatus", "renewal", "ores", "truncated"),
                componentNames(GuideSnapshot.class));
        assertEquals(List.of("enabled", "scheduled", "due", "remainingSeconds"),
                componentNames(GuideSnapshot.Renewal.class));
        assertEquals(List.of(
                        "ruleId", "outputs", "applicability", "heightBands", "relativeFrequency", "truncated"),
                componentNames(GuideSnapshot.OreEntry.class));
        assertEquals(List.of("kind", "sourceId", "iconBlockId"),
                componentNames(GuideSnapshot.Output.class));
        assertEquals(List.of("terrains", "appliesToActiveTerrain", "biomeFiltered",
                        "biomeIncludes", "biomeExcludes"),
                componentNames(GuideSnapshot.Applicability.class));
        assertEquals(List.of("bandId", "distribution", "minY", "maxY", "bestMinY", "bestMaxY", "veinSize"),
                componentNames(GuideSnapshot.HeightBand.class));
    }

    @Test
    void boundedStringsNeverSplitSurrogatesOrExceedCodecLength() {
        String bounded = GuideLimits.boundedText("Mine " + "🪨".repeat(100), 64);
        assertFalse(Character.isHighSurrogate(bounded.charAt(bounded.length() - 1)));
        assertTrue(bounded.length() <= 64);
    }

    private static List<String> componentNames(Class<?> recordType) {
        return java.util.Arrays.stream(recordType.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();
    }
}
