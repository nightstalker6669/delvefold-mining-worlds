package com.nightsta69.delvefold.command;

import com.nightsta69.delvefold.config.model.OreBandPlacement;
import com.nightsta69.delvefold.config.model.OreRule;
import com.nightsta69.delvefold.config.model.OreTarget;
import com.nightsta69.delvefold.config.model.ProvinceSettings;
import com.nightsta69.delvefold.config.model.SpawnBand;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies the immutable ore-profile transformations requested by command handlers.
 *
 * <p>This collaborator deliberately knows nothing about Brigadier, permissions, configuration revisions, audit actors,
 * or translated feedback. {@link DelvefoldCommands} retains those adapter responsibilities and calls these functions
 * only from inside its existing compare-and-swap mutation boundary.
 */
final class OreRuleEdits {
    private static final int RESTORED_VEIN_SIZE = 8;
    private static final double RESTORED_ATTEMPTS_PER_CHUNK = 8.0D;

    private OreRuleEdits() {}

    /**
     * Appends one exact-block output target while retaining the rule's other immutable fields.
     *
     * @param rule rule being edited
     * @param block exact output block registry ID
     * @param replaceTag host block-tag ID, optionally prefixed with {@code #}
     * @param weight relative output weight
     * @return copied rule with the target appended in command order
     */
    static OreRule addExactTarget(OreRule rule, String block, String replaceTag, int weight) {
        List<OreTarget> targets = new ArrayList<>(rule.targets());
        targets.add(OreTarget.of(block, stripHash(replaceTag), weight));
        return withTargets(rule, targets);
    }

    /**
     * Removes every exact target naming the supplied block ID.
     *
     * @param rule rule being edited
     * @param block exact output block registry ID
     * @return copied rule without matching exact targets
     */
    static OreRule removeExactTargets(OreRule rule, String block) {
        return withTargets(
                rule,
                rule.targets().stream()
                        .filter(target -> !target.block().equals(block))
                        .toList());
    }

    /**
     * Appends one output-tag target while retaining the rule's other immutable fields.
     *
     * @param rule rule being edited
     * @param blockTag output block-tag ID, optionally prefixed with {@code #}
     * @param replaceTag host block-tag ID
     * @param weight relative output weight
     * @return copied rule with the target appended in command order
     */
    static OreRule addTagTarget(OreRule rule, String blockTag, String replaceTag, int weight) {
        List<OreTarget> targets = new ArrayList<>(rule.targets());
        targets.add(OreTarget.ofTag(blockTag, replaceTag, weight));
        return withTargets(rule, targets);
    }

    /**
     * Replaces the weight of the unique exact or tag-driven target selected by its source ID.
     *
     * @param rule rule being edited
     * @param sourceId exact block ID or output-tag ID without a leading {@code #}
     * @param tagDriven whether {@code sourceId} selects an output tag
     * @param weight replacement relative output weight
     * @return copied rule containing the replacement weight
     * @throws IllegalArgumentException if no target or more than one target matches the source
     */
    static OreRule replaceTargetWeight(OreRule rule, String sourceId, boolean tagDriven, int weight) {
        long matches = rule.targets().stream()
                .filter(target -> matchesTargetSource(target, sourceId, tagDriven))
                .count();
        if (matches == 0) {
            throw new IllegalArgumentException(
                    "Unknown " + (tagDriven ? "output tag: #" : "target block: ") + sourceId);
        }
        if (matches > 1) {
            throw new IllegalArgumentException("Ambiguous " + (tagDriven ? "output tag: #" : "target block: ")
                    + sourceId + "; multiple targets use that source. Edit the specific target in canonical JSON.");
        }
        List<OreTarget> targets = rule.targets().stream()
                .map(target -> matchesTargetSource(target, sourceId, tagDriven) ? target.withWeight(weight) : target)
                .toList();
        return withTargets(rule, targets);
    }

