package com.nightsta69.delvefold.admin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AdminBackupOperationsSourceContractTest {
    private static final Path ADMIN = Path.of("src/main/java/com/nightsta69/delvefold/admin");

    @Test
    void facadeAuthorizesAndRejectsStaleRequestsBeforeDelegating() throws IOException {
        String facade = Files.readString(ADMIN.resolve("DefaultDelvefoldAdminService.java"));
        String method = between(facade, "public ServiceResult performBackup(", "public ServiceResult perform(");

        int permission = method.indexOf("requireWorldManagement(player)");
        int snapshot = method.indexOf("DelvefoldConfigService.get().snapshot()");
        int revision = method.indexOf("snapshot.settings().revision() != expectedSettingsRevision");
        int delegation = method.indexOf("AdminBackupOperations.perform(");
        assertTrue(permission >= 0 && permission < snapshot);
        assertTrue(snapshot < revision && revision < delegation);
        assertFalse(method.contains("BackupCatalogCache"));
        assertFalse(method.contains("WorldRestoreService"));
    }

    @Test
    void collaboratorRetainsBackupPathsAsyncHandoffsAndOperationCoverage() throws IOException {
        String operations = Files.readString(ADMIN.resolve("AdminBackupOperations.java"));
        String compact = operations.replaceAll("\\s+", "");

        assertTrue(compact.contains("server.getWorldPath(LevelResource.ROOT)"));
        assertTrue(compact.contains("backupCache.snapshot(saveRoot)"));
        assertTrue(compact.contains("requestCached(server,backupId,player.getGameProfile().getName(),summary)"));
        assertTrue(compact.contains("future.whenComplete((result,failure)->server.execute(()->"));
        assertTrue(compact.contains("deletion.whenComplete((deleted,failure)->server.execute(()->"));
        assertTrue(compact.contains("deleteAndRefreshAsync(server,backupId)"));
        assertTrue(compact.contains("casePIN->"));
        assertTrue(compact.contains("caseUNPIN->"));
        assertTrue(compact.contains("caseRESTORE,DELETE,VERIFY,CANCEL_RESTORE->"));
        assertFalse(operations.contains("AdminAccess"));
    }

    private static String between(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        return start < 0 || end < 0 ? "" : source.substring(start, end);
    }
}
