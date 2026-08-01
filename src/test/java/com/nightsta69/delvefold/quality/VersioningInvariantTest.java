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
import java.util.regex.Pattern;
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

    @Test
    void currentReleaseDocumentationMatchesBuildVersion() throws Exception {
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("gradle.properties"))) {
            properties.load(reader);
        }
        String modVersion = properties.getProperty("mod_version");
        String readme = Files.readString(Path.of("README.md"));
        String changelog = Files.readString(Path.of("CHANGELOG.md"));

        assertEquals(
                "delvefold-1.21.1-" + modVersion + ".jar",
                releaseArtifactNamedIn(readme),
                "README release artifact must match mod_version");
        assertEquals(modVersion, newestChangelogVersion(changelog), "newest changelog entry must match mod_version");
    }

    private static void assertSchemaVersion(String file, int expected) throws Exception {
        var schema = JsonParser.parseString(Files.readString(Path.of(file))).getAsJsonObject();
        int declared = schema.getAsJsonObject("properties")
                .getAsJsonObject("schema_version")
                .get("const")
                .getAsInt();
        assertEquals(expected, declared, file + " must match the runtime schema constant");
    }

    private static String releaseArtifactNamedIn(String readme) {
        var matcher = Pattern.compile("build/libs/(delvefold-1\\.21\\.1-[0-9]+\\.[0-9]+\\.[0-9]+\\.jar)")
                .matcher(readme);
        if (!matcher.find()) {
            throw new AssertionError("README does not name a versioned release artifact");
        }
        return matcher.group(1);
    }

    private static String newestChangelogVersion(String changelog) {
        var matcher =
                Pattern.compile("(?m)^## ([0-9]+\\.[0-9]+\\.[0-9]+)(?: |$)").matcher(changelog);
        if (!matcher.find()) {
            throw new AssertionError("CHANGELOG does not contain a semantic-version release heading");
        }
        return matcher.group(1);
    }
}
