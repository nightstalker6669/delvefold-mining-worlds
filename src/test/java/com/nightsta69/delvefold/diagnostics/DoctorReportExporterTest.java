package com.nightsta69.delvefold.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DoctorReportExporterTest {
    @TempDir
    Path temporary;

    @Test
    void exportIsDeterministicWhitelistedAndRedactsSensitiveLookingIdentifiers() throws Exception {
        DoctorReport report = new DoctorReportBuilder(123L)
                .versions("1.3.0", "1.21.1", "21.1.244", 1, 12, 2)
                .addDimension(
                        "/home/alice/server/world/dimensions/delvefold", "wild", DoctorReport.DimensionState.ACTIVE)
                .profile("token=DO_NOT_EXPORT", 4L, 1, 1, 0L, 1L)
                .addIneffectiveTarget("ore", "example:tin_ore", "missing_block")
                .addProfileFinding(DoctorReport.Severity.WARNING, "bad_target", "config/serverconfig/ores.json")
                .addPendingOperation("127.0.0.1:25565", "restore", "waiting", 100L)
                .backups(1, 0, 1, 0, 0, 42L)
                .addBackupProblem("backup-1", "invalid", "confirmation_token=SECRET")
                .retention(new DoctorReport.RetentionPreview(
                        true,
                        1,
                        0,
                        42L,
                        0L,
                        true,
                        List.of(new DoctorReport.RetentionPrune("backup-1", 1L, 42L, List.of("age"))),
                        List.of()))
                .disk(1000L, 42L, 100L, 200L)
                .build();
        DoctorReportExporter exporter = new DoctorReportExporter();

        String first = exporter.toRedactedJson(report);
        String second = exporter.toRedactedJson(report);
        assertEquals(first, second);
        assertFalse(first.contains("/home/alice"));
        assertFalse(first.contains("DO_NOT_EXPORT"));
        assertFalse(first.contains("127.0.0.1"));
        assertFalse(first.contains("config/serverconfig"));
        assertFalse(first.contains("SECRET"));
        assertTrue(first.contains("[redacted]"));

        JsonObject parsed = JsonParser.parseString(first).getAsJsonObject();
        assertTrue(parsed.get("redacted").getAsBoolean());
        assertEquals(1, parsed.get("format_version").getAsInt());
        assertEquals(
                12, parsed.getAsJsonObject("versions").get("network_protocol").getAsInt());
        assertEquals(
                42L,
                parsed.getAsJsonObject("retention").get("reclaimable_bytes").getAsLong());
        assertFalse(parsed.has("confirmation_token"));
        assertFalse(parsed.has("filesystem_path"));
        assertFalse(parsed.has("server_address"));
    }

    @Test
    void writesOnlyInsideAnExistingSafeExportsDirectory() throws Exception {
        Path exports = Files.createDirectory(temporary.resolve("exports"));
        DoctorReport report = new DoctorReportBuilder(123L).build();
        DoctorReportExporter exporter = new DoctorReportExporter();

        Path written = exporter.export(exports, report);
        assertEquals(exports.resolve("delvefold-doctor-0000000000000000123.json"), written);
        assertTrue(Files.isRegularFile(written));
        assertTrue(Files.readString(written).endsWith(System.lineSeparator()));

        String original = Files.readString(written);
        assertEquals(written, exporter.export(exports, report));
        assertEquals(original, Files.readString(written));
        assertThrows(IOException.class, () -> exporter.export(temporary.resolve("missing"), report));
    }

    @Test
    void refusesSymbolicLinkExportDirectoriesWhenSupported() throws Exception {
        Path actual = Files.createDirectory(temporary.resolve("actual"));
        Path link = temporary.resolve("link");
        try {
            Files.createSymbolicLink(link, actual);
        } catch (UnsupportedOperationException | IOException exception) {
            return;
        }

        assertThrows(
                IOException.class, () -> new DoctorReportExporter().export(link, new DoctorReportBuilder(1L).build()));
    }
}
