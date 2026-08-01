package com.nightsta69.delvefold.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.model.WorldSettingsDocument;
import com.nightsta69.delvefold.config.validation.RegistryLookup;
import com.nightsta69.delvefold.config.validation.ValidationReport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfileCatalogOperationsTest {
    @TempDir
    Path directory;

    @Test
    void nullBlankAndExactActiveSelectionsReturnPublishedIdentityWithoutFilesystemWrites() throws Exception {
        ProfileCatalogOperations operations = operations();
        ConfigSnapshot snapshot = snapshot(OrePresets.rich());

        assertSame(snapshot.ores(), operations.load(snapshot, null));
        assertSame(snapshot.ores(), operations.load(snapshot, "  "));
        assertSame(snapshot.ores(), operations.load(snapshot, " rich "));
        assertFalse(Files.exists(directory.resolve("profiles")));
        assertFalse(Files.exists(directory.resolve("imports")));
        assertFalse(Files.exists(directory.resolve("exports")));
    }

    @Test
    void nonActiveBundledSelectionPreservesCatalogDirectoryPreparationWithoutWritingProfiles() throws Exception {
        ProfileCatalogOperations operations = operations();

        OreProfileDocument selected = operations.load(snapshot(OrePresets.rich()), " empty ");

        assertEquals("empty", selected.profile());
        assertTrue(selected.rules().isEmpty());
        assertTrue(Files.isDirectory(directory.resolve("profiles")));
        assertTrue(Files.isDirectory(directory.resolve("imports")));
        assertTrue(Files.isDirectory(directory.resolve("exports")));
        try (var profiles = Files.list(directory.resolve("profiles"))) {
            assertTrue(profiles.findAny().isEmpty());
        }
    }

    @Test
    void strictCatalogLoadRemainsDistinctFromPublishedActiveSelection() throws Exception {
        ProfileCatalogOperations operations = operations();
        ConfigSnapshot snapshot = snapshot(OrePresets.rich());
        assertTrue(operations.createFromPreset("rich", OrePreset.EMPTY, true).saved());

        OreProfileDocument published = operations.load(snapshot, "rich");
        OreProfileDocument stored = operations.loadCatalog("rich");

        assertSame(snapshot.ores(), published);
        assertEquals("rich", stored.profile());
        assertTrue(stored.rules().isEmpty());
        assertFalse(published.rules().isEmpty());
    }

    @Test
    void saveAndDuplicateRemainInactiveUntilTheFacadePublishesAnActivation() throws Exception {
        ProfileCatalogOperations operations = operations();
        ConfigSnapshot snapshot = snapshot(OrePresets.rich());

        var saved = operations.saveCurrent(snapshot, "source", false);
        var duplicate = operations.duplicate("source", "copy", false);

        assertTrue(saved.saved(), saved::message);
        assertTrue(duplicate.saved(), duplicate::message);
        assertSame(snapshot.ores(), operations.load(snapshot, null));
        assertEquals("source", operations.loadCatalog("source").profile());
        assertEquals("copy", operations.loadCatalog("copy").profile());
        assertEquals(snapshot.ores().rules(), operations.loadCatalog("copy").rules());
    }

    private ProfileCatalogOperations operations() {
        ConfigPaths paths =
                new ConfigPaths(directory, directory.resolve("ores.json"), directory.resolve("settings.json"));
        return new ProfileCatalogOperations(new OreProfileCatalog(paths, RegistryLookup.SKIP));
    }

    private static ConfigSnapshot snapshot(OreProfileDocument ores) {
        WorldSettingsDocument settings = WorldSettingsDocument.uninitialized().withActiveProfile(ores.profile());
        return new ConfigSnapshot(ores, settings, new ValidationReport(List.of()), Instant.EPOCH, "test");
    }
}
