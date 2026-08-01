package com.nightsta69.delvefold.client.gui;

import com.nightsta69.delvefold.config.model.HeightDistribution;
import com.nightsta69.delvefold.config.model.OreBandPlacement;
import com.nightsta69.delvefold.config.model.ProvinceSettings;
import com.nightsta69.delvefold.network.ProtocolLimits;
import com.nightsta69.delvefold.network.model.AdminSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Pure parsing and validation rules shared by ore-wizard widgets and unit tests. */
final class OreRuleWizardValidation {
    private OreRuleWizardValidation() {}

    /**
     * Parses a target weight with the same protocol range accepted by ore target drafts.
     *
     * @param text raw weight text, or {@code null}
     * @return parsed weight
     * @throws NumberFormatException if the text is not an integer or is outside the supported range
     */
    static int parseWeight(@Nullable String text) {
        int weight = Integer.parseInt(text == null ? "" : text.trim());
        if (weight < AdminSnapshot.OreVariantDraft.MIN_WEIGHT || weight > AdminSnapshot.OreVariantDraft.MAX_WEIGHT) {
            throw new NumberFormatException("out of range");
        }
        return weight;
    }

    /**
     * Parses comma-separated block-state assignments while retaining insertion order.
     *
     * @param text raw {@code property=value} assignments
     * @return mutable insertion-ordered property map
     * @throws LocalizedValidationException when syntax, length, or uniqueness is invalid
     */
    static Map<String, String> parseState(@Nullable String text) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return result;
        }
        for (String pair : text.split(",", 0)) {
            String[] parts = pair.trim().split("=", 2);
            if (parts.length != 2
                    || !parts[0].matches("[a-z0-9_]+")
                    || parts[1].isBlank()
                    || parts[1].length() > ProtocolLimits.ID_LENGTH) {
                throw validation("screen.delvefold.ore_wizard.validation.state_format");
            }
            if (result.putIfAbsent(parts[0], parts[1].trim()) != null) {
                throw validation("screen.delvefold.ore_wizard.validation.state_duplicate", parts[0]);
            }
        }
        return result;
    }

    /**
     * Parses comma- or whitespace-separated biome IDs and tags, deduplicating first occurrences.
     *
     * @param text raw selector text
     * @return immutable empty list for blank input, otherwise insertion-ordered selectors
     * @throws LocalizedValidationException when a selector is not a resource identifier
     */
    static List<String> parseSelectors(@Nullable String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : text.split("[,\\s]+", 0)) {
            if (value.isBlank()) {
                continue;
            }
            String id = value.startsWith("#") ? value.substring(1) : value;
            if (!ResourceIdentifierText.isValid(id)) {
                throw validation("screen.delvefold.ore_wizard.validation.biome_selector");
            }
            result.add(value);
        }
        return new ArrayList<>(result);
    }

    /**
     * Parses all advanced target filters and enforces their independent protocol collection bounds.
     *
     * @param stateText raw state-property assignments
     * @param includeText raw biome inclusions
     * @param excludeText raw biome exclusions
     * @return immutable parsed filters
     * @throws LocalizedValidationException when parsing fails or a collection exceeds its protocol limit
     */
    static FilterValues parseFilters(
            @Nullable String stateText, @Nullable String includeText, @Nullable String excludeText) {
        Map<String, String> state = parseState(stateText);
        List<String> includes = parseSelectors(includeText);
        List<String> excludes = parseSelectors(excludeText);
        if (state.size() > ProtocolLimits.MAX_STATE_PROPERTIES
                || includes.size() > ProtocolLimits.MAX_BIOME_SELECTORS_PER_LIST
                || excludes.size() > ProtocolLimits.MAX_BIOME_SELECTORS_PER_LIST) {
            throw validation("screen.delvefold.ore_wizard.validation.filter_limit");
        }
        return new FilterValues(state, includes, excludes);
    }

    /**
     * Parses every band field and applies distribution- and placement-specific semantic validation.
     *
     * <p>Province bands intentionally still require numeric vein and attempt text, matching the established widget
     * behavior, but ignore those values after parsing and store province-safe constants.
     *
     * @param raw raw text snapshot
     * @param current committed band supplying distribution, placement, and province settings
     * @return accepted draft or a localized validation result
     */
    static BandParseResult parseBand(OreRuleWizardBandInputs.Values raw, AdminSnapshot.OreBandDraft current) {
        final String id;
        final int veinSize;
        final double attempts;
        final int minY;
        final int maxY;
        final int peakY;
        final int plateauMin;
        final int plateauMax;
        final double airDiscard;
        try {
            id = raw.bandId().trim();
            veinSize = Integer.parseInt(raw.veinSize().trim());
            attempts = Double.parseDouble(raw.attempts().trim());
            minY = Integer.parseInt(raw.minY().trim());
            maxY = Integer.parseInt(raw.maxY().trim());
            peakY = Integer.parseInt(raw.peakY().trim());
            plateauMin = Integer.parseInt(raw.plateauMin().trim());
            plateauMax = Integer.parseInt(raw.plateauMax().trim());
            airDiscard = Double.parseDouble(raw.airDiscard().trim());
        } catch (NumberFormatException exception) {
            return BandParseResult.rejected("screen.delvefold.ore_wizard.validation.number");
        }

        HeightDistribution distribution = current.distribution();
        OreBandPlacement placement = current.placement();
        boolean shapeValuesValid =
                switch (distribution) {
                    case UNIFORM -> true;
                    case TRIANGLE -> peakY >= minY && peakY <= maxY;
                    case TRAPEZOID -> plateauMin >= minY && plateauMax <= maxY && plateauMin <= plateauMax;
                };
        boolean veinValuesValid = placement == OreBandPlacement.PROVINCE
                || (veinSize >= 1
                        && veinSize <= 64
                        && Double.isFinite(attempts)
                        && attempts >= 0.0D
                        && attempts <= 256.0D);
        if (!id.matches("[a-z0-9_.-]{1,128}")
                || !veinValuesValid
                || minY < -64
                || maxY > 320
                || minY > maxY
                || !shapeValuesValid
                || !Double.isFinite(airDiscard)
                || airDiscard < 0.0D
                || airDiscard > 1.0D) {
            return BandParseResult.rejected("screen.delvefold.ore_wizard.validation.band_values");
        }

        return BandParseResult.accepted(new AdminSnapshot.OreBandDraft(
                id,
                placement == OreBandPlacement.PROVINCE ? 1 : veinSize,
                placement == OreBandPlacement.PROVINCE ? 0.0D : attempts,
                distribution,
                minY,
                maxY,
                peakY,
                plateauMin,
                plateauMax,
                airDiscard,
                placement,
                placement == OreBandPlacement.PROVINCE ? provinceOrDefault(current.province()) : null));
    }

    /**
     * Supplies the established defaults for a missing province configuration.
     *
     * @param province optional committed settings
     * @return supplied settings or defaults
     */
    static ProvinceSettings provinceOrDefault(@Nullable ProvinceSettings province) {
        return province == null ? ProvinceSettings.defaults() : province;
    }

    private static LocalizedValidationException validation(String key, String... arguments) {
        return new LocalizedValidationException(key, arguments);
    }

    /** Exception carrying translation inputs without coupling the pure validator to Minecraft components. */
    static final class LocalizedValidationException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        private final String translationKey;
        private final String[] arguments;

        private LocalizedValidationException(String translationKey, String[] arguments) {
            super(translationKey);
            this.translationKey = translationKey;
            this.arguments = arguments.clone();
        }

        /**
         * Returns the untranslated language key.
         *
         * @return Delvefold validation translation key
         */
        String translationKey() {
            return this.translationKey;
        }

        /**
         * Returns immutable translation arguments.
         *
         * @return arguments retained in declaration order
         */
        List<String> arguments() {
            return List.of(this.arguments);
        }
    }

    /**
     * Result of parsing raw band text.
     *
     * @param band accepted band, or {@code null} on rejection
     * @param messageKey empty on acceptance, otherwise a localized validation key
     */
    record BandParseResult(AdminSnapshot.@Nullable OreBandDraft band, String messageKey) {
        private static BandParseResult accepted(AdminSnapshot.OreBandDraft band) {
            return new BandParseResult(band, "");
        }

        private static BandParseResult rejected(String key) {
            return new BandParseResult(null, key);
        }

        /**
         * Indicates whether parsing produced a band.
         *
         * @return {@code true} when {@link #band()} is non-null
         */
        boolean accepted() {
            return this.band != null;
        }
    }

    /**
     * Immutable parsed advanced-filter values.
     *
     * @param state insertion-ordered state properties
     * @param biomeIncludes insertion-ordered biome inclusions
     * @param biomeExcludes insertion-ordered biome exclusions
     */
    record FilterValues(Map<String, String> state, List<String> biomeIncludes, List<String> biomeExcludes) {
        FilterValues {
            state = Collections.unmodifiableMap(new LinkedHashMap<>(state));
            biomeIncludes = List.copyOf(biomeIncludes);
            biomeExcludes = List.copyOf(biomeExcludes);
        }
    }
}
