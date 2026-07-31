package com.nightsta69.delvefold.reset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.ConfigJson;
import com.nightsta69.delvefold.config.model.TerrainMode;
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
        Files.writeString(backup.resolve("config/serverconfig/delvefold/settings.json"), "{}");
        Files.writeString(backup.resolve("config/serverconfig/delvefold/ores.json"), "{}");
        PendingWorldOperation operation = new PendingWorldOperation(
                PendingWorldOperation.CURRENT_SCHEMA_VERSION,
                "11111111-1111-1111-1111-111111111111",
                WorldOperationType.RECREATE,
                TerrainMode.FLAT,
                TerrainMode.CAVERN,
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
}
