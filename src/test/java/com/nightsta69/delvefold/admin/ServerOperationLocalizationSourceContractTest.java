package com.nightsta69.delvefold.admin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ServerOperationLocalizationSourceContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/nightsta69/delvefold");

    @Test
    void worldLifecycleProducersEmitStructuredTranslationEnvelopes() throws IOException {
        String operations = read("reset/WorldOperationService.java");
        String restores = read("reset/WorldRestoreService.java");

        assertTrue(operations.contains("AdminLocalizedMessage.encode(translationKey, arguments)"));
        assertTrue(restores.contains("AdminLocalizedMessage.encode(translationKey, arguments)"));
        assertTrue(operations.contains("message.delvefold.world_operation.preview.backup"));
        assertTrue(restores.contains("message.delvefold.restore.preview"));
        assertFalse(operations.contains("\"Operation scheduled. Restart"));
        assertFalse(operations.contains("\"No world operation is awaiting confirmation"));
        assertFalse(restores.contains("\"Restore scheduled. Restart"));
        assertFalse(restores.contains("\"No restore is pending"));
    }

    @Test
    void commandAndClientConsumersResolveNestedTranslationComponents() throws IOException {
        String commands = read("command/DelvefoldCommands.java");
        String components = read("admin/AdminLocalizedComponents.java");
        String clientText = read("client/gui/DelvefoldText.java");

        assertTrue(commands.contains("AdminLocalizedComponents.resolve(preview.message())"));
        assertTrue(commands.contains("AdminLocalizedComponents.resolve(result.message())"));
        assertFalse(commands.contains("Component.literal(preview.message())"));
        assertFalse(commands.contains("Component.literal(result.message())"));
        assertTrue(components.contains("resolve(argument, depth + 1)"));
        assertTrue(clientText.contains("AdminLocalizedComponents.resolve(message)"));
    }

    @Test
    void snapshotFallbacksAndProfileResultsAreLocalizedAtTheirProducer() throws IOException {
        String snapshot = read("network/model/AdminSnapshot.java");
        String service = read("network/service/DelvefoldAdminService.java");
        String profiles = read("config/OreProfileCatalog.java");
        String config = read("config/DelvefoldConfigService.java");

        assertTrue(snapshot.contains("message.delvefold.admin.snapshot.backend_unavailable"));
        assertTrue(service.contains("message.delvefold.admin.operation_completed"));
        assertTrue(profiles.contains("message.delvefold.profile.saved"));
        assertTrue(config.contains("message.delvefold.profile.deleted"));
        assertFalse(snapshot.contains("\"Administration backend is not installed.\""));
        assertFalse(service.contains("\"Operation completed.\""));
        assertFalse(profiles.contains("\"Profile validation failed\""));
        assertFalse(config.contains("\"The active profile cannot be deleted\""));
    }

    @Test
    void oreImportFixedStatusesAreLocalizedBeforeNetworkDelivery() throws IOException {
        String oreImport = read("admin/OreImportAdminService.java");
        String planner = read("config/importer/OreImportPlanner.java");
        String importScreen = read("client/gui/DelvefoldOreImportScreen.java");

        assertTrue(oreImport.contains("message.delvefold.import.scan.revision_changed"));
        assertTrue(oreImport.contains("message.delvefold.import.session.registry_changed"));
        assertTrue(oreImport.contains("message.delvefold.import.profile_created"));
        assertFalse(oreImport.contains("\"Ore discovery is rate limited;"));
        assertFalse(oreImport.contains("\"The ore import preview expired;"));
        assertFalse(oreImport.contains("\"Created profile '\""));
        assertTrue(planner.contains("message.delvefold.import.diff_message.partially_added"));
        assertTrue(importScreen.contains("DelvefoldText.serverMessage(entry.message())"));
        assertFalse(importScreen.contains("Component.literal(entry.message())"));
    }

    @Test
    void backupVerificationConfigIssuesAndCompatibilityUseStructuredMessages() throws IOException {
        String verification = read("reset/BackupVerificationService.java");
        String admin = read("admin/DefaultDelvefoldAdminService.java");
        String config = read("config/DelvefoldConfigService.java");
        String commands = read("command/DelvefoldCommands.java");

        assertTrue(verification.contains("message.delvefold.backup_verification.verified"));
        assertFalse(verification.contains("\"Backup manifest and contents verified\""));
        assertTrue(admin.contains("ConfigIssueMessages.encode(result.issues().getFirst())"));
        assertTrue(config.contains("message.delvefold.config.compatibility_incompatible"));
        assertFalse(config.contains("\"This save uses an incompatible pre-0.2"));
        assertTrue(commands.contains("AdminLocalizedComponents.resolve(result.message())"));
        assertFalse(commands.contains("Component.literal(exception.getMessage())"));
    }

    private static String read(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative));
    }
}
