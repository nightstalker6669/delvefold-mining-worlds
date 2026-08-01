package com.nightsta69.delvefold.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.ConfigJson;
import com.nightsta69.delvefold.config.ConfigPaths;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.reset.BackupMode;
import com.nightsta69.delvefold.reset.PendingWorldOperation;
import com.nightsta69.delvefold.reset.WorldOperationType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Characterizes the doctor's tolerant, bounded, and path-contained filesystem policy. */
class DoctorFilesystemAnalysisTest {
    private static final String OPERATION_ID = "11111111-1111-1111-1111-111111111111";

    @TempDir
    Path temporary;

    @Test
    void missingPendingFilesProduceNoDiagnosticEntries() {
        DoctorFilesystemAnalysis.PendingJournalScan scan =
                DoctorFilesystemAnalysis.scanPending(temporary.resolve("missing"));

        assertTrue(scan.statuses().isEmpty());
        assertTrue(scan.pendingBackupIds().isEmpty());
    }

    @Test
    void symbolicLinkPendingFileProducesAStableInvalidEntry() throws Exception {
        Path config = Files.createDirectories(temporary.resolve("config"));
        Path target = Files.writeString(temporary.resolve("outside.json"), ConfigJson.GSON.toJson(operation()));
        try {
            Files.createSymbolicLink(config.resolve("pending_world_operation.json"), target);
        } catch (UnsupportedOperationException | IOException exception) {
            return;
        }

        DoctorFilesystemAnalysis.PendingJournalScan scan = DoctorFilesystemAnalysis.scanPending(config);

        assertEquals(1, scan.statuses().size());
        assertEquals("pending_world_operation", scan.statuses().getFirst().operationId());
        assertEquals("world_operation", scan.statuses().getFirst().operation());
        assertEquals("invalid", scan.statuses().getFirst().state());
    }

    @Test
    void pendingJournalAcceptsExactLimitAndRejectsOneAdditionalByte() throws Exception {
        Path config = Files.createDirectories(temporary.resolve("config"));
        Path pending = config.resolve("pending_world_operation.json");
        byte[] json = ConfigJson.GSON.toJson(operation()).getBytes(StandardCharsets.UTF_8);
        byte[] exact = Arrays.copyOf(json, (int) DoctorFilesystemAnalysis.MAX_PENDING_BYTES);
        Arrays.fill(exact, json.length, exact.length, (byte) ' ');
        Files.write(pending, exact);

        DoctorFilesystemAnalysis.PendingJournalScan accepted = DoctorFilesystemAnalysis.scanPending(config);

        assertEquals(1, accepted.statuses().size());
        assertEquals(OPERATION_ID, accepted.statuses().getFirst().operationId());
        assertEquals("restart_required", accepted.statuses().getFirst().state());

        Files.write(pending, Arrays.copyOf(exact, exact.length + 1));
        DoctorFilesystemAnalysis.PendingJournalScan rejected = DoctorFilesystemAnalysis.scanPending(config);

        assertEquals(1, rejected.statuses().size());
        assertEquals("pending_world_operation", rejected.statuses().getFirst().operationId());
        assertEquals("invalid", rejected.statuses().getFirst().state());
    }

    @Test
    void malformedPendingJournalProducesAStableInvalidEntry() throws Exception {
        Path config = Files.createDirectories(temporary.resolve("config"));
        Files.writeString(config.resolve("pending_restore.json"), "{not-json");

        DoctorFilesystemAnalysis.PendingJournalScan scan = DoctorFilesystemAnalysis.scanPending(config);

        assertEquals(1, scan.statuses().size());
        assertEquals("pending_restore", scan.statuses().getFirst().operationId());
        assertEquals("restore", scan.statuses().getFirst().operation());
        assertEquals("invalid", scan.statuses().getFirst().state());
    }

    @Test
    void treeEstimateEnforcesEntryCapAndRejectsSymbolicLinks() throws Exception {
        Path root = Files.createDirectories(temporary.resolve("tree"));
        Files.write(root.resolve("first"), new byte[7]);

        assertEquals(7L, DoctorFilesystemAnalysis.estimateTreeBytes(root, 2));

        Files.write(root.resolve("second"), new byte[11]);
        assertEquals(-1L, DoctorFilesystemAnalysis.estimateTreeBytes(root, 2));

        Path linkedRoot = Files.createDirectories(temporary.resolve("linked-tree"));
        Path target = Files.write(temporary.resolve("outside"), new byte[3]);
        try {
            Files.createSymbolicLink(linkedRoot.resolve("link"), target);
            assertEquals(-1L, DoctorFilesystemAnalysis.estimateTreeBytes(linkedRoot, 10));
        } catch (UnsupportedOperationException | IOException exception) {
            // Link rejection cannot be exercised on filesystems without symbolic-link support.
        }
    }

