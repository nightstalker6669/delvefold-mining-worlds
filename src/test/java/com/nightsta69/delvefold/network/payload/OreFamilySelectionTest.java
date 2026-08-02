package com.nightsta69.delvefold.network.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.network.ProtocolLimits;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies canonical family-ID validation independently of the unavailable Minecraft payload runtime. */
class OreFamilySelectionTest {
    @Test
    void acceptsCanonicalAsciiResourceIdsAndPreservesRequestOrder() {
        List<String> source = new ArrayList<>(List.of(" delvefold:ores/copper ", "other-mod:tin.ore/deep"));

        List<String> normalized = OreFamilySelection.normalize(source);
        source.set(0, "changed:after_validation");

        assertEquals(List.of("delvefold:ores/copper", "other-mod:tin.ore/deep"), normalized);
        assertThrows(UnsupportedOperationException.class, () -> normalized.add("delvefold:ores/lead"));
    }

    @Test
    void rejectsUnicodeAndEveryNonCanonicalResourceIdShape() {
        List<String> invalidIds = List.of(
                "delvefold:orés/copper",
                "火:ore",
                "delvefold:ores/🔥",
                "delvefold:Uppercase",
                "Delvefold:ores/copper",
                "delvefold:ore copper",
                "delvefold:ores\\copper",
                "delvefold:ores:copper",
                "delvefold:",
                ":copper",
                "copper",
                "#delvefold:ores/copper");

        for (String invalidId : invalidIds) {
            assertThrows(
                    IllegalArgumentException.class, () -> OreFamilySelection.normalize(List.of(invalidId)), invalidId);
        }
    }

    @Test
    void rejectsNullEmptyOversizedAndNormalizedDuplicateSelections() {
        assertThrows(IllegalArgumentException.class, () -> OreFamilySelection.normalize(null));
        assertThrows(IllegalArgumentException.class, () -> OreFamilySelection.normalize(List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> OreFamilySelection.normalize(java.util.Collections.singletonList(null)));
        assertThrows(
                IllegalArgumentException.class,
                () -> OreFamilySelection.normalize(List.of("delvefold:ores/copper", " delvefold:ores/copper ")));
        assertThrows(
                IllegalArgumentException.class,
                () -> OreFamilySelection.normalize(
                        java.util.Collections.nCopies(ProtocolLimits.MAX_ORE_LIBRARY_SELECTIONS + 1, "a:b")));
    }

    @Test
    void acceptsTheMaximalDistinctSelectionAndRejectsOverlongIds() {
        List<String> maximal = new ArrayList<>(ProtocolLimits.MAX_ORE_LIBRARY_SELECTIONS);
        for (int index = 0; index < ProtocolLimits.MAX_ORE_LIBRARY_SELECTIONS; index++) {
            maximal.add("delvefold:ores/material_" + index);
        }

        assertEquals(
                ProtocolLimits.MAX_ORE_LIBRARY_SELECTIONS,
                OreFamilySelection.normalize(maximal).size());
        String overlong = "a:" + "b".repeat(ProtocolLimits.ID_LENGTH - 1);
        assertTrue(overlong.length() > ProtocolLimits.ID_LENGTH);
        assertThrows(IllegalArgumentException.class, () -> OreFamilySelection.normalize(List.of(overlong)));
    }
}
