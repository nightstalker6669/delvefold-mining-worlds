package com.nightsta69.delvefold.client.gui;

import com.nightsta69.delvefold.config.analysis.OreDistributionAnalysis;
import com.nightsta69.delvefold.config.model.OreBandPlacement;
import com.nightsta69.delvefold.config.model.ProvinceSettings;
import com.nightsta69.delvefold.config.model.SpawnBand;
import com.nightsta69.delvefold.network.model.AdminSnapshot;

/**
 * Immutable presentation input calculated when a wizard band changes instead of once per rendered frame.
 *
 * <p>The model deliberately retains the complete deterministic distribution summary. Pixel sampling remains a render
 * concern because it depends on the current card width, while height analysis and its hundreds of temporary samples do
 * not.
 */
sealed interface OreBandPreviewModel permits OreBandPreviewModel.Province, OreBandPreviewModel.Vein {
    /** Builds the presentation model for one immutable network draft. */
    static OreBandPreviewModel from(AdminSnapshot.OreBandDraft draft) {
        if (draft.placement() == OreBandPlacement.PROVINCE) {
            ProvinceSettings settings = draft.province() == null ? ProvinceSettings.defaults() : draft.province();
            return new Province(settings, draft.minY(), draft.maxY());
        }
        SpawnBand band = new SpawnBand(
                draft.id(),
                draft.veinSize(),
                draft.attemptsPerChunk(),
                draft.distribution(),
                draft.minY(),
                draft.maxY(),
                draft.peakY(),
                draft.plateauMinY(),
                draft.plateauMaxY(),
                draft.discardOnAirExposure());
        return new Vein(OreDistributionAnalysis.analyze(band), draft.minY(), draft.maxY());
    }

    /** Cached regional-province geometry and visible height bounds. */
    record Province(ProvinceSettings settings, int minY, int maxY) implements OreBandPreviewModel {}

    /** Cached vertical-distribution analysis and visible height bounds. */
    record Vein(OreDistributionAnalysis.Summary analysis, int minY, int maxY) implements OreBandPreviewModel {}
}
