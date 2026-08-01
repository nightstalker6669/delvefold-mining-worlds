package com.nightsta69.delvefold.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Characterizes the exact textual subset used by Minecraft 1.21.1 resource identifiers. */
class ResourceIdentifierTextTest {
    @ParameterizedTest
    @CsvSource({
        "minecraft:deepslate_iron_ore,deepslate_iron_ore",
        "deepslate_iron_ore,deepslate_iron_ore",
        ":stone,stone",
        "example:path/to.ore-1,path/to.ore-1",
        "minecraft:,''"
    })
    void validIdentifiersExposeTheSamePathUsedForHostInference(String value, String expectedPath) {
        assertTrue(ResourceIdentifierText.isValid(value));
        assertEquals(expectedPath, ResourceIdentifierText.path(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Minecraft:stone", "minecraft:Stone", "bad namespace:stone", "a:b:c", "minecraft:ore?"})
    void forbiddenCharactersAreRejected(String value) {
        assertFalse(ResourceIdentifierText.isValid(value));
        assertNull(ResourceIdentifierText.path(value));
    }
}
