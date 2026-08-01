package com.nightsta69.delvefold.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VerticalScrollLayoutTest {
    @Test
    void setupControlsRemainReachableAtThe854By480ScaleTwoLogicalSize() {
        // 427x240 logical: panel top 8, content top 64, setup body top 95.
        VerticalScrollLayout layout = new VerticalScrollLayout(96, 185, 337);
        int seedModeVirtualY = 232;
        int revealOffset = layout.clamp(seedModeVirtualY - layout.viewportTop());

        assertTrue(layout.maximumScroll() > 0);
        assertTrue(layout.fullyVisible(layout.screenY(seedModeVirtualY, revealOffset), 24));
        assertEquals(layout.viewportBottom(), layout.virtualContentBottom() - layout.maximumScroll());
    }

    @Test
    void dashboardIdentitySaveRemainsReachableAtCompactLogicalHeight() {
        // 427x240 logical: dashboard body top 96; fixed section header ends at 118.
        VerticalScrollLayout layout = new VerticalScrollLayout(118, 185, 301);
        int saveVirtualY = 256;

        assertTrue(layout.maximumScroll() > 0);
        assertTrue(layout.fullyVisible(layout.screenY(saveVirtualY, layout.maximumScroll()), 22));
    }

    @Test
    void controlsRemainReachableAt1080pGuiScaleFour() {
        // 1920x1080 at GUI scale 4 is 480x270 logical; panel top 8, body top 95/96.
        VerticalScrollLayout setup = new VerticalScrollLayout(96, 215, 337);
        VerticalScrollLayout dashboard = new VerticalScrollLayout(118, 215, 301);

        assertTrue(setup.fullyVisible(setup.screenY(232, setup.clamp(136)), 24));
        assertTrue(dashboard.fullyVisible(dashboard.screenY(256, dashboard.maximumScroll()), 22));
    }

    @Test
    void normalHeightKeepsTheOriginalZeroOffsetLayout() {
        VerticalScrollLayout layout = new VerticalScrollLayout(133, 388, 374);

        assertEquals(0, layout.maximumScroll());
        assertEquals(232, layout.screenY(232, 999));
    }
}
