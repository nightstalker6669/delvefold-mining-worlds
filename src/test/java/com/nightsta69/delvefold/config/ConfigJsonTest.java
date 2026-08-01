package com.nightsta69.delvefold.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.model.GameplayPreset;
import com.nightsta69.delvefold.config.model.BackupRetentionSettings;
import com.nightsta69.delvefold.config.model.GuideVisibility;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.OreBandPlacement;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.model.OreTarget;
import com.nightsta69.delvefold.config.model.ProvinceSettings;
import com.nightsta69.delvefold.config.model.PortalHubSettings;
import com.nightsta69.delvefold.config.model.PortalRoutingMode;
import com.nightsta69.delvefold.config.model.RenewalSeedMode;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.TerrainVariant;
import com.nightsta69.delvefold.config.model.WorldSettingsDocument;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigJsonTest {
    @Test
    void oreDocumentRoundTripsThroughCanonicalJson() {
        OreProfileDocument original = OrePresets.balanced();
        String json = ConfigJson.GSON.toJson(original);
        assertTrue(json.contains("\"schema_version\""));
        assertTrue(json.contains("\"attempts_per_chunk\""));
        OreProfileDocument decoded = ConfigJson.GSON.fromJson(json, OreProfileDocument.class);
        assertEquals(original, decoded);
    }

    @Test
    void initializationIsExplicitAndEpochChangesOnlyWithWorldLifecycle() {
        WorldSettingsDocument uninitialized = WorldSettingsDocument.uninitialized()
                .withGuideVisibility(GuideVisibility.OPERATORS);
        assertFalse(uninitialized.initialized());
        assertEquals(0, uninitialized.generationEpoch());
        assertEquals(GuideVisibility.OPERATORS, uninitialized.guideVisibility());

        WorldSettingsDocument initialized = uninitialized.initialize(
                TerrainMode.CAVERN, OrePreset.RICH, GameplayPreset.HOSTILE);
        assertTrue(initialized.initialized());
        assertEquals(1, initialized.generationEpoch());
        assertEquals(0L, initialized.generationSalt());
        assertEquals(TerrainMode.CAVERN, initialized.terrainMode());
        assertEquals(GuideVisibility.OPERATORS, initialized.guideVisibility());

        WorldSettingsDocument recreated = initialized.recreate(
                TerrainMode.WILD, TerrainVariant.EXPANSIVE,
                OrePreset.VANILLA_BALANCED, GameplayPreset.SAFE, "operation-1");
        assertEquals(2, recreated.generationEpoch());
        assertEquals(0L, recreated.generationSalt());
        assertEquals("operation-1", recreated.lastWorldOperationId());
        assertEquals(TerrainMode.WILD, recreated.terrainMode());
        assertEquals(TerrainVariant.EXPANSIVE, recreated.identity().terrainVariant());
        assertEquals(GuideVisibility.OPERATORS, recreated.guideVisibility());

        WorldSettingsDocument deleted = recreated.markDeleted("operation-2");
        assertFalse(deleted.initialized());
        assertEquals(3, deleted.generationEpoch());
        assertEquals(0L, deleted.generationSalt());
        assertEquals("operation-2", deleted.lastWorldOperationId());
        assertEquals(GuideVisibility.OPERATORS, deleted.guideVisibility());
    }

    @Test
    void rotatingGenerationSaltsAreDeterministicPersistedAndLifecycleBound() {
        var rotatingIdentity = WorldSettingsDocument.uninitialized().identity().withRenewal(
                WorldSettingsDocument.uninitialized().identity().renewal()
                        .withSeedMode(RenewalSeedMode.ROTATE_ON_RECREATE));
        WorldSettingsDocument base = WorldSettingsDocument.uninitialized();

        WorldSettingsDocument first = base.initialize(
                TerrainMode.FLAT, OrePreset.VANILLA_BALANCED, GameplayPreset.SAFE, rotatingIdentity);
        WorldSettingsDocument repeated = base.initialize(
                TerrainMode.FLAT, OrePreset.VANILLA_BALANCED, GameplayPreset.SAFE, rotatingIdentity);
        assertTrue(first.generationSalt() > 0L);
        assertEquals(first.generationSalt(), repeated.generationSalt());

        WorldSettingsDocument restored = ConfigJson.GSON.fromJson(
                ConfigJson.GSON.toJson(first), WorldSettingsDocument.class);
        assertEquals(first, restored);

        WorldSettingsDocument second = first.recreate(
                TerrainMode.FLAT, TerrainVariant.CLASSIC, null, null, "operation-rotate");
        assertTrue(second.generationSalt() > 0L);
        assertTrue(second.generationSalt() != first.generationSalt());

        WorldSettingsDocument deleted = second.markDeleted("operation-delete");
        assertEquals(0L, deleted.generationSalt());
        WorldSettingsDocument reinitialized = deleted.initialize(
                TerrainMode.FLAT, OrePreset.VANILLA_BALANCED, GameplayPreset.SAFE);
        assertTrue(reinitialized.generationSalt() > 0L);
        assertTrue(reinitialized.generationSalt() != second.generationSalt());
    }

    @Test
    void liveSeedModeChangesOnlyAffectTheNextGeneration() {
        WorldSettingsDocument stable = WorldSettingsDocument.uninitialized().initialize(
                TerrainMode.WILD, OrePreset.EMPTY, GameplayPreset.SAFE);
        var rotating = stable.identity().withRenewal(
                stable.identity().renewal().withSeedMode(RenewalSeedMode.ROTATE_ON_RECREATE));

        WorldSettingsDocument optedIn = stable.withIdentity(rotating);
        assertEquals(0L, optedIn.generationSalt());
        WorldSettingsDocument rotated = optedIn.recreate(
                TerrainMode.WILD, TerrainVariant.CLASSIC, null, null, "operation-opt-in");
        assertTrue(rotated.generationSalt() > 0L);

        var stableAgain = rotated.identity().withRenewal(
                rotated.identity().renewal().withSeedMode(RenewalSeedMode.STABLE));
        WorldSettingsDocument optedOut = rotated.withIdentity(stableAgain);
        assertEquals(rotated.generationSalt(), optedOut.generationSalt());
        assertEquals(0L, optedOut.recreate(
                TerrainMode.WILD, TerrainVariant.CLASSIC, null, null, "operation-opt-out").generationSalt());
    }

    @Test
    void renewalSeedModeUsesStableLowercaseSchemaValuesAndLegacyDefault() {
        var legacy = new com.nightsta69.delvefold.config.model.RenewalSettings(false, 30, 30, 0L);
        assertEquals(RenewalSeedMode.STABLE, legacy.seedMode());

        var rotating = legacy.withSeedMode(RenewalSeedMode.ROTATE_ON_RECREATE);
        String json = ConfigJson.GSON.toJson(rotating);
        assertTrue(json.contains("\"seed_mode\": \"rotate_on_recreate\""));
        assertEquals(rotating,
                ConfigJson.GSON.fromJson(json, com.nightsta69.delvefold.config.model.RenewalSettings.class));
    }

    @Test
    void strictSettingsParsingRejectsInvalidSeedModeAndGenerationSaltOverflow() {
        String valid = ConfigJson.GSON.toJson(WorldSettingsDocument.uninitialized());
        String invalidMode = valid.replace(
                "\"seed_mode\": \"stable\"", "\"seed_mode\": \"random_every_restart\"");
        String overflowingSalt = valid.replace(
                "\"generation_salt\": 0", "\"generation_salt\": 9223372036854775808");

        assertThrows(JsonParseException.class,
                () -> StrictConfigStructure.parseAndValidate(invalidMode, WorldSettingsDocument.class));
        assertThrows(JsonParseException.class,
                () -> StrictConfigStructure.parseAndValidate(overflowingSalt, WorldSettingsDocument.class));
    }

    @Test
    void strictSettingsParsingRejectsIntegralOverflowBeforeGsonCanNarrowIt() {
        String valid = ConfigJson.GSON.toJson(WorldSettingsDocument.uninitialized());
        List<String> invalid = List.of(
                valid.replace("\"schema_version\": 2", "\"schema_version\": 4294967298"),
                valid.replace("\"revision\": 0", "\"revision\": 18446744073709551616"),
                valid.replace("\"generation_epoch\": 0", "\"generation_epoch\": 18446744073709551616"),
                valid.replace("\"next_renewal_at_epoch_millis\": 0",
                        "\"next_renewal_at_epoch_millis\": 18446744073709551616"));

        for (String json : invalid) {
            assertThrows(JsonParseException.class,
                    () -> StrictConfigStructure.parseAndValidate(json, WorldSettingsDocument.class));
        }
    }

    @Test
    void guideVisibilityUsesLowercaseSchemaValues() {
        WorldSettingsDocument settings = WorldSettingsDocument.uninitialized()
                .withGuideVisibility(GuideVisibility.DISABLED);

        String json = ConfigJson.GSON.toJson(settings);
        assertTrue(json.contains("\"guide_visibility\": \"disabled\""));
        assertEquals(GuideVisibility.DISABLED,
                ConfigJson.GSON.fromJson(json, WorldSettingsDocument.class).guideVisibility());
    }

    @Test
    void backupRetentionIsOptionalDisabledAndStrictlyBounded() {
        WorldSettingsDocument defaults = WorldSettingsDocument.uninitialized();
        assertEquals(BackupRetentionSettings.defaults(), defaults.backupRetention());

        WorldSettingsDocument configured = defaults.withBackupRetention(
                new BackupRetentionSettings(true, 12, 90L, 8L * 1024L * 1024L * 1024L));
        String json = ConfigJson.GSON.toJson(configured);
        assertTrue(json.contains("\"backup_retention\""));
        assertEquals(configured, ConfigJson.GSON.fromJson(json, WorldSettingsDocument.class));
        StrictConfigStructure.parseAndValidate(json, WorldSettingsDocument.class);

        assertThrows(JsonParseException.class, () -> StrictConfigStructure.parseAndValidate(
                json.replace("\"max_count\": 12", "\"max_count\": 4294967297"),
                WorldSettingsDocument.class));
        assertThrows(JsonParseException.class, () -> StrictConfigStructure.parseAndValidate(
                json.replace("\"max_total_bytes\": 8589934592", "\"max_total_bytes\": -1"),
                WorldSettingsDocument.class));
    }

    @Test
    void centralHubPortalSettingsRoundTripAndRejectUnsafeBounds() {
        var portal = new com.nightsta69.delvefold.config.model.PortalSettings(
                true, true, 5, 1.0D, PortalRoutingMode.CENTRAL_HUB,
                new PortalHubSettings(128, -256, 24));
        WorldSettingsDocument configured = WorldSettingsDocument.uninitialized().withPortal(portal);
        String json = ConfigJson.GSON.toJson(configured);

        assertTrue(json.contains("\"routing_mode\": \"central_hub\""));
        assertEquals(configured, ConfigJson.GSON.fromJson(json, WorldSettingsDocument.class));
        StrictConfigStructure.parseAndValidate(json, WorldSettingsDocument.class);
        assertThrows(JsonParseException.class, () -> StrictConfigStructure.parseAndValidate(
                json.replace("\"protection_radius\": 24", "\"protection_radius\": 7"),
                WorldSettingsDocument.class));
        assertThrows(JsonParseException.class, () -> StrictConfigStructure.parseAndValidate(
                json.replace("\"routing_mode\": \"central_hub\"", "\"routing_mode\": \"random\""),
                WorldSettingsDocument.class));
    }

    @Test
    void schemaTwoExactTargetsRemainBackwardCompatible() {
        String json = """
                {"schema_version":2,"revision":0,"profile":"legacy","rules":[{
                  "id":"tin","enabled":true,"required":false,"terrain_modes":["wild"],
                  "targets":[{"block":"example:tin_ore","state":{},"replace_tag":"minecraft:stone_ore_replaceables"}],
                  "biomes":{"include":[],"exclude":[]},
                  "bands":[{"id":"main","vein_size":4,"attempts_per_chunk":2.0,"distribution":"uniform",
                    "min_y":-32,"max_y":64,"peak_y":null,"plateau_min_y":null,"plateau_max_y":null,
                    "discard_on_air_exposure":0.0}]
                }]}
                """;
        OreProfileDocument decoded = ConfigJson.GSON.fromJson(json, OreProfileDocument.class);
        assertEquals("example:tin_ore", decoded.rules().getFirst().targets().getFirst().block());
        assertEquals("", decoded.rules().getFirst().targets().getFirst().blockTag());
        assertEquals(OreTarget.DEFAULT_WEIGHT, decoded.rules().getFirst().targets().getFirst().weight());
        assertEquals(OreBandPlacement.VEIN, decoded.rules().getFirst().bands().getFirst().placement());
        assertEquals(null, decoded.rules().getFirst().bands().getFirst().province());
    }

    @Test
    void provinceBandsRemainAdditiveSchemaTwoDataAndRoundTripCanonically() {
        var original = OrePresets.balanced().rules().getFirst();
        var province = com.nightsta69.delvefold.config.model.SpawnBand.province(
                "regional", com.nightsta69.delvefold.config.model.HeightDistribution.TRIANGLE,
                -48, 64, -16, null, null, 0.25D,
                new ProvinceSettings(512, 192, 48, 0.08D, 1024));
        var rule = original.withBands(List.of(province));
        var document = new OreProfileDocument(
                OreProfileDocument.CURRENT_SCHEMA_VERSION, 3L, "province", List.of(rule));

        String json = ConfigJson.GSON.toJson(document);
        assertTrue(json.contains("\"placement\": \"province\""));
        assertTrue(json.contains("\"per_chunk_work_cap\": 1024"));
        StrictConfigStructure.parseAndValidate(json, OreProfileDocument.class);
        assertEquals(document, ConfigJson.GSON.fromJson(json, OreProfileDocument.class));
    }

    @Test
    void strictProvinceParsingRejectsUnknownModesIncompleteSettingsAndIntegerOverflow() {
        var original = OrePresets.balanced().rules().getFirst();
        var province = com.nightsta69.delvefold.config.model.SpawnBand.province(
                "regional", com.nightsta69.delvefold.config.model.HeightDistribution.UNIFORM,
                -32, 32, null, null, null, 0.0D, ProvinceSettings.defaults());
        String valid = ConfigJson.GSON.toJson(new OreProfileDocument(
                2, 0L, "strict_province", List.of(original.withBands(List.of(province)))));

        assertThrows(JsonParseException.class, () -> StrictConfigStructure.parseAndValidate(
                valid.replace("\"placement\": \"province\"", "\"placement\": \"cluster\""),
                OreProfileDocument.class));
        assertThrows(JsonParseException.class, () -> StrictConfigStructure.parseAndValidate(
                valid.replace("\"radius\": 192,", ""), OreProfileDocument.class));
        assertThrows(JsonParseException.class, () -> StrictConfigStructure.parseAndValidate(
                valid.replace("\"per_chunk_work_cap\": 1024",
                        "\"per_chunk_work_cap\": 4294967297"), OreProfileDocument.class));
    }

    @Test
    void targetWeightsRoundTripWithoutBreakingLegacyConstructorsOrFactories() {
        OreTarget legacyConstructor = new OreTarget(
                "minecraft:diamond_ore", "", java.util.Map.of(),
                "minecraft:stone_ore_replaceables");
        OreTarget legacyFactory = OreTarget.of(
                "minecraft:emerald_ore", "minecraft:stone_ore_replaceables");
        OreTarget weightedTag = OreTarget.ofTag(
                "c:ores/tin", "minecraft:stone_ore_replaceables", 37);

        assertEquals(OreTarget.DEFAULT_WEIGHT, legacyConstructor.weight());
        assertEquals(OreTarget.DEFAULT_WEIGHT, legacyFactory.weight());
        assertEquals(37, weightedTag.weight());
        String json = ConfigJson.GSON.toJson(weightedTag);
        assertTrue(json.contains("\"weight\": 37"));
        assertEquals(weightedTag, ConfigJson.GSON.fromJson(json, OreTarget.class));
    }

    @Test
    void strictOreParsingRejectsOutOfRangeWeightsBeforeIntegerDeserialization() {
        for (String invalidWeight : java.util.List.of("-1", "0", "1001", "4294967297")) {
            String json = """
                    {"schema_version":2,"revision":0,"profile":"strict-weight","rules":[{
                      "id":"tin","enabled":true,"required":false,"terrain_modes":["wild"],
                      "targets":[{"block":"example:tin_ore","state":{},
                        "replace_tag":"minecraft:stone_ore_replaceables","weight":%s}],
                      "biomes":{"include":[],"exclude":[]},
                      "bands":[{"id":"main","vein_size":4,"attempts_per_chunk":2.0,
                        "distribution":"uniform","min_y":-32,"max_y":64,
                        "discard_on_air_exposure":0.0}]
                    }]}
                    """.formatted(invalidWeight);

            assertThrows(JsonParseException.class,
                    () -> StrictConfigStructure.parseAndValidate(json, OreProfileDocument.class),
                    "Expected strict parsing to reject weight " + invalidWeight);
        }
    }

    @Test
    void expansiveFlatSurfaceStaysBelowCloudLayer() throws IOException {
        String json = Files.readString(Path.of(
                "src/main/resources/data/delvefold/dimension/delve_flat_expansive.json"));
        var layers = JsonParser.parseString(json).getAsJsonObject()
                .getAsJsonObject("generator").getAsJsonObject("settings").getAsJsonArray("layers");
        int totalHeight = 0;
        for (var layer : layers) {
            totalHeight += layer.getAsJsonObject().get("height").getAsInt();
        }
        assertEquals(193, totalHeight);
        assertTrue(-64 + totalHeight - 1 < 192, "Flat surface must remain below vanilla cloud height");
    }
}
