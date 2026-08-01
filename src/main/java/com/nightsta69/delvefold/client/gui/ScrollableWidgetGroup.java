package com.nightsta69.delvefold.client.gui;

import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Owns the stable identity, virtual Y coordinate, visibility, and offset of widgets inside one vertical viewport.
 *
 * <p>The group never recreates widgets and never changes their active state. Screens own widget construction,
 * registration, clipping, rendering order, and wheel hit-testing; this collaborator applies the shared coordinate and
 * keyboard focus-target policy.
 */
final class ScrollableWidgetGroup {
    private final ScrollableWidgetModel<AbstractWidget> model = new ScrollableWidgetModel<>();

    /** Clears registered widgets for a screen rebuild while retaining the requested scroll position. */
    void reset(int initialVirtualBottom) {
        this.model.reset(initialVirtualBottom);
    }

    /** Registers an existing widget by identity and remembers its current Y coordinate as the virtual position. */
    <T extends AbstractWidget> T register(T widget) {
        this.model.register(widget, widget.getY());
        return widget;
    }

    /** Returns a stable unmodifiable view in widget registration/render order. */
    List<AbstractWidget> widgets() {
        return this.model.items();
    }

    /** Tests membership by object identity so themed widgets cannot alter ownership with value equality. */
    boolean contains(Renderable renderable) {
        return renderable instanceof AbstractWidget widget && this.model.contains(widget);
    }

    /** Includes a virtual content boundary without moving any widget. */
    void includeBottom(int candidate) {
        this.model.includeBottom(candidate);
    }

    /** Replaces the measured virtual content boundary for the current rebuilt section. */
    void setVirtualBottom(int virtualBottom) {
        this.model.setVirtualBottom(virtualBottom);
    }

    /** Returns the measured exclusive virtual content boundary. */
    int virtualBottom() {
        return this.model.virtualBottom();
    }

    /** Restores a nonnegative requested offset before responsive viewport bounds are known. */
    void restoreOffset(int requestedOffset) {
        this.model.restoreOffset(requestedOffset);
    }

    /** Returns the currently clamped scroll offset in logical GUI pixels. */
    int scrollOffset() {
        return this.model.scrollOffset();
    }

    /** Clamps the requested offset and moves every owned widget to its corresponding screen coordinate. */
    void setScrollOffset(int requestedOffset, VerticalScrollLayout layout) {
        this.model.setScrollOffset(requestedOffset, layout);
        apply(layout);
    }

    /** Reapplies the current offset after a resize or widget rebuild. */
    void apply(VerticalScrollLayout layout) {
        this.model.clamp(layout);
        for (AbstractWidget widget : this.model.items()) {
            int y = this.model.screenY(widget, widget.getY(), layout);
            widget.setY(y);
            widget.visible = layout.fullyVisible(y, widget.getHeight());
        }
    }

    /** Scrolls just enough to reveal the complete widget while retaining its original virtual coordinate. */
    void scrollIntoView(AbstractWidget widget, VerticalScrollLayout layout) {
        this.model.reveal(widget, widget.getHeight(), layout);
        apply(layout);
    }

    /**
     * Handles the conventional vertical scroll keys for a focused widget group.
     *
     * @param keyCode GLFW key code
     * @param layout current responsive viewport
     * @param lineStep positive logical-pixel distance for one arrow-key step
     * @return whether the key changed the scroll offset and should therefore suppress normal focus traversal
     */
    boolean handleScrollKey(int keyCode, VerticalScrollLayout layout, int lineStep) {
        ScrollableWidgetModel.ScrollCommand command =
                switch (keyCode) {
                    case GLFW.GLFW_KEY_DOWN -> ScrollableWidgetModel.ScrollCommand.LINE_DOWN;
                    case GLFW.GLFW_KEY_UP -> ScrollableWidgetModel.ScrollCommand.LINE_UP;
                    case GLFW.GLFW_KEY_PAGE_DOWN -> ScrollableWidgetModel.ScrollCommand.PAGE_DOWN;
                    case GLFW.GLFW_KEY_PAGE_UP -> ScrollableWidgetModel.ScrollCommand.PAGE_UP;
                    case GLFW.GLFW_KEY_HOME -> ScrollableWidgetModel.ScrollCommand.START;
                    case GLFW.GLFW_KEY_END -> ScrollableWidgetModel.ScrollCommand.END;
                    default -> null;
                };
        if (command == null) {
            return false;
        }
        if (!this.model.scroll(command, layout, lineStep)) {
            return false;
        }
        apply(layout);
        return true;
    }

    // Finds the next active body widget in identity order, then reveals it before focus changes. The direction is
    // forward for Tab and backward for Shift+Tab; null marks the corresponding group boundary.
    @Nullable AbstractWidget nextFocusable(AbstractWidget current, boolean forward, VerticalScrollLayout layout) {
        AbstractWidget candidate = this.model.next(current, forward, widget -> widget.active);
        if (candidate != null) {
            scrollIntoView(candidate, layout);
            return candidate.visible ? candidate : null;
        }
        return null;
    }

    /* Returns the first active widget completely visible in the current viewport. */
    @Nullable AbstractWidget firstVisibleFocusable() {
        for (AbstractWidget widget : this.model.items()) {
            if (widget.active && widget.visible) {
                return widget;
            }
        }
        return null;
    }

    /* Reveals the first active widget only when a rebuilt viewport would otherwise expose no keyboard target. */
    void ensureFocusableVisible(VerticalScrollLayout layout) {
        if (firstVisibleFocusable() != null) {
            return;
        }
        for (AbstractWidget widget : this.model.items()) {
            if (widget.active) {
                scrollIntoView(widget, layout);
                return;
            }
        }
    }
}
