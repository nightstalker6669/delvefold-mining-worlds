package com.nightsta69.delvefold.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

/** Locks the material, fluid, solid-host, bounded-carver, and pocket contracts for both Cavern generators. */
class CavernGenerationContractTest {
    private static final Path DATA_ROOT = Path.of("src/main/resources/data/delvefold");
    private static final Path DIMENSION_ROOT = DATA_ROOT.resolve("dimension");
    private static final Path WORLDGEN_ROOT = DATA_ROOT.resolve("worldgen");

    @Test
    void cavernDimensionsUseDedicatedSettingsWithoutChangingTheirStableDimensionType() throws Exception {
        assertDimensionSettings("delve_cavern", "delvefold:delve_cavern");
        assertDimensionSettings("delve_cavern_expansive", "delvefold:delve_cavern_expansive");
    }

    @Test
    void bothCavernSettingsUseStoneSurfacesAndNoGlobalFluidTable() throws Exception {
        JsonObject classic = noiseSettings("delve_cavern");
        JsonObject expansive = noiseSettings("delve_cavern_expansive");

        assertDryStoneSettings(classic, 192, true, false, "minecraft:bedrock_roof");
        assertDryStoneSettings(expansive, 384, false, false, "delvefold:bedrock_roof");
        assertSolidHostDensity(classic);
        assertSolidHostDensity(expansive);
        assertFalse(classic.toString().contains("minecraft:grass_block"));
        assertFalse(expansive.toString().contains("minecraft:grass_block"));
        assertFalse(classic.toString().contains("minecraft:dirt"));
        assertFalse(expansive.toString().contains("minecraft:dirt"));
        assertFalse(classic.toString().contains("minecraft:water"));
        assertFalse(expansive.toString().contains("minecraft:water"));
    }

    @Test
    void compactCaveUsesRelativeHeightBoundsAndReducedDimensions() throws Exception {
        JsonObject cave = configuredCarver("compact_cave");
        JsonObject caveConfig = cave.getAsJsonObject("config");
        assertEquals("minecraft:cave", cave.get("type").getAsString());
        assertEquals(0.18D, caveConfig.get("probability").getAsDouble());
        assertRelativeHeight(caveConfig, 12, 32);
        assertEquals(
                8, caveConfig.getAsJsonObject("lava_level").get("above_bottom").getAsInt());
        assertUniformRange(caveConfig.getAsJsonObject("horizontal_radius_multiplier"), 0.55D, 0.9D);
        assertUniformRange(caveConfig.getAsJsonObject("vertical_radius_multiplier"), 0.5D, 0.8D);
        assertUniformRange(caveConfig.getAsJsonObject("yScale"), 0.15D, 0.6D);
        assertUniformRange(caveConfig.getAsJsonObject("floor_level"), -1.0D, -0.55D);
        assertFalse(Files.exists(WORLDGEN_ROOT.resolve("configured_carver/compact_canyon.json")));
    }

    @Test
    void cavernBiomeReplacesVanillaLakesAndSpringsWithBoundedPockets() throws Exception {
        JsonObject biome = readJson(WORLDGEN_ROOT.resolve("biome/mining_cavern.json"));
        JsonArray features = biome.getAsJsonArray("features");
        JsonArray lakes = features.get(1).getAsJsonArray();
        JsonArray localModifications = features.get(8).getAsJsonArray();

        assertEquals(11, features.size());
        assertTrue(lakes.isEmpty());
        assertEquals(List.of("delvefold:cavern_water_pocket"), strings(localModifications));
        assertFalse(biome.toString().contains("minecraft:lake_lava_underground"));
        assertFalse(biome.toString().contains("minecraft:spring_water"));
        assertFalse(biome.toString().contains("minecraft:spring_lava"));
        assertEquals(
                List.of("delvefold:compact_cave"),
                strings(biome.getAsJsonObject("carvers").getAsJsonArray("air")));
    }

    @Test
    void waterPocketIsSmallShallowSparseAndRestrictedToCavernFloors() throws Exception {
        assertPocketConfiguration("cavern_water_pocket", "minecraft:water");
        JsonObject placed = placedFeature("cavern_water_pocket");
        JsonArray placement = placed.getAsJsonArray("placement");

        assertEquals(2, placement.get(0).getAsJsonObject().get("count").getAsInt());
        assertEquals(4, placement.get(1).getAsJsonObject().get("chance").getAsInt());
        assertEquals(
                List.of(
                        "minecraft:count",
                        "minecraft:rarity_filter",
                        "minecraft:in_square",
                        "minecraft:height_range",
                        "minecraft:environment_scan",
                        "minecraft:biome"),
                StreamSupport.stream(placement.spliterator(), false)
                        .map(element -> element.getAsJsonObject().get("type").getAsString())
                        .toList());
        JsonObject height = placement.get(3).getAsJsonObject().getAsJsonObject("height");
        assertEquals("minecraft:uniform", height.get("type").getAsString());
        assertEquals(32, height.getAsJsonObject("min_inclusive").get("absolute").getAsInt());
        assertEquals(
                112, height.getAsJsonObject("max_inclusive").get("absolute").getAsInt());
        assertFloorScan(placement.get(4).getAsJsonObject());
    }

