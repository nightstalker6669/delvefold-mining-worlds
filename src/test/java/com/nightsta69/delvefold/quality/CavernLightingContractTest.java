package com.nightsta69.delvefold.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Locks Cavern visibility improvements without changing real lighting or monster-spawn rules. */
class CavernLightingContractTest {
    private static final Path CAVERN_TYPE =
            Path.of("src/main/resources/data/delvefold/dimension_type/delve_cavern.json");

    @Test
    void cavernTypeAddsAConservativeVisualFloorWithoutChangingRealLight() throws Exception {
        JsonObject cavern = readJson(CAVERN_TYPE);

        assertEquals(0.1F, cavern.get("ambient_light").getAsFloat(), 0.0001F);
        assertFalse(cavern.get("has_skylight").getAsBoolean());
        assertTrue(cavern.get("has_ceiling").getAsBoolean());
        assertFalse(cavern.has("fixed_time"));
    }

    @Test
    void cavernTypePreservesMonsterLightLimits() throws Exception {
        JsonObject cavern = readJson(CAVERN_TYPE);
        JsonObject spawnLight = cavern.getAsJsonObject("monster_spawn_light_level");

        assertEquals(0, cavern.get("monster_spawn_block_light_limit").getAsInt());
        assertEquals("minecraft:uniform", spawnLight.get("type").getAsString());
        assertEquals(0, spawnLight.get("min_inclusive").getAsInt());
        assertEquals(7, spawnLight.get("max_inclusive").getAsInt());
    }

    @Test
    void classicAndExpansiveCavernsShareTheBrightenedDimensionType() throws Exception {
        assertCavernType("delve_cavern.json");
        assertCavernType("delve_cavern_expansive.json");
    }

    private static void assertCavernType(String fileName) throws Exception {
        Path dimension = Path.of("src/main/resources/data/delvefold/dimension", fileName);
        assertEquals("delvefold:delve_cavern", readJson(dimension).get("type").getAsString());
    }

    private static JsonObject readJson(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
