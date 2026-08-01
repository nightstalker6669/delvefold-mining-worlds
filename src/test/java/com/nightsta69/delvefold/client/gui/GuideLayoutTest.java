package com.nightsta69.delvefold.client.gui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GuideLayoutTest {
    @ParameterizedTest
    @CsvSource({
            "854,480",
            "1920,1080",
            "960,540",
            "640,360",
            "480,270",
            "427,240",
            "320,240",
            "200,150"
    })
    void layoutStaysContainedAndNeverCreatesAnInvalidScissor(int width, int height) {
        GuideLayout layout = GuideLayout.calculate(width, height);
        assertTrue(layout.panelLeft() >= 0);
        assertTrue(layout.panelTop() >= 0);
        assertTrue(layout.panelLeft() + layout.panelWidth() <= width);
        assertTrue(layout.panelTop() + layout.panelHeight() <= height);
        assertTrue(layout.listBottom() >= layout.listTop());
        assertTrue(layout.doneX() >= layout.panelLeft());
        assertTrue(layout.doneX() + layout.doneWidth() <= layout.panelLeft() + layout.panelWidth());
        assertTrue(layout.footerTextWidth() >= 0);
        assertTrue(layout.panelLeft() + 14 + layout.footerTextWidth() + 8 <= layout.doneX());
    }
}
