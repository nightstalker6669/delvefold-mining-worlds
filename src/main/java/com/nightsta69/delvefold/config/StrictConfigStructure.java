package com.nightsta69.delvefold.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.model.OreTarget;
import com.nightsta69.delvefold.config.model.WorldSettingsDocument;
import java.math.BigInteger;
import java.util.Set;

/** Rejects misspelled or structurally misplaced JSON fields before Gson applies record defaults. */
final class StrictConfigStructure {
    private static final Set<String> ORE_DOCUMENT = Set.of("schema_version", "revision", "profile", "rules");
    private static final Set<String> ORE_RULE = Set.of(
            "id", "enabled", "required", "terrain_modes", "targets", "biomes", "bands");
    private static final Set<String> ORE_TARGET = Set.of("block", "block_tag", "state", "replace_tag", "weight");
    private static final Set<String> REQUIRED_ORE_TARGET = Set.of("state", "replace_tag");
    private static final Set<String> BIOME_FILTER = Set.of("include", "exclude");
    private static final Set<String> SPAWN_BAND = Set.of(
            "id", "vein_size", "attempts_per_chunk", "distribution", "min_y", "max_y",
            "peak_y", "plateau_min_y", "plateau_max_y", "discard_on_air_exposure");
    private static final Set<String> REQUIRED_SPAWN_BAND = Set.of(
            "id", "vein_size", "attempts_per_chunk", "distribution", "min_y", "max_y",
            "discard_on_air_exposure");
    private static final Set<String> SETTINGS_DOCUMENT = Set.of(
            "schema_version", "revision", "generation_epoch", "last_world_operation_id", "initialized",
            "terrain_mode", "ore_preset", "active_profile_id", "gameplay", "portal", "identity",
            "guide_visibility");
    private static final Set<String> REQUIRED_SETTINGS_DOCUMENT = Set.of(
            "schema_version", "revision", "generation_epoch", "last_world_operation_id", "initialized",
            "terrain_mode", "ore_preset", "active_profile_id", "gameplay", "portal");
    private static final Set<String> GAMEPLAY = Set.of(
            "preset", "monsters", "creatures", "ambient", "water_creatures", "patrols", "phantoms");
    private static final Set<String> PORTAL = Set.of(
            "enabled", "allow_from_overworld_only", "cooldown_seconds", "coordinate_scale");
    private static final Set<String> GUIDE_VISIBILITY = Set.of("public", "operators", "disabled");
    private static final Set<String> IDENTITY = Set.of(
            "display_name", "terrain_variant", "landmark_preset", "survey_stations", "motherlodes",
            "fault_lines", "renewal");
    private static final Set<String> RENEWAL = Set.of(
            "enabled", "interval_days", "warning_minutes", "next_renewal_at_epoch_millis");

    private StrictConfigStructure() {
    }

    static JsonElement parseAndValidate(String json, Class<?> type) {
        JsonElement root = JsonParser.parseString(json);
        if (type == OreProfileDocument.class) {
            validateOreDocument(root);
        } else if (type == WorldSettingsDocument.class) {
            validateSettingsDocument(root);
        }
        return root;
    }

