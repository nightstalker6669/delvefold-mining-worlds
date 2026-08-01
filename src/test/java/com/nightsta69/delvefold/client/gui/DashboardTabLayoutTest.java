package com.nightsta69.delvefold.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DashboardTabLayoutTest {
    @ParameterizedTest(name = "{0}: compact={6} stacked={7}")
    @CsvSource({
        "854x480 scale 2,375,224,64,186,false,true,true",
        "1920x1080 scale 4,428,254,64,216,false,true,true",
        "320x240 logical,268,224,64,186,false,true,true",
        "200x150 logical,184,146,22,121,true,true,true",
        "214x120 logical,198,116,22,91,true,true,true",
        "preferred 600x390 panel,568,390,92,357,false,false,false"
    })
    void targetLogicalSizesSelectStableCompactAndWorldStackingModes(
            String target,
            int contentWidth,
            int panelHeight,
            int contentTop,
            int contentBottom,
            boolean compactChrome,
            boolean compact,
            boolean stacked) {
        DashboardTabLayout layout =
                new DashboardTabLayout(contentWidth, panelHeight, contentTop, contentBottom, compactChrome);

        assertFalse(target.isBlank());
        assertEquals(compact, layout.compactHeight());
        assertEquals(stacked, layout.worldContentStacked());
        assertTrue(layout.footerDoneWidth() >= 54);
        assertTrue(layout.footerPagerButtonWidth() >= 1);
        assertTrue(layout.profilePanel().pageSize() >= 1);
        assertTrue(layout.bodyViewportHeight() >= 22);
    }

    @Test
    void extraSmallChromeLeavesOneCompleteScrollableControlVisible() {
        DashboardTabLayout layout = new DashboardTabLayout(198, 116, 22, 91, true);

        assertEquals(18, layout.tabHeight());
        assertEquals(42, layout.bodyTop());
        assertEquals(60, layout.bodyViewportTop());
        assertEquals(30, layout.bodyViewportHeight());
        assertTrue(layout.bodyTop() + 25 + 22 <= layout.contentBottom() - 1);
    }

    @Test
    void paginationClampsEmptyNegativeOversizedAndLastPages() {
        DashboardTabLayout layout = new DashboardTabLayout(375, 224, 64, 186, false);

        assertEquals(new DashboardTabLayout.Page(0, 1, 0, 0), layout.page(0, -8, 5));
        assertEquals(new DashboardTabLayout.Page(0, 3, 0, 5), layout.page(11, -8, 5));
        assertEquals(new DashboardTabLayout.Page(2, 3, 10, 11), layout.page(11, 2, 5));
        assertEquals(new DashboardTabLayout.Page(2, 3, 10, 11), layout.page(11, 999, 5));
        assertThrows(IllegalArgumentException.class, () -> layout.page(10, 0, 0));
    }

    @Test
    void normalDashboardRetainsOriginalInlineProfileCapacity() {
        DashboardTabLayout normal = new DashboardTabLayout(568, 390, 92, 357, false);
        DashboardTabLayout compact = new DashboardTabLayout(375, 224, 64, 186, false);

        assertFalse(normal.compactHeight());
        assertEquals(5, normal.profilePanel().pageSize());
        assertTrue(compact.profilePanel().pageSize() < normal.profilePanel().pageSize());
    }
}
