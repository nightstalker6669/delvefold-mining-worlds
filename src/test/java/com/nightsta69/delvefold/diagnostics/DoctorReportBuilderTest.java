package com.nightsta69.delvefold.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DoctorReportBuilderTest {
    @Test
    void buildsImmutableDeterministicallyOrderedHealthSnapshot() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-01T12:34:56Z"), ZoneOffset.UTC);
        DoctorReportBuilder builder = new DoctorReportBuilder(clock)
                .versions("1.3.0", "1.21.1", "21.1.244", 1, 12, 2)
                .addDimension("delvefold:delve_wild", "wild", DoctorReport.DimensionState.UNLOADED)
                .addDimension("delvefold:delve_flat", "flat", DoctorReport.DimensionState.ACTIVE)
                .profile("rich", 9L, 2, 3, 0L, 1L)
                .addIneffectiveTarget("zinc", "c:ores/zinc", "missing_tag")
                .addIneffectiveTarget("copper", "example:copper_ore", "no_host_blocks")
                .addProfileFinding(DoctorReport.Severity.WARNING, "shadowed_rule", "zinc")
                .addPendingOperation("operation-b", "restore", "verifying", 200L)
                .addPendingOperation("operation-a", "recreate", "restart_required", 100L)
                .backups(4, 2, 1, 1, 1, 4096L)
                .addBackupProblem("backup-z", "invalid", "hash_mismatch")
                .retention(new DoctorReport.RetentionPreview(
                        true, 4, 3, 4096L, 3072L, false,
                        List.of(new DoctorReport.RetentionPrune(
                                "backup-a", 1L, 1024L, List.of("count"))),
                        List.of("max_count_protected")))
                .disk(1024L, 4096L, 2048L, 3072L);

        DoctorReport report = builder.build();

        assertEquals(1785587696000L, report.generatedAtEpochMillis());
        assertEquals("delvefold:delve_flat", report.dimensions().getFirst().dimensionId());
        assertEquals("copper", report.profile().ineffectiveTargets().getFirst().ruleId());
        assertEquals("operation-a", report.pendingOperations().getFirst().operationId());
        assertEquals(12, report.versions().networkProtocol());
        assertFalse(report.profile().healthy() && report.backups().healthy());
        assertFalse(report.disk().sufficient());
        assertEquals(1024L, report.retention().reclaimableBytes());
        assertFalse(report.healthy());
        assertThrows(UnsupportedOperationException.class,
                () -> report.dimensions().add(new DoctorReport.DimensionStatus(
                        "delvefold:other", "wild", DoctorReport.DimensionState.ACTIVE)));
        assertThrows(IllegalStateException.class, builder::build);
    }

    @Test
    void reportAndNestedViewsDefensivelyCopyCallerLists() {
        List<DoctorReport.IneffectiveTarget> targets = new ArrayList<>();
        targets.add(new DoctorReport.IneffectiveTarget("iron", "minecraft:iron_ore", "no_host_blocks"));
        DoctorReport.ProfileHealth profile = new DoctorReport.ProfileHealth(
                "balanced", 1L, 1, 1, 0L, 0L, targets, List.of());
        targets.clear();

        List<DoctorReport.DimensionStatus> dimensions = new ArrayList<>();
        dimensions.add(new DoctorReport.DimensionStatus(
                "delvefold:delve_flat", "flat", DoctorReport.DimensionState.ACTIVE));
        DoctorReport report = new DoctorReport(
                DoctorReport.CURRENT_FORMAT_VERSION, 1L, DoctorReport.VersionInfo.unknown(),
                dimensions, profile, List.of(), DoctorReport.BackupHealth.empty(),
                new DoctorReport.DiskEstimate(1L, 0L, 0L, 1L));
        dimensions.clear();

        assertEquals(1, report.dimensions().size());
        assertEquals(1, report.profile().ineffectiveTargets().size());
    }

    @Test
    void rejectsImpossibleCountsAndNegativeTimestamps() {
        assertThrows(IllegalArgumentException.class, () -> new DoctorReportBuilder(-1L));
        assertThrows(IllegalArgumentException.class, () -> new DoctorReport.ProfileHealth(
                "bad", 0L, 2, 1, 0L, 0L, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new DoctorReport.BackupHealth(
                1, 2, 0, 0, 0, 0L, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new DoctorReport.BackupHealth(
                1, 1, 1, 0, 0, 0L, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new DoctorReport.DiskEstimate(
                -2L, 0L, 0L, 0L));
    }
}
