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
        String verify = between(source, "private static int verifyBackup(", "private static int disableRetention(");
        String delete = between(source, "private static int deleteBackup(", "private static int requestRestore(");

        int verifyAudit = codeIndexOf(verify, "AuditMutation.Operation.BACKUP_MANIFEST_CREATED");
        int verifyOnlineCheck = codeIndexOf(verify, "server.getPlayerList().getPlayer(requestedBy.getUUID())");
        assertTrue(
                verifyAudit >= 0 && verifyAudit < verifyOnlineCheck,
                "Legacy manifest creation must be audited even if its requesting player disconnected");
        assertTrue(
                containsCode(verify, "result.status() == BackupVerificationResult.Status.LEGACY_UPGRADED"),
                "A stale legacy catalog row must not misreport an ordinary verification as manifest creation");
        assertTrue(
                containsCode(
                        read("admin/DefaultDelvefoldAdminService.java"),
                        "result.status() == BackupVerificationResult.Status.LEGACY_UPGRADED"),
                "GUI legacy verification must use the worker's actual mutation result too");

        int deleteAudit = codeIndexOf(delete, "AuditMutation.Operation.BACKUP_DELETED");
        int deleteOnlineCheck = codeIndexOf(delete, "server.getPlayerList().getPlayer(requestedBy)");
        assertTrue(
                deleteAudit >= 0 && deleteAudit < deleteOnlineCheck,
                "Completed backup deletion must be audited before deciding whether to notify a player");
        assertTrue(containsCode(verify, "AsyncAuditMutationTracker.get().startTracked(saveRoot"));
        assertTrue(
                containsCode(delete, "AsyncAuditMutationTracker.get().startTracked(saveRoot"),
                "Deletion must register its audit shutdown gate before its source mutation starts");
    }

    @Test
    void guiAsyncMutationsUseTheSamePreStartAuditBarrier() throws IOException {
        String source = read("admin/DefaultDelvefoldAdminService.java");
        String verify =
                between(source, "if (operation == BackupOperation.VERIFY)", "if (operation == BackupOperation.DELETE)");
        String delete = between(
                source,
                "if (operation == BackupOperation.DELETE)",
                "WorldBackupCatalog catalog = new WorldBackupCatalog(saveRoot)");

        assertTrue(containsCode(verify, "AsyncAuditMutationTracker.get().startTracked(saveRoot"));
        assertTrue(containsCode(verify, "BackupVerificationResult.Status.LEGACY_UPGRADED"));
        assertTrue(containsCode(delete, "AsyncAuditMutationTracker.get().startTracked(saveRoot"));
        assertTrue(containsCode(delete, "AuditMutation.Operation.BACKUP_DELETED"));
    }

    @Test
    void pinAuditsRequireARealTransitionAndPreserveItsDirection() throws IOException {
        String command = between(
                read("command/DelvefoldCommands.java"),
                "private static int pinBackup(",
                "private static int deleteBackup(");
        String admin = between(
                read("admin/DefaultDelvefoldAdminService.java"),
                "WorldBackupCatalog catalog = new WorldBackupCatalog(saveRoot);",
                "} catch (IOException | IllegalArgumentException exception)");

        for (String source : new String[] {command, admin}) {
            assertTrue(containsCode(source, "boolean changed = catalog.setPinned("));
            assertTrue(containsCode(source, "if (changed)"), "No-op pin requests must not create audit entries");
            assertTrue(containsCode(source, "AuditMutation.Operation.BACKUP_PINNED"));
            assertTrue(containsCode(source, "AuditMutation.Operation.BACKUP_UNPINNED"));
            assertFalse(
                    containsCode(source, "AuditMutation.Operation.BACKUP_PIN_CHANGED"),
                    "Opposite pin transitions must not serialize as the same operation");
        }
    }

    @Test
    void scheduledRenewalAuditsConfirmationAndSuccessfulRollback() throws IOException {
        String renewal = read("reset/RenewalScheduler.java");

        assertTrue(containsCode(renewal, "AuditMutation.Operation.WORLD_OPERATION_ACCEPTED"));
        assertTrue(containsCode(renewal, "if (cancelled.success())"));
        assertTrue(containsCode(renewal, "AuditMutation.Operation.WORLD_OPERATION_CANCELLED"));
        assertTrue(containsCode(renewal, "\"server\","));
        assertTrue(containsCode(renewal, "\"mining_world\","));
    }

    @Test
    void initialLandmarkReloadWaitsForThePerSaveAuditWriter() throws IOException {
        String listener = read("world/landmark/catalog/LandmarkCatalogReloadListener.java");
        String lifecycle = read("server/DelvefoldServerLifecycle.java");

        assertTrue(containsCode(listener, "PENDING_STARTUP_AUDIT"));
        assertTrue(containsCode(listener, "DelvefoldAuditService.get().available()"));
        assertTrue(containsCode(listener, "public static void flushPendingAudit()"));
        assertTrue(
                containsCode(listener, "explicitly attributed to the server"),
                "Reload callbacks have no command-source context, so server attribution must be explicit");

        int start = codeIndexOf(lifecycle, "DelvefoldAuditService.get().start(event.getServer())");
        int flush = codeIndexOf(lifecycle, "LandmarkCatalogReloadListener.flushPendingAudit()");
        assertTrue(
                start >= 0 && flush > start, "The initial catalog publication must flush only after the writer starts");
    }

    @Test
    void serverLifecycleClosesAndDrainsAsyncMutationsBeforeStoppingTheAuditWriter() throws IOException {
        String lifecycle = read("server/DelvefoldServerLifecycle.java");

        int begin = codeIndexOf(lifecycle, "AsyncAuditMutationTracker.get().beginSession");
        int auditStart = codeIndexOf(lifecycle, "DelvefoldAuditService.get().start(event.getServer())");
        int open = codeIndexOf(lifecycle, "AsyncAuditMutationTracker.get().openSession");
        int stopAccepting = codeIndexOf(lifecycle, "asyncAudits.stopAccepting(saveRoot)");
        int revokeDeletes = codeIndexOf(lifecycle, "BackupDeletionGuard.get().clear(event.getServer())");
        int drain = codeIndexOf(lifecycle, "asyncAudits.drain(saveRoot)");
        int auditStop = codeIndexOf(lifecycle, "DelvefoldAuditService.get().stop(event.getServer())");
        int end = codeIndexOf(lifecycle, "asyncAudits.endSession(saveRoot)");

        assertTrue(
                begin >= 0 && begin < auditStart && auditStart < open,
                "A save generation must be reserved before its writer starts and opened only afterward");
        assertTrue(
                stopAccepting >= 0 && stopAccepting < revokeDeletes && revokeDeletes < drain,
                "Shutdown must reject new sources, revoke unstarted deletes, then drain accepted mutations");
        assertTrue(
                drain < auditStop && auditStop < end,
                "The originating writer must drain before its isolated async session can be discarded");
    }

    @Test
    void portalSemanticAuditsHaveOneCentralEmitter() throws IOException {
        String config = read("config/DelvefoldConfigService.java");
        String planner = read("config/ConfigAuditPlanner.java");
        String commands = read("command/DelvefoldCommands.java");
        String routingCommand =
                between(commands, "private static int setPortalRouting(", "private static int setPortalHub(");
        String hubCommand =
                between(commands, "private static int setPortalHub(", "private static int setIdentityName(");
        String adminPortal = between(
                read("admin/DefaultDelvefoldAdminService.java"),
                "public ServiceResult updatePortal(",
                "public ServiceResult updateIdentity(");

        assertTrue(containsCode(config, "ConfigAuditPlanner.plan(before, saved, actor)"));
        assertTrue(containsCode(planner, "AuditMutation.Operation.PORTAL_ROUTING_CHANGED"));
        assertTrue(containsCode(planner, "AuditMutation.Operation.HUB_PROTECTION_CHANGED"));
        for (String caller : new String[] {routingCommand, hubCommand, adminPortal}) {
            assertFalse(
                    containsCode(caller, "AuditMutation.Operation.PORTAL_ROUTING_CHANGED"),
                    "Portal routing changes must be emitted once by the config audit planner");
            assertFalse(
                    containsCode(caller, "AuditMutation.Operation.HUB_PROTECTION_CHANGED"),
                    "Hub changes must be emitted once by the config audit planner");
        }
    }

    private static String read(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative));
    }

    private static String between(String source, String startMarker, String endMarker) {
        String compactSource = compact(source);
        String compactStart = compact(startMarker);
        String compactEnd = compact(endMarker);
        int start = compactSource.indexOf(compactStart);
        int end = compactSource.indexOf(compactEnd, start);
        return start < 0 || end < 0 ? "" : compactSource.substring(start, end);
    }

    private static boolean containsCode(String source, String expected) {
        return codeIndexOf(source, expected) >= 0;
    }

    private static int codeIndexOf(String source, String expected) {
        return compact(source).indexOf(compact(expected));
    }

    private static String compact(String source) {
        return source.replaceAll("\\s+", "");
    }
}
