package com.nightsta69.delvefold.compat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class OptionalCompatIsolationTest {
    private static final Path JAVA_ROOT = Path.of("src/main/java/com/nightsta69/delvefold");
    private static final Path BUILD_FILE = Path.of("build.gradle");
    private static final Path MOD_METADATA = Path.of("src/main/templates/META-INF/neoforge.mods.toml");

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

    @Test
    void compatibilityFixturesStayOutOfPublishedDependencyScopesAndRequiredMetadata() throws IOException {
        String build = Files.readString(BUILD_FILE);
        String metadata = Files.readString(MOD_METADATA);

        assertTrue(build.contains("mekanismGeneratorsTestRuntime"));
        assertTrue(build.contains("mekanismToolsTestRuntime"));
        assertTrue(build.contains("mekanismAdditionsTestRuntime"));
        assertTrue(build.contains("canBeConsumed = false"));
        assertTrue(build.contains("transitive = false"));
        assertTrue(build.contains("if (includeCombinedTestModsRuntime)"));

        Pattern forbiddenPublishedScope = Pattern.compile(
                "^\\s*(?:api|implementation|runtimeOnly)\\s+.*(?:Mekanism|enderio|athena|silentgems|appliedenergistics|worldedit).*$",
                Pattern.MULTILINE);
        assertFalse(forbiddenPublishedScope.matcher(build).find());
        for (String fixtureModId : List.of(
                "mekanism",
                "mekanismgenerators",
                "mekanismtools",
                "mekanismadditions",
                "enderio",
                "athena",
                "silentgems",
                "silentlib",
                "ae2",
                "guideme",
                "worldedit")) {
            assertFalse(
                    metadata.contains("modId=\"" + fixtureModId + "\""),
                    () -> "Development fixture became a declared mod dependency: " + fixtureModId);
        }
    }
}