    private static void assertDimensionSettings(String fileName, String settingsId) throws Exception {
        JsonObject dimension = readJson(DIMENSION_ROOT.resolve(fileName + ".json"));
        JsonObject generator = dimension.getAsJsonObject("generator");
        JsonObject biomeSource = generator.getAsJsonObject("biome_source");
        assertEquals("delvefold:delve_cavern", dimension.get("type").getAsString());
        assertEquals("minecraft:noise", generator.get("type").getAsString());
        assertEquals(settingsId, generator.get("settings").getAsString());
        assertEquals("minecraft:fixed", biomeSource.get("type").getAsString());
        assertEquals("delvefold:mining_cavern", biomeSource.get("biome").getAsString());
    }

    private static void assertDryStoneSettings(
            JsonObject settings,
            int expectedHeight,
            boolean expectedLegacyRandom,
            boolean expectedOreVeins,
            String roofRandomName) {
        assertEquals(-64, settings.get("sea_level").getAsInt());
        assertEquals(
                "minecraft:stone",
                settings.getAsJsonObject("default_block").get("Name").getAsString());
        assertEquals(
                "minecraft:air",
                settings.getAsJsonObject("default_fluid").get("Name").getAsString());
        assertFalse(settings.get("aquifers_enabled").getAsBoolean());
        JsonObject noise = settings.getAsJsonObject("noise");
        assertEquals(-64, noise.get("min_y").getAsInt());
        assertEquals(expectedHeight, noise.get("height").getAsInt());
        assertEquals(1, noise.get("size_horizontal").getAsInt());
        assertEquals(2, noise.get("size_vertical").getAsInt());
        assertFalse(settings.get("disable_mob_generation").getAsBoolean());
        assertEquals(expectedLegacyRandom, settings.get("legacy_random_source").getAsBoolean());
        assertEquals(expectedOreVeins, settings.get("ore_veins_enabled").getAsBoolean());
        assertSurfaceRules(settings.getAsJsonObject("surface_rule"), roofRandomName);
    }

    private static void assertSolidHostDensity(JsonObject settings) {
        JsonObject router = settings.getAsJsonObject("noise_router");
        assertTrue(router.get("final_density").isJsonPrimitive());
        assertTrue(router.get("initial_density_without_jaggedness").isJsonPrimitive());
        assertEquals(1.0D, router.get("final_density").getAsDouble());
        assertEquals(1.0D, router.get("initial_density_without_jaggedness").getAsDouble());
    }

    private static void assertRelativeHeight(JsonObject config, int aboveBottom, int belowTop) {
        JsonObject height = config.getAsJsonObject("y");
        assertEquals("minecraft:uniform", height.get("type").getAsString());
        assertEquals(
                aboveBottom,
                height.getAsJsonObject("min_inclusive").get("above_bottom").getAsInt());
        assertEquals(
                belowTop,
                height.getAsJsonObject("max_inclusive").get("below_top").getAsInt());
    }

    private static void assertUniformRange(JsonObject provider, double minimum, double maximumExclusive) {
        assertEquals("minecraft:uniform", provider.get("type").getAsString());
        assertEquals(minimum, provider.get("min_inclusive").getAsDouble());
        assertEquals(maximumExclusive, provider.get("max_exclusive").getAsDouble());
    }

    private static void assertSurfaceRules(JsonObject surfaceRule, String roofRandomName) {
        assertEquals("minecraft:sequence", surfaceRule.get("type").getAsString());
        JsonArray sequence = surfaceRule.getAsJsonArray("sequence");
        assertEquals(3, sequence.size());

        JsonObject roof = sequence.get(0).getAsJsonObject();
        JsonObject roofGradient = roof.getAsJsonObject("if_true").getAsJsonObject("invert");
        assertEquals(
                "minecraft:not", roof.getAsJsonObject("if_true").get("type").getAsString());
        assertEquals("minecraft:vertical_gradient", roofGradient.get("type").getAsString());
        assertEquals(roofRandomName, roofGradient.get("random_name").getAsString());
        assertEquals(
                "minecraft:bedrock",
                roof.getAsJsonObject("then_run")
                        .getAsJsonObject("result_state")
                        .get("Name")
                        .getAsString());

        JsonObject floor = sequence.get(1).getAsJsonObject();
        JsonObject floorGradient = floor.getAsJsonObject("if_true");
        assertEquals("minecraft:bedrock_floor", floorGradient.get("random_name").getAsString());
        assertEquals(
                0,
                floorGradient
                        .getAsJsonObject("true_at_and_below")
                        .get("above_bottom")
                        .getAsInt());
        assertEquals(
                5,
                floorGradient
                        .getAsJsonObject("false_at_and_above")
                        .get("above_bottom")
                        .getAsInt());
        assertEquals(
                "minecraft:bedrock",
                floor.getAsJsonObject("then_run")
                        .getAsJsonObject("result_state")
                        .get("Name")
                        .getAsString());

        JsonObject deepslate = sequence.get(2).getAsJsonObject();
        JsonObject deepslateGradient = deepslate.getAsJsonObject("if_true");
        assertEquals("minecraft:deepslate", deepslateGradient.get("random_name").getAsString());
        assertEquals(
                0,
                deepslateGradient
                        .getAsJsonObject("true_at_and_below")
                        .get("absolute")
                        .getAsInt());
        assertEquals(
                8,
                deepslateGradient
                        .getAsJsonObject("false_at_and_above")
                        .get("absolute")
                        .getAsInt());
        assertEquals(
                "minecraft:deepslate",
                deepslate
                        .getAsJsonObject("then_run")
                        .getAsJsonObject("result_state")
                        .get("Name")
                        .getAsString());
    }

