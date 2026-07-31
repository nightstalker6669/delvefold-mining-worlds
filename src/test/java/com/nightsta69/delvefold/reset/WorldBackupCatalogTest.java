package com.nightsta69.delvefold.reset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.ConfigJson;
import com.nightsta69.delvefold.config.OrePresets;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.TerrainVariant;
import com.nightsta69.delvefold.config.model.WorldSettingsDocument;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldBackupCatalogTest {
    @TempDir
    Path saveRoot;

    @Test
    void listsOnlyContainedBackupsAndReportsRestoreReadiness() throws Exception {
        String id = "20260731-120000-11111111-1111-1111-1111-111111111111";
        Path backup = saveRoot.resolve("delvefold_backups").resolve(id);
        Files.createDirectories(backup.resolve("dimensions/delvefold/delve_flat"));
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
        assertTrue(summary.restorable());
        assertTrue(summary.valid());
        assertEquals(backup, catalog.resolve(id));
        assertThrows(java.io.IOException.class, () -> catalog.resolve("../escape"));

        assertTrue(catalog.setPinned(id, true));
        assertThrows(java.io.IOException.class, () -> catalog.delete(id));
        assertTrue(catalog.setPinned(id, false));
        assertTrue(catalog.delete(id));
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
}
