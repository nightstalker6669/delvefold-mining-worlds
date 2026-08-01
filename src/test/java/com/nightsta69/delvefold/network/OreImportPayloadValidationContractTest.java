package com.nightsta69.delvefold.network;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source contracts for payloads whose NeoForge supertypes are not on the pure-test classpath. */
class OreImportPayloadValidationContractTest {
    private static final Path PAYLOADS = Path.of("src/main/java/com/nightsta69/delvefold/network/payload");

    @Test
    void forecastAndScanRequestsBoundPagesAndRevisions() throws Exception {
        String forecast = read("ForecastRequestPayload.java");
        String scan = read("OreImportScanRequestPayload.java");
        String scanPage = read("OreImportScanPageRequestPayload.java");
        String previewPage = read("OreImportPreviewPageRequestPayload.java");

        assertTrue(forecast.contains("profileId == null ? \"\" : profileId.trim()"));
        assertTrue(forecast.contains("profileId.length() > ProtocolLimits.ID_LENGTH"));
        assertTrue(forecast.contains("page < 0 || page > ProtocolLimits.MAX_ORE_RULES"));
        assertTrue(scan.contains("expectedOreRevision < 0L"));

        assertTrue(scanPage.contains("value == null ? \"\" : value.trim()"));
        assertTrue(scanPage.contains("safe.length() > ProtocolLimits.MAX_IMPORT_TOKEN_LENGTH"));
        assertTrue(scanPage.contains("page < 0 || page > ProtocolLimits.MAX_IMPORT_GROUPS"));
        assertTrue(previewPage.contains("commitToken == null ? \"\" : commitToken.trim()"));
        assertTrue(previewPage.contains("commitToken.length() > ProtocolLimits.MAX_IMPORT_TOKEN_LENGTH"));
        assertTrue(previewPage.contains("page < 0 || page > ProtocolLimits.MAX_IMPORT_GROUPS"));
    }

    @Test
    void previewSelectionNormalizesBeforeDuplicateDetectionAndBoundsDecodeAllocation() throws Exception {
        String preview = read("OreImportPreviewRequestPayload.java");
        int constructorStart = preview.indexOf("public OreImportPreviewRequestPayload(");
        assertTrue(constructorStart >= 0, "The payload must retain its public canonical constructor");
        String constructor = preview.substring(constructorStart);

        int nullHandling = constructor.indexOf("id == null");
        int idTrimming = constructor.indexOf("id.trim()");
        int duplicateCheck = constructor.indexOf(".distinct().count()");
        assertTrue(
                nullHandling >= 0 && idTrimming > nullHandling && duplicateCheck > idTrimming,
                "Selected IDs must be null-checked, trimmed, and only then checked for duplicates");
        assertTrue(constructor.contains("ProtocolLimits.MAX_IMPORT_SELECTED_GROUPS"));
        assertTrue(constructor.contains("ProtocolLimits.ID_LENGTH"));
        assertTrue(
                constructor.contains("List.copyOf") || constructor.contains(".toList()"),
                "The constructor must retain an immutable defensive copy of selected IDs");

        int wireCountCheck = preview.indexOf("if (count < 1 || count >");
        int wireAllocation = preview.indexOf("new java.util.ArrayList<>(count)");
        assertTrue(
                wireCountCheck >= 0 && wireAllocation > wireCountCheck,
                "The wire count must be validated before allocating the selected-ID list");
    }

    @Test
    void createRequestBoundsAndNormalizesBothMutableInputs() throws Exception {
        String create = read("OreImportCreatePayload.java");

        assertTrue(create.contains("commitToken == null ? \"\" : commitToken.trim()"));
        assertTrue(create.contains("targetProfileId == null ? \"\" : targetProfileId.trim()"));
        assertTrue(create.contains("commitToken.length() > ProtocolLimits.MAX_IMPORT_TOKEN_LENGTH"));
        assertTrue(create.contains("targetProfileId.length() > ProtocolLimits.ID_LENGTH"));
    }

    private static String read(String fileName) throws Exception {
        return Files.readString(PAYLOADS.resolve(fileName));
    }
}
