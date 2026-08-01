package com.nightsta69.delvefold.client.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * Platform-independent identity, ordering, and offset model behind a vertical widget group.
 *
 * <p>Identity maps deliberately prevent widget value equality from changing ownership. The model contains no Minecraft
 * types, which keeps its reveal, traversal, and keyboard math directly unit-testable.
 */
final class ScrollableWidgetModel<T> {
    private final List<T> items = new ArrayList<>();
    private final List<T> itemView = Collections.unmodifiableList(this.items);
    private final IdentityHashMap<T, Integer> virtualY = new IdentityHashMap<>();
    private final IdentityHashMap<T, Integer> registrationIndex = new IdentityHashMap<>();
    private int scrollOffset;
    private int virtualBottom;

    /** Clears registrations for a rebuild while retaining the requested scroll position. */
    void reset(int initialVirtualBottom) {
        this.items.clear();
        this.virtualY.clear();
        this.registrationIndex.clear();
        this.virtualBottom = initialVirtualBottom;
    }

    /** Registers one item by identity and records its virtual Y coordinate. */
    boolean register(T item, int y) {
        if (this.virtualY.containsKey(item)) {
            return false;
        }
        this.registrationIndex.put(item, this.items.size());
        this.items.add(item);
        this.virtualY.put(item, y);
        return true;
    }

    /** Returns a stable unmodifiable view in registration order. */
    List<T> items() {
        return this.itemView;
    }

    /** Tests ownership by identity. */
    boolean contains(T item) {
        return this.virtualY.containsKey(item);
    }

    /** Includes a virtual content boundary. */
    void includeBottom(int candidate) {
        this.virtualBottom = Math.max(this.virtualBottom, candidate);
    }

    /** Replaces the measured virtual content boundary. */
    void setVirtualBottom(int replacement) {
        this.virtualBottom = replacement;
    }

    /** Returns the exclusive virtual content boundary. */
    int virtualBottom() {
        return this.virtualBottom;
    }

    /** Restores a nonnegative requested offset before viewport bounds are known. */
    void restoreOffset(int requestedOffset) {
        this.scrollOffset = Math.max(0, requestedOffset);
    }

    /** Returns the currently clamped offset. */
    int scrollOffset() {
        return this.scrollOffset;
    }

    /** Clamps and stores an offset against the current viewport. */
    void setScrollOffset(int requestedOffset, VerticalScrollLayout layout) {
        this.scrollOffset = layout.clamp(requestedOffset);
    }

    /** Clamps the retained offset after a resize or content rebuild. */
    void clamp(VerticalScrollLayout layout) {
        this.scrollOffset = layout.clamp(this.scrollOffset);
    }

    /** Returns one item's current screen Y, falling back only for an unregistered caller. */
    int screenY(T item, int fallbackY, VerticalScrollLayout layout) {
        return layout.screenY(this.virtualY.getOrDefault(item, fallbackY), this.scrollOffset);
    }

    /** Scrolls just enough to reveal a registered item's full height. */
    void reveal(T item, int height, VerticalScrollLayout layout) {
        Integer position = this.virtualY.get(item);
        if (position == null) {
            return;
        }
        int requestedOffset = this.scrollOffset;
        if (position - requestedOffset < layout.viewportTop()) {
            requestedOffset = position - layout.viewportTop();
        } else if (position + height - requestedOffset > layout.viewportBottom()) {
            requestedOffset = position + height - layout.viewportBottom();
        }
        setScrollOffset(requestedOffset, layout);
    }

    /**
     * Applies a conventional keyboard scroll command.
     *
     * @return {@code true} only when the clamped offset changed
     */
    boolean scroll(ScrollCommand command, VerticalScrollLayout layout, int lineStep) {
        int step = Math.max(1, lineStep);
        int page = Math.max(step, layout.viewportHeight() - step);
        int requested =
                switch (command) {
                    case LINE_DOWN -> this.scrollOffset + step;
                    case LINE_UP -> this.scrollOffset - step;
                    case PAGE_DOWN -> this.scrollOffset + page;
                    case PAGE_UP -> this.scrollOffset - page;
                    case START -> 0;
                    case END -> layout.maximumScroll();
                };
        int previous = this.scrollOffset;
        setScrollOffset(requested, layout);
        return this.scrollOffset != previous;
    }

    /** Finds the next eligible item in identity registration order without wrapping. */
    @Nullable T next(T current, boolean forward, Predicate<T> eligible) {
        int currentIndex = this.registrationIndex.getOrDefault(current, -1);
        if (currentIndex < 0) {
            return null;
        }
        int direction = forward ? 1 : -1;
        for (int index = currentIndex + direction; index >= 0 && index < this.items.size(); index += direction) {
            T candidate = this.items.get(index);
            if (eligible.test(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /** Supported allocation-free keyboard scroll commands. */
    enum ScrollCommand {
        LINE_DOWN,
        LINE_UP,
        PAGE_DOWN,
        PAGE_UP,
        START,
        END
    }
}
