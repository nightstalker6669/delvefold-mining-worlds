package com.nightsta69.delvefold.client.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Guards expensive registry and analysis work against accidental reintroduction into per-frame render methods. */
class ClientRenderCacheContractTest {
    @Test
    void oreImportIconsAreResolvedDuringWidgetInitializationRatherThanEveryFrame() throws IOException {
        String source = source("DelvefoldOreImportScreen.java");
        String render = between(
                source,
                "public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)",
                "private static ItemStack icon(");

        assertTrue(source.contains("new RenderedGroup(icon(group),"));
        assertTrue(render.contains("rendered.icon()"));
        assertFalse(render.contains("icon(rendered.group())"));
        assertFalse(render.contains("BuiltInRegistries"));
        assertFalse(render.contains("new ItemStack"));
    }

    @Test
    void oreBandDistributionAnalysisIsOutsideTheWizardRenderPath() throws IOException {
        String source = source("DelvefoldOreRuleWizardScreen.java");
        String previewRender =
                between(source, "private void drawBandPreview(", "private FormattedCharSequence clipped(");

        assertTrue(source.contains("this.bandPreview = OreBandPreviewModel.from("));
        assertTrue(previewRender.contains("veinPreview.analysis()"));
        assertFalse(previewRender.contains("OreDistributionAnalysis.analyze("));
        assertFalse(previewRender.contains("new SpawnBand("));
    }

    @Test
    void guideRegistryIconsAndForecastGraphMaximumAreSnapshotDerivedOnce() throws IOException {
        String guide = source("DelvefoldGuideScreen.java");
        String guideRender = between(guide, "private void drawOreRows(", "private void drawScrollbar(");
        String forecast = source("DelvefoldOreForecastScreen.java");
        String graphRender = between(forecast, "private void renderHeightGraph(", "private void renderRules(");
        String ruleRender = between(forecast, "private void renderRules(", "private static int statusColor(");

        assertTrue(guide.contains("this.oreRows = snapshot.ores().stream().map(this::present).toList()"));
        assertTrue(guideRender.contains("this.oreRows.get(index)"));
        assertFalse(guideRender.contains("BuiltInRegistries"));
        assertFalse(guideRender.contains("iconFor(ore)"));
        assertFalse(guideRender.contains("Collectors.joining"));
        assertFalse(guideRender.contains("frequency(ore)"));
        assertTrue(forecast.contains("this.heightGraphMaximum = forecast.activeTerrainHeightOverlay().stream()"));
        assertTrue(forecast.contains("this.presentation = present(forecast)"));
        assertTrue(graphRender.contains("double maximum = this.heightGraphMaximum;"));
        assertFalse(graphRender.contains("mapToDouble(HeightSample::expectedWorkUnits)"));
        assertFalse(ruleRender.contains("Component.translatable("));
        assertFalse(ruleRender.contains("ruleMetric("));
        assertFalse(ruleRender.contains("ruleDetails("));
        assertFalse(ruleRender.contains("ruleTooltip("));
        assertFalse(ruleRender.contains("wrapTooltip("));
    }

    private static String source(String name) throws IOException {
        return Files.readString(
                Path.of("src/main/java/com/nightsta69/delvefold/client/gui").resolve(name));
    }

    private static String between(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        if (start < 0 || end < 0) {
            throw new AssertionError("Expected source markers were not found");
        }
        return source.substring(start, end);
    }
}