    /**
     * Removes every tag-driven target naming the supplied output-tag ID.
     *
     * @param rule rule being edited
     * @param blockTag output block-tag ID without a leading {@code #}
     * @return copied rule without matching tag-driven targets
     */
    static OreRule removeTagTargets(OreRule rule, String blockTag) {
        return withTargets(
                rule,
                rule.targets().stream()
                        .filter(target -> !target.blockTag().equals(blockTag))
                        .toList());
    }

    /**
     * Appends a renamed classic-vein copy of a rarity template band.
     *
     * @param rule rule being edited
     * @param bandId command-selected band ID
     * @param template rarity template supplying all placement values
     * @return copied rule with the renamed template appended
     */
    static OreRule addBand(OreRule rule, String bandId, SpawnBand template) {
        SpawnBand band = new SpawnBand(
                bandId,
                template.veinSize(),
                template.attemptsPerChunk(),
                template.distribution(),
                template.minY(),
                template.maxY(),
                template.peakY(),
                template.plateauMinY(),
                template.plateauMaxY(),
                template.discardOnAirExposure());
        List<SpawnBand> bands = new ArrayList<>(rule.bands());
        bands.add(band);
        return rule.withBands(bands);
    }

    /**
     * Removes every placement band naming the supplied ID.
     *
     * @param rule rule being edited
     * @param bandId band ID selected by the command
     * @return copied rule without matching bands
     */
    static OreRule removeBands(OreRule rule, String bandId) {
        return rule.withBands(
                rule.bands().stream().filter(band -> !band.id().equals(bandId)).toList());
    }

    /**
     * Replaces one numeric field on every band matching the supplied ID.
     *
     * @param rule rule being edited
     * @param bandId band ID selected by the command
     * @param field stable command field name
     * @param value replacement value
     * @return copied rule containing the edited bands
     * @throws IllegalArgumentException if the field is unknown or an integer field receives a fractional value
     */
    static OreRule setBandField(OreRule rule, String bandId, String field, double value) {
        return rule.withBands(rule.bands().stream()
                .map(band -> band.id().equals(bandId) ? withBandField(band, field, value) : band)
                .toList());
    }

    /**
     * Changes every matching band's placement algorithm using the command's compatibility defaults.
     *
     * @param rule rule being edited
     * @param bandId band ID selected by the command
     * @param placement replacement placement algorithm
     * @return copied rule containing the edited bands
     */
    static OreRule setBandPlacement(OreRule rule, String bandId, OreBandPlacement placement) {
        return rule.withBands(rule.bands().stream()
                .map(band -> band.id().equals(bandId) ? withBandPlacement(band, placement) : band)
                .toList());
    }

    /**
     * Replaces one province field on every band matching the supplied ID.
     *
     * @param rule rule being edited
     * @param bandId band ID selected by the command
     * @param field stable command field name
     * @param value replacement value
     * @return copied rule containing the edited bands
     * @throws IllegalArgumentException if a matching band is not a province, the field is unknown, or an integer field
     *     receives a fractional value
     */
    static OreRule setProvinceField(OreRule rule, String bandId, String field, double value) {
        return rule.withBands(rule.bands().stream()
                .map(band -> band.id().equals(bandId) ? withProvinceField(band, field, value) : band)
                .toList());
    }

    private static OreRule withTargets(OreRule rule, List<OreTarget> targets) {
        return new OreRule(
                rule.id(), rule.enabled(), rule.required(), rule.terrainModes(), targets, rule.biomes(), rule.bands());
    }

    private static boolean matchesTargetSource(OreTarget target, String sourceId, boolean tagDriven) {
        return tagDriven
                ? target.blockTag().equals(sourceId)
                : !target.tagDriven() && target.block().equals(sourceId);
    }

