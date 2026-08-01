package com.nightsta69.delvefold.world.landmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;

class LandmarkResourceContractTest {
    private static final Path DATA = Path.of("src/main/resources/data/delvefold");
    private static final List<String> IDS = List.of(
            "collapsed_mine_entrance",
            "fault_line_grotto",
            "geode_vault",
            "lift_station",
            "motherlode_chamber",
            "survey_camp");
    private static final Map<String, String> GENERATED_HASHES = Map.of(
            "collapsed_mine_entrance", "9dd86faaeb7d6964fa07ef94f77e67051be7e9cb10859f81959bae72a66146ff",
            "fault_line_grotto", "c08cf2996bee229f1459a0dc537cc219e1a0d8e7db4cc9c4d4555285704af4a1",
            "geode_vault", "6412425f47f2268a71e0786bb62a4cf5c2ebc633766128943eb04200d78a448a",
            "lift_station", "6693df7833b301f9c2cfbb9a8f942edc4504fdf426647a4fa95749aa1070f0f4",
            "motherlode_chamber", "79b7c97ae902d2606ab08e06481bcc9dd228db4937ba3b98e6c9c81922632536",
            "survey_camp", "687828cf75073b12af921a188d2ba4e47ba76c07c31783674534987f9ab1401f");

    @Test
    void allSixDefinitionsReferenceBundledTemplateProcessorAndLootAssets() throws Exception {
        Set<String> seen = new HashSet<>();
        for (String id : IDS) {
            JsonObject definition = readJson(DATA.resolve("delvefold/landmarks/" + id + ".json"));
            assertEquals(1, definition.get("format").getAsInt(), id);
            assertTrue(definition.get("weight").getAsInt() >= 1, id);
            assertTrue(definition.get("weight").getAsInt() <= 1000, id);
            assertFalse(definition.getAsJsonArray("terrain_modes").isEmpty(), id);
            assertTrue(
                    definition.get("min_y").getAsInt()
                            <= definition.get("max_y").getAsInt(),
                    id);

            String template = definition.get("template").getAsString().replace("delvefold:", "");
            assertTrue(seen.add(template), "Duplicate template " + template);
            assertTrue(Files.isRegularFile(DATA.resolve("structure/" + template + ".nbt")), id);
            for (var processor : definition.getAsJsonArray("processors")) {
                String processorId = processor.getAsString().replace("delvefold:", "");
                assertTrue(Files.isRegularFile(DATA.resolve("worldgen/processor_list/" + processorId + ".json")), id);
            }
            String loot = definition.get("loot_table").getAsString().replace("delvefold:", "");
            assertTrue(Files.isRegularFile(DATA.resolve("loot_table/" + loot + ".json")), id);
        }
        assertEquals(6, seen.size());
    }

    @Test
    void generatedNbtIsDeterministicAndContainsARealBuildAndLootMarker() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (String id : IDS) {
            Path template = DATA.resolve("structure/landmarks/" + id + ".nbt");
            byte[] compressed = Files.readAllBytes(template);
            assertEquals(GENERATED_HASHES.get(id), HexFormat.of().formatHex(digest.digest(compressed)), id);
            byte[] nbt;
            try (GZIPInputStream input = new GZIPInputStream(Files.newInputStream(template))) {
                nbt = input.readAllBytes();
            }
            String binaryStrings = new String(nbt, StandardCharsets.ISO_8859_1);
            assertTrue(binaryStrings.contains("minecraft:barrel"), id);
            assertTrue(binaryStrings.contains("minecraft:structure_block"), id);
            assertTrue(binaryStrings.contains("metadata"), id);
            assertTrue(binaryStrings.contains("loot"), id);
            assertTrue(nbt.length > 500, id);
        }
    }

    @Test
    void structureSetUsesTheGenerationSaltedPlacementAndBundledStructure() throws Exception {
        JsonObject structureSet = readJson(DATA.resolve("worldgen/structure_set/mining_landmarks.json"));
        assertEquals(
                "delvefold:mining_landmark",
                structureSet
                        .getAsJsonArray("structures")
                        .get(0)
                        .getAsJsonObject()
                        .get("structure")
                        .getAsString());
        JsonObject placement = structureSet.getAsJsonObject("placement");
        assertEquals(
                "delvefold:generation_salted_random_spread",
                placement.get("type").getAsString());
        assertEquals(12, placement.get("spacing").getAsInt());
        assertEquals(6, placement.get("separation").getAsInt());
    }

    @Test
    void templateBlueprintAndGeneratorCoverExactlyTheShippedAssets() throws Exception {
        JsonObject blueprints = readJson(Path.of("tools/landmark_templates.json"));
        Set<String> blueprintIds = new HashSet<>();
        blueprints
                .getAsJsonArray("templates")
                .forEach(template ->
                        blueprintIds.add(template.getAsJsonObject().get("id").getAsString()));
        assertEquals(Set.copyOf(IDS), blueprintIds);
        String generator = Files.readString(Path.of("tools/generate_landmark_templates.py"));
        assertTrue(generator.contains("gzip.compress(build(template), compresslevel=9, mtime=0)"));
        assertTrue(generator.contains("DATA_VERSION = 3955"));
    }

    private static JsonObject readJson(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
