package com.nightsta69.delvefold.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonParser;
import com.nightsta69.delvefold.api.DelvefoldApi;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.model.WorldSettingsDocument;
import com.nightsta69.delvefold.network.DelvefoldNetwork;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/** Locks the independently versioned integration, configuration, and wire contracts. */
class VersioningInvariantTest {
    @Test
    void apiSchemaAndNetworkVersionsRemainAtTheirOneXCompatibilityValues() {
        assertEquals(1, DelvefoldApi.API_VERSION);
        assertEquals(2, OreProfileDocument.CURRENT_SCHEMA_VERSION);
        assertEquals(2, WorldSettingsDocument.CURRENT_SCHEMA_VERSION);
        assertEquals("12", DelvefoldNetwork.PROTOCOL_VERSION);
    }

    @Test
    void buildMetadataAndJsonSchemasAgreeWithRuntimeConstants() throws Exception {
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("gradle.properties"))) {
            properties.load(reader);
        }
        assertEquals(Integer.toString(DelvefoldApi.API_VERSION), properties.getProperty("mod_api_version"));

        assertSchemaVersion("schemas/ores.schema.json", OreProfileDocument.CURRENT_SCHEMA_VERSION);
        assertSchemaVersion("schemas/settings.schema.json", WorldSettingsDocument.CURRENT_SCHEMA_VERSION);
    }

    private static void assertSchemaVersion(String file, int expected) throws Exception {
        var schema = JsonParser.parseString(Files.readString(Path.of(file))).getAsJsonObject();
        int declared = schema.getAsJsonObject("properties")
                .getAsJsonObject("schema_version")
                .get("const")
                .getAsInt();
        assertEquals(expected, declared, file + " must match the runtime schema constant");
    }
}
