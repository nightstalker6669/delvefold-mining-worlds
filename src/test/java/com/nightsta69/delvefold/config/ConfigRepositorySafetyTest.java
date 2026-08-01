package com.nightsta69.delvefold.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.model.GeologyTheme;
import com.nightsta69.delvefold.config.model.GuideVisibility;
import com.nightsta69.delvefold.config.model.OreTarget;
import com.nightsta69.delvefold.config.model.RenewalSeedMode;
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

    @Test
    void schemaTwoSettingsWithoutGuideVisibilityReceiveNonDestructivePublicDefault() throws Exception {
        ConfigPaths paths = new ConfigPaths(
                temporaryDirectory,
                temporaryDirectory.resolve("ores.json"),
                temporaryDirectory.resolve("settings.json"));
        FileConfigRepository repository = new FileConfigRepository(paths, RegistryLookup.SKIP);
        repository.loadOrCreate(null);
        var root = com.google.gson.JsonParser.parseString(Files.readString(paths.settings())).getAsJsonObject();
        root.remove("guide_visibility");
        String legacySettings = ConfigJson.GSON.toJson(root) + System.lineSeparator();
        Files.writeString(paths.settings(), legacySettings);

        ConfigLoadResult loaded = repository.loadOrCreate(null);

        assertTrue(!loaded.usedFallback());
        assertEquals(GuideVisibility.PUBLIC, loaded.snapshot().settings().guideVisibility());
        assertEquals(legacySettings, Files.readString(paths.settings()));
    }

    @Test
    void schemaTwoSettingsWithoutSeedFieldsReceiveNonDestructiveStableDefaults() throws Exception {
        ConfigPaths paths = new ConfigPaths(
                temporaryDirectory,
                temporaryDirectory.resolve("ores.json"),
                temporaryDirectory.resolve("settings.json"));
        FileConfigRepository repository = new FileConfigRepository(paths, RegistryLookup.SKIP);
        repository.loadOrCreate(null);
        var root = com.google.gson.JsonParser.parseString(Files.readString(paths.settings())).getAsJsonObject();
        root.remove("generation_salt");
        root.getAsJsonObject("identity").getAsJsonObject("renewal").remove("seed_mode");
        String legacySettings = ConfigJson.GSON.toJson(root) + System.lineSeparator();
        Files.writeString(paths.settings(), legacySettings);

        ConfigLoadResult loaded = repository.loadOrCreate(null);

        assertTrue(!loaded.usedFallback());
        assertEquals(0L, loaded.snapshot().settings().generationSalt());
        assertEquals(RenewalSeedMode.STABLE,
                loaded.snapshot().settings().identity().renewal().seedMode());
        assertEquals(legacySettings, Files.readString(paths.settings()));
    }

    @Test
    void schemaTwoOneOneSettingsWithoutGeologyThemeLoadClassicWithoutByteRewrite() throws Exception {
        ConfigPaths paths = new ConfigPaths(
                temporaryDirectory,
                temporaryDirectory.resolve("ores.json"),
                temporaryDirectory.resolve("settings.json"));
        FileConfigRepository repository = new FileConfigRepository(paths, RegistryLookup.SKIP);
        repository.loadOrCreate(null);

        var root = com.google.gson.JsonParser.parseString(Files.readString(paths.settings())).getAsJsonObject();
        var identity = root.getAsJsonObject("identity");
        assertTrue(identity.remove("geology_theme") != null,
                "The current settings fixture must contain the sole post-1.1 identity field");
        byte[] oneOneSettings = (ConfigJson.GSON.toJson(root) + System.lineSeparator())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(paths.settings(), oneOneSettings);

        ConfigLoadResult loaded = new FileConfigRepository(paths, RegistryLookup.SKIP).loadOrCreate(null);

        assertTrue(!loaded.usedFallback());
        assertEquals(GeologyTheme.CLASSIC, loaded.snapshot().settings().identity().geologyTheme());
        assertArrayEquals(oneOneSettings, Files.readAllBytes(paths.settings()),
                "Loading a compatible 1.1 settings file must not rewrite it");
    }

    @Test
    void invalidSeedModeAndOverflowingGenerationSaltAreRejectedWithoutRewriting() throws Exception {
        ConfigPaths paths = new ConfigPaths(
                temporaryDirectory,
                temporaryDirectory.resolve("ores.json"),
                temporaryDirectory.resolve("settings.json"));
        FileConfigRepository repository = new FileConfigRepository(paths, RegistryLookup.SKIP);
        ConfigLoadResult baseline = repository.loadOrCreate(null);
        var root = com.google.gson.JsonParser.parseString(Files.readString(paths.settings())).getAsJsonObject();
        root.addProperty("generation_salt", new java.math.BigInteger("9223372036854775808"));
        root.getAsJsonObject("identity").getAsJsonObject("renewal")
                .addProperty("seed_mode", "random_every_restart");
        String invalidSettings = ConfigJson.GSON.toJson(root) + System.lineSeparator();
        Files.writeString(paths.settings(), invalidSettings);

        ConfigLoadResult rejected = repository.loadOrCreate(baseline.snapshot());

        assertTrue(rejected.usedFallback());
        assertEquals(baseline.snapshot(), rejected.snapshot());
        assertTrue(rejected.issues().stream().anyMatch(issue -> "json.invalid".equals(issue.code())));
        assertEquals(invalidSettings, Files.readString(paths.settings()));
    }

    @Test
    void overflowingSchemaCannotWrapBackToSchemaTwo() throws Exception {
        ConfigPaths paths = new ConfigPaths(
                temporaryDirectory,
                temporaryDirectory.resolve("ores.json"),
                temporaryDirectory.resolve("settings.json"));
        FileConfigRepository repository = new FileConfigRepository(paths, RegistryLookup.SKIP);
        ConfigLoadResult baseline = repository.loadOrCreate(null);
        String invalidSettings = Files.readString(paths.settings())
                .replace("\"schema_version\": 2", "\"schema_version\": 4294967298");
        Files.writeString(paths.settings(), invalidSettings);

        ConfigLoadResult rejected = repository.loadOrCreate(baseline.snapshot());

        assertTrue(rejected.usedFallback());
        assertEquals(baseline.snapshot(), rejected.snapshot());
        assertEquals(invalidSettings, Files.readString(paths.settings()));
    }

    @Test
    void rotatingGenerationSaltSurvivesRepositoryRestart() throws Exception {
        ConfigPaths paths = new ConfigPaths(
                temporaryDirectory,
                temporaryDirectory.resolve("ores.json"),
                temporaryDirectory.resolve("settings.json"));
        FileConfigRepository repository = new FileConfigRepository(paths, RegistryLookup.SKIP);
        ConfigLoadResult baseline = repository.loadOrCreate(null);
        var rotatingIdentity = baseline.snapshot().settings().identity().withRenewal(
                baseline.snapshot().settings().identity().renewal()
                        .withSeedMode(RenewalSeedMode.ROTATE_ON_RECREATE));
        var rotating = baseline.snapshot().settings().initialize(
                com.nightsta69.delvefold.config.model.TerrainMode.FLAT,
                com.nightsta69.delvefold.config.model.OrePreset.VANILLA_BALANCED,
                com.nightsta69.delvefold.config.model.GameplayPreset.SAFE,
                rotatingIdentity);
        repository.save(baseline.snapshot().ores(), rotating);

        ConfigLoadResult restarted = new FileConfigRepository(paths, RegistryLookup.SKIP).loadOrCreate(null);

        assertTrue(!restarted.usedFallback());
        assertTrue(restarted.snapshot().settings().generationSalt() > 0L);
        assertEquals(rotating.generationSalt(), restarted.snapshot().settings().generationSalt());
        assertEquals(RenewalSeedMode.ROTATE_ON_RECREATE,
                restarted.snapshot().settings().identity().renewal().seedMode());
    }

    @Test
    void schemaTwoOresWithoutWeightsReceiveNonDestructiveCompatibilityDefaults() throws Exception {
        ConfigPaths paths = new ConfigPaths(
                temporaryDirectory,
                temporaryDirectory.resolve("ores.json"),
                temporaryDirectory.resolve("settings.json"));
        FileConfigRepository repository = new FileConfigRepository(paths, RegistryLookup.SKIP);
        repository.loadOrCreate(null);
        var root = com.google.gson.JsonParser.parseString(Files.readString(paths.ores())).getAsJsonObject();
        for (var rule : root.getAsJsonArray("rules")) {
            for (var target : rule.getAsJsonObject().getAsJsonArray("targets")) {
                target.getAsJsonObject().remove("weight");
            }
        }
        String legacyOres = ConfigJson.GSON.toJson(root) + System.lineSeparator();
        Files.writeString(paths.ores(), legacyOres);

        ConfigLoadResult loaded = repository.loadOrCreate(null);

        assertTrue(!loaded.usedFallback());
        assertTrue(loaded.snapshot().ores().rules().stream()
                .flatMap(rule -> rule.targets().stream())
                .allMatch(target -> target.weight() == OreTarget.DEFAULT_WEIGHT));
        assertEquals(legacyOres, Files.readString(paths.ores()));
    }

    @Test
    void hugeOreWeightIsRejectedBeforeGsonCanNarrowIt() throws Exception {
        ConfigPaths paths = new ConfigPaths(
                temporaryDirectory,
                temporaryDirectory.resolve("ores.json"),
                temporaryDirectory.resolve("settings.json"));
        FileConfigRepository repository = new FileConfigRepository(paths, RegistryLookup.SKIP);
        ConfigLoadResult baseline = repository.loadOrCreate(null);
        var root = com.google.gson.JsonParser.parseString(Files.readString(paths.ores())).getAsJsonObject();
        var firstTarget = root.getAsJsonArray("rules").get(0).getAsJsonObject()
                .getAsJsonArray("targets").get(0).getAsJsonObject();
        firstTarget.addProperty("weight", new java.math.BigInteger("4294967297"));
        String invalidOres = ConfigJson.GSON.toJson(root) + System.lineSeparator();
        Files.writeString(paths.ores(), invalidOres);

        ConfigLoadResult rejected = repository.loadOrCreate(baseline.snapshot());

        assertTrue(rejected.usedFallback());
        assertEquals(baseline.snapshot(), rejected.snapshot());
        assertTrue(rejected.issues().stream().anyMatch(issue -> "json.invalid".equals(issue.code())));
        assertEquals(invalidOres, Files.readString(paths.ores()));
    }

    @Test
    void explicitGuideVisibilityIsLoadedAndInvalidValuesAreRejected() throws Exception {
        ConfigPaths paths = new ConfigPaths(
                temporaryDirectory,
                temporaryDirectory.resolve("ores.json"),
                temporaryDirectory.resolve("settings.json"));
        FileConfigRepository repository = new FileConfigRepository(paths, RegistryLookup.SKIP);
        ConfigLoadResult baseline = repository.loadOrCreate(null);

        String operators = Files.readString(paths.settings())
                .replace("\"guide_visibility\": \"public\"", "\"guide_visibility\": \"operators\"");
        Files.writeString(paths.settings(), operators);
        ConfigLoadResult accepted = repository.loadOrCreate(baseline.snapshot());
        assertTrue(!accepted.usedFallback());
        assertEquals(GuideVisibility.OPERATORS, accepted.snapshot().settings().guideVisibility());

        Files.writeString(paths.settings(), operators.replace(
                "\"guide_visibility\": \"operators\"", "\"guide_visibility\": \"everyone\""));
        ConfigLoadResult rejected = repository.loadOrCreate(accepted.snapshot());
        assertTrue(rejected.usedFallback());
        assertEquals(accepted.snapshot(), rejected.snapshot());
        assertTrue(rejected.issues().stream()
                .anyMatch(issue -> issue.message().contains("guide_visibility")));
    }
}
