package com.nightsta69.delvefold.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
                  "schema_version": 2,
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

    @Test
    void schemaOneFilesAreLeftUntouchedAndLoadedReadOnlyFromDefaults() throws Exception {
        ConfigPaths paths = new ConfigPaths(
                temporaryDirectory,
                temporaryDirectory.resolve("ores.json"),
                temporaryDirectory.resolve("settings.json"));
        FileConfigRepository repository = new FileConfigRepository(paths, RegistryLookup.SKIP);
        repository.loadOrCreate(null);
        String oldOres = Files.readString(paths.ores()).replace("\"schema_version\": 2", "\"schema_version\": 1");
        String oldSettings = Files.readString(paths.settings()).replace("\"schema_version\": 2", "\"schema_version\": 1");
        Files.writeString(paths.ores(), oldOres);
        Files.writeString(paths.settings(), oldSettings);

        ConfigLoadResult result = repository.loadOrCreate(null);

        assertTrue(result.usedFallback());
        assertTrue(result.issues().stream().anyMatch(issue -> "schema.unsupported".equals(issue.code())));
        assertEquals(oldOres, Files.readString(paths.ores()));
        assertEquals(oldSettings, Files.readString(paths.settings()));
    }

    @Test
    void mismatchedActiveProfileIsRejectedWithoutReplacingTheLastKnownGoodSnapshot() throws Exception {
        ConfigPaths paths = new ConfigPaths(
                temporaryDirectory,
                temporaryDirectory.resolve("ores.json"),
                temporaryDirectory.resolve("settings.json"));
        FileConfigRepository repository = new FileConfigRepository(paths, RegistryLookup.SKIP);
        ConfigLoadResult baseline = repository.loadOrCreate(null);

        String mismatched = Files.readString(paths.settings())
                .replace("\"active_profile_id\": \"vanilla_balanced\"",
                        "\"active_profile_id\": \"rich\"");
        Files.writeString(paths.settings(), mismatched);

        ConfigLoadResult rejected = repository.loadOrCreate(baseline.snapshot());
        assertTrue(rejected.usedFallback());
        assertEquals(baseline.snapshot(), rejected.snapshot());
        assertTrue(rejected.issues().stream()
                .anyMatch(issue -> "profile.active_mismatch".equals(issue.code())));
    }

    @Test
    void mismatchedProfilesCannotBeSaved() throws Exception {
        ConfigPaths paths = new ConfigPaths(
                temporaryDirectory,
                temporaryDirectory.resolve("ores.json"),
                temporaryDirectory.resolve("settings.json"));
        FileConfigRepository repository = new FileConfigRepository(paths, RegistryLookup.SKIP);
        ConfigLoadResult baseline = repository.loadOrCreate(null);

        assertThrows(IllegalArgumentException.class, () -> repository.save(
                baseline.snapshot().ores(), baseline.snapshot().settings().withActiveProfile("rich")));
    }

    @Test
    void schemaTwoSettingsWithoutIdentityReceiveNonDestructiveDefaults() throws Exception {
        ConfigPaths paths = new ConfigPaths(
                temporaryDirectory,
                temporaryDirectory.resolve("ores.json"),
                temporaryDirectory.resolve("settings.json"));
        FileConfigRepository repository = new FileConfigRepository(paths, RegistryLookup.SKIP);
        repository.loadOrCreate(null);
        String withIdentity = Files.readString(paths.settings());
        var oldRoot = com.google.gson.JsonParser.parseString(withIdentity).getAsJsonObject();
        oldRoot.remove("identity");
        String withoutIdentity = ConfigJson.GSON.toJson(oldRoot) + System.lineSeparator();
        Files.writeString(paths.settings(), withoutIdentity);

        ConfigLoadResult loaded = repository.loadOrCreate(null);

        assertTrue(!loaded.usedFallback());
        assertEquals("Delvefold Mining World", loaded.snapshot().settings().identity().displayName());
        assertEquals(withoutIdentity, Files.readString(paths.settings()));
    }
}
