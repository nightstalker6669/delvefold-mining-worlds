package com.nightsta69.delvefold.network;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source contracts for Unified Ores payloads whose Minecraft supertypes are absent from pure unit tests. */
class OreLibraryPayloadTest {
    private static final Path PAYLOADS = Path.of("src/main/java/com/nightsta69/delvefold/network/payload");

    @Test
    void pageRequestNormalizesAndBoundsEveryUntrustedField() throws Exception {
        String request = read("OreLibraryRequestPayload.java");

        assertTrue(request.contains("expectedOreRevision < 0L"));
        assertTrue(request.contains("catalogToken == null ? \"\" : catalogToken.trim()"));
        assertTrue(request.contains("normalizedToken.length() > ProtocolLimits.MAX_IMPORT_TOKEN_LENGTH"));
        assertTrue(request.contains("page < 0 || page > ProtocolLimits.MAX_IMPORT_GROUPS"));
        assertTrue(request.contains("query == null ? \"\" : query.trim()"));
        assertTrue(request.contains("codePointCount(0, normalized.length())"));
        assertTrue(request.contains("> ProtocolLimits.MAX_ORE_LIBRARY_QUERY_LENGTH"));
    }

    @Test
    void atomicAddNormalizesBeforeDuplicateDetectionAndOwnsItsSelection() throws Exception {
        String payload = read("AddOreFamiliesPayload.java");
        int constructorStart = payload.indexOf("public AddOreFamiliesPayload(");
        assertTrue(constructorStart >= 0, "The payload must retain its validating canonical constructor");
        String constructor = payload.substring(constructorStart);

        assertTrue(constructor.contains("expectedRevision < 0L"));
        assertTrue(constructor.contains("normalizedToken.isEmpty()"));
        assertTrue(constructor.contains("ProtocolLimits.MAX_IMPORT_TOKEN_LENGTH"));
        assertTrue(constructor.contains("OreFamilySelection.normalize(familyIds)"));
    }

    @Test
    void atomicAddDecoderChecksCountsBeforeAllocating() throws Exception {
        String payload = read("AddOreFamiliesPayload.java");

        int countRead = payload.indexOf("int count = buffer.readVarInt()");
        int countValidation = payload.indexOf("if (count < 1 || count > ProtocolLimits.MAX_ORE_LIBRARY_SELECTIONS)");
        int allocation = payload.indexOf("new java.util.ArrayList<>(count)");

        assertTrue(countRead >= 0, "The decoder must read an explicit selection count");
        assertTrue(
                countValidation > countRead && allocation > countValidation,
                "The wire count must be bounded before allocating the family-ID list");
    }

    @Test
    void mathematicallyMaximalAtomicAddFitsTheDeclaredServerboundBudgets() {
        int payloadType = asciiText("delvefold:add_ore_families".length());
        int revision = 9;
        int catalogToken = maximumUtf8Text(ProtocolLimits.MAX_IMPORT_TOKEN_LENGTH);
        int selectionCount = varInt(ProtocolLimits.MAX_ORE_LIBRARY_SELECTIONS);
        int familyId = asciiText(ProtocolLimits.ID_LENGTH);
        int maximum = payloadType
                + revision
                + catalogToken
                + selectionCount
                + ProtocolLimits.MAX_ORE_LIBRARY_SELECTIONS * familyId;

        assertTrue(
                maximum <= ProtocolLimits.MAX_ORE_LIBRARY_NETWORK_BYTES,
                () -> "Maximal atomic family-add request requires " + maximum + " bytes");
        assertTrue(
                maximum < ProtocolLimits.MAX_SERVERBOUND_CUSTOM_PAYLOAD_BYTES,
                () -> "Maximal atomic family-add request exceeds the serverbound ceiling: " + maximum);
    }

    private static int asciiText(int characters) {
        return varInt(characters) + characters;
    }

    private static int maximumUtf8Text(int characters) {
        int bytes = Math.multiplyExact(characters, 3);
        return varInt(bytes) + bytes;
    }

    private static int varInt(int value) {
        int bytes = 1;
        while ((value & ~0x7F) != 0) {
            value >>>= 7;
            bytes++;
        }
        return bytes;
    }

    private static String read(String fileName) throws Exception {
        return Files.readString(PAYLOADS.resolve(fileName));
    }
}
