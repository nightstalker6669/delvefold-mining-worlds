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
        String source =
                Files.readString(Path.of("src/main/java/com/nightsta69/delvefold/command/DelvefoldCommands.java"));

        assertTrue(containsCode(source, "Commands.literal(\"set-weight\")"));
        assertTrue(containsCode(source, "Commands.literal(\"set-tag-weight\")"));
        assertTrue(
                containsCode(source, ".executes(context -> addTarget(context, OreTarget.DEFAULT_WEIGHT))"),
                "Exact-target add must still execute without a weight");
        assertTrue(
                containsCode(source, ".executes(context -> addTagTarget(context, OreTarget.DEFAULT_WEIGHT))"),
                "Tag-target add must still execute without a weight");
        assertTrue(
                containsCode(source, "OreTarget.MIN_WEIGHT, OreTarget.MAX_WEIGHT"),
                "Command arguments must use the domain's 1-1000 bounds");
        assertTrue(
                containsCode(source, "if (matches > 1)"),
                "Weight setters must reject ambiguous duplicate source IDs instead of changing every target");
    }

    @Test
    void renewalSeedModeCommandPreservesScheduleAndUsesAtomicInitialization() throws IOException {
        String source = commandSource();

        assertTrue(containsCode(source, "Commands.literal(\"seed-mode\")"));
        assertTrue(containsCode(source, "List.of(\"stable\", \"rotate_on_recreate\")"));
        assertTrue(
                containsCode(source, "current.nextRenewalAtEpochMillis(), mode"),
                "Changing seed mode must preserve the renewal schedule");
        assertTrue(
                containsCode(source, "current.warningMinutes(), 0L, current.seedMode()"),
                "Disabling renewal must preserve seed mode and schedule choices");
        assertTrue(
                containsCode(source, "gameplay, snapshot.settings().identity())"),
                "Command initialization must use the identity-aware atomic overload");
    }

    @Test
    void doctorAndExportAreRegisteredBehindConfigurePermission() throws IOException {
        String source = compact(commandSource());

        assertTrue(containsCode(source, ".then(doctorCommands())"), "The doctor tree must be attached to /delvefold");
        assertTrue(
                containsCode(
                        source,
                        "Commands.literal(\"doctor\").requires(AdminAccess::canConfigure)"
                                + ".executes(DelvefoldCommands::doctor)"),
                "Doctor output may expose administrative diagnostics and must require configure access");
        assertTrue(
                containsCode(
                        source,
                        "doctor.then(Commands.literal(\"export\")"
                                + ".executes(DelvefoldCommands::exportDoctorReport))"),
                "Doctor export must inherit the doctor permission rather than becoming a public root");
        assertTrue(
                containsCode(source, "DelvefoldDoctorService.get().renderedLinesAsync(server).whenComplete("),
                "Doctor report construction must not block the server tick thread");
        assertTrue(
                containsCode(source, "DelvefoldDoctorService.get().exportAsync(server).whenComplete("),
                "Doctor export must not block the server tick thread");
        assertTrue(
                containsCode(source, "server.execute(()->"),
                "Async doctor completion must switch back to the server executor before messaging players");
    }

    @Test
    void backupVerificationAndRetentionCommandsInheritWorldManagementPermission() throws IOException {
        String source = compact(commandSource());

        assertTrue(
                containsCode(source, "Commands.literal(\"backup\");backup.requires(AdminAccess::canManageWorld)"),
                "All backup mutations, verification, and retention settings must require world management");
        assertTrue(containsCode(
                source,
                "Commands.literal(\"verify\").then(backupArgument()" + ".executes(DelvefoldCommands::verifyBackup))"));
        assertTrue(containsCode(
                source, "Commands.literal(\"retention\")" + ".executes(DelvefoldCommands::retentionStatus)"));
        assertTrue(containsCode(
                source, "Commands.literal(\"disable\")" + ".executes(DelvefoldCommands::disableRetention)"));
        assertTrue(containsCode(source, "Commands.argument(\"max_count\",IntegerArgumentType.integer(0))"));
        assertTrue(containsCode(source, "Commands.argument(\"max_age_days\",LongArgumentType.longArg(0L))"));
        assertTrue(containsCode(source, "Commands.argument(\"max_total_bytes\",LongArgumentType.longArg(0L))"));
        assertTrue(
                containsCode(
                        source,
                        "varfuture=summary.legacy()?"
                                + "AsyncAuditMutationTracker.get().startTracked(saveRoot,"
                                + "()->verification.validateLegacyAndCreateManifestAsync(id),"),
                "Legacy validation must remain on the explicit verify command's tracked worker path");
        assertTrue(
                containsCode(source, "}):verification.verifyAsync(id);"),
                "Legacy manifest creation must only occur on the explicit verify path");
        assertEquals(
                1,
                occurrences(source, "validateLegacyAndCreateManifestAsync(id)"),
                "No second command path may implicitly create a manifest for a legacy backup");
        assertTrue(
                containsCode(source, "future.whenComplete((result,failure)->server.execute(()->"),
                "The asynchronous result must return to the server executor before touching player state");
        assertTrue(
                containsCode(source, "backupSnapshot(context).backups().stream()"),
                "Backup suggestions and commands must use the asynchronous catalog cache");
        assertTrue(
                containsCode(
                        source,
                        "AsyncAuditMutationTracker.get().startTracked(saveRoot,"
                                + "()->BackupCatalogCache.get().deleteAndRefreshAsync(server,id),"),
                "Backup deletion must synchronously capture protected references inside the tracked operation "
                        + "before its off-thread walk");
    }

    @Test
    void portalRoutingIsConfigureOnlyButHubMutationRequiresWorldManagement() throws IOException {
        String source = compact(commandSource());

        assertTrue(containsCode(source, "Commands.literal(\"portal\");portal.requires(AdminAccess::canConfigure)"));
        assertTrue(
                containsCode(
                        source,
                        "portal.then(Commands.literal(\"routing\")"
                                + ".then(Commands.argument(\"mode\",StringArgumentType.word())"),
                "Routing inherits configure permission from /delvefold portal");
        assertTrue(
                containsCode(
                        source, "portal.then(Commands.literal(\"hub\")" + ".requires(AdminAccess::canManageWorld)"),
                "Hub placement and its protected region require world-management permission");
        assertTrue(containsCode(source, "PortalHubSettings.MIN_PROTECTION_RADIUS"));
        assertTrue(containsCode(source, "PortalHubSettings.MAX_PROTECTION_RADIUS"));
    }

    private static String commandSource() throws IOException {
        return Files.readString(Path.of("src/main/java/com/nightsta69/delvefold/command/DelvefoldCommands.java"));
    }

    private static String compact(String source) {
        return source.replaceAll("\\s+", "");
    }

    private static boolean containsCode(String source, String expected) {
        return compact(source).contains(compact(expected));
    }

    private static int occurrences(String source, String needle) {
        String compactSource = compact(source);
        String compactNeedle = compact(needle);
        return (compactSource.length()
                        - compactSource.replace(compactNeedle, "").length())
                / compactNeedle.length();
    }
}
