package com.nightsta69.delvefold.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Characterizes the protocol-12 payload directions, payload codecs, and codec inventory. */
class NetworkSurfaceCompatibilityTest {
    private static final Path NETWORK = Path.of("src/main/java/com/nightsta69/delvefold/network");
    private static final Set<String> TO_SERVER = Set.of(
            "AdminActionPayload",
            "BackupActionPayload",
            "DeleteOreRulePayload",
            "ForecastRequestPayload",
            "GameplayUpdatePayload",
            "GuideOpenedPayload",
            "IdentityUpdatePayload",
            "InitializeWorldPayload",
            "OpenGuiRequestPayload",
            "OreImportCreatePayload",
            "OreImportPreviewPageRequestPayload",
            "OreImportPreviewRequestPayload",
            "OreImportScanPageRequestPayload",
            "OreImportScanRequestPayload",
            "OrePageRequestPayload",
            "PortalUpdatePayload",
            "ProfileActionPayload",
            "ProfileExportRequestPayload",
            "SaveOreRulePayload");
    private static final Set<String> TO_CLIENT = Set.of(
            "ActionResultPayload",
            "OpenForecastPayload",
            "OpenGuiPayload",
            "OpenGuidePayload",
            "OpenOreImportPreviewPayload",
            "OpenOreImportScanPayload",
            "ProfileExportPayload");
    private static final Set<String> CODEC_FILES =
            Set.of("DelvefoldStreamCodecs", "GuideStreamCodecs", "OreForecastStreamCodecs", "OreImportStreamCodecs");
    private static final Pattern REGISTRATION = Pattern.compile(
            "registrar\\.playTo(Server|Client)\\(\\s*(\\w+)\\.TYPE\\s*,\\s*\\2\\.STREAM_CODEC\\s*,", Pattern.DOTALL);

    @Test
    void protocolTwelveRegistersTheExactVersion130PayloadInventory() throws Exception {
        String source = Files.readString(NETWORK.resolve("DelvefoldNetwork.java"));
        assertTrue(
                source.matches("(?s).*public\\s+static\\s+final\\s+String\\s+PROTOCOL_VERSION\\s*=\\s*\"12\"\\s*;.*"));

        Map<String, Set<String>> actual = new TreeMap<>();
        actual.put("Server", new TreeSet<>());
        actual.put("Client", new TreeSet<>());
        var matcher = REGISTRATION.matcher(source);
        while (matcher.find()) {
            actual.get(matcher.group(1)).add(matcher.group(2));
        }

        assertEquals(new TreeSet<>(TO_SERVER), actual.get("Server"), "Client-to-server payload inventory changed");
        assertEquals(new TreeSet<>(TO_CLIENT), actual.get("Client"), "Server-to-client payload inventory changed");
    }

    @Test
    void everyPayloadOwnsATypeAndStreamCodecAndIsRegisteredOnce() throws Exception {
        Path payloadDirectory = NETWORK.resolve("payload");
        Set<String> payloadFiles;
        try (var files = Files.list(payloadDirectory)) {
            payloadFiles = files.filter(path -> path.getFileName().toString().endsWith("Payload.java"))
                    .map(path -> path.getFileName().toString().replaceFirst("\\.java$", ""))
                    .collect(Collectors.toCollection(TreeSet::new));
        }
        Set<String> expected = new TreeSet<>(TO_SERVER);
        expected.addAll(TO_CLIENT);
        assertEquals(expected, payloadFiles, "Protocol payload source inventory changed");

        for (String payload : expected) {
            String source = Files.readString(payloadDirectory.resolve(payload + ".java"));
            assertTrue(source.contains("implements CustomPacketPayload"), payload + " must remain a custom payload");
            assertTrue(
                    source.matches("(?s).*public\\s+static\\s+final\\s+.*\\bTYPE\\s*=.*"),
                    payload + " must expose its stable payload type");
            assertTrue(
                    source.matches("(?s).*public\\s+static\\s+final\\s+StreamCodec<.*\\bSTREAM_CODEC\\s*=.*"),
                    payload + " must expose its bounded stream codec");
        }
    }

    @Test
    void protocolCodecUtilityInventoryRemainsStable() throws Exception {
        try (var files = Files.list(NETWORK.resolve("codec"))) {
            Set<String> actual = files.filter(
                            path -> path.getFileName().toString().endsWith(".java"))
                    .map(path -> path.getFileName().toString().replaceFirst("\\.java$", ""))
                    .collect(Collectors.toCollection(TreeSet::new));
            assertEquals(new TreeSet<>(CODEC_FILES), actual, "Protocol codec source inventory changed");
        }
    }
}
