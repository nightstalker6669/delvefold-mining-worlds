package com.nightsta69.delvefold.qualityruntime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class SourceHygieneCharacterizationTest {
    private static final Pattern SUPPRESSION = Pattern.compile("@SuppressWarnings\\(\\\"([^\\\"]+)\\\"\\)");
    private static final Map<String, Set<String>> RELEASED_SUPPRESSIONS = Map.of(
            "config/DelvefoldPermissions.java", Set.of("unchecked"),
            "compat/jei/DelvefoldJeiPortalCategory.java", Set.of("removal"));

    @Test
    void productionHasNoConsoleDebuggingStackTracePrintingOrWorkMarkers() throws IOException {
        for (Path path : ProductionSources.allJavaFiles()) {
            String source = Files.readString(path);
            assertFalse(source.contains("System.out."), () -> "Console debugging remains in " + path);
            assertFalse(source.contains("System.err."), () -> "Console error printing remains in " + path);
            assertFalse(source.contains(".printStackTrace("), () -> "Direct stack-trace printing remains in " + path);
            assertFalse(source.matches("(?s).*\\b(?:TODO|FIXME)\\b.*"), () -> "Unresolved work marker remains in " + path);
        }
    }

    @Test
    void suppressionsRemainNarrowAndNoNewSuppressionsAreAddedSilently() throws IOException {
        Path root = Path.of("src/main/java/com/nightsta69/delvefold");
        for (Path path : ProductionSources.allJavaFiles()) {
            String source = Files.readString(path);
            assertFalse(source.contains("@SuppressWarnings({"),
                    () -> "Multiple warning categories are hidden at once in " + path);
            assertFalse(path.getFileName().toString().equals("package-info.java")
                            && source.contains("@SuppressWarnings"),
                    () -> "Package-wide warning suppression is forbidden in " + path);

            String relative = root.relativize(path).toString().replace('\\', '/');
            Matcher matcher = SUPPRESSION.matcher(source);
            while (matcher.find()) {
                String category = matcher.group(1);
                assertTrue(RELEASED_SUPPRESSIONS.getOrDefault(relative, Set.of()).contains(category),
                        () -> "Unexpected @SuppressWarnings(\"" + category + "\") in " + relative);
                String declarationTail = source.substring(matcher.end(), Math.min(source.length(), matcher.end() + 120));
                assertFalse(declarationTail.stripLeading().startsWith("package "),
                        () -> "Suppression applies to an entire package in " + relative);
            }
        }
    }
}
