package com.nightsta69.delvefold.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RotatingAuditLogTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-01T12:34:56Z"), ZoneOffset.UTC);

    @TempDir
    Path temporary;

    @Test
    void appendsOneCompactDeterministicJsonObjectPerLine() throws Exception {
        RotatingAuditLog log = new RotatingAuditLog(temporary.resolve("audit"), FIXED_CLOCK);
        AuditEntry entry = log.append(new AuditMutation(
                "nightsta69",
                AuditMutation.Operation.PROFILE_ACTIVATED,
                AuditMutation.ObjectType.PROFILE,
                "custom-rich",
                8L,
                9L));

        assertEquals("2026-08-01T12:34:56Z", entry.timestamp());
        List<String> lines = Files.readAllLines(log.activePath());
        assertEquals(1, lines.size());
        JsonObject json = JsonParser.parseString(lines.getFirst()).getAsJsonObject();
        assertEquals(1, json.get("format_version").getAsInt());
        assertEquals("profile_activated", json.get("operation").getAsString());
        assertEquals("profile:custom-rich", json.get("affected_object").getAsString());
        assertEquals(8L, json.get("old_revision").getAsLong());
        assertEquals(9L, json.get("new_revision").getAsLong());
        assertFalse(json.has("confirmation_token"));
        assertFalse(json.has("profile_json"));
        assertFalse(json.has("server_address"));
        assertFalse(json.has("player_data"));
    }

    @Test
    void rejectsInputsThatCouldSmuggleSecretsPathsAddressesOrStructuredData() {
        assertThrows(IllegalArgumentException.class, () -> mutation("actor\nsecret", "settings"));
        assertThrows(IllegalArgumentException.class, () -> mutation("console", "confirmation_token=SECRET"));
        assertThrows(IllegalArgumentException.class, () -> mutation("console", "/home/alice/world"));
        assertThrows(IllegalArgumentException.class, () -> mutation("console", "127.0.0.1:25565"));
        assertThrows(IllegalArgumentException.class, () -> mutation("console", "server.example.com:25565"));
        assertThrows(IllegalArgumentException.class, () -> mutation("console", "{complete-profile-json}"));
        assertThrows(IllegalArgumentException.class, () -> new AuditMutation(
                "console", AuditMutation.Operation.CONFIGURATION_ACCEPTED,
                AuditMutation.ObjectType.SETTINGS, "settings", -2L, 1L));
    }

    @Test
    void rotatesBeforeLimitAndRetainsExactlyConfiguredFileCount() throws Exception {
        Path auditDirectory = temporary.resolve("rotation");
        RotatingAuditLog log = new RotatingAuditLog(auditDirectory, FIXED_CLOCK, 240L, 3);
        for (int revision = 0; revision < 7; revision++) {
            log.append(new AuditMutation(
                    "console", AuditMutation.Operation.CONFIGURATION_ACCEPTED,
                    AuditMutation.ObjectType.SETTINGS, "settings-" + revision,
                    revision - 1L, revision));
        }

        List<Path> files;
        try (Stream<Path> listed = Files.list(auditDirectory)) {
            files = listed.sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
        }
        assertEquals(3, files.size());
        assertTrue(Files.exists(log.activePath()));
        assertTrue(Files.exists(log.archivePath(1)));
        assertTrue(Files.exists(log.archivePath(2)));
        assertTrue(Files.readString(log.activePath()).contains("settings-6"));
        assertTrue(Files.readString(log.archivePath(1)).contains("settings-5"));
        assertTrue(Files.readString(log.archivePath(2)).contains("settings-4"));
        for (Path file : files) {
            assertTrue(Files.size(file) <= 240L);
            for (String line : Files.readAllLines(file)) {
                assertTrue(JsonParser.parseString(line).isJsonObject());
            }
        }
    }

    @Test
    void productionPolicyIsTenMiBAndFiveTotalFiles() {
        assertEquals(10L * 1024L * 1024L, RotatingAuditLog.ROTATE_BYTES);
        assertEquals(5, RotatingAuditLog.RETAINED_FILES);
    }

    @Test
    void refusesOversizedEntriesAndSymbolicLinkLogTargets() throws Exception {
        RotatingAuditLog tiny = new RotatingAuditLog(temporary.resolve("tiny"), FIXED_CLOCK, 128L, 2);
        assertThrows(IOException.class, () -> tiny.append(new AuditMutation(
                "console", AuditMutation.Operation.CONFIGURATION_ACCEPTED,
                AuditMutation.ObjectType.SETTINGS, "x".repeat(100), 0L, 1L)));

        Path directory = Files.createDirectory(temporary.resolve("symlink-audit"));
        Path target = Files.writeString(temporary.resolve("target.jsonl"), "do not overwrite");
        try {
            Files.createSymbolicLink(directory.resolve(RotatingAuditLog.ACTIVE_FILENAME), target);
        } catch (UnsupportedOperationException | IOException exception) {
            return;
        }
        RotatingAuditLog unsafe = new RotatingAuditLog(directory, FIXED_CLOCK);
        assertThrows(IOException.class, () -> unsafe.append(mutation("console", "settings")));
        assertEquals("do not overwrite", Files.readString(target));
    }

    private static AuditMutation mutation(String actor, String objectId) {
        return new AuditMutation(
                actor, AuditMutation.Operation.CONFIGURATION_ACCEPTED,
                AuditMutation.ObjectType.SETTINGS, objectId, 0L, 1L);
    }
}
