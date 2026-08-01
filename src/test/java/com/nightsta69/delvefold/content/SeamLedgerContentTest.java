package com.nightsta69.delvefold.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SeamLedgerContentTest {
    @Test
    void recipeUsesTheRequestedThreeIngredients() {
        JsonObject recipe = resource("/data/delvefold/recipe/seam_ledger.json");
        assertEquals("minecraft:crafting_shapeless", recipe.get("type").getAsString());
        assertEquals("misc", recipe.get("category").getAsString());
        assertEquals("delvefold:seam_ledger", recipe.getAsJsonObject("result").get("id").getAsString());
        assertEquals(1, recipe.getAsJsonObject("result").get("count").getAsInt());

        Set<String> ingredients = new HashSet<>();
        JsonArray ingredientArray = recipe.getAsJsonArray("ingredients");
        ingredientArray.forEach(element -> ingredients.add(element.getAsJsonObject().get("item").getAsString()));
        assertEquals(Set.of("minecraft:book", "minecraft:compass", "minecraft:copper_ingot"), ingredients);
        assertEquals(3, ingredientArray.size());
    }

    @Test
    void modelReusesTheVanillaWritableBookTexture() {
        JsonObject model = resource("/assets/delvefold/models/item/seam_ledger.json");
        assertEquals("minecraft:item/generated", model.get("parent").getAsString());
        assertEquals("minecraft:item/writable_book",
                model.getAsJsonObject("textures").get("layer0").getAsString());
    }

    @Test
    void advancementChainMatchesThePublicConsultTrigger() {
        JsonObject obtain = resource("/data/delvefold/advancement/obtain_seam_ledger.json");
        assertEquals("delvefold:root", obtain.get("parent").getAsString());
        JsonObject obtainCriterion = obtain.getAsJsonObject("criteria").getAsJsonObject("obtain");
        assertEquals("minecraft:inventory_changed", obtainCriterion.get("trigger").getAsString());
        String requiredItem = obtainCriterion.getAsJsonObject("conditions")
                .getAsJsonArray("items").get(0).getAsJsonObject().get("items").getAsString();
        assertEquals("delvefold:seam_ledger", requiredItem);

        JsonObject consult = resource("/data/delvefold/advancement/consult_seam_ledger.json");
        assertEquals("delvefold:obtain_seam_ledger", consult.get("parent").getAsString());
        JsonObject consultCriterion = consult.getAsJsonObject("criteria")
                .getAsJsonObject("consult");
        assertNotNull(consultCriterion);
        assertEquals("minecraft:impossible", consultCriterion.get("trigger").getAsString());
    }

    @Test
    void englishTranslationsCoverTheLedgerAndItsAdvancements() {
        JsonObject language = resource("/assets/delvefold/lang/en_us.json");
        assertTrue(language.has("item.delvefold.seam_ledger"));
        assertTrue(language.has("advancement.delvefold.seam_ledger.obtain.title"));
        assertTrue(language.has("advancement.delvefold.seam_ledger.obtain.description"));
        assertTrue(language.has("advancement.delvefold.seam_ledger.consult.title"));
        assertTrue(language.has("advancement.delvefold.seam_ledger.consult.description"));
    }

    private static JsonObject resource(String path) {
        InputStream input = SeamLedgerContentTest.class.getResourceAsStream(path);
        assertNotNull(input, "Missing resource " + path);
        try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception exception) {
            throw new AssertionError("Could not read " + path, exception);
        }
    }
}