    @Test
    void arithmeticSaturatesAndNormalizesCounts() {
        assertEquals(Long.MAX_VALUE, DoctorFilesystemAnalysis.saturatingAdd(Long.MAX_VALUE - 2L, 3L));
        assertEquals(7L, DoctorFilesystemAnalysis.saturatingAdd(3L, 4L));
        assertEquals(Integer.MAX_VALUE, DoctorFilesystemAnalysis.saturatingCount(Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(5, DoctorFilesystemAnalysis.saturatingCount(-4, 5));
    }

    @Test
    void currentBackupEstimateIncludesOnlyContainedDimensionAndConfigBytes() throws Exception {
        Path saveRoot = Files.createDirectories(temporary.resolve("save"));
        Path dimensions = Files.createDirectories(saveRoot.resolve("dimensions/delvefold/delve_flat"));
        Files.write(dimensions.resolve("region.mca"), new byte[17]);
        Path config = Files.createDirectories(saveRoot.resolve("serverconfig/delvefold"));
        Path ores = Files.write(config.resolve("ores.json"), new byte[5]);
        Path settings = Files.write(config.resolve("settings.json"), new byte[7]);
        ConfigPaths contained = new ConfigPaths(config, ores, settings);

        assertEquals(29L, DoctorFilesystemAnalysis.estimateCurrentBackupBytes(saveRoot, contained));

        Path outside = Files.write(temporary.resolve("outside-settings.json"), new byte[1]);
        ConfigPaths escaped = new ConfigPaths(config, ores, outside);
        assertEquals(-1L, DoctorFilesystemAnalysis.estimateCurrentBackupBytes(saveRoot, escaped));
    }

    @Test
    void diskEstimateRetainsUnknownUsableSpaceAndNormalizesBackupSentinel() {
        Path missingRoot = temporary.resolve("missing-save");
        Path config = missingRoot.resolve("serverconfig/delvefold");
        ConfigPaths paths = new ConfigPaths(config, config.resolve("ores.json"), config.resolve("settings.json"));

        DoctorReport.DiskEstimate estimate = DoctorFilesystemAnalysis.diskEstimate(missingRoot, paths, -9L);

        assertEquals(-1L, estimate.usableBytes());
        assertEquals(-1L, estimate.backupBytes());
        assertEquals(0L, estimate.estimatedNextBackupBytes());
        assertEquals(16L * 1024L * 1024L, estimate.requiredHeadroomBytes());
    }

    @Test
    void exportDirectoryMustBeAContainedNonLinkDirectory() throws Exception {
        Path config = temporary.resolve("serverconfig/delvefold");
        ConfigPaths paths = new ConfigPaths(config, config.resolve("ores.json"), config.resolve("settings.json"));

        Path exports = DoctorFilesystemAnalysis.ensureExportsDirectory(paths);

        assertEquals(config.toAbsolutePath().normalize().resolve("exports"), exports);
        assertTrue(Files.isDirectory(exports));

        Files.delete(exports);
        Files.writeString(exports, "not-a-directory");
        assertThrows(IOException.class, () -> DoctorFilesystemAnalysis.ensureExportsDirectory(paths));

        Files.delete(exports);
        try {
            Files.createSymbolicLink(exports, temporary);
            assertThrows(IOException.class, () -> DoctorFilesystemAnalysis.ensureExportsDirectory(paths));
        } catch (UnsupportedOperationException | IOException exception) {
            // Link rejection cannot be exercised on filesystems without symbolic-link support.
        }
    }

    private static PendingWorldOperation operation() {
        return new PendingWorldOperation(
                PendingWorldOperation.CURRENT_SCHEMA_VERSION,
                OPERATION_ID,
                WorldOperationType.DELETE,
                TerrainMode.FLAT,
                null,
                null,
                null,
                null,
                null,
                BackupMode.KEEP_BACKUP,
                false,
                100L,
                "redacted-actor");
    }
}
