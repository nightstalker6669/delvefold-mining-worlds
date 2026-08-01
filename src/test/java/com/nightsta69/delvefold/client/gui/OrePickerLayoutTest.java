package com.nightsta69.delvefold.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Verifies picker rows and paging chrome without loading Minecraft client classes. */
class OrePickerLayoutTest {
    @Test
    void acceptanceScaleLayoutsUseOneCompleteRowAboveTheFooter() {
        OrePickerLayout at427 = OrePickerLayout.calculate(10, 407, 375, 146, 203);
        OrePickerLayout at320 = OrePickerLayout.calculate(10, 300, 268, 146, 203);

        assertEquals(1, at427.rows());
        assertEquals(1, at320.rows());
        assertEquals(13, at427.columns());
        assertEquals(9, at320.columns());
        assertTrue(at427.pagerBottom() <= 203 - OrePickerLayout.FOOTER_GAP);
        assertTrue(at320.pagerBottom() <= 203 - OrePickerLayout.FOOTER_GAP);
    }

    @Test
    void normalSizeRetainsTheFiveRowCap() {
        OrePickerLayout layout = OrePickerLayout.calculate(127, 600, 568, 157, 406);

        assertEquals(14, layout.columns());
        assertEquals(5, layout.rows());
        assertEquals(70, layout.pageSize());
        assertEquals(300, layout.pagerY());
    }
}
