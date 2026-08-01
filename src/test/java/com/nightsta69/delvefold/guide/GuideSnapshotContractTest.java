package com.nightsta69.delvefold.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class GuideSnapshotContractTest {
    @Test
    void publicContractContainsOnlyWhitelistedReadOnlyFields() {
        assertEquals(
                List.of(
                        "formatVersion",
                        "worldName",
                        "terrain",
                        "terrainVariant",
                        "geologyTheme",
                        "activeProfile",
                        "portalStatus",
                        "renewal",
                        "ores",
                        "truncated"),
                componentNames(GuideSnapshot.class));
        assertEquals(
                List.of("enabled", "scheduled", "due", "remainingSeconds"),
                componentNames(GuideSnapshot.Renewal.class));
        assertEquals(
                List.of("ruleId", "outputs", "applicability", "heightBands", "relativeFrequency", "truncated"),
                componentNames(GuideSnapshot.OreEntry.class));
        assertEquals(List.of("kind", "sourceId", "iconBlockId"), componentNames(GuideSnapshot.Output.class));
        assertEquals(
                List.of("terrains", "appliesToActiveTerrain", "biomeFiltered", "biomeIncludes", "biomeExcludes"),
                componentNames(GuideSnapshot.Applicability.class));
        assertEquals(
                List.of("bandId", "distribution", "minY", "maxY", "bestMinY", "bestMaxY", "veinSize"),
                componentNames(GuideSnapshot.HeightBand.class));
    }

    @Test
    void boundedStringsNeverSplitSurrogatesOrExceedCodecLength() {
        String bounded = GuideLimits.boundedText("Mine " + "🪨".repeat(100), 64);
        assertFalse(Character.isHighSurrogate(bounded.charAt(bounded.length() - 1)));
        assertTrue(bounded.length() <= 64);
    }

    @Test
    void legacyGuideConstructorDefaultsToClassicGeology() {
        GuideSnapshot snapshot = new GuideSnapshot(
                GuideSnapshot.LEGACY_FORMAT_VERSION,
                "Legacy Mine",
                "flat",
                "classic",
                "legacy_profile",
                GuideSnapshot.PortalStatus.AVAILABLE,
                new GuideSnapshot.Renewal(false, false, false, 0L),
                List.of(),
                false);

        assertEquals("classic", snapshot.geologyTheme());
        assertEquals(GuideSnapshot.LEGACY_FORMAT_VERSION, snapshot.formatVersion());
    }

    private static List<String> componentNames(Class<?> recordType) {
        return java.util.Arrays.stream(recordType.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();
    }
}