    private static void validateOreDocument(JsonElement root) {
        JsonObject document = object(root, "$");
        fields(document, ORE_DOCUMENT, ORE_DOCUMENT, "$");
        integer(document.get("schema_version"), "$.schema_version");
        integer(document.get("revision"), "$.revision");
        string(document.get("profile"), "$.profile", false);
        JsonArray rules = array(document.get("rules"), "$.rules");
        for (int ruleIndex = 0; ruleIndex < rules.size(); ruleIndex++) {
            String rulePath = "$.rules[" + ruleIndex + ']';
            JsonObject rule = object(rules.get(ruleIndex), rulePath);
            fields(rule, ORE_RULE, ORE_RULE, rulePath);
            string(rule.get("id"), rulePath + ".id", false);
            bool(rule.get("enabled"), rulePath + ".enabled");
            bool(rule.get("required"), rulePath + ".required");
            stringArray(rule.get("terrain_modes"), rulePath + ".terrain_modes");

            JsonArray targets = array(rule.get("targets"), rulePath + ".targets");
            for (int targetIndex = 0; targetIndex < targets.size(); targetIndex++) {
                String targetPath = rulePath + ".targets[" + targetIndex + ']';
                JsonObject target = object(targets.get(targetIndex), targetPath);
                fields(target, ORE_TARGET, REQUIRED_ORE_TARGET, targetPath);
                if (target.has("block")) string(target.get("block"), targetPath + ".block", false);
                if (target.has("block_tag")) string(target.get("block_tag"), targetPath + ".block_tag", false);
                JsonObject state = object(target.get("state"), targetPath + ".state");
                for (String property : state.keySet()) {
                    string(state.get(property), targetPath + ".state." + property, false);
                }
                string(target.get("replace_tag"), targetPath + ".replace_tag", false);
                if (target.has("weight")) {
                    boundedInteger(target.get("weight"), targetPath + ".weight",
                            OreTarget.MIN_WEIGHT, OreTarget.MAX_WEIGHT);
                }
            }

            JsonObject biomes = object(rule.get("biomes"), rulePath + ".biomes");
            fields(biomes, BIOME_FILTER, BIOME_FILTER, rulePath + ".biomes");
            stringArray(biomes.get("include"), rulePath + ".biomes.include");
            stringArray(biomes.get("exclude"), rulePath + ".biomes.exclude");

            JsonArray bands = array(rule.get("bands"), rulePath + ".bands");
            for (int bandIndex = 0; bandIndex < bands.size(); bandIndex++) {
                String bandPath = rulePath + ".bands[" + bandIndex + ']';
                JsonObject band = object(bands.get(bandIndex), bandPath);
                fields(band, SPAWN_BAND, REQUIRED_SPAWN_BAND, bandPath);
                string(band.get("id"), bandPath + ".id", false);
                integer(band.get("vein_size"), bandPath + ".vein_size");
                number(band.get("attempts_per_chunk"), bandPath + ".attempts_per_chunk");
                string(band.get("distribution"), bandPath + ".distribution", false);
                integer(band.get("min_y"), bandPath + ".min_y");
                integer(band.get("max_y"), bandPath + ".max_y");
                optionalInteger(band, "peak_y", bandPath);
                optionalInteger(band, "plateau_min_y", bandPath);
                optionalInteger(band, "plateau_max_y", bandPath);
                number(band.get("discard_on_air_exposure"), bandPath + ".discard_on_air_exposure");
            }
        }
    }

    private static void validateSettingsDocument(JsonElement root) {
        JsonObject settings = object(root, "$");
        fields(settings, SETTINGS_DOCUMENT, REQUIRED_SETTINGS_DOCUMENT, "$");
        integer(settings.get("schema_version"), "$.schema_version");
        integer(settings.get("revision"), "$.revision");
        integer(settings.get("generation_epoch"), "$.generation_epoch");
        string(settings.get("last_world_operation_id"), "$.last_world_operation_id", false);
        bool(settings.get("initialized"), "$.initialized");
        string(settings.get("terrain_mode"), "$.terrain_mode", true);
        string(settings.get("ore_preset"), "$.ore_preset", false);
        string(settings.get("active_profile_id"), "$.active_profile_id", false);
        if (settings.has("guide_visibility")) {
            string(settings.get("guide_visibility"), "$.guide_visibility", false);
            String visibility = settings.get("guide_visibility").getAsString();
            if (!GUIDE_VISIBILITY.contains(visibility)) {
                throw new JsonParseException("$.guide_visibility must be public, operators, or disabled");
            }
        }
        JsonObject gameplay = object(settings.get("gameplay"), "$.gameplay");
        fields(gameplay, GAMEPLAY, GAMEPLAY, "$.gameplay");
        string(gameplay.get("preset"), "$.gameplay.preset", false);
        bool(gameplay.get("monsters"), "$.gameplay.monsters");
        bool(gameplay.get("creatures"), "$.gameplay.creatures");
        bool(gameplay.get("ambient"), "$.gameplay.ambient");
        bool(gameplay.get("water_creatures"), "$.gameplay.water_creatures");
        bool(gameplay.get("patrols"), "$.gameplay.patrols");
        bool(gameplay.get("phantoms"), "$.gameplay.phantoms");
        JsonObject portal = object(settings.get("portal"), "$.portal");
        fields(portal, PORTAL, PORTAL, "$.portal");
        bool(portal.get("enabled"), "$.portal.enabled");
        bool(portal.get("allow_from_overworld_only"), "$.portal.allow_from_overworld_only");
        integer(portal.get("cooldown_seconds"), "$.portal.cooldown_seconds");
        number(portal.get("coordinate_scale"), "$.portal.coordinate_scale");
        if (settings.has("identity")) {
            JsonObject identity = object(settings.get("identity"), "$.identity");
            fields(identity, IDENTITY, IDENTITY, "$.identity");
            string(identity.get("display_name"), "$.identity.display_name", false);
            string(identity.get("terrain_variant"), "$.identity.terrain_variant", false);
            string(identity.get("landmark_preset"), "$.identity.landmark_preset", false);
            bool(identity.get("survey_stations"), "$.identity.survey_stations");
            bool(identity.get("motherlodes"), "$.identity.motherlodes");
            bool(identity.get("fault_lines"), "$.identity.fault_lines");
            JsonObject renewal = object(identity.get("renewal"), "$.identity.renewal");
            fields(renewal, RENEWAL, RENEWAL, "$.identity.renewal");
            bool(renewal.get("enabled"), "$.identity.renewal.enabled");
            integer(renewal.get("interval_days"), "$.identity.renewal.interval_days");
            integer(renewal.get("warning_minutes"), "$.identity.renewal.warning_minutes");
            integer(renewal.get("next_renewal_at_epoch_millis"),
                    "$.identity.renewal.next_renewal_at_epoch_millis");
        }
    }

