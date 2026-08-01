package com.nightsta69.delvefold.network;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Source-contract checks for NeoForge-only forecast and ore-import network wiring. */
class ForecastImportNetworkContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/nightsta69/delvefold");

    @Test
    void currentProtocolRegistersEveryForecastAndImportPayloadInTheCorrectDirection() throws Exception {
        String network = compact(read("network/DelvefoldNetwork.java"));

        assertTrue(network.contains("PROTOCOL_VERSION=\"11\""));
        for (String payload : List.of(
                "ForecastRequestPayload",
                "OreImportScanRequestPayload",
                "OreImportScanPageRequestPayload",
                "OreImportPreviewRequestPayload",
                "OreImportPreviewPageRequestPayload",
                "OreImportCreatePayload")) {
            assertTrue(network.contains("registrar.playToServer(" + payload + ".TYPE,"),
                    payload + " must be registered as a client-to-server request");
        }
        for (String payload : List.of(
                "OpenForecastPayload",
                "OpenOreImportScanPayload",
                "OpenOreImportPreviewPayload")) {
            assertTrue(network.contains("registrar.playToClient(" + payload + ".TYPE,"),
                    payload + " must be registered as a server-to-client view");
        }
    }

    @Test
    void everyNewServerEndpointRequiresConfigurePermission() throws Exception {
        String network = read("network/DelvefoldNetwork.java");

        for (String handler : List.of(
                "handleForecastRequest",
                "handleImportScan",
                "handleImportScanPage",
                "handleImportPreview",
                "handleImportPreviewPage",
                "handleImportCreate")) {
            String body = privateMethod(network, handler);
            assertTrue(body.contains("authorizedPlayer(context, AdminAccess.CONFIGURE_PERMISSION)"),
                    handler + " must reject unauthorized network callers before doing work");
        }

        String service = read("admin/OreImportAdminService.java");
        assertTrue(occurrences(service, "requireConfigure(player);") >= 5,
                "The import service must retain defense-in-depth permission checks");
        assertTrue(service.contains("!AdminAccess.canConfigure(player)"),
                "The import service permission check must fail closed");
    }

    @Test
    void clientBootstrapInstallsAllThreeNewViewHandlers() throws Exception {
        String bootstrap = read("client/DelvefoldClientEvents.java");
        String handler = read("client/DelvefoldClientPayloadHandler.java");

        for (String method : List.of("openForecast", "openOreImportScan", "openOreImportPreview")) {
            assertTrue(bootstrap.contains("DelvefoldClientPayloadHandler::" + method),
                    method + " must be installed during client setup");
            assertTrue(handler.contains("public static void " + method + "("),
                    method + " must have a client payload handler");
        }
    }

    @Test
    void codecsBoundCollectionsStringsMetricsAndTotalBytes() throws Exception {
        String forecastCodec = read("network/codec/OreForecastStreamCodecs.java");
        String importCodec = read("network/codec/OreImportStreamCodecs.java");
        String previewPayload = read("network/payload/OreImportPreviewRequestPayload.java");

        assertTrue(forecastCodec.contains("readCount(buffer, TerrainMode.values().length"));
        assertTrue(forecastCodec.contains("readCount(buffer, MAX_HEIGHT_SAMPLES"));
        assertTrue(forecastCodec.contains("readCount(buffer, OreProfileForecast.MAX_RULES_PER_PAGE"));
        assertTrue(forecastCodec.contains("Double.isFinite(value)"));
        assertTrue(forecastCodec.contains("bytes > MAX_NETWORK_BYTES"));

        assertTrue(importCodec.contains(
                "readCount(buffer, ProtocolLimits.MAX_IMPORT_GROUPS_PER_PAGE"));
        assertTrue(importCodec.contains("readCount(buffer, ProtocolLimits.MAX_VARIANTS"));
        assertTrue(importCodec.contains("readCount(buffer, ProtocolLimits.MAX_IMPORT_DIFF_PER_PAGE"));
        assertTrue(importCodec.contains("readCount(buffer, ProtocolLimits.MAX_IMPORT_ISSUES"));
        assertTrue(importCodec.contains("Double.isFinite(value)"));
        assertTrue(importCodec.contains("bytes > ProtocolLimits.MAX_IMPORT_NETWORK_BYTES"));

        int countValidation = previewPayload.indexOf("if (count < 1 || count >");
        int allocation = previewPayload.indexOf("new java.util.ArrayList<>(count)");
        assertTrue(countValidation >= 0 && allocation > countValidation,
                "The selected-group count must be checked before allocating its decode list");
    }

    @Test
    void importRegistrySnapshotIsCachedAndInvalidatedWithSessions() throws Exception {
        String registry = read("config/importer/MinecraftOreImportRegistry.java");
        String service = read("admin/OreImportAdminService.java");
        String lifecycle = read("server/DelvefoldServerLifecycle.java");
        String reload = read("config/EcosystemProfileReloadListener.java");

        assertTrue(registry.contains("private static volatile CachedSnapshot cachedSnapshot"));
        assertTrue(registry.contains("OreImportFingerprints.registry(registry)"),
                "The expensive registry fingerprint must be captured with the cached snapshot");
        assertTrue(service.contains("MinecraftOreImportRegistry.cachedSnapshot()"));
        assertTrue(!service.contains("new MinecraftOreImportRegistry()"),
                "Import pages must not rebuild the block/tag registry on every request");

        assertTrue(occurrences(lifecycle, "MinecraftOreImportRegistry.invalidateCache();") >= 2,
                "Server start and stop must invalidate the registry snapshot");
        assertTrue(reload.contains("MinecraftOreImportRegistry.invalidateCache();"));
        assertTrue(reload.contains("OreImportSessionService.get().invalidateAll();"),
                "A datapack/tag reload must invalidate every bound import session");
    }

    private static String read(String relative) throws Exception {
        return Files.readString(MAIN.resolve(relative));
    }

    private static String compact(String source) {
        return source.replaceAll("\\s+", "");
    }

    private static String privateMethod(String source, String name) {
        String marker = "private static void " + name + "(";
        int start = source.indexOf(marker);
        if (start < 0) {
            return "";
        }
        int end = source.indexOf("\n    private static ", start + marker.length());
        return source.substring(start, end < 0 ? source.length() : end);
    }

    private static int occurrences(String source, String value) {
        int count = 0;
        for (int index = source.indexOf(value); index >= 0; index = source.indexOf(value, index + value.length())) {
            count++;
        }
        return count;
    }
}
