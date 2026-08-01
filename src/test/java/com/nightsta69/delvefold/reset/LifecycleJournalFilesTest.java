package com.nightsta69.delvefold.reset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonSyntaxException;
import com.nightsta69.delvefold.config.ConfigJson;
import com.nightsta69.delvefold.config.model.TerrainMode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Characterization tests for lifecycle journal names, safety limits, and typed validation. */
class LifecycleJournalFilesTest {
    private static final String OPERATION_ID = "11111111-1111-1111-1111-111111111111";

    @TempDir
    Path temporaryDirectory;

    @Test
    void canonicalPathsPreserveAllThreeJournalNames() {
        Path config = temporaryDirectory.resolve("serverconfig/delvefold");
        Path backup = temporaryDirectory.resolve("backup");

        assertEquals(
                config.resolve("pending_world_operation.json"), LifecycleJournalFiles.pendingWorldOperation(config));
        assertEquals(config.resolve("pending_restore.json"), LifecycleJournalFiles.pendingRestore(config));
        assertEquals(backup.resolve("operation.json"), LifecycleJournalFiles.operationMarker(backup));
    }

    @Test
    void typedReadersAcceptValidJournalsAtTheExactSizeLimit() throws Exception {
        PendingWorldOperation operation = operation(OPERATION_ID, PendingWorldOperation.CURRENT_SCHEMA_VERSION);
        Path operationPath = temporaryDirectory.resolve("operation-at-limit.json");
        writePaddedJson(operationPath, operation, LifecycleJournalFiles.MAX_JOURNAL_BYTES);

        assertEquals(LifecycleJournalFiles.MAX_JOURNAL_BYTES, Files.size(operationPath));
        assertEquals(operation, LifecycleJournalFiles.readWorldOperation(operationPath));

        PendingWorldRestore restore = restore(OPERATION_ID, PendingWorldRestore.CURRENT_SCHEMA_VERSION);
        Path restorePath = temporaryDirectory.resolve("restore-at-limit.json");
        writePaddedJson(restorePath, restore, LifecycleJournalFiles.MAX_JOURNAL_BYTES);

        assertEquals(LifecycleJournalFiles.MAX_JOURNAL_BYTES, Files.size(restorePath));
        assertEquals(restore, LifecycleJournalFiles.readRestore(restorePath));
    }

    @Test
    void oversizedJournalsAreRejectedBeforeParsingWithTypeSpecificMessages() throws Exception {
        Path operationPath = temporaryDirectory.resolve("oversized-operation.json");
        Files.writeString(operationPath, " ".repeat(LifecycleJournalFiles.MAX_JOURNAL_BYTES + 1));
        assertEquals(
                "Pending operation file failed safety checks",
                assertThrows(IOException.class, () -> LifecycleJournalFiles.readWorldOperation(operationPath))
                        .getMessage());

        Path restorePath = temporaryDirectory.resolve("oversized-restore.json");
        Files.writeString(restorePath, " ".repeat(LifecycleJournalFiles.MAX_JOURNAL_BYTES + 1));
        assertEquals(
                "Pending restore failed safety checks",
                assertThrows(IOException.class, () -> LifecycleJournalFiles.readRestore(restorePath))
                        .getMessage());
    }

    @Test
    void absentAndSymbolicLinkJournalsRetainTheirCallerSpecificSafetyErrors() throws Exception {
        Path missing = temporaryDirectory.resolve("missing.json");
        assertEquals(
                "Pending operation file failed safety checks",
                assertThrows(IOException.class, () -> LifecycleJournalFiles.readWorldOperation(missing))
                        .getMessage());

        Path target = temporaryDirectory.resolve("restore-target.json");
        Files.writeString(target, ConfigJson.GSON.toJson(restore(OPERATION_ID, 1)));
        Path link = temporaryDirectory.resolve("restore-link.json");
        Files.createSymbolicLink(link, target);
        assertEquals(
                "Pending restore failed safety checks",
                assertThrows(IOException.class, () -> LifecycleJournalFiles.readRestore(link))
                        .getMessage());
    }

    @Test
    void nullAndUnsupportedDocumentsRetainTypeSpecificSchemaErrors() throws Exception {
        Path nullOperation = temporaryDirectory.resolve("null-operation.json");
        Files.writeString(nullOperation, "null");
        assertEquals(
                "Pending operation has an unsupported schema",
                assertThrows(IOException.class, () -> LifecycleJournalFiles.readWorldOperation(nullOperation))
                        .getMessage());

        Path unsupportedRestore = temporaryDirectory.resolve("unsupported-restore.json");
        Files.writeString(unsupportedRestore, ConfigJson.GSON.toJson(restore(OPERATION_ID, 99)));
        assertEquals(
                "Pending restore schema is unsupported",
                assertThrows(IOException.class, () -> LifecycleJournalFiles.readRestore(unsupportedRestore))
                        .getMessage());
    }

    @Test
    void malformedJsonRetainsGsonParseFailuresForBothJournalTypes() throws Exception {
        Path operationPath = temporaryDirectory.resolve("malformed-operation.json");
        Files.writeString(operationPath, "{not-json");
        assertThrows(JsonSyntaxException.class, () -> LifecycleJournalFiles.readWorldOperation(operationPath));

        Path restorePath = temporaryDirectory.resolve("malformed-restore.json");
        Files.writeString(restorePath, "{not-json");
        assertThrows(JsonSyntaxException.class, () -> LifecycleJournalFiles.readRestore(restorePath));
    }

    @Test
    void invalidUuidTextRemainsAnUnwrappedIllegalArgumentException() throws Exception {
        Path operationPath = temporaryDirectory.resolve("invalid-operation-id.json");
        Files.writeString(
                operationPath,
                ConfigJson.GSON.toJson(operation("not-a-uuid", PendingWorldOperation.CURRENT_SCHEMA_VERSION)));
        assertThrows(IllegalArgumentException.class, () -> LifecycleJournalFiles.readWorldOperation(operationPath));

        Path restorePath = temporaryDirectory.resolve("invalid-restore-id.json");
        Files.writeString(
                restorePath, ConfigJson.GSON.toJson(restore("not-a-uuid", PendingWorldRestore.CURRENT_SCHEMA_VERSION)));
        assertThrows(IllegalArgumentException.class, () -> LifecycleJournalFiles.readRestore(restorePath));
    }

    private static PendingWorldOperation operation(String operationId, int schemaVersion) {
        return new PendingWorldOperation(
                schemaVersion,
                operationId,
                WorldOperationType.DELETE,
                TerrainMode.FLAT,
                null,
                null,
                null,
                null,
                null,
                BackupMode.KEEP_BACKUP,
                false,
                1_700_000_000_000L,
                "tester");
    }

    private static PendingWorldRestore restore(String operationId, int schemaVersion) {
        return new PendingWorldRestore(
                schemaVersion,
                operationId,
                "selected-backup",
                PendingWorldRestore.Phase.REQUESTED,
                1_700_000_000_000L,
                "tester");
    }

    private static void writePaddedJson(Path path, Object value, int size) throws Exception {
        byte[] json = ConfigJson.GSON.toJson(value).getBytes(StandardCharsets.UTF_8);
        Files.write(path, json);
        Files.writeString(
                path, " ".repeat(size - json.length), StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
    }
}
