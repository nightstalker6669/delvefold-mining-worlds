package com.nightsta69.delvefold.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class DelvefoldCommandsTest {
    @Test
    void registersDelvefoldAsTheOnlyCommandRoot() {
        assertEquals(List.of("delvefold"), DelvefoldCommandNames.REGISTERED_ROOTS);
    }

    @Test
    void weightedTargetCommandsAreRegisteredWithoutRemovingLegacyAddForms() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/nightsta69/delvefold/command/DelvefoldCommands.java"));

        assertTrue(source.contains("Commands.literal(\"set-weight\")"));
        assertTrue(source.contains("Commands.literal(\"set-tag-weight\")"));
        assertTrue(source.contains(".executes(context -> addTarget(context, OreTarget.DEFAULT_WEIGHT))"),
                "Exact-target add must still execute without a weight");
        assertTrue(source.contains(".executes(context -> addTagTarget(context, OreTarget.DEFAULT_WEIGHT))"),
                "Tag-target add must still execute without a weight");
        assertTrue(source.contains("OreTarget.MIN_WEIGHT, OreTarget.MAX_WEIGHT"),
                "Command arguments must use the domain's 1-1000 bounds");
        assertTrue(source.contains("if (matches > 1)"),
                "Weight setters must reject ambiguous duplicate source IDs instead of changing every target");
    }

    @Test
    void renewalSeedModeCommandPreservesScheduleAndUsesAtomicInitialization() throws IOException {
        String source = commandSource();

        assertTrue(source.contains("Commands.literal(\"seed-mode\")"));
        assertTrue(source.contains("List.of(\"stable\", \"rotate_on_recreate\")"));
        assertTrue(source.contains("current.nextRenewalAtEpochMillis(), mode"),
                "Changing seed mode must preserve the renewal schedule");
        assertTrue(source.contains("current.warningMinutes(), 0L, current.seedMode()"),
                "Disabling renewal must preserve seed mode and schedule choices");
        assertTrue(source.contains("gameplay,\n                    snapshot.settings().identity())"),
                "Command initialization must use the identity-aware atomic overload");
    }

    @Test
    void doctorAndExportAreRegisteredBehindConfigurePermission() throws IOException {
        String source = compact(commandSource());

        assertTrue(source.contains(".then(doctorCommands())"),
                "The doctor tree must be attached to /delvefold");
        assertTrue(source.contains("Commands.literal(\"doctor\").requires(AdminAccess::canConfigure)"
                        + ".executes(DelvefoldCommands::doctor)"),
                "Doctor output may expose administrative diagnostics and must require configure access");
        assertTrue(source.contains("doctor.then(Commands.literal(\"export\")"
                        + ".executes(DelvefoldCommands::exportDoctorReport))"),
                "Doctor export must inherit the doctor permission rather than becoming a public root");
        assertTrue(source.contains("DelvefoldDoctorService.get().renderedLinesAsync(server).whenComplete("),
                "Doctor report construction must not block the server tick thread");
        assertTrue(source.contains("DelvefoldDoctorService.get().exportAsync(server).whenComplete("),
                "Doctor export must not block the server tick thread");
        assertTrue(source.contains("server.execute(()->"),
                "Async doctor completion must switch back to the server executor before messaging players");
    }

    @Test
    void backupVerificationAndRetentionCommandsInheritWorldManagementPermission() throws IOException {
        String source = compact(commandSource());

        assertTrue(source.contains("Commands.literal(\"backup\");backup.requires(AdminAccess::canManageWorld)"),
                "All backup mutations, verification, and retention settings must require world management");
        assertTrue(source.contains("Commands.literal(\"verify\").then(backupArgument()"
                        + ".executes(DelvefoldCommands::verifyBackup))"));
        assertTrue(source.contains("Commands.literal(\"retention\")"
                        + ".executes(DelvefoldCommands::retentionStatus)"));
        assertTrue(source.contains("Commands.literal(\"disable\")"
                        + ".executes(DelvefoldCommands::disableRetention)"));
        assertTrue(source.contains("Commands.argument(\"max_count\",IntegerArgumentType.integer(0))"));
        assertTrue(source.contains("Commands.argument(\"max_age_days\",LongArgumentType.longArg(0L))"));
        assertTrue(source.contains("Commands.argument(\"max_total_bytes\",LongArgumentType.longArg(0L))"));
        assertTrue(source.contains("varfuture=summary.legacy()?"
                        + "AsyncAuditMutationTracker.get().startTracked(saveRoot,"
                        + "()->verification.validateLegacyAndCreateManifestAsync(id),"),
                "Legacy validation must remain on the explicit verify command's tracked worker path");
        assertTrue(source.contains("}):verification.verifyAsync(id);"),
                "Legacy manifest creation must only occur on the explicit verify path");
        assertEquals(1, occurrences(source, "validateLegacyAndCreateManifestAsync(id)"),
                "No second command path may implicitly create a manifest for a legacy backup");
        assertTrue(source.contains("future.whenComplete((result,failure)->server.execute(()->"),
                "The asynchronous result must return to the server executor before touching player state");
        assertTrue(source.contains("backupSnapshot(context).backups().stream()"),
                "Backup suggestions and commands must use the asynchronous catalog cache");
        assertTrue(source.contains("AsyncAuditMutationTracker.get().startTracked(saveRoot,"
                        + "()->BackupCatalogCache.get().deleteAndRefreshAsync(server,id),"),
                "Backup deletion must synchronously capture protected references inside the tracked operation "
                        + "before its off-thread walk");
    }

    @Test
    void portalRoutingIsConfigureOnlyButHubMutationRequiresWorldManagement() throws IOException {
        String source = compact(commandSource());

        assertTrue(source.contains("Commands.literal(\"portal\");portal.requires(AdminAccess::canConfigure)"));
        assertTrue(source.contains("portal.then(Commands.literal(\"routing\")"
                        + ".then(Commands.argument(\"mode\",StringArgumentType.word())"),
                "Routing inherits configure permission from /delvefold portal");
        assertTrue(source.contains("portal.then(Commands.literal(\"hub\")"
                        + ".requires(AdminAccess::canManageWorld)"),
                "Hub placement and its protected region require world-management permission");
        assertTrue(source.contains("PortalHubSettings.MIN_PROTECTION_RADIUS"));
        assertTrue(source.contains("PortalHubSettings.MAX_PROTECTION_RADIUS"));
    }

    private static String commandSource() throws IOException {
        return Files.readString(Path.of(
                "src/main/java/com/nightsta69/delvefold/command/DelvefoldCommands.java"));
    }

    private static String compact(String source) {
        return source.replaceAll("\\s+", "");
    }

    private static int occurrences(String source, String needle) {
        return (source.length() - source.replace(needle, "").length()) / needle.length();
    }
}
