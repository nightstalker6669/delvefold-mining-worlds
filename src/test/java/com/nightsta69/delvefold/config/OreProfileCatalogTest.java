package com.nightsta69.delvefold.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.validation.RegistryLookup;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
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

    @Test
    void strictCreateRejectsBuiltInAndLocalCollisionsWithoutChangingBytes() throws Exception {
        OreProfileCatalog catalog = catalog();

        var builtIn = catalog.createNew("empty", OrePresets.rich());
        assertFalse(builtIn.saved());
        assertFalse(Files.exists(directory.resolve("profiles/empty.json")));

        var first = catalog.createNew("detected_ores", OrePresets.empty());
        assertTrue(first.saved(), first::message);
        Path target = directory.resolve("profiles/detected_ores.json");
        byte[] before = Files.readAllBytes(target);

        var replacement = catalog.createNew("detected_ores", OrePresets.rich());
        assertFalse(replacement.saved());
        assertArrayEquals(before, Files.readAllBytes(target));
        assertEquals(0, catalog.load("detected_ores").rules().size());
    }

    @Test
    void strictCreateRejectsAnEcosystemCollision() throws Exception {
        var ecosystem = new OreProfileDocument(
                OreProfileDocument.CURRENT_SCHEMA_VERSION, 0, "ecosystem", OrePresets.empty().rules());
        EcosystemProfileRegistry.replaceDatapackProfiles(Map.of(
                "ecosystem", new EcosystemProfileRegistry.RegisteredProfile(ecosystem, "test")));
        try {
            OreProfileCatalog catalog = catalog();
            var result = catalog.createNew("ecosystem", OrePresets.rich());

            assertFalse(result.saved());
            assertFalse(Files.exists(directory.resolve("profiles/ecosystem.json")));
        } finally {
            EcosystemProfileRegistry.replaceDatapackProfiles(Map.of());
        }
    }

    @Test
    void strictCreateNormalizesIdentityAndRevisionWithoutMutatingItsSource() throws Exception {
        OreProfileDocument source = new OreProfileDocument(
                OreProfileDocument.CURRENT_SCHEMA_VERSION, 42L, "source", OrePresets.rich().rules());

        var result = catalog().createNew("new_profile", source);

        assertTrue(result.saved(), result::message);
        assertEquals("new_profile", result.profile().profile());
        assertEquals(0L, result.profile().revision());
        assertEquals("source", source.profile());
        assertEquals(42L, source.revision());
    }

    private OreProfileCatalog catalog() {
        ConfigPaths paths = new ConfigPaths(directory, directory.resolve("ores.json"), directory.resolve("settings.json"));
        return new OreProfileCatalog(paths, RegistryLookup.SKIP);
    }
}
