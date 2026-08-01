package com.nightsta69.delvefold.client.gui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AdminPanelLayoutTest {
    @ParameterizedTest(name = "framebuffer {0}x{1}, requested GUI scale {2}")
    @CsvSource({
        "854,480,1",
        "854,480,2",
        "854,480,3",
        "854,480,4",
        "1920,1080,1",
        "1920,1080,2",
        "1920,1080,3",
        "1920,1080,4"
    })
    void targetResolutionAndGuiScaleMatrixStaysCenteredContainedAndUsable(
            int framebufferWidth, int framebufferHeight, int requestedScale) {
        int effectiveScale = minecraftScale(framebufferWidth, framebufferHeight, requestedScale);
        int width = divideCeil(framebufferWidth, effectiveScale);
        int height = divideCeil(framebufferHeight, effectiveScale);
        AdminPanelLayout layout = AdminPanelLayout.calculate(width, height, 760, 440);

        assertTrue(width >= 320);
        assertTrue(height >= 240);
        assertUsable(width, height, layout);
    }

    @ParameterizedTest(name = "supported logical GUI floor {0}x{1}")
    @CsvSource({"320,240", "427,240"})
    void supportedLogicalFloorRetainsUsableContentAndFooter(int width, int height) {
        AdminPanelLayout layout = AdminPanelLayout.calculate(width, height, 760, 440);

        assertUsable(width, height, layout);
        assertTrue(layout.contentBottom() - layout.contentTop() >= 120);
    }

    private static void assertUsable(int width, int height, AdminPanelLayout layout) {
        assertTrue(layout.left() >= 0);
        assertTrue(layout.top() >= 0);
        assertTrue(layout.width() >= 1);
        assertTrue(layout.height() >= 1);
        assertTrue(layout.left() + layout.width() <= width);
        assertTrue(layout.top() + layout.height() <= height);
        assertTrue(Math.abs(width - (layout.left() * 2 + layout.width())) <= 1);
        assertTrue(Math.abs(height - (layout.top() * 2 + layout.height())) <= 1);
        assertTrue(layout.contentRight() - layout.contentLeft() >= 80);
        assertTrue(
                layout.contentBottom() - layout.contentTop() >= 48,
                "responsive chrome must leave room for at least one complete large setup control");
        assertTrue(layout.footerButtonY() >= layout.contentBottom());
        assertTrue(layout.footerButtonY() + 22 <= layout.top() + layout.height());
    }

    /** Mirrors Minecraft 1.21.1's Window.calculateScale minimum logical-size policy. */
    private static int minecraftScale(int framebufferWidth, int framebufferHeight, int requestedScale) {
        int scale = 1;
        while (scale != requestedScale
                && scale < framebufferWidth
                && scale < framebufferHeight
                && framebufferWidth / (scale + 1) >= 320
                && framebufferHeight / (scale + 1) >= 240) {
            scale++;
        }
        return scale;
    }

    private static int divideCeil(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }
}
