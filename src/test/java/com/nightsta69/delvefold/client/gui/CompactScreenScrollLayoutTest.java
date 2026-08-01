package com.nightsta69.delvefold.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CompactScreenScrollLayoutTest {
    @ParameterizedTest(name = "wizard {0} page")
    @CsvSource({
            "targets,277,249,20",
            "filters,282,256,18",
            "bands,250,222,20"
    })
    void everyWizardPageCanRevealItsLastControlOrHelpLineAt427By240(
            String page, int virtualBottom, int lastY, int lastHeight) {
        // 427x240 logical: panel top 8, wizard body top 96, viewport 97..185.
        VerticalScrollLayout layout = new VerticalScrollLayout(97, 185, virtualBottom);

        assertTrue(layout.maximumScroll() > 0, page);
        assertTrue(layout.fullyVisible(
                layout.screenY(lastY, layout.maximumScroll()), lastHeight), page);
        assertEquals(layout.viewportBottom(),
                layout.virtualContentBottom() - layout.maximumScroll(), page);
    }

    @ParameterizedTest(name = "wizard {0} normal layout")
    @CsvSource({
            "targets,333",
            "filters,274",
            "bands,311"
    })
    void wizardNormalLayoutDoesNotAcquireUnnecessaryScrolling(String page, int virtualBottom) {
        // A 390px panel has a wizard body viewport of 89..343 relative to its top.
        VerticalScrollLayout layout = new VerticalScrollLayout(89, 343, virtualBottom);

        assertEquals(0, layout.maximumScroll(), page);
    }

    @ParameterizedTest(name = "setup {0} page")
    @CsvSource({
            // Representative two-line English help measurements at 427x240 logical size.
            "terrain,424,398,18",
            "resources,352,326,18",
            "review_with_status,345,285,52"
    })
    void measuredSetupContentCanRevealItsFinalWrappedSection(
            String page, int virtualBottom, int lastY, int lastHeight) {
        // Setup body top 95, viewport 96..185; tabs and footer stay outside it.
        VerticalScrollLayout layout = new VerticalScrollLayout(96, 185, virtualBottom);

        assertTrue(layout.maximumScroll() > 0, page);
        assertTrue(layout.fullyVisible(
                layout.screenY(lastY, layout.maximumScroll()), lastHeight), page);
        assertEquals(layout.viewportBottom(),
                layout.virtualContentBottom() - layout.maximumScroll(), page);
    }
}
