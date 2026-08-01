package com.nightsta69.delvefold.client.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Ensures compact administration screens share one tested widget-identity and visibility implementation. */
class ScrollableWidgetIntegrationContractTest {
    @ParameterizedTest
    @ValueSource(
            strings = {"DelvefoldSetupScreen.java", "DelvefoldDashboardScreen.java", "DelvefoldOreRuleWizardScreen.java"
            })
    void scrollableAdministrationScreensDelegateWidgetMovement(String name) throws IOException {
        String source = Files.readString(
                Path.of("src/main/java/com/nightsta69/delvefold/client/gui").resolve(name));

        assertTrue(source.contains("new ScrollableWidgetGroup()"));
        assertTrue(source.contains("this.bodyScroll.apply(layout)"));
        assertTrue(source.contains("this.bodyScroll.ensureFocusableVisible(layout)"));
        assertTrue(source.contains("this.bodyScroll.contains(renderable)"));
        assertTrue(source.contains("this.bodyScroll.widgets()"));
        assertTrue(source.contains("this.bodyScroll.nextFocusable("));
        assertTrue(source.contains("this.bodyScroll.handleScrollKey("));
        assertFalse(source.contains("IdentityHashMap<AbstractWidget, Integer>"));
        assertFalse(source.contains("widget.visible = layout.fullyVisible"));
    }
}
