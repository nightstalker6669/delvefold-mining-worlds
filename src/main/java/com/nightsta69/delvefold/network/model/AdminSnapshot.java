package com.nightsta69.delvefold.network.model;

import com.nightsta69.delvefold.config.model.GameplayPreset;
import com.nightsta69.delvefold.config.model.GameplaySettings;
import com.nightsta69.delvefold.config.model.HeightDistribution;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.network.ProtocolLimits;
import java.util.List;

/**
 * Immutable, bounded server snapshot used to render the administration GUI.
 * The server remains the source of truth; clients never mutate this object in
 * place and every write includes {@link #revision()} for stale-write checks.
 */
public record AdminSnapshot(
        long oreRevision,
        long settingsRevision,
        boolean backendReady,
        boolean initialized,
        TerrainMode terrainMode,
        OrePreset orePreset,
        GameplaySettings gameplay,
        String portalStatus,
        String worldStatus,
        boolean resetPending,
        List<String> diagnostics,
        int oreRuleTotal,
        int orePage,
        List<OreRuleDraft> oreRules) {

    public AdminSnapshot {
        oreRevision = Math.max(0L, oreRevision);
        settingsRevision = Math.max(0L, settingsRevision);
        terrainMode = terrainMode == null ? TerrainMode.FLAT : terrainMode;
        orePreset = orePreset == null ? OrePreset.VANILLA_BALANCED : orePreset;
        gameplay = gameplay == null ? GameplaySettings.fromPreset(GameplayPreset.SAFE) : gameplay;
        portalStatus = clean(portalStatus, "Portal is not available yet.");
        worldStatus = clean(worldStatus, initialized ? "Mining world ready." : "Mining world is not initialized.");
        diagnostics = limitedStrings(diagnostics, ProtocolLimits.MAX_DIAGNOSTICS, ProtocolLimits.MESSAGE_LENGTH);
        oreRules = limitedCopy(oreRules, ProtocolLimits.MAX_ORE_RULES_PER_PAGE);
        oreRuleTotal = Math.max(oreRules.size(), Math.min(oreRuleTotal, ProtocolLimits.MAX_ORE_RULES));
        orePage = Math.min(ProtocolLimits.MAX_ORE_RULES, Math.max(0, orePage));
    }

    public AdminSnapshot(
            long oreRevision,
            long settingsRevision,
            boolean backendReady,
            boolean initialized,
            TerrainMode terrainMode,
            OrePreset orePreset,
            GameplaySettings gameplay,
            String portalStatus,
            String worldStatus,
            boolean resetPending,
            List<String> diagnostics,
            List<OreRuleDraft> oreRules) {
        this(oreRevision, settingsRevision, backendReady, initialized, terrainMode, orePreset, gameplay,
                portalStatus, worldStatus, resetPending, diagnostics,
                oreRules == null ? 0 : oreRules.size(), 0, oreRules);
    }

    public static AdminSnapshot unavailable() {
        return new AdminSnapshot(
                0L,
                0L,
                false,
                false,
                TerrainMode.FLAT,
                OrePreset.VANILLA_BALANCED,
                GameplaySettings.fromPreset(GameplayPreset.SAFE),
                "Portal disabled until the administration backend is installed.",
                "Administration backend is not installed.",
                false,
                List.of("Delvefold GUI networking is active, but no DelvefoldAdminService has been installed."),
                0,
                0,
                List.of());
    }

    /** A compact display-only revision; writes use the domain-specific values. */
    public long revision() {
        return Math.max(this.oreRevision, this.settingsRevision);
    }

    private static String clean(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.length() > ProtocolLimits.MESSAGE_LENGTH
                ? value.substring(0, ProtocolLimits.MESSAGE_LENGTH)
                : value;
    }

    private static <T> List<T> limitedCopy(List<T> values, int maximum) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return List.copyOf(values.subList(0, Math.min(values.size(), maximum)));
    }

    private static List<String> limitedStrings(List<String> values, int maximumCount, int maximumLength) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .limit(maximumCount)
                .map(value -> {
                    String safe = value == null ? "" : value;
                    return safe.length() > maximumLength ? safe.substring(0, maximumLength) : safe;
                })
                .toList();
    }

    public record OreRuleDraft(
            String id,
            boolean enabled,
            boolean required,
            String primaryBlockId,
            List<OreVariantDraft> variants,
            List<TerrainMode> terrainModes,
            List<OreBandDraft> bands) {

        public OreRuleDraft {
            id = cleanId(id, "new_ore");
            primaryBlockId = cleanId(primaryBlockId, "minecraft:iron_ore");
            variants = limitedCopy(variants, ProtocolLimits.MAX_VARIANTS);
            terrainModes = limitedCopy(terrainModes, ProtocolLimits.MAX_TERRAIN_MODES);
            bands = limitedCopy(bands, ProtocolLimits.MAX_BANDS);
        }

        public static OreRuleDraft createDefault(String blockId, String replaceTag) {
            String normalizedBlock = cleanId(blockId, "minecraft:iron_ore");
            String path = normalizedBlock.substring(normalizedBlock.indexOf(':') + 1);
            String ruleId = normalizedBlock.substring(0, normalizedBlock.indexOf(':')) + "." + path;
            return new OreRuleDraft(
                    ruleId,
                    true,
                    false,
                    normalizedBlock,
                    List.of(new OreVariantDraft(normalizedBlock, replaceTag)),
                    List.of(TerrainMode.values()),
                    List.of(OreBandDraft.defaultBand()));
        }

        public OreRuleDraft withEnabled(boolean value) {
            return new OreRuleDraft(id, value, required, primaryBlockId, variants, terrainModes, bands);
        }
    }

    public record OreVariantDraft(String blockId, String replaceTag) {
        public OreVariantDraft {
            blockId = cleanId(blockId, "minecraft:iron_ore");
            replaceTag = cleanId(replaceTag, "minecraft:stone_ore_replaceables");
        }
    }

    public record OreBandDraft(
            String id,
            int veinSize,
            double attemptsPerChunk,
            HeightDistribution distribution,
            int minY,
            int maxY,
            int peakY,
            int plateauMinY,
            int plateauMaxY,
            double discardOnAirExposure) {

        public OreBandDraft {
            id = cleanId(id, "main");
            distribution = distribution == null ? HeightDistribution.UNIFORM : distribution;
        }

        public static OreBandDraft defaultBand() {
            return new OreBandDraft("main", 8, 8.0D, HeightDistribution.UNIFORM,
                    -64, 64, 0, -16, 16, 0.0D);
        }
    }

    private static String cleanId(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.length() > ProtocolLimits.ID_LENGTH
                ? trimmed.substring(0, ProtocolLimits.ID_LENGTH)
                : trimmed;
    }
}
