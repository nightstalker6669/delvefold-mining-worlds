package com.nightsta69.delvefold.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.admin.AdminLocalizedMessage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DoctorReportRendererTest {
    @Test
    void rendersStableLocalizedLinesInReportOrder() {
        DoctorReport report = new DoctorReportBuilder(123L)
                .versions("1.3.0", "1.21.1", "21.1.244", 1, 12, 2)
                .addDimension("delvefold:z", "wild", DoctorReport.DimensionState.UNLOADED)
                .addDimension("delvefold:a", "flat", DoctorReport.DimensionState.ACTIVE)
                .profile("rich", 2L, 3, 4, 0L, 1L)
                .addIneffectiveTarget("zinc", "c:ores/zinc", "missing_tag")
                .addIneffectiveTarget("copper", "minecraft:copper_ore", "no_host_blocks")
                .addPendingOperation("operation-1", "recreate", "waiting", 20L)
                .backups(2, 1, 0, 1, 1, 1024L)
                .disk(4096L, 1024L, 2048L, 3072L)
                .build();
        DoctorReportRenderer renderer = new DoctorReportRenderer();

        List<String> first = renderer.render(report);
        assertEquals(first, renderer.render(report));
        assertEquals(
                "message.delvefold.doctor.line.header",
                decoded(first.getFirst()).translationKey());
        assertEquals(List.of("1", "123"), decoded(first.getFirst()).arguments());
        assertTrue(decoded(first.get(2)).arguments().contains("1.3.0"));
        int dimensionA = indexWithArgument(first, "delvefold:a");
        int dimensionZ = indexWithArgument(first, "delvefold:z");
        int copper = indexWithArgument(first, "copper");
        int zinc = indexWithArgument(first, "zinc");
        assertTrue(dimensionA < dimensionZ);
        assertTrue(copper < zinc);
        assertTrue(first.stream()
                .map(DoctorReportRendererTest::decoded)
                .anyMatch(line -> line.translationKey().equals("message.delvefold.doctor.line.disk")));
    }

    @Test
    void redactsAndBoundsLargeReportsForExistingProtocolLimits() {
        List<DoctorReport.DimensionStatus> dimensions = new ArrayList<>();
        for (int index = 0; index < 200; index++) {
            dimensions.add(new DoctorReport.DimensionStatus(
                    index == 0 ? "/home/alice/secret-world" : "delvefold:dimension_" + index,
                    "wild",
                    DoctorReport.DimensionState.ACTIVE));
        }
        DoctorReport report = new DoctorReport(
                1,
                1L,
                DoctorReport.VersionInfo.unknown(),
                dimensions,
                DoctorReport.ProfileHealth.unknown(),
                List.of(),
                DoctorReport.BackupHealth.empty(),
                DoctorReport.DiskEstimate.unknown());

        List<String> lines = new DoctorReportRenderer().render(report);
        assertTrue(lines.size() <= DoctorReportRenderer.MAX_LINES);
        assertTrue(lines.stream().allMatch(line -> line.length() <= DoctorReportRenderer.MAX_LINE_CHARACTERS));
        int bytes = lines.stream()
                .mapToInt(line -> line.getBytes(StandardCharsets.UTF_8).length)
                .sum();
        assertTrue(bytes <= DoctorReportRenderer.MAX_TOTAL_UTF8_BYTES);
        assertEquals(
                "message.delvefold.doctor.line.truncated",
                decoded(lines.getLast()).translationKey());
        String joined = lines.stream()
                .map(DoctorReportRendererTest::decoded)
                .flatMap(line -> line.arguments().stream())
                .collect(java.util.stream.Collectors.joining("\n"));
        assertFalse(joined.contains("/home/alice"));
        assertTrue(joined.contains("[redacted]"));
    }

    private static int indexWithArgument(List<String> lines, String expected) {
        for (int index = 0; index < lines.size(); index++) {
            if (decoded(lines.get(index)).arguments().contains(expected)) {
                return index;
            }
        }
        throw new AssertionError("Missing diagnostic line containing argument: " + expected);
    }

    private static AdminLocalizedMessage.Decoded decoded(String line) {
        return AdminLocalizedMessage.decode(line)
                .orElseThrow(() -> new AssertionError("Diagnostic line was not localized: " + line));
    }
}
