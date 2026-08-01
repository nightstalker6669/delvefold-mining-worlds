package com.nightsta69.delvefold.qualityruntime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class RestartBoundLifecycleCharacterizationTest {
    @Test
    void confirmationPersistsIntentAndEvacuatesWithoutMutatingLoadedDimensionFolders() throws IOException {
        String worldOperations = ProductionSources.read("reset/WorldOperationService.java");
        String worldConfirmation =
                ProductionSources.block(worldOperations, "private WorldOperationResult confirmCoordinated(");
        assertTrue(worldConfirmation.contains("writeJsonAtomically(pendingPath(server), draft.operation())"));
        assertTrue(worldConfirmation.contains("entryBlocked.set(true)"));
        assertTrue(worldConfirmation.contains("evacuateMiningPlayersForOperation(server)"));
        assertFalse(worldConfirmation.contains("moveIntoHolding("));
        assertFalse(worldConfirmation.contains("deleteTree("));

        String restores = ProductionSources.read("reset/WorldRestoreService.java");
        String restoreConfirmation =
                ProductionSources.block(restores, "private WorldOperationResult confirmCoordinated(");
        assertTrue(restoreConfirmation.contains("writePending(pendingPath(server), draft.operation())"));
        assertTrue(restoreConfirmation.contains("entryBlocked.set(true)"));
        assertTrue(restoreConfirmation.contains("evacuateMiningPlayersForOperation(server)"));
        assertFalse(restoreConfirmation.contains("stageSelectedBackup("));
        assertFalse(restoreConfirmation.contains("installStaged("));
    }

    @Test
    void filesystemChangesRunDuringOrderedStartupBeforeConfigurationAndDimensionUse() throws IOException {
        String lifecycle = ProductionSources.read("server/DelvefoldServerLifecycle.java");
        int highest = lifecycle.indexOf("EventPriority.HIGHEST, ServerAboutToStartEvent.class");
        int restorePreparation = lifecycle.indexOf("WorldRestoreService.get().prepareStartup(event.getServer())");
        int worldPreparation = lifecycle.indexOf("WorldOperationService.get().prepareStartup(event.getServer())");
        int normal = lifecycle.indexOf("EventPriority.NORMAL, ServerAboutToStartEvent.class");
        int lowest = lifecycle.indexOf("EventPriority.LOWEST, ServerAboutToStartEvent.class");
        int finish = lifecycle.indexOf("WorldOperationService.get().finishStartup(event.getServer())");

        assertTrue(highest >= 0);
        assertTrue(highest < restorePreparation && restorePreparation < worldPreparation);
        assertTrue(worldPreparation < normal, "restart journals must be prepared before config publication");
        assertTrue(normal < lowest && lowest < finish, "world-operation metadata must finish after config loading");

        String worldPreparationBlock = ProductionSources.block(
                ProductionSources.read("reset/WorldOperationService.java"),
                "public void prepareStartup(MinecraftServer server)");
        assertTrue(worldPreparationBlock.contains("moveIntoHolding("));

        String restorePreparationBlock = ProductionSources.block(
                ProductionSources.read("reset/WorldRestoreService.java"),
                "public void prepareStartup(MinecraftServer server)");
        assertTrue(restorePreparationBlock.contains("stageSelectedBackup("));
        assertTrue(restorePreparationBlock.contains("installStaged("));
    }

    @Test
    void scheduledResultsKeepDedicatedRestartAndIntegratedReopenInstructionsDistinct() throws IOException {
        String worldConfirmation = ProductionSources.block(
                ProductionSources.read("reset/WorldOperationService.java"),
                "private WorldOperationResult confirmCoordinated(");
        assertTrue(worldConfirmation.contains("server.isDedicatedServer()"));
        assertTrue(worldConfirmation.contains("world_operation.scheduled.dedicated"));
        assertTrue(worldConfirmation.contains("world_operation.scheduled.integrated"));

        String restoreConfirmation = ProductionSources.block(
                ProductionSources.read("reset/WorldRestoreService.java"),
                "private WorldOperationResult confirmCoordinated(");
        assertTrue(restoreConfirmation.contains("server.isDedicatedServer()"));
        assertTrue(restoreConfirmation.contains("restore.scheduled.dedicated"));
        assertTrue(restoreConfirmation.contains("restore.scheduled.integrated"));
    }
}
