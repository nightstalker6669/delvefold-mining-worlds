package com.nightsta69.delvefold.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Verifies importer row capacity and feedback-line reservation independently of Minecraft widgets. */
class OreImportPanelLayoutTest {
    @Test
    void truncatedAcceptanceScalePreviewReservesFeedbackAndDisplaysNoPartialRow() {
        OreImportPanelLayout at427 = OreImportPanelLayout.preview(64, 186, true);
        OreImportPanelLayout at320 = OreImportPanelLayout.preview(64, 186, true);

        assertEquals(87, at427.controlsY());
        assertEquals(157, at427.rowsTop());
        assertEquals(15, at427.availableRowsHeight());
        assertEquals(0, at427.completeRows(OreImportScreenState.PREVIEW_ROW_HEIGHT));
        assertEquals(0, at320.completeRows(OreImportScreenState.PREVIEW_ROW_HEIGHT));
    }

    @Test
    void ordinaryPreviewAndScanRetainTheirExistingSingleRowsAtAcceptanceHeight() {
        OreImportPanelLayout preview = OreImportPanelLayout.preview(64, 186, false);
        OreImportPanelLayout scan = OreImportPanelLayout.scan(64, 186, false);

        assertEquals(1, preview.completeRows(OreImportScreenState.PREVIEW_ROW_HEIGHT));
        assertEquals(1, scan.completeRows(OreImportScreenState.SCAN_ROW_HEIGHT));
    }
}