    private static void assertPocketConfiguration(String name, String fluid) throws Exception {
        JsonObject configured = readJson(WORLDGEN_ROOT.resolve("configured_feature/" + name + ".json"));
        JsonObject config = configured.getAsJsonObject("config");
        JsonObject radius = config.getAsJsonObject("radius");
        JsonObject target = config.getAsJsonObject("target");
        JsonObject state = config.getAsJsonObject("state_provider")
                .getAsJsonObject("fallback")
                .getAsJsonObject("state");
        JsonObject stateProvider = config.getAsJsonObject("state_provider");

        assertEquals("minecraft:disk", configured.get("type").getAsString());
        assertEquals(0, config.get("half_height").getAsInt());
        assertEquals("minecraft:uniform", radius.get("type").getAsString());
        assertEquals(1, radius.get("min_inclusive").getAsInt());
        assertEquals(2, radius.get("max_inclusive").getAsInt());
        assertEquals(fluid, state.get("Name").getAsString());
        assertEquals("0", state.getAsJsonObject("Properties").get("level").getAsString());
        assertEquals(
                "minecraft:simple_state_provider",
                stateProvider.getAsJsonObject("fallback").get("type").getAsString());
        assertTrue(stateProvider.getAsJsonArray("rules").isEmpty());
        assertEquals("minecraft:all_of", target.get("type").getAsString());
        JsonArray predicates = target.getAsJsonArray("predicates");
        assertEquals(2, predicates.size());
        assertEquals(
                "minecraft:base_stone_overworld",
                predicates.get(0).getAsJsonObject().get("tag").getAsString());
        assertEquals(
                List.of("minecraft:air", "minecraft:cave_air"),
                strings(predicates.get(1).getAsJsonObject().getAsJsonArray("blocks")));
        assertEquals(
                List.of("0", "1", "0"),
                strings(predicates.get(1).getAsJsonObject().getAsJsonArray("offset")));
    }

    private static void assertFloorScan(JsonObject scan) {
        assertEquals("minecraft:environment_scan", scan.get("type").getAsString());
        assertEquals("down", scan.get("direction_of_search").getAsString());
        assertEquals(32, scan.get("max_steps").getAsInt());
        assertEquals(
                List.of("minecraft:air", "minecraft:cave_air"),
                strings(scan.getAsJsonObject("allowed_search_condition").getAsJsonArray("blocks")));
        assertEquals(
                "minecraft:all_of",
                scan.getAsJsonObject("target_condition").get("type").getAsString());
        JsonArray floorPredicates = scan.getAsJsonObject("target_condition").getAsJsonArray("predicates");
        assertEquals(3, floorPredicates.size());
        assertEquals(
                "minecraft:matching_block_tag",
                floorPredicates.get(0).getAsJsonObject().get("type").getAsString());
        assertEquals(
                "minecraft:matching_blocks",
                floorPredicates.get(1).getAsJsonObject().get("type").getAsString());
        assertEquals(
                List.of("0", "1", "0"),
                strings(floorPredicates.get(1).getAsJsonObject().getAsJsonArray("offset")));
        assertEquals(
                "minecraft:inside_world_bounds",
                floorPredicates.get(2).getAsJsonObject().get("type").getAsString());
        assertEquals(
                List.of("0", "-1", "0"),
                strings(floorPredicates.get(2).getAsJsonObject().getAsJsonArray("offset")));
    }

    private static JsonObject noiseSettings(String fileName) throws Exception {
        return readJson(WORLDGEN_ROOT.resolve("noise_settings/" + fileName + ".json"));
    }

    private static JsonObject configuredCarver(String fileName) throws Exception {
        return readJson(WORLDGEN_ROOT.resolve("configured_carver/" + fileName + ".json"));
    }

    private static JsonObject placedFeature(String fileName) throws Exception {
        return readJson(WORLDGEN_ROOT.resolve("placed_feature/" + fileName + ".json"));
    }

    private static List<String> strings(JsonArray array) {
        return StreamSupport.stream(array.spliterator(), false)
                .map(element -> element.getAsString())
                .toList();
    }

    private static JsonObject readJson(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
