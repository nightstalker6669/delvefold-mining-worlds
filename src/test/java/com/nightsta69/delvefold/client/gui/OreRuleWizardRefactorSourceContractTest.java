package com.nightsta69.delvefold.client.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Locks the stable screen boundary and its authority, accessibility, scrolling, and render-cache integrations. */
class OreRuleWizardRefactorSourceContractTest {
    private static final Path SOURCE =
            Path.of("src/main/java/com/nightsta69/delvefold/client/gui/DelvefoldOreRuleWizardScreen.java");

    @Test
    void publicScreenBoundaryAndRevisionGuardRemainStable() throws IOException {
        String source = Files.readString(SOURCE);
        String normalized = source.replaceAll("\\s+", " ");

        assertTrue(
                normalized.contains(
                        "public DelvefoldOreRuleWizardScreen(Screen parent, AdminSnapshot snapshot, AdminSnapshot.OreRuleDraft draft)"));
        assertTrue(normalized.contains(
                "new SaveOreRulePayload(this.snapshot.oreRevision(), currentDraft(), !this.existingRule)"));
        assertTrue(normalized.contains("new DeleteOreRulePayload(this.snapshot.oreRevision(), this.originalRuleId)"));
        assertTrue(source.contains("return this.draftState.toDraft();"));
        assertTrue(source.contains("refreshed.draftState.copyRawInputsFrom(this.draftState);"));
    }

    @Test
    void scrollPreviewFocusNarrationAndRenderPathsRemainOwnedByTheScreen() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("new ScrollableWidgetGroup()"));
        assertTrue(source.contains("applyBodyScroll();"));
        assertTrue(source.contains("this.bodyScroll.contains(renderable)"));
        assertTrue(source.contains("this.bodyScroll.widgets()"));
        assertTrue(source.contains("this.bandPreview = OreBandPreviewModel.from("));
        assertTrue(source.contains("drawBandPreview(graphics, this.bandPreview,"));
        assertTrue(source.contains("focusFirstBodyWidget();"));
        assertTrue(source.contains("public Component getNarrationMessage()"));
        assertTrue(source.contains("int footerY = this.footerButtonY();"));
        assertFalse(source.contains("this.panelTop + this.panelHeight - 29"));
        assertOrdered(
                source,
                "screen.delvefold.ore_wizard.tab.blocks",
                "screen.delvefold.ore_wizard.tab.filters",
                "screen.delvefold.ore_wizard.tab.bands");
        assertOrdered(source, "this.ruleIdBox =", "this.hostTagBox =", "this.weightBox =");
        assertOrdered(source, "this.statePropertiesBox =", "this.biomeIncludesBox =", "this.biomeExcludesBox =");
        assertFalse(source.contains("OreDistributionAnalysis.analyze("));
    }

    private static void assertOrdered(String source, String... markers) {
        int previous = -1;
        for (String marker : markers) {
            int current = source.indexOf(marker);
            assertTrue(current > previous, () -> marker + " must retain its relative widget/message order");
            previous = current;
        }
    }
}
