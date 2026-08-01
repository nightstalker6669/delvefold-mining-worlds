package com.nightsta69.delvefold.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.ConfigJson;
import com.nightsta69.delvefold.config.ConfigPaths;
import com.nightsta69.delvefold.config.model.BackupRetentionSettings;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.reset.BackupMode;
import com.nightsta69.delvefold.reset.BackupRetentionPlanner;
import com.nightsta69.delvefold.reset.BackupRetentionRunState;
import com.nightsta69.delvefold.reset.PendingWorldOperation;
import com.nightsta69.delvefold.reset.PendingWorldRestore;
import com.nightsta69.delvefold.reset.WorldBackupCatalog;
import com.nightsta69.delvefold.reset.WorldOperationType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DelvefoldDoctorServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

    @TempDir
    Path temporary;

    @Test
    void mapsManifestStatesWithoutDoubleCountingInvalidBackups() {
        List<WorldBackupCatalog.BackupSummary> backups = List.of(
                backup("verified", 400L, true, true, true, true, false),
                backup("legacy", 300L, false, false, true, false, true),
                backup("invalid", -1L, false, false, false, true, false),
                backup("unverified", 200L, false, false, true, true, false));

        DelvefoldDoctorService.BackupAnalysis analysis = DelvefoldDoctorService.analyzeBackups(backups);

        assertEquals(4, analysis.total());
        assertEquals(1, analysis.verified());
        assertEquals(1, analysis.invalid());
        assertEquals(1, analysis.legacy());
        assertEquals(1, analysis.pinned());
        assertEquals(900L, analysis.knownBytes());
        assertEquals(-1L, analysis.diskBytes());
        assertEquals(4, analysis.candidates().size());
        Set<String> reasons = analysis.problems().stream()
                .map(DoctorReport.BackupProblem::reasonCode)
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(reasons.containsAll(
                Set.of("manifest_required", "backup_invalid", "verification_required", "size_unavailable")));
    }

    @Test
    void mapsRetentionPlanIntoStableRedactedPreview() {
        List<BackupRetentionPlanner.Candidate> candidates =
                List.of(candidate("old", 100, 1000), candidate("middle", 10, 2000), candidate("newest", 1, 3000));
        BackupRetentionPlanner.Plan plan =
                BackupRetentionPlanner.plan(new BackupRetentionSettings(true, 2, 0, 0), candidates, Set.of(), NOW);

        DoctorReport.RetentionPreview preview = DelvefoldDoctorService.retentionPreview(plan);

        assertTrue(preview.enabled());
        assertEquals(3, preview.beforeCount());
        assertEquals(2, preview.afterCount());
        assertEquals(
                List.of("old"),
                preview.prunes().stream()
                        .map(DoctorReport.RetentionPrune::backupId)
                        .toList());
        assertEquals(List.of("count"), preview.prunes().getFirst().reasons());
        assertEquals(1000L, preview.reclaimableBytes());
    }

    @Test
    void includesLastAutomaticRetentionProposalAndBoundedResultCounts() {
        BackupRetentionPlanner.Plan plan =
                BackupRetentionPlanner.plan(BackupRetentionSettings.defaults(), List.of(), Set.of(), NOW);
        BackupRetentionRunState.Snapshot run = new BackupRetentionRunState.Snapshot(
                NOW.toEpochMilli(),
                true,
                7,
                4,
                7000L,
                4000L,
                true,
                List.of(new BackupRetentionRunState.Proposal(
                        "old-backup", List.of(BackupRetentionPlanner.Reason.AGE, BackupRetentionPlanner.Reason.COUNT))),
                2,
                List.of("protected-backup"),
                3,
                List.of(),
                List.of("old-backup"),
                4,
                List.of("failed-backup"),
                5,
                true);

        DoctorReport.RetentionPreview preview = DelvefoldDoctorService.retentionPreview(plan, run);

        assertTrue(preview.lastRun().available());
        assertEquals(3, preview.lastRun().proposedCount());
        assertEquals(
                List.of("age", "count"),
                preview.lastRun().proposals().getFirst().reasons());
        assertEquals(5, preview.lastRun().appliedCount());
        assertEquals(6, preview.lastRun().failureCount());
        assertEquals(4, preview.lastRun().protectedCount());
    }

    @Test
    void readsBothPendingJournalsWithoutIncludingActorsOrTokens() throws Exception {
        Path config = Files.createDirectories(temporary.resolve("serverconfig/delvefold"));
        PendingWorldOperation operation = new PendingWorldOperation(
                PendingWorldOperation.CURRENT_SCHEMA_VERSION,
                "11111111-1111-1111-1111-111111111111",
                WorldOperationType.DELETE,
                TerrainMode.FLAT,
                null,
                null,
                null,
                null,
                BackupMode.KEEP_BACKUP,
                false,
                100L,
                "private-actor");
        PendingWorldRestore restore = new PendingWorldRestore(
                PendingWorldRestore.CURRENT_SCHEMA_VERSION,
                "22222222-2222-2222-2222-222222222222",
                "backup-selected",
                PendingWorldRestore.Phase.STAGED,
                200L,
                "other-private-actor");
        Files.writeString(config.resolve("pending_world_operation.json"), ConfigJson.GSON.toJson(operation));
        Files.writeString(config.resolve("pending_restore.json"), ConfigJson.GSON.toJson(restore));

        DelvefoldDoctorService.PendingScan scan = DelvefoldDoctorService.scanPending(config);

        assertEquals(2, scan.statuses().size());
        assertEquals("delete", scan.statuses().getFirst().operation());
        assertEquals("staged", scan.statuses().get(1).state());
        assertEquals(Set.of("backup-selected"), scan.pendingBackupIds());
        String rendered = scan.statuses().toString();
        assertFalse(rendered.contains("private-actor"));
    }

    @Test
    void malformedPendingJournalBecomesAStableDiagnostic() throws Exception {
        Path config = Files.createDirectories(temporary.resolve("config"));
        Files.writeString(config.resolve("pending_restore.json"), "not-json");

        DelvefoldDoctorService.PendingScan scan = DelvefoldDoctorService.scanPending(config);

        assertEquals(1, scan.statuses().size());
        assertEquals("pending_restore", scan.statuses().getFirst().operationId());
        assertEquals("invalid", scan.statuses().getFirst().state());
        assertTrue(scan.pendingBackupIds().isEmpty());
    }

    @Test
    void sizeEstimatesAreContainedBoundedAndRejectLinks() throws Exception {
        Path dimensions = Files.createDirectories(temporary.resolve("dimensions/delvefold/delve_flat"));
        Files.write(dimensions.resolve("region.mca"), new byte[17]);
        Path config = Files.createDirectories(temporary.resolve("serverconfig/delvefold"));
        Path ores = Files.write(config.resolve("ores.json"), new byte[5]);
        Path settings = Files.write(config.resolve("settings.json"), new byte[7]);
        ConfigPaths paths = new ConfigPaths(config, ores, settings);

        assertEquals(17L, DelvefoldDoctorService.estimateTreeBytes(temporary.resolve("dimensions/delvefold"), 10));
        assertEquals(29L, DelvefoldDoctorService.estimateCurrentBackupBytes(temporary, paths));

        Files.write(dimensions.resolve("second.mca"), new byte[1]);
        assertEquals(-1L, DelvefoldDoctorService.estimateTreeBytes(dimensions, 2));
        Path target = Files.write(temporary.resolve("outside"), new byte[1]);
        try {
            Files.createSymbolicLink(dimensions.resolve("unsafe"), target);
            assertEquals(-1L, DelvefoldDoctorService.estimateTreeBytes(dimensions, 20));
        } catch (UnsupportedOperationException | IOException ignored) {
        }
    }

    @Test
    void createsOnlyTheContainedExportsDirectory() throws Exception {
        Path config = temporary.resolve("new-serverconfig/delvefold");
        ConfigPaths paths = new ConfigPaths(config, config.resolve("ores.json"), config.resolve("settings.json"));

        Path exports = DelvefoldDoctorService.ensureExportsDirectory(paths);

        assertEquals(config.resolve("exports"), exports);
        assertTrue(Files.isDirectory(exports));
        Files.delete(exports);
        Files.createSymbolicLink(exports, temporary);
        assertThrows(IOException.class, () -> DelvefoldDoctorService.ensureExportsDirectory(paths));
    }

    @Test
    void lifecycleInvalidatesReportsAndCompletionCannotRepopulateAClearedSession() throws Exception {
        String doctor = Files.readString(
                Path.of("src/main/java/com/nightsta69/delvefold/diagnostics/DelvefoldDoctorService.java"));
        String lifecycle = Files.readString(
                Path.of("src/main/java/com/nightsta69/delvefold/server/DelvefoldServerLifecycle.java"));

        assertTrue(doctor.contains("boolean currentSession = inFlight.remove(key, future);"));
        assertTrue(
                doctor.contains("if (currentSession && failure == null && report != null)"),
                "A completed scan from a stopped session must not repopulate the same save key");
        assertTrue(doctor.contains("public void invalidate(MinecraftServer server)"));
        assertTrue(doctor.contains("public void clear()"));
        assertTrue(
                lifecycle.contains("DelvefoldDoctorService.get().clear();"),
                "Server shutdown must clear reports before the same save can reopen");
    }

    private static WorldBackupCatalog.BackupSummary backup(
            String id, long size, boolean pinned, boolean restorable, boolean valid, boolean manifest, boolean legacy) {
        return new WorldBackupCatalog.BackupSummary(
                id, 1L, "recreate", "flat", size, pinned, restorable, valid, manifest, restorable && manifest, legacy);
    }

    private static BackupRetentionPlanner.Candidate candidate(String id, long ageDays, long bytes) {
        return new BackupRetentionPlanner.Candidate(
                id, NOW.minus(ageDays, ChronoUnit.DAYS).toEpochMilli(), bytes, false);
    }
}
