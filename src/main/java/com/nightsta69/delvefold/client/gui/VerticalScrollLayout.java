package com.nightsta69.delvefold.client.gui;

/**
 * Pure vertical-viewport math shared by administration screens that move real widgets while keeping headers and footers
 * fixed.
 */
record VerticalScrollLayout(int viewportTop, int viewportBottom, int virtualContentBottom) {
    VerticalScrollLayout {
        viewportBottom = Math.max(viewportTop + 1, viewportBottom);
        virtualContentBottom = Math.max(viewportTop, virtualContentBottom);
    }

    int viewportHeight() {
        return viewportBottom - viewportTop;
    }

    int maximumScroll() {
        return Math.max(0, virtualContentBottom - viewportBottom);
    }

    int clamp(int requestedOffset) {
        return Math.clamp(requestedOffset, 0, maximumScroll());
    }

    int screenY(int virtualY, int scrollOffset) {
        return virtualY - clamp(scrollOffset);
    }

    boolean fullyVisible(int screenY, int height) {
        return screenY >= viewportTop && screenY + height <= viewportBottom;
    }
}
