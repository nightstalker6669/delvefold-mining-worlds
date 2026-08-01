package com.nightsta69.delvefold.reset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RestoreConfigSnapshotTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void incompleteCopyIsDiscardedAndOnlyCompletedSnapshotBecomesReusable() throws Exception {
        Path source = temporaryDirectory.resolve("serverconfig/delvefold");
        Files.createDirectories(source.resolve("profiles"));
        Files.writeString(source.resolve("settings.json"), "settings-v1");
        Files.writeString(source.resolve("ores.json"), "ores-v1");
        Files.writeString(source.resolve("profiles/custom.json"), "profile-v1");
        Files.writeString(source.resolve("pending_restore.json"), "transient");
        Files.createDirectories(source.resolve("audit"));
        Files.writeString(source.resolve("audit/delvefold-audit.jsonl"), "live-audit");
        Files.createDirectories(source.resolve("exports"));
        Files.writeString(source.resolve("exports/doctor.json"), "live-export");
        Files.createDirectories(source.resolve("imports"));
        Files.writeString(source.resolve("imports/inbox.json"), "live-import");
        Files.createDirectories(source.resolve("world_operations"));
        Files.writeString(source.resolve("world_operations/history.json"), "live-history");

        Path backup = temporaryDirectory.resolve("pre-restore");
        Path partial = backup.resolve("config/serverconfig/delvefold");
        Files.createDirectories(partial);
        Files.writeString(partial.resolve("settings.json"), "partial");
        Files.createDirectories(backup.resolve(".config-staging"));
        Files.writeString(backup.resolve(".config-staging/orphan"), "partial");

        RestoreConfigSnapshot.capture(source, backup);

        assertEquals("settings-v1", Files.readString(partial.resolve("settings.json")));
        assertEquals("ores-v1", Files.readString(partial.resolve("ores.json")));
        assertEquals("profile-v1", Files.readString(partial.resolve("profiles/custom.json")));
        assertFalse(Files.exists(partial.resolve("pending_restore.json")));
        assertFalse(Files.exists(partial.resolve("audit")));
        assertFalse(Files.exists(partial.resolve("exports")));
        assertFalse(Files.exists(partial.resolve("imports")));
        assertFalse(Files.exists(partial.resolve("world_operations")));
        assertFalse(Files.exists(backup.resolve(".config-staging")));
        assertTrue(Files.isRegularFile(backup.resolve(RestoreConfigSnapshot.COMPLETE_MARKER)));

        Files.writeString(source.resolve("settings.json"), "settings-v2");
        RestoreConfigSnapshot.capture(source, backup);
        assertEquals("settings-v1", Files.readString(partial.resolve("settings.json")),
                "a completed pre-restore snapshot must remain immutable across restart recovery");
    }

    @Test
    void bothLifecycleBackupPathsUseTheCrashSafeSnapshotPublisher() throws Exception {
        String worldOperation = Files.readString(Path.of(
                "src/main/java/com/nightsta69/delvefold/reset/WorldOperationService.java"));
        String restore = Files.readString(Path.of(
                "src/main/java/com/nightsta69/delvefold/reset/WorldRestoreService.java"));
        String restoreTransaction = Files.readString(Path.of(
                "src/main/java/com/nightsta69/delvefold/reset/RestoreCurrentBackupTransaction.java"));

        assertTrue(worldOperation.contains(
                "RestoreConfigSnapshot.capture(ConfigPaths.forServer(server).directory(), holdingRoot)"));
        assertTrue(restore.contains(
                "RestoreCurrentBackupTransaction.backup(configDirectory, activeRoot, preRestore, pending)"),
                "Restore startup must delegate its pre-restore backup to the transactional publisher");
        assertTrue(restoreTransaction.contains("RestoreConfigSnapshot.capture(config, backup)"),
                "The transactional pre-restore backup must publish configuration crash-safely");
    }

    @Test
    void restoreReplacesConfigurationButPreservesOperationalHistory() throws Exception {
        Path staged = temporaryDirectory.resolve("staged/config/serverconfig/delvefold");
        Files.createDirectories(staged.resolve("profiles"));
        Files.writeString(staged.resolve("settings.json"), "selected-settings");
        Files.writeString(staged.resolve("ores.json"), "selected-ores");
        Files.writeString(staged.resolve("profiles/selected.json"), "selected-profile");
        Files.createDirectories(staged.resolve("audit"));
        Files.writeString(staged.resolve("audit/delvefold-audit.jsonl"), "stale-audit");
        Files.createDirectories(staged.resolve("exports"));
        Files.writeString(staged.resolve("exports/doctor.json"), "stale-export");
        Files.createDirectories(staged.resolve("imports"));
        Files.writeString(staged.resolve("imports/inbox.json"), "stale-import");
        Files.createDirectories(staged.resolve("world_operations"));
        Files.writeString(staged.resolve("world_operations/history.json"), "stale-history");
        Files.writeString(staged.resolve("pending_restore.json"), "stale-pending");

        Path active = temporaryDirectory.resolve("active/serverconfig/delvefold");
        Files.createDirectories(active.resolve("audit"));
        Files.writeString(active.resolve("settings.json"), "current-settings");
        Files.writeString(active.resolve("ores.json"), "current-ores");
        Files.writeString(active.resolve("audit/delvefold-audit.jsonl"), "current-audit");
        Files.createDirectories(active.resolve("exports"));
        Files.writeString(active.resolve("exports/doctor.json"), "current-export");
        Files.createDirectories(active.resolve("imports"));
        Files.writeString(active.resolve("imports/inbox.json"), "current-import");
        Files.createDirectories(active.resolve("world_operations"));
        Files.writeString(active.resolve("world_operations/history.json"), "current-history");

        RestoreConfigSnapshot.install(staged, active);

        assertEquals("selected-settings", Files.readString(active.resolve("settings.json")));
        assertEquals("selected-ores", Files.readString(active.resolve("ores.json")));
        assertEquals("selected-profile", Files.readString(active.resolve("profiles/selected.json")));
        assertEquals("current-audit", Files.readString(active.resolve("audit/delvefold-audit.jsonl")));
        assertEquals("current-export", Files.readString(active.resolve("exports/doctor.json")));
        assertEquals("current-import", Files.readString(active.resolve("imports/inbox.json")));
        assertEquals("current-history", Files.readString(active.resolve("world_operations/history.json")));
        assertFalse(Files.exists(active.resolve("pending_restore.json")));
    }

    @Test
    void restoreRefusesSymlinksInsideTheActiveConfiguration() throws Exception {
        Path staged = temporaryDirectory.resolve("staged-safe");
        Files.createDirectories(staged.resolve("profiles"));
        Files.writeString(staged.resolve("settings.json"), "selected-settings");
        Files.writeString(staged.resolve("profiles/selected.json"), "selected-profile");

        Path active = temporaryDirectory.resolve("active-safe");
        Path escaped = temporaryDirectory.resolve("escaped");
        Files.createDirectories(active);
        Files.createDirectories(escaped);
        try {
            Files.createSymbolicLink(active.resolve("profiles"), escaped);
        } catch (UnsupportedOperationException | java.io.IOException exception) {
            return;
        }

        assertThrows(java.io.IOException.class, () -> RestoreConfigSnapshot.install(staged, active));
        assertFalse(Files.exists(escaped.resolve("selected.json")));
    }
}
