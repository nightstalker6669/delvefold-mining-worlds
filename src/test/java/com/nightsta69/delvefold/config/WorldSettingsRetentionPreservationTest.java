package com.nightsta69.delvefold.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nightsta69.delvefold.config.model.BackupRetentionSettings;
import com.nightsta69.delvefold.config.model.GameplayPreset;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.WorldSettingsDocument;
import com.nightsta69.delvefold.config.validation.ValidationReport;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorldSettingsRetentionPreservationTest {
    private static final BackupRetentionSettings RETENTION = new BackupRetentionSettings(true, 8, 30, 4_000_000_000L);

    @Test
    void ordinarySettingsAndLifecycleChangesPreserveRetentionPolicy() {
        WorldSettingsDocument configured = WorldSettingsDocument.uninitialized().withBackupRetention(RETENTION);
        WorldSettingsDocument initialized =
                configured.initialize(TerrainMode.FLAT, OrePreset.VANILLA_BALANCED, GameplayPreset.SAFE);

        assertEquals(RETENTION, initialized.backupRetention());
        assertEquals(RETENTION, initialized.withActiveProfile("custom_profile").backupRetention());
        assertEquals(RETENTION, initialized.withPortal(initialized.portal()).backupRetention());
        assertEquals(RETENTION, initialized.withGameplay(initialized.gameplay()).backupRetention());
        assertEquals(RETENTION, initialized.withIdentity(initialized.identity()).backupRetention());
        assertEquals(RETENTION, initialized.markDeleted("delete-operation").backupRetention());
        assertEquals(
                RETENTION,
                initialized
                        .recreate(TerrainMode.CAVERN, OrePreset.RICH, GameplayPreset.HOSTILE, "recreate-operation")
                        .backupRetention());
    }

    @Test
    void activatingAStoredOreProfileCarriesRetentionIntoTheSavedSettings() {
        WorldSettingsDocument settings = WorldSettingsDocument.uninitialized().withBackupRetention(RETENTION);
        OreProfileDocument ores = OrePresets.balanced();
        ConfigSnapshot before =
                new ConfigSnapshot(ores, settings, new ValidationReport(List.of()), Instant.EPOCH, "test");

        ConfigDocumentTransitions.ProfileActivation activation =
                ConfigDocumentTransitions.activateProfile(before, OrePresets.rich());

        assertEquals(RETENTION, activation.settings().backupRetention());
        assertEquals("rich", activation.settings().activeProfileId());
    }
}
