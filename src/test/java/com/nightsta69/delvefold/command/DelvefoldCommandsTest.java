package com.nightsta69.delvefold.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class DelvefoldCommandsTest {
    @Test
    void registersDelvefoldAsTheOnlyCommandRoot() {
        assertEquals(List.of("delvefold"), DelvefoldCommandNames.REGISTERED_ROOTS);
    }

    @Test
    void weightedTargetCommandsAreRegisteredWithoutRemovingLegacyAddForms() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/nightsta69/delvefold/command/DelvefoldCommands.java"));

        assertTrue(source.contains("Commands.literal(\"set-weight\")"));
        assertTrue(source.contains("Commands.literal(\"set-tag-weight\")"));
        assertTrue(source.contains(".executes(context -> addTarget(context, OreTarget.DEFAULT_WEIGHT))"),
                "Exact-target add must still execute without a weight");
        assertTrue(source.contains(".executes(context -> addTagTarget(context, OreTarget.DEFAULT_WEIGHT))"),
                "Tag-target add must still execute without a weight");
        assertTrue(source.contains("OreTarget.MIN_WEIGHT, OreTarget.MAX_WEIGHT"),
                "Command arguments must use the domain's 1-1000 bounds");
        assertTrue(source.contains("if (matches > 1)"),
                "Weight setters must reject ambiguous duplicate source IDs instead of changing every target");
    }
}
