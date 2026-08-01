package com.nightsta69.delvefold.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProfilePanelLayoutTest {
    @Test
    void profileActionsRemainReachableAt854By480ScaleTwoLogicalHeight() {
        ProfilePanelLayout layout = ProfilePanelLayout.calculate(96, 186, true);

        assertEquals(141, layout.controlsY());
        assertEquals(1, layout.pageSize());
        assertTrue(96 + 25 + layout.rowHeight() <= layout.controlsY());
        assertTrue(layout.controlsY() + 45 <= 186);
    }

    @Test
    void profileActionsRemainReachableAt1080pGuiScaleFour() {
        ProfilePanelLayout layout = ProfilePanelLayout.calculate(96, 216, true);

        assertEquals(171, layout.controlsY());
        assertEquals(2, layout.pageSize());
        assertTrue(96 + 25 + layout.pageSize() * layout.rowStep() <= layout.controlsY());
        assertTrue(layout.controlsY() + 45 <= 216);
    }

    @Test
    void normalHeightKeepsFiveProfileRowsAndEstablishedControlPosition() {
        ProfilePanelLayout layout = ProfilePanelLayout.calculate(100, 390, false);

        assertEquals(254, layout.controlsY());
        assertEquals(5, layout.pageSize());
        assertEquals(23, layout.rowStep());
        assertEquals(20, layout.rowHeight());
    }
}
