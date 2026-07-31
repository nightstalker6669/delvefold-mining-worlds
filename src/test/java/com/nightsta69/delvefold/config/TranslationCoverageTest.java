package com.nightsta69.delvefold.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nightsta69.delvefold.config.model.GameplayPreset;
import com.nightsta69.delvefold.config.model.LandmarkPreset;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.TerrainVariant;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class TranslationCoverageTest {
    private static final Pattern KEY = Pattern.compile("(?m)^\\s*\"([^\"]+)\"\\s*:");

    @Test
    void englishLanguageHasUniqueKeysAndAllSelectableOptions() throws Exception {
        String resource = "/assets/delvefold/lang/en_us.json";
        try (InputStream input = TranslationCoverageTest.class.getResourceAsStream(resource)) {
            assertNotNull(input, "Missing " + resource);
            String json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            List<String> keys = new ArrayList<>();
            KEY.matcher(json).results().forEach(match -> keys.add(match.group(1)));
            assertEquals(keys.size(), new HashSet<>(keys).size(), "Duplicate translation key");

            JsonObject language = JsonParser.parseString(json).getAsJsonObject();
            for (TerrainMode value : TerrainMode.values()) {
                assertKey(language, "option.delvefold.terrain." + value.serializedName());
            }
            for (TerrainVariant value : TerrainVariant.values()) {
                assertKey(language, "option.delvefold.terrain_variant." + value.serializedName());
            }
            for (OrePreset value : OrePreset.values()) {
                assertKey(language, "option.delvefold.ore_preset." + value.serializedName());
            }
            for (GameplayPreset value : GameplayPreset.values()) {
                assertKey(language, "option.delvefold.gameplay." + value.serializedName());
            }
            for (LandmarkPreset value : LandmarkPreset.values()) {
                assertKey(language, "option.delvefold.landmark." + value.serializedName());
            }
        }
    }

    private static void assertKey(JsonObject language, String key) {
        assertTrue(language.has(key), () -> "Missing translation " + key);
    }
}
