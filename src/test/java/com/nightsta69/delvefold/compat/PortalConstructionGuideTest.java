package com.nightsta69.delvefold.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

class PortalConstructionGuideTest {
    @Test
    void minimumAndMaximumFrameCountsMatchSupportedPortalGeometry() {
        PortalConstructionGuide guide = PortalConstructionGuide.INSTANCE;
        assertEquals(2, guide.minimumInteriorWidth());
        assertEquals(3, guide.minimumInteriorHeight());
        assertEquals(21, guide.maximumInteriorWidth());
        assertEquals(21, guide.maximumInteriorHeight());
        assertEquals(14, guide.minimumFrameCount());
        assertEquals(88, guide.maximumFrameCount());
    }

    @Test
    void minimumDiagramContainsEveryBorderCellExactlyOnce() {
        PortalConstructionGuide guide = PortalConstructionGuide.INSTANCE;
        assertEquals(guide.minimumFrameCount(), guide.minimumFrameCells().size());
        assertEquals(guide.minimumFrameCells().size(), new HashSet<>(guide.minimumFrameCells()).size());
        assertTrue(guide.minimumFrameCells().contains(new PortalConstructionGuide.FrameCell(0, 0)));
        assertTrue(guide.minimumFrameCells().contains(new PortalConstructionGuide.FrameCell(3, 4)));
        assertFalse(guide.minimumFrameCells().contains(new PortalConstructionGuide.FrameCell(1, 1)));
    }

    @Test
    void invalidFrameCountDimensionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> PortalConstructionGuide.frameCount(0, 3));
    }
}
