package com.nightsta69.delvefold.network.codec;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.network.ProtocolLimits;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Wire-shape and byte-budget contracts for the NeoForge-only Unified Ores page codec. */
class OreLibraryStreamCodecTest {
    private static final Path CODEC =
            Path.of("src/main/java/com/nightsta69/delvefold/network/codec/OreLibraryStreamCodec.java");

    @Test
    void encoderAndDecoderTraverseEveryFieldInSymmetricOrder() throws Exception {
        String source = compact(Files.readString(CODEC));
        String write = method(source, "publicstaticvoidwrite(", "publicstaticOreLibraryViewread(");
        String read = method(source, "publicstaticOreLibraryViewread(", "privatestaticvoidwriteId(");

        assertInOrder(
                write,
                List.of(
                        "view.catalogToken()",
                        "view.expectedOreRevision()",
                        "view.page()",
                        "view.pageCount()",
                        "view.totalFamilies()",
                        "view.query()",
                        "view.showConfigured()",
                        "view.truncated()",
                        "view.families().size()",
                        "family.id()",
                        "family.material()",
                        "family.suggestedRuleId()",
                        "family.preferredBlockId()",
                        "family.providerCount()",
                        "family.candidateCount()",
                        "family.importableCandidateCount()",
                        "writeEvidence(family.evidence())",
                        "family.configured()",
                        "family.reviewRequired()",
                        "family.overflow()"));
        assertInOrder(
                read,
                List.of(
                        "StringcatalogToken=buffer.readUtf(",
                        "longrevision=buffer.readVarLong()",
                        "intpage=buffer.readVarInt()",
                        "intpageCount=buffer.readVarInt()",
                        "inttotal=buffer.readVarInt()",
                        "Stringquery=buffer.readUtf(",
                        "booleanshowConfigured=buffer.readBoolean()",
                        "booleantruncated=buffer.readBoolean()",
                        "intcount=buffer.readVarInt()",
                        "Stringid=readId(buffer)",
                        "Stringmaterial=readId(buffer)",
                        "StringsuggestedRuleId=readId(buffer)",
                        "StringpreferredBlock=readId(buffer)",
                        "intproviders=buffer.readVarInt()",
                        "intcandidates=buffer.readVarInt()",
                        "intimportable=buffer.readVarInt()",
                        "intevidenceOrdinal=buffer.readVarInt()",
                        "readEvidence(evidenceOrdinal)",
                        "buffer.readBoolean()",
                        "buffer.readBoolean()",
                        "buffer.readBoolean()"));
    }

    @Test
    void decoderRejectsInvalidCountsAndEvidenceBeforeUnsafeUse() throws Exception {
        String source = Files.readString(CODEC);

        int countRead = source.indexOf("int count = buffer.readVarInt()");
        int countValidation =
                source.indexOf("if (count < 0 || count > ProtocolLimits.MAX_ORE_LIBRARY_FAMILIES_PER_PAGE)");
        int allocation = source.indexOf("new ArrayList<>(count)");
        assertTrue(countRead >= 0 && countValidation > countRead && allocation > countValidation);

        for (int ordinal = 0; ordinal <= 2; ordinal++) {
            assertTrue(source.contains("case " + ordinal + " -> Evidence."));
        }
        assertTrue(
                source.contains("default -> throw new IllegalArgumentException(\"Invalid ore library evidence value:"));
    }

    @Test
    void codecEnforcesTheAggregateBudgetOnWriteAndIncrementalRead() throws Exception {
        String source = compact(Files.readString(CODEC));
        String write = method(source, "publicstaticvoidwrite(", "publicstaticOreLibraryViewread(");
        String read = method(source, "publicstaticOreLibraryViewread(", "privatestaticvoidwriteId(");

        assertTrue(write.contains("ensureBudget(buffer.writerIndex()-start);"));
        assertTrue(read.contains("ensureBudget(buffer.readerIndex()-start);"));
        assertTrue(source.contains("bytes<0||bytes>ProtocolLimits.MAX_ORE_LIBRARY_NETWORK_BYTES"));
    }

    @Test
    void mathematicallyMaximalLibraryPageFitsTheDeclaredBudget() {
        int header = maximumUtf8Text(ProtocolLimits.MAX_IMPORT_TOKEN_LENGTH)
                + 9
                + 3 * 5
                + maximumUtf8Text(ProtocolLimits.MAX_ORE_LIBRARY_QUERY_LENGTH)
                + 2
                + 5;
        int family = 4 * maximumUtf8Text(ProtocolLimits.ID_LENGTH) + 4 * 5 + 3;
        int maximum = header + ProtocolLimits.MAX_ORE_LIBRARY_FAMILIES_PER_PAGE * family;

        assertTrue(
                maximum <= ProtocolLimits.MAX_ORE_LIBRARY_NETWORK_BYTES,
                () -> "Maximal Unified Ores page requires " + maximum + " bytes");
    }

    private static void assertInOrder(String source, List<String> markers) {
        int cursor = -1;
        for (String marker : markers) {
            int next = source.indexOf(marker, cursor + 1);
            assertTrue(next > cursor, () -> "Missing or out-of-order codec marker: " + marker);
            cursor = next;
        }
    }

    private static String method(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start + startMarker.length());
        assertTrue(start >= 0 && end > start, () -> "Could not isolate codec method " + startMarker);
        return source.substring(start, end);
    }

    private static String compact(String source) {
        return source.replaceAll("\\s+", "");
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
}
