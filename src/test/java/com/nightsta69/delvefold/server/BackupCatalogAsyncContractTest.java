package com.nightsta69.delvefold.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BackupCatalogAsyncContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/nightsta69/delvefold");

    @Test
    void adminSnapshotsAndGuiVerificationUseOnlyTheAsyncCatalogView() throws IOException {
        String admin = read("admin/DefaultDelvefoldAdminService.java");
        String snapshotMethod = read("admin/AdminSnapshotAssembler.java");
        String backupMethod = between(
                admin, "public ServiceResult performBackup(", "\n    @Override\n    public ServiceResult perform(");

        assertTrue(admin.contains("return AdminSnapshotAssembler.assemble("));
        assertTrue(snapshotMethod.contains("BackupCatalogCache.get().snapshot(saveRoot)"));
        assertTrue(snapshotMethod.contains("backupCatalog.refreshing()"));
        assertFalse(snapshotMethod.contains("new WorldBackupCatalog"));
        assertFalse(snapshotMethod.contains(".list()"));
        assertTrue(backupMethod.contains("cachedCatalog.backups().stream()"));
        assertFalse(
                backupMethod.contains("catalog.list()"),
                "GUI verify and restore must not parse manifests on the server thread");
        assertTrue(backupMethod.contains("requestCached("));
        assertTrue(
                backupMethod.contains("deleteAndRefreshAsync(server, backupId)"),
                "GUI deletion must capture pending-operation references on the server thread");
        assertFalse(backupMethod.contains("catalog.delete("));
    }

    @Test
    void everyInteractiveDeletionUsesTheServerAwareGuard() throws IOException {
        String command = read("command/DelvefoldCommands.java");
        String admin = read("admin/DefaultDelvefoldAdminService.java");
        String cache = read("reset/BackupCatalogCache.java");
        String restore = read("reset/WorldRestoreService.java");

        assertTrue(
                command.contains("deleteAndRefreshAsync(server, id)"),
                "commands must not schedule a path-only deletion");
        assertTrue(
                admin.contains("deleteAndRefreshAsync(server, backupId)"),
                "GUI actions must not schedule a path-only deletion");
        assertTrue(
                cache.contains("BackupDeletionGuard.get().reserve(server, backupId)"),
                "the cache entry point must capture the deletion reservation before queuing work");
        assertTrue(
                cache.contains("permitImmediatelyBeforeDelete()"),
                "the worker must recheck that its reservation is still active immediately before deletion");
        assertTrue(
                restore.contains("coordinateRestoreRequest("),
                "restore draft creation must be atomic with deletion reservation checks");
    }

    @Test
    void lifecycleWarmsAfterRetentionAndClearsOnStop() throws IOException {
        String lifecycle = read("server/DelvefoldServerLifecycle.java");
        assertTrue(lifecycle.contains("BackupCatalogCache.get().refresh(saveRoot)"));
        assertTrue(lifecycle.contains("BackupCatalogCache.get().clear()"));
        assertTrue(
                lifecycle.contains("BackupDeletionGuard.get().clear(event.getServer())"),
                "server shutdown must revoke any queued deletion reservations");
    }

    @Test
    void cacheUsesBoundedDaemonWorkerAndImmutablePublishedLists() throws IOException {
        String cache = read("reset/BackupCatalogCache.java");
        assertTrue(cache.contains("DEFAULT_MAX_SAVES = 16"));
        assertTrue(cache.contains("Executors.newSingleThreadExecutor"));
        assertTrue(cache.contains("new NamedDaemonThreadFactory(\"delvefold-backup-catalog-\")"));
        assertTrue(cache.contains("List.copyOf(loader.load(key))"));
        assertTrue(cache.contains("if (entry.refreshing)"), "refreshes must deduplicate per save");
    }

    private static String read(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative));
    }

    private static String between(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        return start < 0 || end < 0 ? "" : source.substring(start, end);
    }
}