    private static void fields(JsonObject object, Set<String> allowed, Set<String> required, String path) {
        for (String name : object.keySet()) {
            if (!allowed.contains(name)) {
                throw new JsonParseException("Unknown field " + path + '.' + name);
            }
        }
        for (String name : required) {
            if (!object.has(name)) {
                throw new JsonParseException("Missing required field " + path + '.' + name);
            }
        }
    }

    private static JsonObject object(JsonElement element, String path) {
        if (element == null || !element.isJsonObject()) {
            throw new JsonParseException(path + " must be a JSON object");
        }
        return element.getAsJsonObject();
    }

    private static JsonArray array(JsonElement element, String path) {
        if (element == null || !element.isJsonArray()) {
            throw new JsonParseException(path + " must be a JSON array");
        }
        return element.getAsJsonArray();
    }

    private static void stringArray(JsonElement element, String path) {
        JsonArray values = array(element, path);
        for (int index = 0; index < values.size(); index++) {
            string(values.get(index), path + '[' + index + ']', false);
        }
    }

    private static void string(JsonElement element, String path, boolean nullable) {
        if (nullable && element != null && element.isJsonNull()) {
            return;
        }
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new JsonParseException(path + " must be a JSON string" + (nullable ? " or null" : ""));
        }
    }

    private static void bool(JsonElement element, String path) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new JsonParseException(path + " must be a JSON boolean");
        }
    }

    private static void number(JsonElement element, String path) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new JsonParseException(path + " must be a JSON number");
        }
    }

    private static void integer(JsonElement element, String path) {
        number(element, path);
        try {
            element.getAsBigDecimal().toBigIntegerExact();
        } catch (ArithmeticException exception) {
            throw new JsonParseException(path + " must be a whole number", exception);
        }
    }

    private static void boundedInteger(JsonElement element, String path, int minimum, int maximum) {
        number(element, path);
        final BigInteger value;
        try {
            value = element.getAsBigDecimal().toBigIntegerExact();
        } catch (ArithmeticException exception) {
            throw new JsonParseException(path + " must be a whole number", exception);
        }
        if (value.compareTo(BigInteger.valueOf(minimum)) < 0
                || value.compareTo(BigInteger.valueOf(maximum)) > 0) {
            throw new JsonParseException(path + " must be between " + minimum + " and " + maximum);
        }
    }

    private static void optionalInteger(JsonObject object, String name, String parentPath) {
        if (object.has(name) && !object.get(name).isJsonNull()) {
            integer(object.get(name), parentPath + '.' + name);
        }
    }
}
