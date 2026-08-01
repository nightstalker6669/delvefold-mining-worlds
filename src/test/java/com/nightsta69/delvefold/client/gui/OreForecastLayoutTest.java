package com.nightsta69.delvefold.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OreForecastLayoutTest {
    @ParameterizedTest(name = "logical GUI {0}x{1}, truncated={2}")
    @CsvSource({"427,240,false", "427,240,true", "320,240,false", "320,240,true"})
    void minecraftMinimumLogicalSizesKeepOneCompleteRuleRow(int width, int height, boolean truncated) {
        AdminPanelLayout panel = AdminPanelLayout.calculate(width, height, 760, 440);
        OreForecastLayout layout = OreForecastLayout.calculate(panel, truncated);

        assertContained(panel, layout);
        assertTrue(layout.compactColumns());
        assertTrue(layout.compactSummary());
        assertEquals(42, layout.summaryHeight());
        assertEquals(layout.ruleRowHeight(), layout.ruleViewportHeight());
        assertTrue(layout.ruleViewportWidth() > 0);
        assertFalse(layout.showsHeightGraph());
    }

    @ParameterizedTest(name = "normal GUI {0}x{1}, truncated={2}")
    @CsvSource({"854,480,false", "854,480,true", "1920,1080,false", "1920,1080,true"})
    void normalSizesPreserveFullSummaryAndTwoColumnLayout(int width, int height, boolean truncated) {
        AdminPanelLayout panel = AdminPanelLayout.calculate(width, height, 760, 440);
        OreForecastLayout layout = OreForecastLayout.calculate(panel, truncated);

        assertContained(panel, layout);
        assertFalse(layout.compactColumns());
        assertFalse(layout.compactSummary());
        assertEquals(truncated ? 104 : 93, layout.summaryHeight());
        assertEquals(45, layout.ruleRowHeight());
        assertEquals(Math.min(276, Math.max(190, layout.contentWidth() * 2 / 5)), layout.graphWidth());
        assertTrue(layout.showsHeightGraph());
        assertTrue(layout.ruleViewportHeight() >= layout.ruleRowHeight());
    }

    @Test
    void standardSizeMatchesLegacyGeometryExactly() {
        AdminPanelLayout panel = AdminPanelLayout.calculate(1920, 1080, 760, 440);
        OreForecastLayout layout = OreForecastLayout.calculate(panel, false);

        assertEquals(panel.contentTop() + 93 + 6, layout.bodyTop());
        assertEquals(panel.contentBottom(), layout.bodyBottom());
        assertEquals(panel.contentLeft() + layout.graphWidth() + 6, layout.rulesLeft());
        assertEquals(layout.bodyTop() + 25, layout.ruleViewportTop());
        assertEquals(panel.contentBottom() - 6, layout.ruleViewportBottom());
        assertEquals(layout.rulesLeft() + 8, layout.ruleViewportLeft());
        assertEquals(layout.rulesWidth() - 16, layout.ruleViewportWidth());
    }

    @ParameterizedTest(name = "stress logical GUI {0}x{1}")
    @CsvSource({"285,160", "214,120", "200,150", "80,80", "1,1"})
    void extraSmallStressSizesNeverProduceNegativeOrInvertedGeometry(int width, int height) {
        AdminPanelLayout panel = AdminPanelLayout.calculate(width, height, 760, 440);
        OreForecastLayout layout = OreForecastLayout.calculate(panel, true);

        assertContained(panel, layout);
        assertTrue(layout.summaryHeight() >= 0);
        assertTrue(layout.bodyHeight() >= 0);
        assertTrue(layout.rulesWidth() >= 0);
        assertTrue(layout.ruleViewportWidth() >= 0);
        assertTrue(layout.ruleViewportHeight() >= 0);
        assertTrue(layout.ruleViewportBottom() >= layout.ruleViewportTop());
    }

    private static void assertContained(AdminPanelLayout panel, OreForecastLayout layout) {
        assertTrue(layout.contentLeft() >= panel.left());
        assertTrue(layout.contentTop() >= panel.top());
        assertTrue(layout.contentLeft() + layout.contentWidth() <= panel.left() + panel.width());
        assertTrue(layout.bodyTop() >= layout.contentTop());
        assertTrue(layout.bodyBottom() >= layout.bodyTop());
        assertTrue(layout.bodyBottom() <= panel.top() + panel.height());
        assertTrue(layout.rulesLeft() >= layout.contentLeft());
        assertTrue(layout.rulesLeft() + layout.rulesWidth() <= panel.left() + panel.width());
        assertTrue(layout.ruleViewportLeft() >= layout.rulesLeft());
        assertTrue(layout.ruleViewportLeft() + layout.ruleViewportWidth() <= panel.left() + panel.width());
        assertTrue(layout.ruleViewportTop() >= layout.bodyTop());
        assertTrue(layout.ruleViewportBottom() <= layout.bodyBottom());
    }
}
