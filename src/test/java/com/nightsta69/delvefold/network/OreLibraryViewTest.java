package com.nightsta69.delvefold.network;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nightsta69.delvefold.config.importer.OreImportModels.Evidence;
import com.nightsta69.delvefold.config.importer.OreImportModels.HostKind;
import com.nightsta69.delvefold.network.model.OreLibraryView;
import com.nightsta69.delvefold.network.model.OreLibraryView.Family;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class OreLibraryViewTest {
    @Test
    void emptyLibraryUsesOneEmptyPageAndNormalizesText() {
        OreLibraryView view = assertDoesNotThrow(
                () -> new OreLibraryView("  token  ", 4L, 0, 1, 0, "  Copper  ", false, false, null));

        assertEquals("token", view.catalogToken());
        assertEquals("Copper", view.query());
        assertEquals(List.of(), view.families());
    }

    @Test
    void pageContentsMustExactlyMatchTheBoundedGlobalTotal() {
        List<Family> firstPage = families(0, ProtocolLimits.MAX_ORE_LIBRARY_FAMILIES_PER_PAGE);
        List<Family> secondPage = families(ProtocolLimits.MAX_ORE_LIBRARY_FAMILIES_PER_PAGE, 21);

        assertDoesNotThrow(() -> new OreLibraryView("token", 0L, 0, 2, 21, "", true, false, firstPage));
        assertDoesNotThrow(() -> new OreLibraryView("token", 0L, 1, 2, 21, "", true, false, secondPage));

        assertThrows(
                IllegalArgumentException.class,
                () -> new OreLibraryView("token", 0L, 0, 1, 21, "", true, false, firstPage));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OreLibraryView("token", 0L, 1, 2, 21, "", true, false, List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OreLibraryView("token", 0L, 2, 2, 21, "", true, false, secondPage));
    }

    @Test
    void pageRejectsDuplicateFamilyIdsEvenWhenEveryOtherCountMatches() {
        List<Family> duplicate = new ArrayList<>(families(0, 2));
        duplicate.set(1, duplicate.getFirst());

        assertThrows(
                IllegalArgumentException.class,
                () -> new OreLibraryView("token", 0L, 0, 1, 2, "", true, false, duplicate));
    }

    @Test
    void libraryAndFamilyBoundsFailClosed() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new OreLibraryView(" ", 0L, 0, 1, 0, "", false, false, List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OreLibraryView("token", -1L, 0, 1, 0, "", false, false, List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OreLibraryView(
                        "token", 0L, 0, 1, ProtocolLimits.MAX_IMPORT_GROUPS + 1, "", false, false, List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OreLibraryView(
                        "token",
                        0L,
                        0,
                        1,
                        0,
                        "x".repeat(ProtocolLimits.MAX_ORE_LIBRARY_QUERY_LENGTH + 1),
                        false,
                        false,
                        List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OreLibraryView(
                        "token",
                        0L,
                        0,
                        1,
                        0,
                        "\uD83D\uDD0D".repeat(ProtocolLimits.MAX_ORE_LIBRARY_QUERY_LENGTH / 2 + 1),
                        false,
                        false,
                        List.of()));

        assertThrows(
                IllegalArgumentException.class,
                () -> new Family(
                        "",
                        "copper",
                        "delvefold_copper",
                        "example:copper_ore",
                        HostKind.STONE,
                        1,
                        1,
                        1,
                        Evidence.CONVENTIONAL_TAG,
                        false,
                        false,
                        false));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Family(
                        "delvefold:ores/copper",
                        "copper",
                        "Invalid:Rule",
                        "example:copper_ore",
                        HostKind.STONE,
                        1,
                        1,
                        1,
                        Evidence.CONVENTIONAL_TAG,
                        false,
                        false,
                        false));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Family(
                        "delvefold:ores/copper",
                        "copper",
                        "delvefold_copper",
                        "example:copper_ore",
                        HostKind.STONE,
                        2,
                        1,
                        1,
                        Evidence.CONVENTIONAL_TAG,
                        false,
                        false,
                        false));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Family(
                        "delvefold:ores/copper",
                        "copper",
                        "delvefold_copper",
                        "example:copper_ore",
                        HostKind.STONE,
                        1,
                        1,
                        2,
                        Evidence.CONVENTIONAL_TAG,
                        false,
                        false,
                        false));
    }

    private static List<Family> families(int startInclusive, int endExclusive) {
        return IntStream.range(startInclusive, endExclusive)
                .mapToObj(OreLibraryViewTest::family)
                .toList();
    }

    private static Family family(int index) {
        return new Family(
                "delvefold:ores/material_" + index,
                "material_" + index,
                "delvefold_material_" + index,
                "example:material_" + index + "_ore",
                HostKind.STONE,
                1,
                1,
                1,
                Evidence.CONVENTIONAL_TAG,
                false,
                false,
                false);
    }
}
