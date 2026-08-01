package com.nightsta69.delvefold.network;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Pure wire-size proofs for maximal server-produced ore-import pages. */
class OreImportPageBudgetTest {
    private static final int MAX_SCANNED_BLOCKS = 1_000_000;
    private static final int NON_NEGATIVE_VAR_LONG_BYTES = 9;
    private static final int DOUBLE_BYTES = 8;

    @Test
    void maximalScanPageStaysBelowTwentyFourKiB() {
        int candidate = asciiText(ProtocolLimits.ID_LENGTH)
                + asciiText(ProtocolLimits.ID_LENGTH)
                + enumValue() + enumValue();
        int group = 3 * asciiText(ProtocolLimits.ID_LENGTH)
                + enumValue() + booleanValue()
                + varInt(ProtocolLimits.MAX_VARIANTS)
                + ProtocolLimits.MAX_VARIANTS * candidate;
        int header = asciiText(ProtocolLimits.MAX_IMPORT_TOKEN_LENGTH)
                + NON_NEGATIVE_VAR_LONG_BYTES
                + asciiText(ProtocolLimits.ID_LENGTH)
                + 3 * varInt(ProtocolLimits.MAX_IMPORT_GROUPS)
                + varInt(MAX_SCANNED_BLOCKS)
                + booleanValue()
                + varInt(ProtocolLimits.MAX_IMPORT_GROUPS_PER_PAGE);
        int maximalPage = header + ProtocolLimits.MAX_IMPORT_GROUPS_PER_PAGE * group;

        assertWithinBudget("scan", maximalPage);
    }

    @Test
    void maximalPreviewPageStaysBelowTwentyFourKiB() {
        int idList = varInt(ProtocolLimits.MAX_VARIANTS)
                + ProtocolLimits.MAX_VARIANTS * asciiText(ProtocolLimits.ID_LENGTH);
        int diffEntry = 2 * asciiText(ProtocolLimits.ID_LENGTH)
                + enumValue() + 2 * idList
                + maximumUtf8Text(ProtocolLimits.MAX_IMPORT_MESSAGE_LENGTH);
        int header = asciiText(ProtocolLimits.MAX_IMPORT_TOKEN_LENGTH)
                + NON_NEGATIVE_VAR_LONG_BYTES
                + asciiText(ProtocolLimits.ID_LENGTH)
                + 3 * varInt(ProtocolLimits.MAX_IMPORT_GROUPS)
                + booleanValue() + NON_NEGATIVE_VAR_LONG_BYTES
                + varInt(ProtocolLimits.MAX_IMPORT_DIFF_PER_PAGE);
        int workloads = varInt(ProtocolLimits.MAX_TERRAIN_MODES)
                + ProtocolLimits.MAX_TERRAIN_MODES * (enumValue() + 4 * DOUBLE_BYTES);
        int issue = enumValue()
                + asciiText(ProtocolLimits.ID_LENGTH)
                + maximumUtf8Text(ProtocolLimits.SHORT_TEXT_LENGTH)
                + maximumUtf8Text(ProtocolLimits.MAX_IMPORT_MESSAGE_LENGTH);
        int issues = varInt(ProtocolLimits.MAX_IMPORT_ISSUES)
                + ProtocolLimits.MAX_IMPORT_ISSUES * issue;
        int maximalPage = header
                + ProtocolLimits.MAX_IMPORT_DIFF_PER_PAGE * diffEntry
                + workloads + issues + booleanValue();

        assertWithinBudget("preview", maximalPage);
    }

    private static void assertWithinBudget(String pageType, int bytes) {
        assertTrue(bytes <= ProtocolLimits.MAX_IMPORT_NETWORK_BYTES,
                () -> "Maximal " + pageType + " page requires " + bytes
                        + " bytes, over the " + ProtocolLimits.MAX_IMPORT_NETWORK_BYTES + "-byte budget");
    }

    /** Tokens, resource locations, profile IDs, rule IDs, and validation codes are ASCII by grammar. */
    private static int asciiText(int characters) {
        return varInt(characters) + characters;
    }

    /** FriendlyByteBuf allows at most three UTF-8 bytes per permitted Java character. */
    private static int maximumUtf8Text(int characters) {
        int bytes = Math.multiplyExact(characters, 3);
        return varInt(bytes) + bytes;
    }

    private static int enumValue() {
        return 1;
    }

    private static int booleanValue() {
        return 1;
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
