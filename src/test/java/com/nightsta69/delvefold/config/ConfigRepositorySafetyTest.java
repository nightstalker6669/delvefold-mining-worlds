package com.nightsta69.delvefold.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.validation.RegistryLookup;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigRepositorySafetyTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void malformedNestedJsonKeepsTheLastKnownGoodSnapshot() throws Exception {
        ConfigPaths paths = new ConfigPaths(
                temporaryDirectory,
                temporaryDirectory.resolve("ores.json"),
                temporaryDirectory.resolve("settings.json"));
        FileConfigRepository repository = new FileConfigRepository(paths, RegistryLookup.SKIP);
        ConfigLoadResult baseline = repository.loadOrCreate(null);

        Files.writeString(paths.ores(), """
                {
                  "schema_version": 1,
                  "revision": 2,
                  "profile": "broken",
                  "rules": [null]
                }
                """);

        ConfigLoadResult rejected = repository.loadOrCreate(baseline.snapshot());
        assertTrue(rejected.usedFallback());
        assertEquals(baseline.snapshot(), rejected.snapshot());
        assertTrue(rejected.issues().stream().anyMatch(issue -> "json.invalid".equals(issue.code())));
    }

    @Test
    void unknownFieldsAreRejectedInsteadOfSilentlyDefaulted() throws Exception {
        ConfigPaths paths = new ConfigPaths(
                temporaryDirectory,
                temporaryDirectory.resolve("ores.json"),
                temporaryDirectory.resolve("settings.json"));
        FileConfigRepository repository = new FileConfigRepository(paths, RegistryLookup.SKIP);
        ConfigLoadResult baseline = repository.loadOrCreate(null);

        String misspelled = Files.readString(paths.settings())
                .replace("\"cooldown_seconds\"", "\"cooldown_secondz\"");
        Files.writeString(paths.settings(), misspelled);

        ConfigLoadResult rejected = repository.loadOrCreate(baseline.snapshot());
        assertTrue(rejected.usedFallback());
        assertEquals(baseline.snapshot(), rejected.snapshot());
        assertTrue(rejected.issues().stream().anyMatch(issue -> issue.message().contains("Unknown field")));
    }

    @Test
    void quotedScalarsAreRejectedInsteadOfBeingCoerced() throws Exception {
        ConfigPaths paths = new ConfigPaths(
                temporaryDirectory,
                temporaryDirectory.resolve("ores.json"),
                temporaryDirectory.resolve("settings.json"));
        FileConfigRepository repository = new FileConfigRepository(paths, RegistryLookup.SKIP);
        ConfigLoadResult baseline = repository.loadOrCreate(null);

        String coerced = Files.readString(paths.settings())
                .replace("\"enabled\": true", "\"enabled\": \"true\"");
        Files.writeString(paths.settings(), coerced);

        ConfigLoadResult rejected = repository.loadOrCreate(baseline.snapshot());
        assertTrue(rejected.usedFallback());
        assertTrue(rejected.issues().stream().anyMatch(issue -> issue.message().contains("JSON boolean")));
    }
}
