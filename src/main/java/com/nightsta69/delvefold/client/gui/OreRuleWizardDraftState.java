package com.nightsta69.delvefold.client.gui;

import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.network.model.AdminSnapshot;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Local, non-authoritative ore-rule draft owned by one wizard instance.
 *
 * <p>Insertion-ordered collections retain the historical payload order. Committed values are kept separately from raw
 * widget text so reopening a page uses committed values while a server refresh can preserve invalid unsaved text.
 */
final class OreRuleWizardDraftState {
    final String primaryBlockId;
    final LinkedHashMap<String, String> selectedVariants = new LinkedHashMap<>();
    final LinkedHashMap<String, Map<String, String>> variantStates = new LinkedHashMap<>();
    final LinkedHashMap<String, Integer> variantWeights = new LinkedHashMap<>();
    final List<TerrainMode> terrainModes;
    final List<String> biomeIncludes;
    final List<String> biomeExcludes;
    final List<AdminSnapshot.OreBandDraft> bands;
    final boolean unsupportedDuplicateSources;
    final OreRuleWizardBandInputs bandInputs = new OreRuleWizardBandInputs();
    String focusedVariant;
    String ruleId;
    boolean enabled;
    boolean required;
    String rawStateProperties = "";
    String rawWeight = "";
    String rawBiomeIncludes;
    String rawBiomeExcludes;

    /**
     * Creates editable state from an immutable network draft.
     *
     * @param draft source rule draft
     * @param requestedFocusedVariant preferred source selector, or {@code null} to focus the first target
     * @param inheritedDuplicateSources whether an earlier screen already detected duplicate source selectors
     */
    OreRuleWizardDraftState(
            AdminSnapshot.OreRuleDraft draft,
            @Nullable String requestedFocusedVariant,
            boolean inheritedDuplicateSources) {
        this.primaryBlockId = draft.primaryBlockId();
        this.ruleId = draft.id();
        this.enabled = draft.enabled();
        this.required = draft.required();
        boolean duplicateSources = inheritedDuplicateSources;
        for (AdminSnapshot.OreVariantDraft variant : draft.variants()) {
            if (this.selectedVariants.putIfAbsent(variant.sourceId(), variant.replaceTag()) != null) {
                duplicateSources = true;
                continue;
            }
            this.variantStates.put(variant.sourceId(), variant.state());
            this.variantWeights.put(variant.sourceId(), variant.weight());
        }
        this.unsupportedDuplicateSources = duplicateSources;
        if (this.selectedVariants.isEmpty()) {
            this.selectedVariants.put(this.primaryBlockId, inferredHost(this.primaryBlockId));
            this.variantStates.put(this.primaryBlockId, Map.of());
            this.variantWeights.put(this.primaryBlockId, AdminSnapshot.OreVariantDraft.MIN_WEIGHT);
        }
        this.terrainModes = new ArrayList<>(draft.terrainModes());
        if (this.terrainModes.isEmpty()) {
            this.terrainModes.addAll(List.of(TerrainMode.values()));
        }
        this.biomeIncludes = new ArrayList<>(draft.biomeIncludes());
        this.biomeExcludes = new ArrayList<>(draft.biomeExcludes());
        this.bands = new ArrayList<>(draft.bands());
        if (this.bands.isEmpty()) {
            this.bands.add(AdminSnapshot.OreBandDraft.defaultBand());
        }
        this.focusedVariant =
                requestedFocusedVariant != null && this.selectedVariants.containsKey(requestedFocusedVariant)
                        ? requestedFocusedVariant
                        : this.selectedVariants.keySet().iterator().next();
        resetVariantInputs();
        this.rawBiomeIncludes = String.join(", ", this.biomeIncludes);
        this.rawBiomeExcludes = String.join(", ", this.biomeExcludes);
    }

    /**
     * Copies only uncommitted widget text from a previous screen after committed state has been reconstructed.
     *
     * @param source previous wizard state
     */
    void copyRawInputsFrom(OreRuleWizardDraftState source) {
        this.bandInputs.copyFrom(source.bandInputs);
        this.rawStateProperties = source.rawStateProperties;
        this.rawWeight = source.rawWeight;
        this.rawBiomeIncludes = source.rawBiomeIncludes;
        this.rawBiomeExcludes = source.rawBiomeExcludes;
    }

    /** Resets the state and weight text to the committed values for the focused variant. */
    void resetVariantInputs() {
        this.rawStateProperties = formatState(this.variantStates.getOrDefault(this.focusedVariant, Map.of()));
        this.rawWeight = Integer.toString(
                this.variantWeights.getOrDefault(this.focusedVariant, AdminSnapshot.OreVariantDraft.MIN_WEIGHT));
    }

    /**
     * Builds the exact immutable payload draft represented by committed local state.
     *
     * @return normalized rule draft with deterministic variant ordering
     */
    AdminSnapshot.OreRuleDraft toDraft() {
        List<AdminSnapshot.OreVariantDraft> variants = this.selectedVariants.entrySet().stream()
                .map(entry -> new AdminSnapshot.OreVariantDraft(
                        entry.getKey().startsWith("#") ? "" : entry.getKey(),
                        entry.getKey().startsWith("#") ? entry.getKey().substring(1) : "",
                        entry.getValue(),
                        this.variantStates.getOrDefault(entry.getKey(), Map.of()),
                        this.variantWeights.getOrDefault(entry.getKey(), AdminSnapshot.OreVariantDraft.MIN_WEIGHT)))
                .toList();
        return new AdminSnapshot.OreRuleDraft(
                this.ruleId,
                this.enabled,
                this.required,
                this.primaryBlockId,
                variants,
                this.terrainModes,
                this.biomeIncludes,
                this.biomeExcludes,
                this.bands);
    }

    /**
     * Detects source selectors that the editor cannot represent without flattening one variant.
     *
     * @param draft draft to inspect
     * @return whether a source selector occurs more than once
     */
    static boolean hasDuplicateSources(AdminSnapshot.OreRuleDraft draft) {
        Set<String> seen = new HashSet<>();
        for (AdminSnapshot.OreVariantDraft variant : draft.variants()) {
            if (!seen.add(variant.sourceId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Infers the established default replacement host for a block source.
     *
     * @param blockId source block identifier or tag selector
     * @return deepslate replacement tag for deepslate blocks, otherwise stone replacement tag
     */
    static String inferredHost(String blockId) {
        String path = ResourceIdentifierText.path(blockId);
        return path != null && path.startsWith("deepslate_")
                ? "minecraft:deepslate_ore_replaceables"
                : "minecraft:stone_ore_replaceables";
    }

    private static String formatState(Map<String, String> state) {
        return state.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(", "));
    }
}
