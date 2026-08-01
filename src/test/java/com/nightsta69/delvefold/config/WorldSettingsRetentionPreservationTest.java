package com.nightsta69.delvefold.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.model.BackupRetentionSettings;
import com.nightsta69.delvefold.config.model.GameplayPreset;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.WorldSettingsDocument;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WorldSettingsRetentionPreservationTest {
    private static final BackupRetentionSettings RETENTION =
            new BackupRetentionSettings(true, 8, 30, 4_000_000_000L);

    @Test
    void ordinarySettingsAndLifecycleChangesPreserveRetentionPolicy() {
        WorldSettingsDocument configured = WorldSettingsDocument.uninitialized()
                .withBackupRetention(RETENTION);
        WorldSettingsDocument initialized = configured.initialize(
                TerrainMode.FLAT, OrePreset.VANILLA_BALANCED, GameplayPreset.SAFE);

        assertEquals(RETENTION, initialized.backupRetention());
        assertEquals(RETENTION, initialized.withActiveProfile("custom_profile").backupRetention());
        assertEquals(RETENTION, initialized.withPortal(initialized.portal()).backupRetention());
        assertEquals(RETENTION, initialized.withGameplay(initialized.gameplay()).backupRetention());
        assertEquals(RETENTION, initialized.withIdentity(initialized.identity()).backupRetention());
        assertEquals(RETENTION, initialized.markDeleted("delete-operation").backupRetention());
        assertEquals(RETENTION, initialized.recreate(
                TerrainMode.CAVERN, OrePreset.RICH, GameplayPreset.HOSTILE, "recreate-operation")
                .backupRetention());
    }

    @Test
    void activatingAStoredOreProfileCarriesRetentionIntoTheSavedSettings() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/nightsta69/delvefold/config/DelvefoldConfigService.java"));
        int activation = source.indexOf("public ConfigWriteResult activateProfile");
        int deletion = source.indexOf("public ProfileDeleteResult deleteProfile", activation);
        String method = source.substring(activation, deletion);

        assertTrue(method.contains("before.settings().backupRetention()"),
                "Profile activation must not silently reset an administrator's retention policy");
    }
}
