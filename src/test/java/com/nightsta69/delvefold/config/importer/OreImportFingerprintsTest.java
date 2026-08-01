package com.nightsta69.delvefold.config.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.OrePresets;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OreImportFingerprintsTest {
    @Test
    void registryFingerprintIsOrderIndependentAndMergesDuplicateEntries() {
        OreImportRegistry first = registry(List.of(
                new OreImportRegistry.BlockEntry("example:tin_ore", Set.of("c:ores/tin", "c:ores")),
                new OreImportRegistry.BlockEntry("example:lead_ore", Set.of("c:ores/lead"))));
        OreImportRegistry reordered = registry(List.of(
                new OreImportRegistry.BlockEntry("example:lead_ore", Set.of("c:ores/lead")),
                new OreImportRegistry.BlockEntry("example:tin_ore", Set.of("c:ores")),
                new OreImportRegistry.BlockEntry("example:tin_ore", Set.of("c:ores/tin"))));

        String fingerprint = OreImportFingerprints.registry(first);

        assertEquals(fingerprint, OreImportFingerprints.registry(reordered));
        assertEquals(64, fingerprint.length());
        assertTrue(fingerprint.matches("[0-9a-f]{64}"));
    }

    @Test
    void registryFingerprintChangesWithBlocksOrTagMembership() {
        OreImportRegistry baseline = registry(List.of(
                new OreImportRegistry.BlockEntry("example:tin_ore", Set.of("c:ores/tin"))));
        OreImportRegistry changedTag = registry(List.of(
                new OreImportRegistry.BlockEntry("example:tin_ore", Set.of("c:ores/lead"))));
        OreImportRegistry changedBlock = registry(List.of(
                new OreImportRegistry.BlockEntry("example:raw_tin_block", Set.of("c:ores/tin"))));

        assertNotEquals(OreImportFingerprints.registry(baseline), OreImportFingerprints.registry(changedTag));
        assertNotEquals(OreImportFingerprints.registry(baseline), OreImportFingerprints.registry(changedBlock));
    }

    @Test
    void profileFingerprintIsDeterministicAndConservativelyTracksContent() {
        OreProfileDocument baseline = OrePresets.create(OrePreset.VANILLA_BALANCED);
        OreProfileDocument identical = new OreProfileDocument(
                baseline.schemaVersion(), baseline.revision(), baseline.profile(), baseline.rules());
        OreProfileDocument changedRevision = new OreProfileDocument(
                baseline.schemaVersion(), baseline.revision() + 1L, baseline.profile(), baseline.rules());

        String fingerprint = OreImportFingerprints.profile(baseline);

        assertEquals(fingerprint, OreImportFingerprints.profile(identical));
        assertNotEquals(fingerprint, OreImportFingerprints.profile(changedRevision));
        assertEquals(64, fingerprint.length());
        assertTrue(fingerprint.matches("[0-9a-f]{64}"));
    }

    private static OreImportRegistry registry(List<OreImportRegistry.BlockEntry> blocks) {
        return new OreImportRegistry() {
            @Override
            public List<BlockEntry> blocks() {
                return blocks;
            }

            @Override
            public List<String> tagMembers(String tagId) {
                return Map.<String, List<String>>of().getOrDefault(tagId, List.of());
            }
        };
    }
}
