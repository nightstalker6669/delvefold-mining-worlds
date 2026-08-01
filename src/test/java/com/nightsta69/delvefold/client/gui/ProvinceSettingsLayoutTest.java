package com.nightsta69.delvefold.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProvinceSettingsLayoutTest {
    @ParameterizedTest
    @CsvSource({
            "320,240,true",
            "427,240,true",
            "854,480,false",
            "1920,1080,false"
    })
    void fieldsRemainContainedAndSeparatedAtSupportedGuiSizes(
            int screenWidth, int screenHeight, boolean compact) {
        ProvinceSettingsLayout layout = ProvinceSettingsLayout.forScreen(screenWidth, screenHeight);
        List<ProvinceSettingsLayout.Field> fields = List.of(
                layout.region(), layout.radius(), layout.verticalThickness(),
                layout.density(), layout.workCap());

        assertEquals(compact, layout.compact());
        for (ProvinceSettingsLayout.Field field : fields) {
            assertTrue(field.x() >= layout.contentLeft() + 12);
            assertTrue(field.right() <= layout.contentRight() - 12);
            assertTrue(field.labelY() >= layout.sectionTitleY() + 11);
            assertTrue(field.bottom() <= layout.contentBottom());
        }
        assertTrue(layout.region().right() < layout.radius().x());
        assertTrue(layout.verticalThickness().right() < layout.density().x());
        assertTrue(layout.region().bottom() < layout.verticalThickness().y());
        assertTrue(layout.verticalThickness().bottom() < layout.workCap().y());
    }

    @ParameterizedTest
    @CsvSource({"320,240", "427,240"})
    void compactLayoutLeavesAVisibleValidationLineBelowTheLastField(int width, int height) {
        ProvinceSettingsLayout layout = ProvinceSettingsLayout.forScreen(width, height);

        assertTrue(layout.compact());
        assertFalse(layout.showHelp());
        assertEquals(layout.workCap().bottom() + 2, layout.errorY());
        assertTrue(layout.errorY() + 9 <= layout.contentBottom());
    }

    @ParameterizedTest
    @CsvSource({"854,480", "1920,1080"})
    void normalLayoutRetainsTheOriginalFieldGeometry(int width, int height) {
        ProvinceSettingsLayout layout = ProvinceSettingsLayout.forScreen(width, height);

        assertFalse(layout.compact());
        assertTrue(layout.showHelp());
        assertEquals(layout.contentTop() + 37, layout.region().y());
        assertEquals(20, layout.region().height());
        assertEquals(43, layout.verticalThickness().y() - layout.region().y());
        assertEquals(86, layout.workCap().y() - layout.region().y());
        assertEquals(layout.region().y() + 113, layout.helpY());
        assertEquals(layout.region().y() + 150, layout.errorY());
    }
}
