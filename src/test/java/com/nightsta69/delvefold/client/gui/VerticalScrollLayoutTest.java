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
    void everyScrollableDashboardTabReachesItsLastActionAt427By240() {
        // 427x240 logical: body top 96, fixed section header ends at 118,
        // and the body ends at 185 before the fixed footer at y=203.
        assertLastActionReachable(new VerticalScrollLayout(118, 185, 325), 297, 22); // Ores row 7
        assertLastActionReachable(new VerticalScrollLayout(118, 185, 252), 224, 22); // Gameplay Save
        assertLastActionReachable(new VerticalScrollLayout(118, 185, 223), 178, 22); // Portal Save
        assertLastActionReachable(new VerticalScrollLayout(118, 185, 330), 290, 22); // Identity Save
        assertLastActionReachable(new VerticalScrollLayout(118, 185, 338), 225, 22); // Backup Management
    }

    @Test
    void controlsRemainReachableAt1080pGuiScaleFour() {
        // 1920x1080 at GUI scale 4 is 480x270 logical; panel top 8, body top 95/96.
        VerticalScrollLayout setup = new VerticalScrollLayout(96, 215, 337);

        assertTrue(setup.fullyVisible(setup.screenY(232, setup.clamp(136)), 24));
        assertLastActionReachable(new VerticalScrollLayout(118, 215, 325), 297, 22); // Ores row 7
        assertLastActionReachable(new VerticalScrollLayout(118, 215, 252), 224, 22); // Gameplay Save
        assertLastActionReachable(new VerticalScrollLayout(118, 215, 223), 178, 22); // Portal Save
        assertLastActionReachable(new VerticalScrollLayout(118, 215, 330), 290, 22); // Identity Save
        assertLastActionReachable(new VerticalScrollLayout(118, 215, 338), 225, 22); // Backup Management
    }

    @Test
    void normalHeightKeepsTheOriginalZeroOffsetLayout() {
        // A normal 600x390 dashboard has a 124..357 body viewport. Each tab's
        // measured content bottom fits, so the reusable scroller is inert.
        for (int virtualBottom : new int[] {335, 281, 259, 353, 353}) {
            VerticalScrollLayout layout = new VerticalScrollLayout(124, 357, virtualBottom);
            assertEquals(0, layout.maximumScroll());
            assertEquals(232, layout.screenY(232, 999));
        }
    }

    private static void assertLastActionReachable(VerticalScrollLayout layout, int virtualY, int height) {
        assertTrue(layout.maximumScroll() > 0);
        boolean reachable = false;
        for (int requestedOffset = 0; requestedOffset <= layout.maximumScroll() + 24; requestedOffset += 24) {
            int offset = layout.clamp(requestedOffset);
            reachable |= layout.fullyVisible(layout.screenY(virtualY, offset), height);
        }
        assertTrue(reachable, "24-pixel wheel steps must expose the complete action");
    }
}
