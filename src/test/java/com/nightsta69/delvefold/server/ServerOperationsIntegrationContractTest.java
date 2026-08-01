package com.nightsta69.delvefold.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ServerOperationsIntegrationContractTest {
    @Test
    void auditServiceFollowsServerLifecycleAndRecordsAutomaticPrunes() throws IOException {
        String lifecycle = source("server/DelvefoldServerLifecycle.java");

        int configStart = lifecycle.indexOf("DelvefoldConfigService.get().start(event.getServer())");
        int auditStart = lifecycle.indexOf("DelvefoldAuditService.get().start(event.getServer())");
        int auditStop = lifecycle.indexOf("DelvefoldAuditService.get().stop(event.getServer())");
        int doctorClear = lifecycle.indexOf("DelvefoldDoctorService.get().clear()");
        int configStop = lifecycle.indexOf("DelvefoldConfigService.get().stop(event.getServer())");
        assertTrue(
                auditStart >= 0 && configStart > auditStart,
                "The audit log must start before configuration loading so accepted startup mutations are recorded");
        assertTrue(
                auditStop >= 0 && configStop > auditStop,
                "The audit log must flush before per-save configuration shuts down");
        assertTrue(
                doctorClear >= 0 && configStop > doctorClear,
                "The doctor cache must clear before per-save configuration shuts down");
        assertTrue(lifecycle.contains("AuditMutation.Operation.RETENTION_PRUNE_ACCEPTED"));
        assertTrue(
                lifecycle.contains("AuditMutation.Operation.RETENTION_PRUNE_PREVIEWED"),
                "Every proposed automatic prune must be recorded before retention applies it");
        assertTrue(lifecycle.contains("AuditMutation.ObjectType.BACKUP"));
    }

    @Test
    void acceptedConfigurationMutationsAndProfileActivationReachTheAuditFacade() throws IOException {
        String config = source("config/DelvefoldConfigService.java");

        assertTrue(config.contains("auditSavedConfiguration(before, saved)"));
        assertTrue(config.contains("AuditMutation.Operation.CONFIGURATION_ACCEPTED"));
        assertTrue(config.contains("AuditMutation.Operation.PROFILE_ACTIVATED"));
        assertTrue(
                config.contains("before.settings().revision(),\n" + "                    saved.settings().revision()"));
        assertTrue(config.contains("before.ores().revision(),\n" + "                    saved.ores().revision()"));
    }

    @Test
    void doctorReportIsIncludedInTheAdministrativeDiagnosticsGui() throws IOException {
        String admin = source("admin/DefaultDelvefoldAdminService.java");
        String dashboard = source("client/gui/DelvefoldDashboardScreen.java");

        assertTrue(
                admin.contains(
                        "diagnostics.addAll(DelvefoldDoctorService.get().cachedRenderedLines(player.getServer())"),
                "The server-authoritative cached doctor report must be included without blocking the snapshot");
        assertTrue(
                admin.contains(".limit(ProtocolLimits.MAX_DIAGNOSTICS)"),
                "Doctor output must remain within the admin protocol's diagnostics bound");
        assertFalse(admin.contains("DelvefoldDoctorService.get().renderedLines(player.getServer())"));
        assertFalse(
                admin.contains("DelvefoldDoctorService.get().build(player.getServer())"),
                "Opening or refreshing the dashboard must never perform the heavy doctor scan inline");
        assertTrue(dashboard.contains("case DIAGNOSTICS -> initDiagnostics()"));
        assertTrue(dashboard.contains("renderDiagnostics(graphics, x, y, width, height)"));
        assertTrue(
                dashboard.contains("List<String> diagnostics = this.snapshot.diagnostics()"),
                "The diagnostics tab must render the server snapshot rather than local-only state");
    }

    @Test
    void asynchronousDoctorCapturesServerStateBeforeWorkerBuildAndCachesTheResult() throws IOException {
        String doctor = source("diagnostics/DelvefoldDoctorService.java");
        String compact = doctor.replaceAll("\\s+", "");

        assertTrue(
                compact.contains("CapturedStatecaptured=capture(server);"),
                "Minecraft server state must be captured before dispatching background work");
        assertTrue(
                compact.contains("CompletableFuture.supplyAsync(()->buildCaptured(captured),WORKER)"),
                "Filesystem hashing and report assembly must run on the dedicated doctor worker");
        assertTrue(compact.contains("cache.put(key,report,clock.millis())"));
        assertTrue(compact.contains("renderedLinesAsync(MinecraftServerserver)"));
        assertTrue(compact.contains("exportAsync(MinecraftServerserver)"));
    }

    @Test
    void guiBackupVerificationUsesAsyncLegacyAndManifestPaths() throws IOException {
        String admin = source("admin/DefaultDelvefoldAdminService.java");
        String compact = admin.replaceAll("\\s+", "");

        assertTrue(compact.contains("if(operation==BackupOperation.VERIFY)"));
        assertTrue(
                compact.contains("summary.legacy()?AsyncAuditMutationTracker.get().startTracked(saveRoot,"
                        + "()->verification.validateLegacyAndCreateManifestAsync(backupId)"),
                "Legacy manifest creation must reserve its audit barrier before the worker starts");
        assertTrue(compact.contains(":verification.verifyAsync(backupId)"));
        assertTrue(
                compact.contains("future.whenComplete((result,failure)->server.execute(()->"),
                "Async verification completion, including worker failures, must switch back to the server executor");
        assertTrue(
                compact.contains("if(failure!=null)"),
                "Exceptional verification completion must be handled before notifying the player");
        assertTrue(admin.contains("DelvefoldNetwork.sendAsyncResult"));
    }

    private static String source(String relative) throws IOException {
        return Files.readString(
                Path.of("src/main/java/com/nightsta69/delvefold").resolve(relative));
    }
}
