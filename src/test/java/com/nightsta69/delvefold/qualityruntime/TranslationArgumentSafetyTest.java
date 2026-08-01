package com.nightsta69.delvefold.qualityruntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.Test;

/** Guards translations that display registry and filesystem identifiers. */
class TranslationArgumentSafetyTest {
    @Test
    void resourceLocationsAreConvertedForOrePickerText() throws IOException {
        String source = ProductionSources.read("client/gui/widget/OreIconButton.java");

        assertEquals(2, occurrences(source, "entry.id().toString()"));
        assertFalse(source.contains("entry.translatedName(), entry.id()"));
    }

    @Test
    void commandTranslationArgumentsUseSupportedTextValues() throws IOException {
        String source = ProductionSources.read("command/DelvefoldCommands.java");

        assertTrue(source.contains("exported.getFileName().toString()"));
        assertTrue(source.contains("match.toString()"));
        assertFalse(source.contains("exported.getFileName())"));
        assertFalse(source.contains("ore_scan_row\", match)"));
    }

    private static int occurrences(String value, String token) {
        int count = 0;
        int start = 0;
        while ((start = value.indexOf(token, start)) >= 0) {
            count++;
            start += token.length();
        }
        return count;
    }
}
