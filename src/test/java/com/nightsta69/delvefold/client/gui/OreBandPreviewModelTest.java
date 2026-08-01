package com.nightsta69.delvefold.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.model.HeightDistribution;
import com.nightsta69.delvefold.config.model.OreBandPlacement;
import com.nightsta69.delvefold.config.model.ProvinceSettings;
import com.nightsta69.delvefold.network.model.AdminSnapshot;
import org.junit.jupiter.api.Test;

class OreBandPreviewModelTest {
    @Test
    void veinModelCalculatesTheCompleteDistributionOnce() {
        AdminSnapshot.OreBandDraft draft = new AdminSnapshot.OreBandDraft(
                "main", 8, 4.0D, HeightDistribution.TRIANGLE, -32, 64, 12, -8, 8, 0.25D, OreBandPlacement.VEIN, null);

        OreBandPreviewModel.Vein preview =
                assertInstanceOf(OreBandPreviewModel.Vein.class, OreBandPreviewModel.from(draft));

        assertEquals(-32, preview.minY());
        assertEquals(64, preview.maxY());
        assertEquals(97, preview.analysis().samples().size());
        assertEquals(4.0D, preview.analysis().attemptsPerChunk());
        assertEquals(32.0D, preview.analysis().workUnits());
        assertTrue(preview.analysis().maximumProbability() > 0.0D);
    }

    @Test
    void provinceModelRetainsGeometryAndUsesTheEstablishedMissingValueDefault() {
        AdminSnapshot.OreBandDraft draft = new AdminSnapshot.OreBandDraft(
                "province",
                1,
                0.0D,
                HeightDistribution.UNIFORM,
                -16,
                96,
                0,
                -16,
                96,
                0.0D,
                OreBandPlacement.PROVINCE,
                null);

        OreBandPreviewModel.Province preview =
                assertInstanceOf(OreBandPreviewModel.Province.class, OreBandPreviewModel.from(draft));

        assertEquals(ProvinceSettings.defaults(), preview.settings());
        assertEquals(-16, preview.minY());
        assertEquals(96, preview.maxY());
    }
}
