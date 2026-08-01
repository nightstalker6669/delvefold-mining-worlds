package com.nightsta69.delvefold.reset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.ConfigJson;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.TerrainVariant;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BackupDeletionGuardTest {
    private static final String RESTORE_OPERATION_ID = "11111111-1111-1111-1111-111111111111";
    private static final String WORLD_OPERATION_ID = "22222222-2222-2222-2222-222222222222";
    private static final long RESTORE_CREATED_AT = Instant.parse("2026-08-01T00:00:00Z").toEpochMilli();
    private static final long WORLD_OPERATION_CREATED_AT = Instant.parse("2026-07-31T23:00:00Z").toEpochMilli();
    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter
            .ofPattern("uuuuMMdd-HHmmss", Locale.ROOT)
            .withZone(ZoneOffset.UTC);

    @TempDir
    Path saveRoot;

    private BackupDeletionGuard.Reservation openReservation;

    @AfterEach
    void releaseReservation() {
        if (openReservation != null) {
            openReservation.close();
        }
    }

    @Test
    void draftReferenceRejectsDeletion() {
        assertThrows(IOException.class, () -> BackupDeletionGuard.get().reserveForTest(
                saveRoot, "draft-backup", Set.of("draft-backup")));
    }

    @Test
    void persistedRestoreAndWorldOperationReferencesRejectDeletion() throws Exception {
        Path config = saveRoot.resolve("serverconfig/delvefold");
        Files.createDirectories(config);
        Files.writeString(config.resolve("pending_restore.json"), ConfigJson.GSON.toJson(
                new PendingWorldRestore(
                        PendingWorldRestore.CURRENT_SCHEMA_VERSION,
                        RESTORE_OPERATION_ID,
                        "selected-backup",
                        PendingWorldRestore.Phase.STAGED,
                        RESTORE_CREATED_AT,
                        "tester")));
        Files.writeString(config.resolve("pending_world_operation.json"), ConfigJson.GSON.toJson(
                new PendingWorldOperation(
                        PendingWorldOperation.CURRENT_SCHEMA_VERSION,
                        WORLD_OPERATION_ID,
                        WorldOperationType.RECREATE,
                        TerrainMode.FLAT,
                        TerrainMode.CAVERN,
                        TerrainVariant.CLASSIC,
                        null,
                        null,
                        BackupMode.KEEP_BACKUP,
                        false,
                        WORLD_OPERATION_CREATED_AT,
                        "tester")));

        String preRestoreBackup = BACKUP_TIMESTAMP.format(Instant.ofEpochMilli(RESTORE_CREATED_AT))
                + "-pre-restore-" + RESTORE_OPERATION_ID;
        String worldOperationBackup = BACKUP_TIMESTAMP.format(Instant.ofEpochMilli(WORLD_OPERATION_CREATED_AT))
                + '-' + WORLD_OPERATION_ID;

        for (String referenced : Set.of("selected-backup", preRestoreBackup, worldOperationBackup)) {
            assertThrows(IOException.class, () -> BackupDeletionGuard.get().reserveForTest(
                    saveRoot, referenced, Set.of()), referenced);
        }
    }

    @Test
    void unreferencedReservationPermitsDeletionAndBlocksRestoreUntilReleased() throws Exception {
        BackupDeletionGuard guard = BackupDeletionGuard.get();
        AtomicInteger coordinatedActions = new AtomicInteger();
        openReservation = guard.reserveForTest(saveRoot, "ordinary-backup", Set.of());

        assertTrue(openReservation.permitImmediatelyBeforeDelete());
        assertFalse(guard.coordinateReferenceForTest(
                saveRoot, "ordinary-backup", coordinatedActions::incrementAndGet));
        assertEquals(0, coordinatedActions.get());

        openReservation.close();
        openReservation = null;
        assertTrue(guard.coordinateReferenceForTest(
                saveRoot, "ordinary-backup", coordinatedActions::incrementAndGet));
        assertEquals(1, coordinatedActions.get());
    }
}
