package com.nightsta69.delvefold.network;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NetworkLocalizationSourceContractTest {
    @Test
    void serverResultFallbacksUseTheBoundedLocalizedEnvelope() throws IOException {
        String network =
                Files.readString(Path.of("src/main/java/com/nightsta69/delvefold/network/DelvefoldNetwork.java"));
        String doctor = Files.readString(
                Path.of("src/main/java/com/nightsta69/delvefold/diagnostics/DelvefoldDoctorService.java"));

        assertTrue(network.contains("AdminLocalizedMessage.encode(\"message.delvefold.network."));
        assertFalse(network.contains("\"The server rejected the request due to an internal error"));
        assertFalse(network.contains("\"The server could not create the Delvefold administration snapshot"));
        assertTrue(doctor.contains("AdminLocalizedMessage.encode(\"message.delvefold.doctor.preparing\")"));
    }
}
