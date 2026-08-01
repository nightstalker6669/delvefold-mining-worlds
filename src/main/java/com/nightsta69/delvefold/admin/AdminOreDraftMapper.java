package com.nightsta69.delvefold.admin;

import com.nightsta69.delvefold.config.model.BiomeFilter;
import com.nightsta69.delvefold.config.model.HeightDistribution;
import com.nightsta69.delvefold.config.model.OreRule;
import com.nightsta69.delvefold.config.model.OreTarget;
import com.nightsta69.delvefold.config.model.SpawnBand;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.network.model.AdminSnapshot;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Bidirectional mapping between bounded administration drafts and canonical ore-rule models. */
final class AdminOreDraftMapper {
    private AdminOreDraftMapper() {}

    static OreRule fromDraft(AdminSnapshot.OreRuleDraft draft) {
        List<OreTarget> targets = draft.variants().stream()
                .map(variant -> new OreTarget(
                        variant.blockId(),
                        variant.blockTag(),
                        variant.state(),
                        stripHash(variant.replaceTag()),
                        variant.weight()))
                .toList();
        List<SpawnBand> bands =
                draft.bands().stream().map(AdminOreDraftMapper::bandFromDraft).toList();
        Set<TerrainMode> terrainModes = Set.copyOf(draft.terrainModes());
        return new OreRule(
                draft.id(),
                draft.enabled(),
                draft.required(),
                terrainModes,
                targets,
                new BiomeFilter(draft.biomeIncludes(), draft.biomeExcludes()),
                bands);
    }

    static AdminSnapshot.OreRuleDraft toDraft(OreRule rule) {
        String primary = rule.targets().stream()
                .map(OreTarget::block)
                .filter(block -> !block.isBlank())
                .findFirst()
                .orElse("minecraft:air");
        return new AdminSnapshot.OreRuleDraft(
                rule.id(),
                rule.enabled(),
                rule.required(),
                primary,
                rule.targets().stream()
                        .map(target -> new AdminSnapshot.OreVariantDraft(
                                target.block(),
                                target.blockTag(),
                                target.replaceTag(),
                                target.state(),
                                target.weight()))
                        .toList(),
                List.copyOf(rule.terrainModes()),
                rule.biomes().include(),
                rule.biomes().exclude(),
                rule.bands().stream().map(AdminOreDraftMapper::bandToDraft).toList());
    }

    private static SpawnBand bandFromDraft(AdminSnapshot.OreBandDraft draft) {
        Integer peak = draft.distribution() == HeightDistribution.TRIANGLE ? draft.peakY() : null;
        Integer plateauMin = draft.distribution() == HeightDistribution.TRAPEZOID ? draft.plateauMinY() : null;
        Integer plateauMax = draft.distribution() == HeightDistribution.TRAPEZOID ? draft.plateauMaxY() : null;
        return new SpawnBand(
                draft.id(),
                draft.veinSize(),
                draft.attemptsPerChunk(),
                draft.distribution(),
                draft.minY(),
                draft.maxY(),
                peak,
                plateauMin,
                plateauMax,
                draft.discardOnAirExposure(),
                draft.placement(),
                draft.province());
    }

    private static AdminSnapshot.OreBandDraft bandToDraft(SpawnBand band) {
        Integer peak = band.peakY();
        Integer plateauMin = band.plateauMinY();
        Integer plateauMax = band.plateauMaxY();
        return new AdminSnapshot.OreBandDraft(
                band.id(),
                band.veinSize(),
                band.attemptsPerChunk(),
                band.distribution(),
                band.minY(),
                band.maxY(),
                peak == null ? 0 : peak,
                plateauMin == null ? band.minY() : plateauMin,
                plateauMax == null ? band.maxY() : plateauMax,
                band.discardOnAirExposure(),
                band.placement(),
                band.province());
    }

    private static String stripHash(@Nullable String value) {
        return value == null ? "" : value.startsWith("#") ? value.substring(1) : value;
    }
}
