package com.nightsta69.delvefold.reset;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WorldOperationStartupSafetyContractTest {
    private static final Path RESET_ROOT = Path.of("src/main/java/com/nightsta69/delvefold/reset");

    @Test
    void pendingDeleteOrRecreateCannotContinueStartupAfterPreparationOrFinalizationFailure() throws Exception {
        String source = Files.readString(RESET_ROOT.resolve("WorldOperationService.java"));
        String prepare = between(
                source,
                "public void prepareStartup(MinecraftServer server)",
                "private static void auditBackupManifest");
        String finish = between(
                source,
                "public void finishStartup(MinecraftServer server)",
                "private static PendingWorldOperation readPending");

        assertTrue(
                prepare.contains("throw new IllegalStateException("),
                "post-move preparation failures must abort startup before dimensions can regenerate");
        assertTrue(prepare.contains("startup was stopped"));
        assertTrue(
                finish.contains("throw new IllegalStateException("),
                "failed operation finalization must not let the server load a partial world state");
        assertTrue(finish.contains("startup was stopped"));
    }

    @Test
    void pendingRestoreUsesTransactionalBackupAndAbortsBeforeRestoredPhaseOnFailure() throws Exception {
        String restore = Files.readString(RESET_ROOT.resolve("WorldRestoreService.java"));
        String prepare = between(
                restore,
                "public void prepareStartup(MinecraftServer server)",
                "private static void auditBackupManifest");

        assertTrue(prepare.contains("backupCurrent(server, preRestore, pending)"));
        assertTrue(
                prepare.contains("throw new IllegalStateException("),
                "a failed pending restore must abort before Minecraft loads missing dimensions");
        assertTrue(restore.contains("RestoreCurrentBackupTransaction.backup("));
    }

    private static String between(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        return start < 0 || end < 0 ? "" : source.substring(start, end);
    }
}
