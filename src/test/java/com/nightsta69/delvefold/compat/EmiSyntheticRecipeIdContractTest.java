package com.nightsta69.delvefold.compat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Guards EMI's leading-slash convention for recipes absent from Minecraft's recipe manager. */
class EmiSyntheticRecipeIdContractTest {
    private static final Path EMI_ROOT = Path.of("src/main/java/com/nightsta69/delvefold/compat/emi");

    @Test
    void syntheticRecipeIdsUseEmiSyntheticPathPrefix() throws IOException {
        String ids = Files.readString(EMI_ROOT.resolve("DelvefoldEmiIds.java"));
        String portalRecipe = Files.readString(EMI_ROOT.resolve("DelvefoldEmiPortalRecipe.java"));
        String plugin = normalize(Files.readString(EMI_ROOT.resolve("DelvefoldEmiPlugin.java")));

        assertTrue(ids.contains("SYNTHETIC_PATH_PREFIX = \"/\""));
        assertTrue(ids.contains("SYNTHETIC_PATH_PREFIX + path"));
        assertTrue(portalRecipe.contains("DelvefoldEmiIds.syntheticRecipe("));
        assertTrue(plugin.contains("DelvefoldEmiIds.syntheticRecipe(Delvefold.MOD_ID, \"info/portal_activation\")"));
    }

    @Test
    void categoryIdRemainsARegularResourceLocation() throws IOException {
        String plugin = normalize(Files.readString(EMI_ROOT.resolve("DelvefoldEmiPlugin.java")));

        assertTrue(plugin.contains("new EmiRecipeCategory( ResourceLocation.fromNamespaceAndPath("));
        assertFalse(plugin.contains("new EmiRecipeCategory( DelvefoldEmiIds.syntheticRecipe("));
    }

    private static String normalize(String source) {
        return source.replaceAll("\\s+", " ");
    }
}
