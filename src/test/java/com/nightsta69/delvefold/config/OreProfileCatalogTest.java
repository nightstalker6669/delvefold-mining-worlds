package com.nightsta69.delvefold.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.validation.RegistryLookup;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OreProfileCatalogTest {
    @TempDir
    Path directory;

    @Test
    void listsBuiltInsAndRoundTripsLocalProfile() throws Exception {
        OreProfileCatalog catalog = catalog();
        assertTrue(catalog.list().stream().anyMatch(profile -> profile.id().equals("vanilla_balanced")));

        var result = catalog.saveAs("my_pack", OrePresets.rich(), false);
        assertTrue(result.saved(), result::message);
        assertEquals("my_pack", catalog.load("my_pack").profile());
        assertTrue(catalog.list().stream().anyMatch(profile -> profile.id().equals("my_pack") && profile.localOverride()));
    }

    @Test
    void importRejectsTraversalAndInvalidDocuments() throws Exception {
        OreProfileCatalog catalog = catalog();
        assertThrows(IllegalArgumentException.class, () -> catalog.saveAs("../escape", OrePresets.empty(), false));
        assertFalse(catalog.importJson("broken", "{", false).saved());
        assertFalse(Files.exists(directory.resolve("profiles/broken.json")));
    }

    @Test
    void clipboardAndServerFileExportsAreBounded() throws Exception {
        OreProfileCatalog catalog = catalog();
        Path exported = catalog.exportFile("empty", "empty-profile.json");
        assertTrue(Files.isRegularFile(exported));
        assertTrue(catalog.exportJson("empty").contains("\"profile\": \"empty\""));
    }

    private OreProfileCatalog catalog() {
        ConfigPaths paths = new ConfigPaths(directory, directory.resolve("ores.json"), directory.resolve("settings.json"));
        return new OreProfileCatalog(paths, RegistryLookup.SKIP);
    }
}
