package com.nightsta69.delvefold.reset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.ConfigJson;
import com.nightsta69.delvefold.config.OrePresets;
import com.nightsta69.delvefold.config.model.GameplayPreset;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.TerrainVariant;
import com.nightsta69.delvefold.config.model.WorldIdentitySettings;
import com.nightsta69.delvefold.config.model.WorldSettingsDocument;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldBackupCatalogTest {
    @TempDir
    Path saveRoot;

    @TempDir
    Path externalTemporaryDirectory;

    @Test
    void listsOnlyContainedBackupsAndReportsRestoreReadiness() throws Exception {
        String id = "20260731-120000-11111111-1111-1111-1111-111111111111";
        Path backup = saveRoot.resolve("delvefold_backups").resolve(id);
        Files.createDirectories(backup.resolve("dimensions/delvefold/delve_flat"));
        Files.writeString(backup.resolve("dimensions/delvefold/delve_flat/level.dat"), "dimension-data");
        Files.createDirectories(backup.resolve("config/serverconfig/delvefold"));
        Files.writeString(backup.resolve("config/serverconfig/delvefold/settings.json"),
                ConfigJson.GSON.toJson(WorldSettingsDocument.uninitialized()));
        Files.writeString(backup.resolve("config/serverconfig/delvefold/ores.json"),
                ConfigJson.GSON.toJson(OrePresets.create(OrePreset.VANILLA_BALANCED)));
        PendingWorldOperation operation = new PendingWorldOperation(
                PendingWorldOperation.CURRENT_SCHEMA_VERSION,
                "11111111-1111-1111-1111-111111111111",
                WorldOperationType.RECREATE,
                TerrainMode.FLAT,
                TerrainMode.CAVERN,
                TerrainVariant.EXPANSIVE,
                null,
                null,
                BackupMode.KEEP_BACKUP,
                false,
                1234,
                "tester");
        Files.writeString(backup.resolve("operation.json"), ConfigJson.GSON.toJson(operation));

        WorldBackupCatalog catalog = new WorldBackupCatalog(saveRoot);
        var summary = catalog.list().getFirst();
        assertEquals(id, summary.id());
        assertFalse(summary.restorable());
        assertTrue(summary.valid());
        assertTrue(summary.legacy());
        assertEquals(-1L, summary.sizeBytes());
        assertEquals(backup, catalog.resolve(id));
        assertThrows(java.io.IOException.class, () -> catalog.resolve("../escape"));

        BackupManifest manifest = new BackupManifestService().createVerifiedManifest(backup).manifest();
        summary = catalog.list().getFirst();
        assertTrue(summary.restorable());
        assertTrue(summary.manifestPresent());
        assertTrue(summary.verified());
        assertFalse(summary.legacy());
        assertEquals(manifest.totalBytes(), summary.sizeBytes());

        Path settings = backup.resolve("config/serverconfig/delvefold/settings.json");
        String settingsJson = Files.readString(settings);
        Files.delete(settings);
        summary = catalog.list().getFirst();
        assertFalse(summary.valid());
        assertEquals(-1L, summary.sizeBytes());
        Files.writeString(settings, settingsJson);

        assertTrue(catalog.setPinned(id, true));
        assertFalse(catalog.setPinned(id, true));
        assertThrows(java.io.IOException.class, () -> catalog.delete(id));
        assertTrue(catalog.setPinned(id, false));
        assertFalse(catalog.setPinned(id, false));
        assertTrue(catalog.delete(id));
    }

    @Test
    void catalogRejectsEmptyUnknownAndWrongActiveDimensionSnapshots() throws Exception {
        createCatalogBackup("empty", WorldSettingsDocument.uninitialized(), TerrainMode.FLAT,
                "delve_flat", false);
        createCatalogBackup("unknown", WorldSettingsDocument.uninitialized(), TerrainMode.FLAT,
                "not_a_delvefold_dimension", true);
        WorldSettingsDocument expansiveCavern = WorldSettingsDocument.uninitialized().initialize(
                TerrainMode.CAVERN,
                OrePreset.VANILLA_BALANCED,
                GameplayPreset.SAFE,
                WorldIdentitySettings.defaults().withTerrainVariant(TerrainVariant.EXPANSIVE));
        createCatalogBackup("wrong-active", expansiveCavern, TerrainMode.CAVERN,
                "delve_cavern", true);
        createCatalogBackup("valid-active", expansiveCavern, TerrainMode.CAVERN,
                "delve_cavern_expansive", true);
        createCatalogBackup("valid-legacy-expansive", WorldSettingsDocument.uninitialized(), TerrainMode.FLAT,
                "delve_flat_expansive", true);

        var summaries = new WorldBackupCatalog(saveRoot).list().stream()
                .collect(java.util.stream.Collectors.toMap(
                        WorldBackupCatalog.BackupSummary::id, java.util.function.Function.identity()));

        assertFalse(summaries.get("empty").valid());
        assertFalse(summaries.get("unknown").valid());
        assertFalse(summaries.get("wrong-active").valid());
        assertTrue(summaries.get("valid-active").valid());
        assertTrue(summaries.get("valid-active").legacy());
        assertFalse(summaries.get("valid-active").restorable());
        assertTrue(summaries.get("valid-legacy-expansive").valid());
        assertTrue(summaries.get("valid-legacy-expansive").legacy());
    }

    @Test
    void legacyPendingRecreateDefaultsToClassicTerrainScale() {
        PendingWorldOperation operation = ConfigJson.GSON.fromJson("""
                {
                  "schema_version": 1,
                  "operation_id": "11111111-1111-1111-1111-111111111111",
                  "type": "RECREATE",
                  "source_terrain": "flat",
                  "target_terrain": "cavern",
                  "backup_mode": "KEEP_BACKUP",
                  "reset_ore_configuration": false,
                  "created_at_epoch_millis": 1234,
                  "requested_by": "tester"
                }
                """, PendingWorldOperation.class);

        assertEquals(TerrainVariant.CLASSIC, operation.targetVariant());
    }

    @Test
    void deletePreflightsTheWholeTreeBeforeRemovingAnything() throws Exception {
        String id = "unsafe-backup";
        Path backup = saveRoot.resolve("delvefold_backups").resolve(id);
        Files.createDirectories(backup);
        Path retained = backup.resolve("retained.dat");
        Files.writeString(retained, "keep me");
        Files.createSymbolicLink(backup.resolve("unsafe-link"), retained);

        WorldBackupCatalog catalog = new WorldBackupCatalog(saveRoot);
        assertThrows(java.io.IOException.class, () -> catalog.delete(id));
        assertTrue(Files.exists(retained));
        assertTrue(Files.isSymbolicLink(backup.resolve("unsafe-link")));
    }

    @Test
    void directOperationsRejectASymbolicLinkBackupRoot() throws Exception {
        String id = "external-backup";
        Path externalRoot = externalTemporaryDirectory.resolve("external-backups");
        Path externalBackup = Files.createDirectories(externalRoot.resolve(id));
        Path retained = externalBackup.resolve("retained.dat");
        Files.writeString(retained, "must remain outside the save");
        Files.createSymbolicLink(saveRoot.resolve("delvefold_backups"), externalRoot);

        WorldBackupCatalog catalog = new WorldBackupCatalog(saveRoot);

        assertThrows(java.io.IOException.class, catalog::list);
        assertThrows(java.io.IOException.class, () -> catalog.resolve(id));
        assertThrows(java.io.IOException.class, () -> catalog.setPinned(id, true));
        assertThrows(java.io.IOException.class, () -> catalog.delete(id));
        assertFalse(Files.exists(externalBackup.resolve(".pinned")));
        assertEquals("must remain outside the save", Files.readString(retained));
    }

    @Test
    void pinningRejectsADanglingSymbolicLinkMarkerWithoutWritingOutsideTheBackup() throws Exception {
        String id = "dangling-pin-backup";
        Path backup = Files.createDirectories(saveRoot.resolve("delvefold_backups").resolve(id));
        Path externalMarker = externalTemporaryDirectory.resolve("external-pin-marker");
        Files.createSymbolicLink(backup.resolve(".pinned"), externalMarker);

        WorldBackupCatalog catalog = new WorldBackupCatalog(saveRoot);
        assertThrows(java.io.IOException.class, () -> catalog.setPinned(id, true));
        assertThrows(java.io.IOException.class, () -> catalog.setPinned(id, false));
        assertFalse(Files.exists(externalMarker));
        assertTrue(Files.isSymbolicLink(backup.resolve(".pinned")));
    }

    @Test
    void pinAndUnpinRejectANonRegularMarker() throws Exception {
        String id = "directory-pin-backup";
        Path backup = Files.createDirectories(saveRoot.resolve("delvefold_backups").resolve(id));
        Path marker = Files.createDirectory(backup.resolve(".pinned"));
        WorldBackupCatalog catalog = new WorldBackupCatalog(saveRoot);

        assertThrows(java.io.IOException.class, () -> catalog.setPinned(id, true));
        assertThrows(java.io.IOException.class, () -> catalog.setPinned(id, false));
        assertTrue(Files.isDirectory(marker));
    }

    private Path createCatalogBackup(
            String id,
            WorldSettingsDocument settings,
            TerrainMode sourceTerrain,
            String dimensionFolder,
            boolean withData) throws Exception {
        Path backup = saveRoot.resolve("delvefold_backups").resolve(id);
        Path dimension = Files.createDirectories(
                backup.resolve("dimensions/delvefold").resolve(dimensionFolder));
        if (withData) {
            Files.writeString(dimension.resolve("level.dat"), "dimension-data");
        }
        Path config = Files.createDirectories(backup.resolve("config/serverconfig/delvefold"));
        Files.writeString(config.resolve("settings.json"), ConfigJson.GSON.toJson(settings));
        Files.writeString(config.resolve("ores.json"),
                ConfigJson.GSON.toJson(OrePresets.create(OrePreset.VANILLA_BALANCED)));
        PendingWorldOperation operation = new PendingWorldOperation(
                PendingWorldOperation.CURRENT_SCHEMA_VERSION,
                "11111111-1111-1111-1111-111111111111",
                WorldOperationType.RECREATE,
                sourceTerrain,
                TerrainMode.FLAT,
                TerrainVariant.CLASSIC,
                null,
                null,
                BackupMode.KEEP_BACKUP,
                false,
                1234,
                "tester");
        Files.writeString(backup.resolve("operation.json"), ConfigJson.GSON.toJson(operation));
        return backup;
    }
}
