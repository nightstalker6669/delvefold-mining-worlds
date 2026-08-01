package com.nightsta69.delvefold.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.nightsta69.delvefold.reset.BackupCatalogCache;
import com.nightsta69.delvefold.reset.BackupDeletionGuard;
import com.nightsta69.delvefold.reset.WorldBackupCatalog;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdminBackupOperationsTest {
    @Test
    void summaryLookupUsesOnlyThePublishedCatalogAndExactIdentifier() {
        WorldBackupCatalog.BackupSummary first = summary("backup-a");
        WorldBackupCatalog.BackupSummary second = summary("backup-b");
        BackupCatalogCache.Snapshot catalog = new BackupCatalogCache.Snapshot(List.of(first, second), true, "ignored");

        assertSame(second, AdminBackupOperations.summary(catalog, "backup-b"));
        assertNull(AdminBackupOperations.summary(catalog, "BACKUP-B"));
        assertNull(AdminBackupOperations.summary(catalog, null));
    }

    @Test
    void deletionFailuresRetainSpecificGuardReasonsThroughWrappedAsyncFailures() {
        assertDeletionMessage(BackupDeletionGuard.Reason.REFERENCED, "message.delvefold.backup_delete.referenced");
        assertDeletionMessage(BackupDeletionGuard.Reason.IN_PROGRESS, "message.delvefold.backup_delete.in_progress");
        assertDeletionMessage(
                BackupDeletionGuard.Reason.SESSION_CLOSED, "message.delvefold.backup_delete.session_closed");
    }

    @Test
    void ordinaryAndMissingDeletionFailuresUseTheGenericLocalizedResult() {
        assertMessage(
                "message.delvefold.admin.backup.delete_failed",
                AdminBackupOperations.backupDeleteFailure("backup-a", new IllegalStateException("failed")));
        assertMessage(
                "message.delvefold.admin.backup.delete_failed",
                AdminBackupOperations.backupDeleteFailure("backup-a", null));
        assertMessage(
                "message.delvefold.admin.backup.delete_failed",
                AdminBackupOperations.backupDeleteFailure("backup-a", new CyclicFailure()));
    }

    private static void assertDeletionMessage(BackupDeletionGuard.Reason reason, String expectedKey) {
        Throwable failure = new IllegalStateException(
                "worker wrapper", new BackupDeletionGuard.DeletionRejectedException("backup-a", reason));
        assertMessage(expectedKey, AdminBackupOperations.backupDeleteFailure("backup-a", failure));
    }

    private static void assertMessage(String expectedKey, String encoded) {
        AdminLocalizedMessage.Decoded message =
                AdminLocalizedMessage.decode(encoded).orElseThrow();
        assertEquals(expectedKey, message.translationKey());
        assertEquals(List.of("backup-a"), message.arguments());
    }

    private static WorldBackupCatalog.BackupSummary summary(String id) {
        return new WorldBackupCatalog.BackupSummary(
                id, 1L, "RECREATE", "FLAT", 2L, false, true, true, true, true, false);
    }

    private static final class CyclicFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;

        @Override
        public synchronized Throwable getCause() {
            return this;
        }
    }
}
