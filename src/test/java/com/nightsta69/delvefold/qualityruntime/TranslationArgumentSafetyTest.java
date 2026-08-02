package com.nightsta69.delvefold.qualityruntime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.Test;

/** Guards translations that display registry and filesystem identifiers. */
class TranslationArgumentSafetyTest {
    @Test
    void commandTranslationArgumentsUseSupportedTextValues() throws IOException {
        String source = ProductionSources.read("command/DelvefoldCommands.java");

        assertTrue(source.contains("exported.getFileName().toString()"));
        assertTrue(source.contains("match.toString()"));
        assertFalse(source.contains("exported.getFileName())"));
        assertFalse(source.contains("ore_scan_row\", match)"));
    }
}
