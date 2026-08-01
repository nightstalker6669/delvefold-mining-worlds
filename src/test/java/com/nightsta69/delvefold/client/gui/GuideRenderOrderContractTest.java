package com.nightsta69.delvefold.client.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Guards the Seam Ledger against drawing its content before a second background/blur pass. */
class GuideRenderOrderContractTest {
    @Test
    void customContentIsRenderedByTheSingleVanillaBackgroundPass() throws IOException {
        String source = Files.readString(
                Path.of("src/main/java/com/nightsta69/delvefold/client/gui/DelvefoldGuideScreen.java"));
        String render = between(
                source,
                "public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)",
                "public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)");
        String background = between(
                source,
                "public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)",
                "private void drawPanel(GuiGraphics graphics)");

        assertTrue(render.contains("super.render(graphics, mouseX, mouseY, partialTick);"));
        assertFalse(render.contains("this.renderBackground("));
        assertInOrder(
                background,
                "super.renderBackground(graphics, mouseX, mouseY, partialTick);",
                "graphics.fillGradient(",
                "drawPanel(graphics);",
                "drawOverview(graphics);",
                "drawOreRows(graphics, mouseX, mouseY);");
    }

    private static String between(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        if (start < 0 || end < 0) {
            throw new AssertionError("Expected source markers were not found");
        }
        return source.substring(start, end);
    }

    private static void assertInOrder(String source, String... markers) {
        int previous = -1;
        for (String marker : markers) {
            int current = source.indexOf(marker, previous + 1);
            assertTrue(current > previous, () -> "Expected render step after the previous step: " + marker);
            previous = current;
        }
    }
}
