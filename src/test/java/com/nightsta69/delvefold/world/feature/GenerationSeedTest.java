package com.nightsta69.delvefold.world.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class GenerationSeedTest {
    @Test
    void zeroSaltUsesTheExactLegacyOreFormula() {
        List<int[]> chunks = List.of(new int[]{0, 0}, new int[]{17, -29}, new int[]{-8, -41});
        List<String> ids = List.of("iron/main", "example:tin/rich", "delvefold:test");

        for (int[] chunk : chunks) {
            long chunkPosition = chunkPosition(chunk[0], chunk[1]);
            for (String id : ids) {
                assertEquals(
                        legacyOreSeed(0x1234_5678_9ABCDEFL, chunkPosition, id),
                        GenerationSeedMixer.oreSeed(0x1234_5678_9ABCDEFL, chunkPosition, id, 0L));
            }
        }
    }

    @Test
    void rotatedOreSeedsRepeatAndChangeWithThePersistedSalt() {
        long chunkPosition = chunkPosition(-37, 91);
        long first = GenerationSeedMixer.oreSeed(8675309L, chunkPosition, "diamond/main", 41L);

        assertEquals(first, GenerationSeedMixer.oreSeed(8675309L, chunkPosition, "diamond/main", 41L));
        assertNotEquals(first, GenerationSeedMixer.oreSeed(8675309L, chunkPosition, "diamond/main", 42L));
        assertNotEquals(first, GenerationSeedMixer.oreSeed(
                8675309L, chunkPosition(-36, 91), "diamond/main", 41L));
    }

    @Test
    void provinceCenterOutputChunkAndPositionStreamsAreStableAndSeparated() {
        long center = GenerationSeedMixer.oreProvinceCenterSeed(
                1234L, -7L, 11L, "diamond/province", 41L);
        long output = GenerationSeedMixer.oreProvinceOutputSeed(
                1234L, -7L, 11L, "diamond/province", 41L);
        long chunk = GenerationSeedMixer.oreProvinceChunkSeed(
                1234L, -7L, 11L, chunkPosition(4, -2), "diamond/province", 41L);
        long position = GenerationSeedMixer.oreProvincePositionSeed(chunk, 57);

        assertEquals(center, GenerationSeedMixer.oreProvinceCenterSeed(
                1234L, -7L, 11L, "diamond/province", 41L));
        assertNotEquals(center, GenerationSeedMixer.oreProvinceCenterSeed(
                1234L, -7L, 11L, "diamond/province", 42L));
        assertNotEquals(center, GenerationSeedMixer.oreProvinceCenterSeed(
                1234L, -6L, 11L, "diamond/province", 41L));
        assertNotEquals(center, output);
        assertNotEquals(center, chunk);
        assertNotEquals(output, chunk);
        assertNotEquals(chunk, position);
        assertNotEquals(position, GenerationSeedMixer.oreProvincePositionSeed(chunk, 58));
    }

    @Test
    void rotatedLandmarkSeedsRepeatAndChangeAcrossSaltsAndChunks() {
        long chunkPosition = chunkPosition(23, -51);
        long first = GenerationSeedMixer.landmarkPlacementSeed(
                0x7A11_CAFE_BABEL, chunkPosition, 101L);
        long content = GenerationSeedMixer.landmarkContentSeed(
                0x7A11_CAFE_BABEL, chunkPosition, 101L);

        assertEquals(first, GenerationSeedMixer.landmarkPlacementSeed(
                0x7A11_CAFE_BABEL, chunkPosition, 101L));
        assertNotEquals(first, GenerationSeedMixer.landmarkPlacementSeed(
                0x7A11_CAFE_BABEL, chunkPosition, 102L));
        assertNotEquals(first, GenerationSeedMixer.landmarkPlacementSeed(
                0x7A11_CAFE_BABEL, chunkPosition(24, -51), 101L));
        assertNotEquals(first, content, "Placement and content require separate seed domains");
        assertEquals(content, GenerationSeedMixer.landmarkContentSeed(
                0x7A11_CAFE_BABEL, chunkPosition, 101L));
    }

    @Test
    void landmarkPlacementModifierRemainsDataDriven() throws Exception {
        var root = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/data/delvefold/worldgen/placed_feature/mining_landmark.json")))
                .getAsJsonObject();

        var placement = root.getAsJsonArray("placement");
        assertEquals(1, placement.size());
        assertEquals("delvefold:generation_salted_landmark",
                placement.get(0).getAsJsonObject().get("type").getAsString());
        assertEquals(12, placement.get(0).getAsJsonObject().get("chance").getAsInt());
    }

    private static long chunkPosition(int x, int z) {
        return (long) x & 0xFFFF_FFFFL | ((long) z & 0xFFFF_FFFFL) << 32;
    }

    private static long legacyOreSeed(long worldSeed, long chunkPosition, String id) {
        long seed = worldSeed ^ chunkPosition * 0x9E3779B97F4A7C15L;
        seed ^= stableHash64(id) * 0xD1B54A32D192ED03L;
        return mix64(seed);
    }

    private static long stableHash64(String value) {
        long hash = 0xCBF29CE484222325L;
        for (int index = 0; index < value.length(); index++) {
            hash ^= value.charAt(index);
            hash *= 0x100000001B3L;
        }
        return hash;
    }

    private static long mix64(long value) {
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }
}
