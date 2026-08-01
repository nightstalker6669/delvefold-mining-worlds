package com.nightsta69.delvefold.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Exhaustively characterizes the platform-independent scroll and focus-order model. */
class ScrollableWidgetModelTest {
    @Test
    void registrationUsesIdentityRetainsOrderAndExposesNoMutableCollection() {
        ScrollableWidgetModel<ValueToken> model = new ScrollableWidgetModel<>();
        model.reset(10);
        ValueToken first = new ValueToken("equal");
        ValueToken equalButDistinct = new ValueToken("equal");

        assertTrue(model.register(first, 20));
        assertFalse(model.register(first, 99));
        assertTrue(model.register(equalButDistinct, 40));
        assertEquals(List.of(first, equalButDistinct), model.items());
        assertTrue(model.contains(first));
        assertTrue(model.contains(equalButDistinct));
        assertThrows(UnsupportedOperationException.class, () -> model.items().clear());
    }

    @Test
    void offsetAndVirtualCoordinatesRemainStableAcrossRepeatedLayoutApplication() {
        ScrollableWidgetModel<ValueToken> model = new ScrollableWidgetModel<>();
        model.reset(100);
        ValueToken first = new ValueToken("first");
        ValueToken second = new ValueToken("second");
        model.register(first, 120);
        model.register(second, 170);
        model.includeBottom(225);
        model.includeBottom(210);
        VerticalScrollLayout layout = new VerticalScrollLayout(101, 181, model.virtualBottom());

        model.setScrollOffset(30, layout);

        assertEquals(30, model.scrollOffset());
        assertEquals(90, model.screenY(first, -1, layout));
        assertEquals(140, model.screenY(second, -1, layout));
        assertEquals(90, model.screenY(first, 777, layout));
        assertEquals(777 - 30, model.screenY(new ValueToken("unknown"), 777, layout));
    }

    @Test
    void resetClearsOwnershipAndMeasurementButRetainsRequestedOffsetUntilClamped() {
        ScrollableWidgetModel<ValueToken> model = new ScrollableWidgetModel<>();
        ValueToken old = new ValueToken("old");
        model.restoreOffset(500);
        model.reset(100);
        model.register(old, 110);
        model.setVirtualBottom(140);
        model.clamp(new VerticalScrollLayout(101, 181, model.virtualBottom()));
        assertEquals(0, model.scrollOffset());

        model.restoreOffset(42);
        model.reset(75);

        assertFalse(model.contains(old));
        assertTrue(model.items().isEmpty());
        assertEquals(75, model.virtualBottom());
        assertEquals(42, model.scrollOffset());
    }

    @Test
    void revealUsesTheSmallestOffsetThatMakesTheCompleteItemVisible() {
        ScrollableWidgetModel<ValueToken> model = new ScrollableWidgetModel<>();
        model.reset(100);
        ValueToken above = new ValueToken("above");
        ValueToken below = new ValueToken("below");
        model.register(above, 90);
        model.register(below, 210);
        model.setVirtualBottom(250);
        VerticalScrollLayout layout = new VerticalScrollLayout(100, 180, model.virtualBottom());
        model.restoreOffset(40);
        model.clamp(layout);

        model.reveal(above, 20, layout);
        assertEquals(0, model.scrollOffset());
        model.reveal(below, 20, layout);
        assertEquals(50, model.scrollOffset());
        model.reveal(new ValueToken("unknown"), 20, layout);
        assertEquals(50, model.scrollOffset());
    }

    @Test
    void keyboardCommandsReportOnlyEffectiveMovementAndClampAtBothEdges() {
        ScrollableWidgetModel<ValueToken> model = new ScrollableWidgetModel<>();
        model.reset(100);
        model.setVirtualBottom(300);
        VerticalScrollLayout layout = new VerticalScrollLayout(100, 180, model.virtualBottom());

        assertFalse(model.scroll(ScrollableWidgetModel.ScrollCommand.LINE_UP, layout, 20));
        assertTrue(model.scroll(ScrollableWidgetModel.ScrollCommand.LINE_DOWN, layout, 20));
        assertEquals(20, model.scrollOffset());
        assertTrue(model.scroll(ScrollableWidgetModel.ScrollCommand.PAGE_DOWN, layout, 20));
        assertEquals(80, model.scrollOffset());
        assertTrue(model.scroll(ScrollableWidgetModel.ScrollCommand.END, layout, 20));
        assertEquals(120, model.scrollOffset());
        assertFalse(model.scroll(ScrollableWidgetModel.ScrollCommand.LINE_DOWN, layout, 20));
        assertFalse(model.scroll(ScrollableWidgetModel.ScrollCommand.END, layout, 20));
        assertTrue(model.scroll(ScrollableWidgetModel.ScrollCommand.PAGE_UP, layout, 20));
        assertEquals(60, model.scrollOffset());
        assertTrue(model.scroll(ScrollableWidgetModel.ScrollCommand.START, layout, 20));
        assertEquals(0, model.scrollOffset());
        assertFalse(model.scroll(ScrollableWidgetModel.ScrollCommand.START, layout, 20));
    }

    @Test
    void keyboardCommandsDoNotClaimKeysWhenContentCannotScroll() {
        ScrollableWidgetModel<ValueToken> model = new ScrollableWidgetModel<>();
        model.reset(100);
        model.setVirtualBottom(150);
        VerticalScrollLayout layout = new VerticalScrollLayout(100, 180, model.virtualBottom());

        for (ScrollableWidgetModel.ScrollCommand command : ScrollableWidgetModel.ScrollCommand.values()) {
            assertFalse(model.scroll(command, layout, 0), command.name());
        }
    }

    @Test
    void traversalSkipsIneligibleItemsUsesIdentityAndNeverWraps() {
        ScrollableWidgetModel<ValueToken> model = new ScrollableWidgetModel<>();
        model.reset(0);
        ValueToken first = new ValueToken("same");
        ValueToken disabled = new ValueToken("disabled");
        ValueToken last = new ValueToken("same");
        model.register(first, 10);
        model.register(disabled, 20);
        model.register(last, 30);

        assertSame(last, model.next(first, true, token -> !token.value().equals("disabled")));
        assertSame(first, model.next(last, false, token -> !token.value().equals("disabled")));
        assertNull(model.next(last, true, token -> true));
        assertNull(model.next(first, false, token -> true));
        assertNull(model.next(new ValueToken("same"), true, token -> true));
    }

    private record ValueToken(String value) {}
}
