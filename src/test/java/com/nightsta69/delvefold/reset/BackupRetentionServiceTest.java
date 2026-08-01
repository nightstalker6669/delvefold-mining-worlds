package com.nightsta69.delvefold.reset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.ConfigJson;
import com.nightsta69.delvefold.config.OrePresets;
import com.nightsta69.delvefold.config.model.BackupRetentionSettings;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.TerrainVariant;
import com.nightsta69.delvefold.config.model.WorldSettingsDocument;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BackupRetentionServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");
    private static final String RESTORE_OPERATION_ID = "11111111-1111-1111-1111-111111111111";
    private static final String WORLD_OPERATION_ID = "22222222-2222-2222-2222-222222222222";

    @TempDir
    Path saveRoot;

    @Test
    void derivesEveryPersistedPendingBackupReference() throws Exception {
        Path config = saveRoot.resolve("serverconfig/delvefold");
        Files.createDirectories(config);
        PendingWorldRestore restore = new PendingWorldRestore(
                PendingWorldRestore.CURRENT_SCHEMA_VERSION,
                RESTORE_OPERATION_ID,
                "selected-backup",
                PendingWorldRestore.Phase.STAGED,
                NOW.toEpochMilli(),
                "tester");
        Files.writeString(config.resolve("pending_restore.json"), ConfigJson.GSON.toJson(restore));
        PendingWorldOperation operation =
                operation(WORLD_OPERATION_ID, NOW.minus(1, ChronoUnit.HOURS).toEpochMilli());
        Files.writeString(config.resolve("pending_world_operation.json"), ConfigJson.GSON.toJson(operation));

        assertEquals(
                Set.of(
                        "selected-backup",
                        "20260801-000000-pre-restore-" + RESTORE_OPERATION_ID,
                        "20260731-230000-" + WORLD_OPERATION_ID),
                BackupRetentionService.protectedBackupIds(saveRoot));
    }

    @Test
    void malformedPendingJournalDisablesPruningConservatively() throws Exception {
        Path config = saveRoot.resolve("serverconfig/delvefold");
        Files.createDirectories(config);
        Files.writeString(config.resolve("pending_restore.json"), "not json");

        assertThrows(
                IOException.class,
                () -> BackupRetentionService.preview(saveRoot, new BackupRetentionSettings(true, 2, 0, 0), NOW));
    }

    @Test
    void parseableButIncompletePendingJournalsDisablePruningConservatively() throws Exception {
        Path config = saveRoot.resolve("serverconfig/delvefold");
        Files.createDirectories(config);
        Files.writeString(config.resolve("pending_world_operation.json"), """
                {"schema_version":1,"operation_id":"22222222-2222-2222-2222-222222222222",
                 "backup_mode":"keep_backup","created_at_epoch_millis":1785542400000,
                 "requested_by":"tester"}
                """);
        assertThrows(
                IOException.class,
                () -> BackupRetentionService.preview(saveRoot, new BackupRetentionSettings(true, 2, 0, 0), NOW));

        Files.delete(config.resolve("pending_world_operation.json"));
        Files.writeString(config.resolve("pending_restore.json"), """
                {"schema_version":1,"operation_id":"11111111-1111-1111-1111-111111111111",
                 "backup_id":"selected-backup","created_at_epoch_millis":1785542400000,
                 "requested_by":"tester"}
                """);
        assertThrows(
                IOException.class,
                () -> BackupRetentionService.preview(saveRoot, new BackupRetentionSettings(true, 2, 0, 0), NOW));
    }

    @Test
    void previewAndApplyProtectPendingNewestAndNewlyPinnedBackups() throws Exception {
        createBackup("a-old", NOW.minus(100, ChronoUnit.DAYS).toEpochMilli(), "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        createBackup("b-old", NOW.minus(90, ChronoUnit.DAYS).toEpochMilli(), "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        createBackup("c-newer", NOW.minus(2, ChronoUnit.DAYS).toEpochMilli(), "cccccccc-cccc-cccc-cccc-cccccccccccc");
        createBackup("d-newest", NOW.minus(1, ChronoUnit.DAYS).toEpochMilli(), "dddddddd-dddd-dddd-dddd-dddddddddddd");

        BackupRetentionSettings settings = new BackupRetentionSettings(true, 2, 0, 0);
        BackupRetentionService.Preview preview = BackupRetentionService.preview(saveRoot, settings, NOW);
        assertEquals(
                List.of("a-old", "b-old"),
                preview.plan().prunes().stream()
                        .map(BackupRetentionPlanner.Prune::id)
                        .toList());

        new WorldBackupCatalog(saveRoot).setPinned("a-old", true);
        BackupRetentionService.ApplyResult applied = BackupRetentionService.apply(preview);

        assertEquals(List.of("b-old"), applied.prunedIds());
        assertTrue(applied.failures().containsKey("a-old"));
        assertTrue(Files.isDirectory(saveRoot.resolve("delvefold_backups/a-old")));
        assertFalse(Files.exists(saveRoot.resolve("delvefold_backups/b-old")));
        assertTrue(Files.isDirectory(saveRoot.resolve("delvefold_backups/c-newer")));
        assertTrue(Files.isDirectory(saveRoot.resolve("delvefold_backups/d-newest")));
    }

    @Test
    void pendingRestoreReferenceFlowsIntoThePlanner() throws Exception {
        createBackup(
                "a-selected", NOW.minus(100, ChronoUnit.DAYS).toEpochMilli(), "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        createBackup("b-old", NOW.minus(90, ChronoUnit.DAYS).toEpochMilli(), "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        createBackup("c-newer", NOW.minus(2, ChronoUnit.DAYS).toEpochMilli(), "cccccccc-cccc-cccc-cccc-cccccccccccc");
        createBackup("d-newest", NOW.minus(1, ChronoUnit.DAYS).toEpochMilli(), "dddddddd-dddd-dddd-dddd-dddddddddddd");
        Path config = saveRoot.resolve("serverconfig/delvefold");
        Files.createDirectories(config);
        Files.writeString(
                config.resolve("pending_restore.json"),
                ConfigJson.GSON.toJson(new PendingWorldRestore(
                        PendingWorldRestore.CURRENT_SCHEMA_VERSION,
                        RESTORE_OPERATION_ID,
                        "a-selected",
                        PendingWorldRestore.Phase.REQUESTED,
                        NOW.toEpochMilli(),
                        "tester")));

        BackupRetentionService.Preview preview =
                BackupRetentionService.preview(saveRoot, new BackupRetentionSettings(true, 2, 0, 0), NOW);

        assertTrue(preview.protectedBackupIds().contains("a-selected"));
        assertEquals(
                List.of("b-old"),
                preview.plan().prunes().stream()
                        .map(BackupRetentionPlanner.Prune::id)
                        .toList());
    }

    @Test
    void previewPreservesUnsafeBackupsAndPrunesOnlyVerifiedEligibleBackup() throws Exception {
        createBackup(
                "a-eligible", NOW.minus(100, ChronoUnit.DAYS).toEpochMilli(), "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        Path legacy = createBackup(
                "b-legacy", NOW.minus(90, ChronoUnit.DAYS).toEpochMilli(), "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        Files.delete(legacy.resolve(BackupManifest.FILE_NAME));
        Files.deleteIfExists(legacy.resolve(BackupVerificationReceipt.FILE_NAME));
        Path unverified = createBackup(
                "c-unverified", NOW.minus(80, ChronoUnit.DAYS).toEpochMilli(), "cccccccc-cccc-cccc-cccc-cccccccccccc");
        Files.delete(unverified.resolve(BackupVerificationReceipt.FILE_NAME));
        Path invalid = createBackup(
                "d-invalid", NOW.minus(70, ChronoUnit.DAYS).toEpochMilli(), "dddddddd-dddd-dddd-dddd-dddddddddddd");
        Files.delete(invalid.resolve("config/serverconfig/delvefold/settings.json"));
        createBackup("e-unknown-time", 0L, "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
        createBackup("f-newer", NOW.minus(2, ChronoUnit.DAYS).toEpochMilli(), "ffffffff-ffff-ffff-ffff-ffffffffffff");
        createBackup("g-newest", NOW.minus(1, ChronoUnit.DAYS).toEpochMilli(), "99999999-9999-9999-9999-999999999999");

        BackupRetentionService.Preview preview =
                BackupRetentionService.preview(saveRoot, new BackupRetentionSettings(true, 2, 0, 0), NOW);

        assertEquals(
                List.of("a-eligible"),
                preview.plan().prunes().stream()
                        .map(BackupRetentionPlanner.Prune::id)
                        .toList());
        assertTrue(preview.plan().protections().containsKey("b-legacy"));
        assertTrue(preview.plan().protections().containsKey("c-unverified"));
        assertTrue(preview.plan().protections().containsKey("d-invalid"));
        assertTrue(preview.plan().protections().containsKey("e-unknown-time"));

        BackupRetentionService.ApplyResult applied = BackupRetentionService.apply(preview);
        assertEquals(List.of("a-eligible"), applied.prunedIds());
        assertTrue(Files.isDirectory(legacy));
        assertTrue(Files.isDirectory(unverified));
        assertTrue(Files.isDirectory(invalid));
    }

    @Test
    void applyRechecksCurrentVerificationBeforeDeleting() throws Exception {
        Path old = createBackup(
                "a-old", NOW.minus(100, ChronoUnit.DAYS).toEpochMilli(), "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        createBackup("b-newer", NOW.minus(2, ChronoUnit.DAYS).toEpochMilli(), "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        createBackup("c-newest", NOW.minus(1, ChronoUnit.DAYS).toEpochMilli(), "cccccccc-cccc-cccc-cccc-cccccccccccc");
        BackupRetentionService.Preview preview =
                BackupRetentionService.preview(saveRoot, new BackupRetentionSettings(true, 2, 0, 0), NOW);
        assertEquals(
                List.of("a-old"),
                preview.plan().prunes().stream()
                        .map(BackupRetentionPlanner.Prune::id)
                        .toList());

        Files.delete(old.resolve(BackupVerificationReceipt.FILE_NAME));
        BackupRetentionService.ApplyResult applied = BackupRetentionService.apply(preview);

        assertTrue(applied.prunedIds().isEmpty());
        assertTrue(applied.failures().containsKey("a-old"));
        assertTrue(Files.isDirectory(old));
    }

    @Test
    void disabledRetentionIsAnApplyNoOp() throws Exception {
        createBackup("only", NOW.minus(100, ChronoUnit.DAYS).toEpochMilli(), "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
        BackupRetentionService.Preview preview =
                BackupRetentionService.preview(saveRoot, BackupRetentionSettings.defaults(), NOW);

        BackupRetentionService.ApplyResult result = BackupRetentionService.apply(preview);

        assertTrue(result.prunedIds().isEmpty());
        assertTrue(result.failures().isEmpty());
        assertTrue(Files.isDirectory(saveRoot.resolve("delvefold_backups/only")));
    }

    private Path createBackup(String id, long createdAt, String operationId) throws Exception {
        Path backup = saveRoot.resolve("delvefold_backups").resolve(id);
        Path dimension = Files.createDirectories(backup.resolve("dimensions/delvefold/delve_flat"));
        Files.writeString(dimension.resolve("level.dat"), "dimension-data");
        Files.createDirectories(backup.resolve("config/serverconfig/delvefold"));
        Files.writeString(
                backup.resolve("config/serverconfig/delvefold/settings.json"),
                ConfigJson.GSON.toJson(WorldSettingsDocument.uninitialized()));
        Files.writeString(
                backup.resolve("config/serverconfig/delvefold/ores.json"),
                ConfigJson.GSON.toJson(OrePresets.create(OrePreset.VANILLA_BALANCED)));
        Files.writeString(backup.resolve("operation.json"), ConfigJson.GSON.toJson(operation(operationId, createdAt)));
        new BackupManifestService().createVerifiedManifest(backup);
        return backup;
    }

    private static PendingWorldOperation operation(String operationId, long createdAt) {
        return new PendingWorldOperation(
                PendingWorldOperation.CURRENT_SCHEMA_VERSION,
                operationId,
                WorldOperationType.RECREATE,
                TerrainMode.FLAT,
                TerrainMode.CAVERN,
                TerrainVariant.CLASSIC,
                null,
                null,
                BackupMode.KEEP_BACKUP,
                false,
                createdAt,
                "tester");
    }
}
