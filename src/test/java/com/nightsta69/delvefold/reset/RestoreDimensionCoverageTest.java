package com.nightsta69.delvefold.reset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class RestoreDimensionCoverageTest {
    @Test
    void restoreCoversClassicAndExpansiveVariantsForEveryTerrain() throws Exception {
        List<String> expected = List.of(
                "delve_cavern",
                "delve_cavern_expansive",
                "delve_flat",
                "delve_flat_expansive",
                "delve_wild",
                "delve_wild_expansive");

        assertEquals(expected, DelvefoldDimensionFolders.ALL);
        assertThrows(UnsupportedOperationException.class, () -> DelvefoldDimensionFolders.ALL.add("unsafe"));

        // WorldRestoreService loads Minecraft server classes, so verify its use of the
        // independently tested immutable folder catalog without loading it in plain JUnit.
        String restore =
                Files.readString(Path.of("src/main/java/com/nightsta69/delvefold/reset/WorldRestoreService.java"));
        assertTrue(restore.contains("private static final List<String> DIMENSIONS = DelvefoldDimensionFolders.ALL;"));
        assertTrue(restore.contains("return DIMENSIONS;"));
    }
}
