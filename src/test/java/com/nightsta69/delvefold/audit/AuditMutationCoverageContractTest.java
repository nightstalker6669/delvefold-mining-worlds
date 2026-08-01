package com.nightsta69.delvefold.audit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuditMutationCoverageContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/nightsta69/delvefold");

    @Test
    void commandAsyncMutationsAreAuditedBeforeAnOfflinePlayerSuppressesNotification() throws IOException {
        String source = read("command/DelvefoldCommands.java");
        String verify = between(source, "private static int verifyBackup(",
                "private static int disableRetention(");
        String delete = between(source, "private static int deleteBackup(",
                "private static int requestRestore(");

        int verifyAudit = verify.indexOf("AuditMutation.Operation.BACKUP_MANIFEST_CREATED");
        int verifyOnlineCheck = verify.indexOf("server.getPlayerList().getPlayer(requestedBy.getUUID())");
        assertTrue(verifyAudit >= 0 && verifyAudit < verifyOnlineCheck,
                "Legacy manifest creation must be audited even if its requesting player disconnected");
        assertTrue(verify.contains("result.status() == BackupVerificationResult.Status.LEGACY_UPGRADED"),
                "A stale legacy catalog row must not misreport an ordinary verification as manifest creation");
        assertTrue(read("admin/DefaultDelvefoldAdminService.java").contains(
                        "result.status() == BackupVerificationResult.Status.LEGACY_UPGRADED"),
                "GUI legacy verification must use the worker's actual mutation result too");

        int deleteAudit = delete.indexOf("AuditMutation.Operation.BACKUP_DELETED");
        int deleteOnlineCheck = delete.indexOf("server.getPlayerList().getPlayer(requestedBy)");
        assertTrue(deleteAudit >= 0 && deleteAudit < deleteOnlineCheck,
                "Completed backup deletion must be audited before deciding whether to notify a player");
        assertTrue(verify.contains("AsyncAuditMutationTracker.get().startTracked(saveRoot"));
        assertTrue(delete.contains("AsyncAuditMutationTracker.get().startTracked(saveRoot"),
                "Deletion must register its audit shutdown gate before its source mutation starts");
    }

    @Test
    void guiAsyncMutationsUseTheSamePreStartAuditBarrier() throws IOException {
        String source = read("admin/DefaultDelvefoldAdminService.java");
        String verify = between(source, "if (operation == BackupOperation.VERIFY)",
                "if (operation == BackupOperation.DELETE)");
        String delete = between(source, "if (operation == BackupOperation.DELETE)",
                "WorldBackupCatalog catalog = new WorldBackupCatalog(saveRoot)");

        assertTrue(verify.contains("AsyncAuditMutationTracker.get().startTracked(saveRoot"));
        assertTrue(verify.contains("BackupVerificationResult.Status.LEGACY_UPGRADED"));
        assertTrue(delete.contains("AsyncAuditMutationTracker.get().startTracked(saveRoot"));
        assertTrue(delete.contains("AuditMutation.Operation.BACKUP_DELETED"));
    }

    @Test
    void pinAuditsRequireARealTransitionAndPreserveItsDirection() throws IOException {
        String command = between(read("command/DelvefoldCommands.java"),
                "private static int pinBackup(", "private static int deleteBackup(");
        String admin = between(read("admin/DefaultDelvefoldAdminService.java"),
                "WorldBackupCatalog catalog = new WorldBackupCatalog(saveRoot);",
                "} catch (IOException | IllegalArgumentException exception)");

        for (String source : new String[] {command, admin}) {
            assertTrue(source.contains("boolean changed = catalog.setPinned("));
            assertTrue(source.contains("if (changed)"), "No-op pin requests must not create audit entries");
            assertTrue(source.contains("AuditMutation.Operation.BACKUP_PINNED"));
            assertTrue(source.contains("AuditMutation.Operation.BACKUP_UNPINNED"));
            assertFalse(source.contains("AuditMutation.Operation.BACKUP_PIN_CHANGED"),
                    "Opposite pin transitions must not serialize as the same operation");
        }
    }

    @Test
    void scheduledRenewalAuditsConfirmationAndSuccessfulRollback() throws IOException {
        String renewal = read("reset/RenewalScheduler.java");

        assertTrue(renewal.contains("AuditMutation.Operation.WORLD_OPERATION_ACCEPTED"));
        assertTrue(renewal.contains("if (cancelled.success())"));
        assertTrue(renewal.contains("AuditMutation.Operation.WORLD_OPERATION_CANCELLED"));
        assertTrue(renewal.contains("\"server\","));
        assertTrue(renewal.contains("\"mining_world\","));
    }

    @Test
    void initialLandmarkReloadWaitsForThePerSaveAuditWriter() throws IOException {
        String listener = read("world/landmark/catalog/LandmarkCatalogReloadListener.java");
        String lifecycle = read("server/DelvefoldServerLifecycle.java");

        assertTrue(listener.contains("PENDING_STARTUP_AUDIT"));
        assertTrue(listener.contains("DelvefoldAuditService.get().available()"));
        assertTrue(listener.contains("public static void flushPendingAudit()"));
        assertTrue(listener.contains("explicitly attributed to the server"),
                "Reload callbacks have no command-source context, so server attribution must be explicit");

        int start = lifecycle.indexOf("DelvefoldAuditService.get().start(event.getServer())");
        int flush = lifecycle.indexOf("LandmarkCatalogReloadListener.flushPendingAudit()");
        assertTrue(start >= 0 && flush > start,
                "The initial catalog publication must flush only after the writer starts");
    }

    @Test
    void serverLifecycleClosesAndDrainsAsyncMutationsBeforeStoppingTheAuditWriter() throws IOException {
        String lifecycle = read("server/DelvefoldServerLifecycle.java");

        int begin = lifecycle.indexOf("AsyncAuditMutationTracker.get().beginSession");
        int auditStart = lifecycle.indexOf("DelvefoldAuditService.get().start(event.getServer())");
        int open = lifecycle.indexOf("AsyncAuditMutationTracker.get().openSession");
        int stopAccepting = lifecycle.indexOf("asyncAudits.stopAccepting(saveRoot)");
        int revokeDeletes = lifecycle.indexOf("BackupDeletionGuard.get().clear(event.getServer())");
        int drain = lifecycle.indexOf("asyncAudits.drain(saveRoot)");
        int auditStop = lifecycle.indexOf("DelvefoldAuditService.get().stop(event.getServer())");
        int end = lifecycle.indexOf("asyncAudits.endSession(saveRoot)");

        assertTrue(begin >= 0 && begin < auditStart && auditStart < open,
                "A save generation must be reserved before its writer starts and opened only afterward");
        assertTrue(stopAccepting >= 0 && stopAccepting < revokeDeletes && revokeDeletes < drain,
                "Shutdown must reject new sources, revoke unstarted deletes, then drain accepted mutations");
        assertTrue(drain < auditStop && auditStop < end,
                "The originating writer must drain before its isolated async session can be discarded");
    }

    @Test
    void portalSemanticAuditsHaveOneCentralEmitter() throws IOException {
        String config = read("config/DelvefoldConfigService.java");
        String commands = read("command/DelvefoldCommands.java");
        String routingCommand = between(commands, "private static int setPortalRouting(",
                "private static int setPortalHub(");
        String hubCommand = between(commands, "private static int setPortalHub(",
                "private static int setIdentityName(");
        String adminPortal = between(read("admin/DefaultDelvefoldAdminService.java"),
                "public ServiceResult updatePortal(", "public ServiceResult updateIdentity(");

        assertTrue(config.contains("AuditMutation.Operation.PORTAL_ROUTING_CHANGED"));
        assertTrue(config.contains("AuditMutation.Operation.HUB_PROTECTION_CHANGED"));
        for (String caller : new String[] {routingCommand, hubCommand, adminPortal}) {
            assertFalse(caller.contains("AuditMutation.Operation.PORTAL_ROUTING_CHANGED"),
                    "Portal routing changes must be emitted once by the config audit planner");
            assertFalse(caller.contains("AuditMutation.Operation.HUB_PROTECTION_CHANGED"),
                    "Hub changes must be emitted once by the config audit planner");
        }
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
