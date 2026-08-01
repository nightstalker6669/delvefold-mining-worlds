package com.nightsta69.delvefold.qualityruntime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class AsyncIsolationCharacterizationTest {
    private static final List<String> EXPLICIT_EXECUTOR_METHODS = List.of(
            "CompletableFuture.supplyAsync", "CompletableFuture.runAsync", ".thenApplyAsync",
            ".thenAcceptAsync", ".whenCompleteAsync", ".handleAsync");

    @Test
    void asynchronousStagesNeverFallBackToTheSharedCommonPool() throws IOException {
        for (Path path : ProductionSources.allJavaFiles()) {
            String source = java.nio.file.Files.readString(path);
            for (String method : EXPLICIT_EXECUTOR_METHODS) {
                for (String arguments : ProductionSources.invocations(source, method)) {
                    assertTrue(ProductionSources.hasTopLevelComma(arguments),
                            () -> "Async stage lacks an explicit executor in " + path + ": " + method);
                }
            }
        }
    }

    @Test
    void backupCatalogVerificationDoctorAndAuditOwnIndependentDispatchQueues() throws IOException {
        assertDedicatedDispatch("reset/BackupCatalogCache.java", "Executor executor", "executor.execute(");
        assertDedicatedDispatch("reset/BackupVerificationService.java", "Executor executor", "executor.execute(");
        assertDedicatedDispatch(
                "diagnostics/DelvefoldDoctorService.java", "ExecutorService WORKER", "CompletableFuture.supplyAsync(");
        assertDedicatedDispatch("audit/AuditWriteQueue.java", "ThreadPoolExecutor executor", "executor.execute(");

        String audit = ProductionSources.read("audit/AuditWriteQueue.java");
        assertTrue(audit.contains("new ArrayBlockingQueue<>(capacity)"), "audit handoff must remain bounded");
        assertTrue(audit.contains("new ThreadPoolExecutor(\n                1, 1"),
                "audit writes must remain ordered on one worker");
    }

    @Test
    void interactiveAndTickEntrypointsDoNotPerformBackupScansOrDoctorBuildsInline() throws IOException {
        String adminSnapshot = ProductionSources.block(
                ProductionSources.read("admin/DefaultDelvefoldAdminService.java"),
                "public AdminSnapshot snapshot(");
        assertTrue(adminSnapshot.contains("BackupCatalogCache.get().snapshot(saveRoot)"));
        assertTrue(adminSnapshot.contains("cachedRenderedLines(player.getServer())"));
        assertFalse(adminSnapshot.contains("new WorldBackupCatalog("));
        assertFalse(adminSnapshot.contains("DelvefoldDoctorService.get().build("));

        String tick = ProductionSources.block(
                ProductionSources.read("reset/RenewalScheduler.java"),
                "public static void onServerTick(ServerTickEvent.Post event)");
        assertFalse(tick.contains("Files."));
        assertFalse(tick.contains("WorldBackupCatalog"));
        assertFalse(tick.contains("BackupManifestService"));
        assertFalse(tick.contains(".join()"));
        assertFalse(tick.contains("Thread.sleep("));
    }

    private static void assertDedicatedDispatch(String relativePath, String executorField, String dispatch)
            throws IOException {
        String source = ProductionSources.read(relativePath);
        assertTrue(source.contains(executorField), () -> "Missing owned executor in " + relativePath);
        assertTrue(source.contains(dispatch), () -> "Missing async dispatch in " + relativePath);
    }
}
