package com.nightsta69.delvefold.compat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class OptionalCompatIsolationTest {
    private static final Path JAVA_ROOT = Path.of("src/main/java/com/nightsta69/delvefold");

    @Test
    void optionalViewerApisStayInsideTheirDedicatedPackages() throws IOException {
        try (var sources = Files.walk(JAVA_ROOT)) {
            List<Path> javaFiles =
                    sources.filter(path -> path.toString().endsWith(".java")).toList();
            for (Path source : javaFiles) {
                String normalized = source.toString().replace('\\', '/');
                String text = Files.readString(source);
                if (text.contains("mezz.jei")) {
                    assertTrue(
                            normalized.contains("/compat/jei/"),
                            () -> "JEI reference escaped optional package: " + source);
                }
                if (text.contains("dev.emi")) {
                    assertTrue(
                            normalized.contains("/compat/emi/"),
                            () -> "EMI reference escaped optional package: " + source);
                }
            }
        }
    }

    @Test
    void commonPortalGuideHasNoRecipeViewerDependency() throws IOException {
        String text = Files.readString(JAVA_ROOT.resolve("compat/PortalConstructionGuide.java"));
        assertFalse(text.contains("mezz.jei"));
        assertFalse(text.contains("dev.emi"));
    }
}
