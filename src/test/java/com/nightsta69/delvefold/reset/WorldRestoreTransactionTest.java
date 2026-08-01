package com.nightsta69.delvefold.reset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.ConfigJson;
import com.nightsta69.delvefold.config.OrePresets;
import com.nightsta69.delvefold.config.model.GameplayPreset;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.WorldIdentitySettings;
import com.nightsta69.delvefold.config.model.WorldSettingsDocument;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldRestoreTransactionTest {
    private static final String OPERATION_ID = "11111111-1111-1111-1111-111111111111";

    @TempDir
    Path temporaryDirectory;

    @Test
    void lateUnsafeDimensionLeavesEveryActiveFolderIntactAndCanBeRetried() throws Exception {
        Path config = createConfiguration();
        Path active = temporaryDirectory.resolve("dimensions/delvefold");
        Path preRestore = temporaryDirectory.resolve("delvefold_backups/pre-restore");
        Map<String, String> expected = createActiveDimensions(active);

        String unsafeName = DelvefoldDimensionFolders.ALL.get(4);
        Path unsafe = active.resolve(unsafeName);
        deleteTree(unsafe);
        Path escaped = Files.createDirectories(temporaryDirectory.resolve("outside-save"));
        Files.writeString(escaped.resolve("region.mca"), "outside");
        Files.createSymbolicLink(unsafe, escaped);

        PendingWorldRestore pending = pending();
        assertThrows(IOException.class,
                () -> RestoreCurrentBackupTransaction.backup(config, active, preRestore, pending));

        for (var entry : expected.entrySet()) {
            Path dimension = active.resolve(entry.getKey());
            if (entry.getKey().equals(unsafeName)) {
                assertTrue(Files.isSymbolicLink(dimension));
                continue;
            }
            assertEquals(entry.getValue(), Files.readString(dimension.resolve("region.mca")),
                    "a failed backup transaction must restore " + entry.getKey());
            assertFalse(Files.exists(preRestore.resolve("dimensions/delvefold")
                    .resolve(entry.getKey())), "rollback must not leave a duplicate staged folder");
        }
        assertEquals("outside", Files.readString(escaped.resolve("region.mca")));

        Files.delete(unsafe);
        Files.createDirectories(unsafe);
        Files.writeString(unsafe.resolve("region.mca"), expected.get(unsafeName));

        RestoreCurrentBackupTransaction.backup(config, active, preRestore, pending);
        assertSnapshotPublished(active, preRestore, expected);

        // A crash after the folders moved but before the phase journal advanced must be retryable.
        RestoreCurrentBackupTransaction.backup(config, active, preRestore, pending);
        assertSnapshotPublished(active, preRestore, expected);
    }

    @Test
    void unsafeLateDestinationCannotCauseAPartialActiveSnapshot() throws Exception {
        Path config = createConfiguration();
        Path active = temporaryDirectory.resolve("destination-test/dimensions/delvefold");
        Path preRestore = temporaryDirectory.resolve("destination-test/backups/pre-restore");
        Map<String, String> expected = createActiveDimensions(active);

        String unsafeName = DelvefoldDimensionFolders.ALL.get(3);
        Path unsafeDestination = preRestore.resolve("dimensions/delvefold").resolve(unsafeName);
        Files.createDirectories(unsafeDestination.getParent());
        Files.writeString(unsafeDestination, "not a directory");

        assertThrows(IOException.class,
                () -> RestoreCurrentBackupTransaction.backup(config, active, preRestore, pending()));

        for (var entry : expected.entrySet()) {
            assertEquals(entry.getValue(), Files.readString(
                    active.resolve(entry.getKey()).resolve("region.mca")));
        }
        assertEquals("not a directory", Files.readString(unsafeDestination));
    }

    @Test
    void oneShotMidMoveFailureRollsBackOnlyThisAttemptAndThenRetriesCleanly() throws Exception {
        Path config = createConfiguration();
        Path active = temporaryDirectory.resolve("injected-failure/dimensions/delvefold");
        Path preRestore = temporaryDirectory.resolve("injected-failure/backups/pre-restore");
        Map<String, String> expected = createActiveDimensions(active);
        AtomicInteger moveCalls = new AtomicInteger();

        RestoreCurrentBackupTransaction.DirectoryMover failOnce = (source, destination) -> {
            int call = moveCalls.incrementAndGet();
            if (call == 4) {
                throw new IOException("injected move failure");
            }
            Files.move(source, destination);
        };

        IOException failure = assertThrows(IOException.class,
                () -> RestoreCurrentBackupTransaction.backup(
                        config, active, preRestore, pending(), failOnce));
        assertEquals("injected move failure", failure.getMessage());
        assertEquals(7, moveCalls.get(),
                "three successful moves must be rolled back in reverse order after the fourth fails");
        for (var entry : expected.entrySet()) {
            assertEquals(entry.getValue(), Files.readString(
                    active.resolve(entry.getKey()).resolve("region.mca")));
            assertFalse(Files.exists(preRestore.resolve("dimensions/delvefold")
                    .resolve(entry.getKey())));
        }
        assertFalse(Files.exists(preRestore.resolve(BackupManifest.FILE_NAME)));
        assertFalse(Files.exists(preRestore.resolve(BackupVerificationReceipt.FILE_NAME)));

        RestoreCurrentBackupTransaction.backup(config, active, preRestore, pending());
        assertSnapshotPublished(active, preRestore, expected);
    }

    @Test
    void manifestFailureAfterEveryMoveRollsBackAndRecapturesRepairedConfiguration() throws Exception {
        Path config = createConfiguration();
        Files.writeString(config.resolve("ores.json"), "{}\n");
        Path active = temporaryDirectory.resolve("manifest-failure/dimensions/delvefold");
        Path preRestore = temporaryDirectory.resolve("manifest-failure/backups/pre-restore");
        Map<String, String> expected = createActiveDimensions(active);

        assertThrows(IOException.class,
                () -> RestoreCurrentBackupTransaction.backup(config, active, preRestore, pending()));

        for (var entry : expected.entrySet()) {
            assertEquals(entry.getValue(), Files.readString(
                    active.resolve(entry.getKey()).resolve("region.mca")));
            assertFalse(Files.exists(preRestore.resolve("dimensions/delvefold").resolve(entry.getKey())));
        }
        assertFalse(Files.exists(preRestore.resolve(BackupManifest.FILE_NAME)));
        assertFalse(Files.exists(preRestore.resolve(BackupVerificationReceipt.FILE_NAME)));
        assertFalse(Files.exists(preRestore.resolve(RestoreConfigSnapshot.COMPLETE_MARKER)));

        Files.writeString(config.resolve("ores.json"),
                ConfigJson.GSON.toJson(OrePresets.create(OrePreset.VANILLA_BALANCED)));
        RestoreCurrentBackupTransaction.backup(config, active, preRestore, pending());
        assertSnapshotPublished(active, preRestore, expected);
        assertTrue(Files.isRegularFile(preRestore.resolve(BackupManifest.FILE_NAME)));
        assertTrue(Files.isRegularFile(preRestore.resolve(BackupVerificationReceipt.FILE_NAME)));
    }

    @Test
    void rollbackPreservesFoldersPublishedByAnEarlierCrashForForwardRetry() throws Exception {
        Path config = createConfiguration();
        Path active = temporaryDirectory.resolve("crash-retry/dimensions/delvefold");
        Path preRestore = temporaryDirectory.resolve("crash-retry/backups/pre-restore");
        Map<String, String> expected = createActiveDimensions(active);

        String previouslyMovedName = DelvefoldDimensionFolders.ALL.getFirst();
        Path previouslyMoved = preRestore.resolve("dimensions/delvefold").resolve(previouslyMovedName);
        Files.createDirectories(previouslyMoved.getParent());
        Files.move(active.resolve(previouslyMovedName), previouslyMoved);

        String unsafeName = DelvefoldDimensionFolders.ALL.get(4);
        Path unsafe = active.resolve(unsafeName);
        deleteTree(unsafe);
        Path escaped = Files.createDirectories(temporaryDirectory.resolve("crash-retry-outside"));
        Files.createSymbolicLink(unsafe, escaped);

        assertThrows(IOException.class,
                () -> RestoreCurrentBackupTransaction.backup(config, active, preRestore, pending()));

        assertFalse(Files.exists(active.resolve(previouslyMovedName)));
        assertEquals(expected.get(previouslyMovedName),
                Files.readString(previouslyMoved.resolve("region.mca")),
                "rollback must not undo a folder published by an earlier startup");
        for (var entry : expected.entrySet()) {
            if (entry.getKey().equals(previouslyMovedName) || entry.getKey().equals(unsafeName)) {
                continue;
            }
            assertEquals(entry.getValue(), Files.readString(
                    active.resolve(entry.getKey()).resolve("region.mca")));
            assertFalse(Files.exists(preRestore.resolve("dimensions/delvefold")
                    .resolve(entry.getKey())));
        }

        Files.delete(unsafe);
        Files.createDirectories(unsafe);
        Files.writeString(unsafe.resolve("region.mca"), expected.get(unsafeName));
        RestoreCurrentBackupTransaction.backup(config, active, preRestore, pending());
        assertSnapshotPublished(active, preRestore, expected);
    }

    private Path createConfiguration() throws Exception {
        Path config = Files.createDirectories(temporaryDirectory.resolve("serverconfig/delvefold"));
        WorldSettingsDocument settings = WorldSettingsDocument.uninitialized().initialize(
                TerrainMode.FLAT,
                OrePreset.VANILLA_BALANCED,
                GameplayPreset.SAFE,
                WorldIdentitySettings.defaults());
        Files.writeString(config.resolve("settings.json"), ConfigJson.GSON.toJson(settings));
        Files.writeString(config.resolve("ores.json"),
                ConfigJson.GSON.toJson(OrePresets.create(OrePreset.VANILLA_BALANCED)));
        return config;
    }

    private static Map<String, String> createActiveDimensions(Path active) throws Exception {
        Map<String, String> expected = new LinkedHashMap<>();
        int index = 0;
        for (String name : DelvefoldDimensionFolders.ALL) {
            String contents = "dimension-" + index++;
            Path dimension = Files.createDirectories(active.resolve(name));
            Files.writeString(dimension.resolve("region.mca"), contents);
            expected.put(name, contents);
        }
        return expected;
    }

    private static void assertSnapshotPublished(
            Path active, Path preRestore, Map<String, String> expected) throws Exception {
        for (var entry : expected.entrySet()) {
            assertFalse(Files.exists(active.resolve(entry.getKey())));
            assertEquals(entry.getValue(), Files.readString(preRestore.resolve("dimensions/delvefold")
                    .resolve(entry.getKey()).resolve("region.mca")));
        }
        assertTrue(Files.isRegularFile(preRestore.resolve("operation.json")));
        assertTrue(Files.isRegularFile(preRestore.resolve(RestoreConfigSnapshot.COMPLETE_MARKER)));
    }

    private static PendingWorldRestore pending() {
        return new PendingWorldRestore(
                PendingWorldRestore.CURRENT_SCHEMA_VERSION,
                OPERATION_ID,
                "selected-backup",
                PendingWorldRestore.Phase.STAGED,
                1_700_000_000_000L,
                "tester");
    }

    private static void deleteTree(Path root) throws Exception {
        if (Files.notExists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