    private static SpawnBand withBandPlacement(SpawnBand band, OreBandPlacement placement) {
        if (placement == OreBandPlacement.PROVINCE) {
            ProvinceSettings configuredProvince = band.province();
            return new SpawnBand(
                    band.id(),
                    1,
                    0.0D,
                    band.distribution(),
                    band.minY(),
                    band.maxY(),
                    band.peakY(),
                    band.plateauMinY(),
                    band.plateauMaxY(),
                    band.discardOnAirExposure(),
                    OreBandPlacement.PROVINCE,
                    configuredProvince == null ? ProvinceSettings.defaults() : configuredProvince);
        }
        int veinSize = band.placement() == OreBandPlacement.VEIN ? band.veinSize() : RESTORED_VEIN_SIZE;
        double attempts =
                band.placement() == OreBandPlacement.VEIN ? band.attemptsPerChunk() : RESTORED_ATTEMPTS_PER_CHUNK;
        return new SpawnBand(
                band.id(),
                veinSize,
                attempts,
                band.distribution(),
                band.minY(),
                band.maxY(),
                band.peakY(),
                band.plateauMinY(),
                band.plateauMaxY(),
                band.discardOnAirExposure(),
                OreBandPlacement.VEIN,
                null);
    }

    private static SpawnBand withProvinceField(SpawnBand band, String field, double value) {
        if (band.placement() != OreBandPlacement.PROVINCE) {
            throw new IllegalArgumentException(
                    "Band " + band.id() + " is not a province; set its placement to province first");
        }
        ProvinceSettings configuredProvince = band.province();
        ProvinceSettings current = configuredProvince == null ? ProvinceSettings.defaults() : configuredProvince;
        int regionSize = current.regionSize();
        int radius = current.radius();
        int thickness = current.verticalThickness();
        double density = current.density();
        int workCap = current.perChunkWorkCap();
        switch (field) {
            case "region_size" -> regionSize = requireWhole(value, field);
            case "radius" -> radius = requireWhole(value, field);
            case "vertical_thickness" -> thickness = requireWhole(value, field);
            case "density" -> density = value;
            case "work_cap" -> workCap = requireWhole(value, field);
            default -> throw new IllegalArgumentException("Unknown province field: " + field);
        }
        return new SpawnBand(
                band.id(),
                1,
                0.0D,
                band.distribution(),
                band.minY(),
                band.maxY(),
                band.peakY(),
                band.plateauMinY(),
                band.plateauMaxY(),
                band.discardOnAirExposure(),
                OreBandPlacement.PROVINCE,
                new ProvinceSettings(regionSize, radius, thickness, density, workCap));
    }

    private static SpawnBand withBandField(SpawnBand band, String field, double value) {
        int vein = band.veinSize();
        double attempts = band.attemptsPerChunk();
        int min = band.minY();
        int max = band.maxY();
        Integer peak = band.peakY();
        Integer plateauMin = band.plateauMinY();
        Integer plateauMax = band.plateauMaxY();
        double discard = band.discardOnAirExposure();
        switch (field) {
            case "vein_size" -> vein = requireWhole(value, field);
            case "attempts" -> attempts = value;
            case "min_y" -> min = requireWhole(value, field);
            case "max_y" -> max = requireWhole(value, field);
            case "peak_y" -> peak = requireWhole(value, field);
            case "plateau_min_y" -> plateauMin = requireWhole(value, field);
            case "plateau_max_y" -> plateauMax = requireWhole(value, field);
            case "discard" -> discard = value;
            default -> throw new IllegalArgumentException("Unknown band field: " + field);
        }
        return new SpawnBand(
                band.id(),
                vein,
                attempts,
                band.distribution(),
                min,
                max,
                peak,
                plateauMin,
                plateauMax,
                discard,
                band.placement(),
                band.province());
    }

    private static int requireWhole(double value, String field) {
        if (!Double.isFinite(value) || value != Math.rint(value)) {
            throw new IllegalArgumentException(field + " requires a whole number");
        }
        return (int) value;
    }

    private static String stripHash(String value) {
        return value.startsWith("#") ? value.substring(1) : value;
    }
}
