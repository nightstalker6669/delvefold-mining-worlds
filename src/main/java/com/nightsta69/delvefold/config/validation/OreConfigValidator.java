package com.nightsta69.delvefold.config.validation;

import com.nightsta69.delvefold.config.analysis.OreWorkBudgetAnalysis;
import com.nightsta69.delvefold.config.model.HeightDistribution;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.model.OreBandPlacement;
import com.nightsta69.delvefold.config.model.OreRule;
import com.nightsta69.delvefold.config.model.OreTarget;
import com.nightsta69.delvefold.config.model.ProvinceSettings;
import com.nightsta69.delvefold.config.model.SpawnBand;
import com.nightsta69.delvefold.config.model.TerrainMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class OreConfigValidator {
    public static final int MIN_WORLD_Y = -64;
    public static final int MAX_WORLD_Y = 320;
    public static final int MAX_RULES = 512;
    public static final int MAX_TARGETS_PER_RULE = 16;
    public static final int MAX_BANDS_PER_RULE = 16;
    public static final int MAX_BIOME_SELECTORS = 128;
    public static final int MAX_ID_LENGTH = 128;
    public static final int MIN_TARGET_WEIGHT = OreTarget.MIN_WEIGHT;
    public static final int MAX_TARGET_WEIGHT = OreTarget.MAX_WEIGHT;
    public static final double MAX_ATTEMPTS_PER_CHUNK_PER_TERRAIN = 4096.0D;
    public static final double MAX_ORE_WORK_PER_CHUNK_PER_TERRAIN = 65536.0D;
    public static final int MIN_PROVINCE_REGION_SIZE = 16;
    public static final int MAX_PROVINCE_REGION_SIZE = 8192;
    public static final int MAX_PROVINCE_WORK_PER_CHUNK = 4096;
    private static final Pattern RESOURCE_LOCATION = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern RULE_ID = Pattern.compile("[a-z0-9_.-]+");

    private OreConfigValidator() {
    }

    public static ValidationReport validate(OreProfileDocument document, RegistryLookup registries) {
        List<ConfigIssue> issues = new ArrayList<>();
        if (document == null) {
            return new ValidationReport(List.of(ConfigIssue.error("document.missing", "$", "Ore document is missing")));
        }
        if (document.schemaVersion() != OreProfileDocument.CURRENT_SCHEMA_VERSION) {
            issues.add(ConfigIssue.error(
                    "schema.unsupported",
                    "$.schema_version",
                    "Expected schema version " + OreProfileDocument.CURRENT_SCHEMA_VERSION + " but found " + document.schemaVersion()
            ));
        }
        if (document.revision() < 0) {
            issues.add(ConfigIssue.error("revision.negative", "$.revision", "Revision cannot be negative"));
        }
        if (document.profile().length() > MAX_ID_LENGTH) {
            issues.add(ConfigIssue.error("profile.too_long", "$.profile", "Profile name cannot exceed " + MAX_ID_LENGTH + " characters"));
        }
        if (document.rules().size() > MAX_RULES) {
            issues.add(ConfigIssue.error("rules.too_many", "$.rules", "At most " + MAX_RULES + " ore rules are allowed"));
        }

        Set<String> ruleIds = new HashSet<>();
        for (int ruleIndex = 0; ruleIndex < document.rules().size(); ruleIndex++) {
            OreRule rule = document.rules().get(ruleIndex);
            String path = "$.rules[" + ruleIndex + "]";
            validateRule(rule, path, registries, issues);
            if (!ruleIds.add(rule.id())) {
                issues.add(ConfigIssue.error("rule.duplicate_id", path + ".id", "Duplicate rule id: " + rule.id()));
            }
        }
        validateAggregateGenerationBudget(document, issues);
        return new ValidationReport(issues);
    }

    private static void validateRule(OreRule rule, String path, RegistryLookup registries, List<ConfigIssue> issues) {
        if (rule.id().isBlank() || rule.id().length() > MAX_ID_LENGTH || !RULE_ID.matcher(rule.id()).matches()) {
            issues.add(ConfigIssue.error("rule.invalid_id", path + ".id", "Use lowercase letters, digits, dots, dashes, or underscores"));
        }
        if (rule.terrainModes().isEmpty()) {
            issues.add(ConfigIssue.error("rule.no_terrain", path + ".terrain_modes", "Select at least one terrain mode"));
        }
        if (rule.targets().isEmpty()) {
            issues.add(ConfigIssue.error("rule.no_targets", path + ".targets", "Add at least one ore target"));
        } else if (rule.targets().size() > MAX_TARGETS_PER_RULE) {
            issues.add(ConfigIssue.error("rule.too_many_targets", path + ".targets", "At most " + MAX_TARGETS_PER_RULE + " targets are allowed"));
        }
        if (rule.bands().isEmpty()) {
            issues.add(ConfigIssue.error("rule.no_bands", path + ".bands", "Add at least one spawn band"));
        } else if (rule.bands().size() > MAX_BANDS_PER_RULE) {
            issues.add(ConfigIssue.error("rule.too_many_bands", path + ".bands", "At most " + MAX_BANDS_PER_RULE + " bands are allowed"));
        }
        validateBiomeSelectors(rule.biomes().include(), path + ".biomes.include", issues);
        validateBiomeSelectors(rule.biomes().exclude(), path + ".biomes.exclude", issues);

        Set<String> targetKeys = new HashSet<>();
        for (int i = 0; i < rule.targets().size(); i++) {
            OreTarget target = rule.targets().get(i);
            String targetPath = path + ".targets[" + i + "]";
            boolean exact = !target.block().isBlank();
            boolean tagged = !target.blockTag().isBlank();
            if (target.weight() < MIN_TARGET_WEIGHT || target.weight() > MAX_TARGET_WEIGHT) {
                issues.add(ConfigIssue.error("target.invalid_weight", targetPath + ".weight",
                        "Target weight must be between " + MIN_TARGET_WEIGHT + " and " + MAX_TARGET_WEIGHT));
            }
            if (exact == tagged) {
                issues.add(ConfigIssue.error("target.source", targetPath,
                        "Set exactly one of block or block_tag"));
            } else if (exact) {
                if (target.block().length() > MAX_ID_LENGTH || !isResourceLocation(target.block())) {
                    issues.add(ConfigIssue.error("target.invalid_block", targetPath + ".block", "Invalid block registry id: " + target.block()));
                } else if (!registries.blockExists(target.block())) {
                    ConfigIssue issue = rule.required()
                            ? ConfigIssue.error("target.missing_block", targetPath + ".block", "Required block is not registered: " + target.block())
                            : ConfigIssue.warning("target.missing_block", targetPath + ".block", "Optional block is not installed and will be skipped: " + target.block());
                    issues.add(issue);
                }
            } else if (target.blockTag().length() > MAX_ID_LENGTH || !isResourceLocation(target.blockTag())) {
                issues.add(ConfigIssue.error("target.invalid_block_tag", targetPath + ".block_tag",
                        "Invalid output block tag: " + target.blockTag()));
            } else if (!registries.blockTagExists(target.blockTag())) {
                ConfigIssue issue = rule.required()
                        ? ConfigIssue.error("target.missing_block_tag", targetPath + ".block_tag",
                                "Required output block tag is missing or empty: " + target.blockTag())
                        : ConfigIssue.warning("target.missing_block_tag", targetPath + ".block_tag",
                                "Optional output block tag is missing or empty and will be skipped: " + target.blockTag());
                issues.add(issue);
            }
            if (stripHash(target.replaceTag()).length() > MAX_ID_LENGTH
                    || !isResourceLocation(stripHash(target.replaceTag()))) {
                issues.add(ConfigIssue.error("target.invalid_replace_tag", targetPath + ".replace_tag", "Invalid host block tag: " + target.replaceTag()));
            } else if (!registries.blockTagExists(stripHash(target.replaceTag()))) {
                issues.add(ConfigIssue.error("target.missing_replace_tag", targetPath + ".replace_tag", "Host block tag is missing or empty: " + target.replaceTag()));
            }
            for (Map.Entry<String, String> property : target.state().entrySet()) {
                String propertyPath = targetPath + ".state." + property.getKey();
                if (property.getKey() == null || property.getKey().isBlank() || property.getKey().length() > MAX_ID_LENGTH) {
                    issues.add(ConfigIssue.error("target.invalid_state_property", propertyPath,
                            "Block-state property names must be non-empty and at most " + MAX_ID_LENGTH + " characters"));
                }
                if (property.getValue() == null || property.getValue().length() > MAX_ID_LENGTH) {
                    issues.add(ConfigIssue.error("target.invalid_state_value", propertyPath,
                            "Block-state values must be strings of at most " + MAX_ID_LENGTH + " characters"));
                }
            }
            String targetKey = target.sourceId() + '|' + target.state() + '|' + target.replaceTag();
            if (!targetKeys.add(targetKey)) {
                issues.add(ConfigIssue.warning("target.duplicate", targetPath,
                        "Overlapping target resolves to a block state already contributed for this host"));
            }
        }

        Set<String> bandIds = new HashSet<>();
        for (int i = 0; i < rule.bands().size(); i++) {
            SpawnBand band = rule.bands().get(i);
            String bandPath = path + ".bands[" + i + "]";
            validateBand(band, bandPath, issues);
            if (!bandIds.add(band.id())) {
                issues.add(ConfigIssue.error("band.duplicate_id", bandPath + ".id", "Duplicate band id within rule: " + band.id()));
            }
        }
    }

    private static void validateBand(SpawnBand band, String path, List<ConfigIssue> issues) {
        if (band.id().isBlank() || band.id().length() > MAX_ID_LENGTH || !RULE_ID.matcher(band.id()).matches()) {
            issues.add(ConfigIssue.error("band.invalid_id", path + ".id", "Use lowercase letters, digits, dots, dashes, or underscores"));
        }
        // Province bands retain these canonical schema-2 fields for wire and disk compatibility.
        // Validate them even though province placement does not consume them at generation time.
        if (band.veinSize() < 1 || band.veinSize() > 64) {
            issues.add(ConfigIssue.error("band.invalid_vein_size", path + ".vein_size", "Vein size must be between 1 and 64"));
        }
        if (!Double.isFinite(band.attemptsPerChunk()) || band.attemptsPerChunk() < 0 || band.attemptsPerChunk() > 256) {
            issues.add(ConfigIssue.error("band.invalid_attempts", path + ".attempts_per_chunk", "Attempts must be between 0 and 256"));
        }
        if (band.placement() == OreBandPlacement.VEIN) {
            if (band.province() != null) {
                issues.add(ConfigIssue.error("band.unexpected_province", path + ".province",
                        "Vein bands cannot contain province settings"));
            }
        } else if (band.placement() == OreBandPlacement.PROVINCE) {
            validateProvince(band.province(), path + ".province", issues);
        }
        if (band.minY() < MIN_WORLD_Y || band.maxY() > MAX_WORLD_Y || band.minY() > band.maxY()) {
            issues.add(ConfigIssue.error("band.invalid_height", path, "Height must stay within -64..320 and min_y cannot exceed max_y"));
        }
        if (!Double.isFinite(band.discardOnAirExposure()) || band.discardOnAirExposure() < 0 || band.discardOnAirExposure() > 1) {
            issues.add(ConfigIssue.error("band.invalid_air_discard", path + ".discard_on_air_exposure", "Air-exposure discard must be between 0 and 1"));
        }
        if (band.distribution() == HeightDistribution.TRIANGLE
                && (band.peakY() == null || band.peakY() < band.minY() || band.peakY() > band.maxY())) {
            issues.add(ConfigIssue.error("band.invalid_peak", path + ".peak_y", "Triangle peak must be inside the height range"));
        }
        if (band.distribution() == HeightDistribution.TRAPEZOID) {
            Integer low = band.plateauMinY();
            Integer high = band.plateauMaxY();
            if (low == null || high == null || low < band.minY() || high > band.maxY() || low > high) {
                issues.add(ConfigIssue.error("band.invalid_plateau", path, "Trapezoid plateau must be ordered and inside the height range"));
            }
        }
    }

    private static void validateProvince(
            ProvinceSettings province, String path, List<ConfigIssue> issues) {
        if (province == null) {
            issues.add(ConfigIssue.error("band.missing_province", path,
                    "Province bands require province settings"));
            return;
        }
        if (province.regionSize() < MIN_PROVINCE_REGION_SIZE
                || province.regionSize() > MAX_PROVINCE_REGION_SIZE
                || province.regionSize() % 16 != 0) {
            issues.add(ConfigIssue.error("province.invalid_region_size", path + ".region_size",
                    "Region size must be a multiple of 16 from " + MIN_PROVINCE_REGION_SIZE
                            + " through " + MAX_PROVINCE_REGION_SIZE));
        }
        if (province.radius() < 1 || province.radius() > province.regionSize()) {
            issues.add(ConfigIssue.error("province.invalid_radius", path + ".radius",
                    "Province radius must be positive and cannot exceed region size"));
        }
        int worldHeight = MAX_WORLD_Y - MIN_WORLD_Y + 1;
        if (province.verticalThickness() < 1 || province.verticalThickness() > worldHeight) {
            issues.add(ConfigIssue.error("province.invalid_vertical_thickness", path + ".vertical_thickness",
                    "Province vertical thickness must be between 1 and " + worldHeight));
        }
        if (!Double.isFinite(province.density())
                || province.density() <= 0.0D || province.density() > 1.0D) {
            issues.add(ConfigIssue.error("province.invalid_density", path + ".density",
                    "Province density must be greater than 0 and at most 1"));
        }
        if (province.perChunkWorkCap() < 1
                || province.perChunkWorkCap() > MAX_PROVINCE_WORK_PER_CHUNK) {
            issues.add(ConfigIssue.error("province.invalid_work_cap", path + ".per_chunk_work_cap",
                    "Province per-chunk work cap must be between 1 and "
                            + MAX_PROVINCE_WORK_PER_CHUNK));
        }
    }

    private static void validateBiomeSelectors(List<String> selectors, String path, List<ConfigIssue> issues) {
        if (selectors.size() > MAX_BIOME_SELECTORS) {
            issues.add(ConfigIssue.error("biomes.too_many", path,
                    "At most " + MAX_BIOME_SELECTORS + " biome selectors are allowed"));
        }
        Set<String> unique = new HashSet<>();
        for (int index = 0; index < selectors.size(); index++) {
            String selector = selectors.get(index);
            String selectorPath = path + '[' + index + ']';
            if (selector == null || selector.length() > MAX_ID_LENGTH + 1
                    || !isResourceLocation(stripHash(selector))) {
                issues.add(ConfigIssue.error("biomes.invalid_selector", selectorPath,
                        "Use a biome registry ID or #tag selector"));
            }
            if (!unique.add(selector)) {
                issues.add(ConfigIssue.warning("biomes.duplicate_selector", selectorPath,
                        "Duplicate biome selector: " + selector));
            }
        }
    }

    private static void validateAggregateGenerationBudget(OreProfileDocument document, List<ConfigIssue> issues) {
        OreWorkBudgetAnalysis.ProfileBudget budget = OreWorkBudgetAnalysis.analyze(document);
        for (TerrainMode terrain : TerrainMode.values()) {
            OreWorkBudgetAnalysis.Budget terrainBudget = budget.terrain(terrain);
            if (terrainBudget.attemptsPerChunk() > MAX_ATTEMPTS_PER_CHUNK_PER_TERRAIN) {
                issues.add(ConfigIssue.error("budget.too_many_attempts", "$.rules",
                        "Enabled " + terrain.serializedName() + " rules request "
                                + terrainBudget.attemptsPerChunk()
                                + " ore attempts per chunk; the safety limit is "
                                + MAX_ATTEMPTS_PER_CHUNK_PER_TERRAIN));
            }
            if (terrainBudget.workUnitsPerChunk() > MAX_ORE_WORK_PER_CHUNK_PER_TERRAIN) {
                issues.add(ConfigIssue.error("budget.too_much_work", "$.rules",
                        "Enabled " + terrain.serializedName() + " rules exceed the aggregate ore-work safety budget of "
                                + MAX_ORE_WORK_PER_CHUNK_PER_TERRAIN));
            }
        }
    }

    private static boolean isResourceLocation(String value) {
        return value != null && RESOURCE_LOCATION.matcher(value).matches();
    }

    private static String stripHash(String value) {
        return value != null && value.startsWith("#") ? value.substring(1) : value;
    }
}
