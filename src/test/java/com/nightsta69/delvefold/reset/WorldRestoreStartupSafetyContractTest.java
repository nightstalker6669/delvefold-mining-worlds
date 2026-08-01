package com.nightsta69.delvefold.reset;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source contract for lifecycle ordering that cannot be exercised without a full server bootstrap. */
class WorldRestoreStartupSafetyContractTest {
    @Test
    void incompleteRestoreAbortsBeforeConfigurationAndWorldLoadingCanContinue() throws Exception {
        String restore = read("reset/WorldRestoreService.java");
        String lifecycle = read("server/DelvefoldServerLifecycle.java");

        assertTrue(restore.contains("throw new IllegalStateException("),
                "restore failures must propagate out of the about-to-start event");
        assertTrue(restore.contains(
                "Delvefold could not safely complete its pending restore; startup was stopped"));

        int highestListener = lifecycle.indexOf(
                "gameBus.addListener(EventPriority.HIGHEST, ServerAboutToStartEvent.class");
        int restoreCall = lifecycle.indexOf("WorldRestoreService.get().prepareStartup(event.getServer())");
        int configListener = lifecycle.indexOf(
                "gameBus.addListener(EventPriority.NORMAL, ServerAboutToStartEvent.class");
        assertTrue(highestListener >= 0 && restoreCall > highestListener && configListener > restoreCall,
                "restore recovery must run before configuration publication and dimension loading");
    }

    @Test
    void completedRestoreCleanupNoLongerDependsOnTheSelectedBackup() throws Exception {
        String restore = read("reset/WorldRestoreService.java");

        int skipCompletedVerification = restore.indexOf(
                "pending.phase() != PendingWorldRestore.Phase.RESTORED");
        int selectedResolution = restore.indexOf("new WorldBackupCatalog(saveRoot(server)).resolve");
        int completedCleanup = restore.indexOf(
                "pending.phase() == PendingWorldRestore.Phase.RESTORED");
        assertTrue(skipCompletedVerification >= 0
                        && selectedResolution > skipCompletedVerification
                        && completedCleanup > selectedResolution,
                "RESTORED cleanup must be able to finish even if the original selected backup is unavailable");
    }

    private static String read(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/nightsta69/delvefold").resolve(relative));
    }
}
