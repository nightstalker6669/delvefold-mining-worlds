package com.nightsta69.delvefold.client.gui;

/** Pure responsive outer-panel and chrome geometry shared by all administration screens. */
record AdminPanelLayout(int left, int top, int width, int height) {
    private static final int COMPACT_WIDTH_THRESHOLD = 300;
    private static final int COMPACT_HEIGHT_THRESHOLD = 180;
    private static final int COMPACT_CONTENT_PADDING = 6;
    private static final int STANDARD_CONTENT_PADDING = 16;
    private static final int COMPACT_HEADER_HEIGHT = 18;
    private static final int STANDARD_HEADER_HEIGHT = 46;
    private static final int COMPACT_FOOTER_HEIGHT = 26;
    private static final int STANDARD_FOOTER_HEIGHT = 38;

    /** Calculates the existing centered panel policy for one logical GUI size. */
    static AdminPanelLayout calculate(int screenWidth, int screenHeight, int preferredWidth, int preferredHeight) {
        int horizontalMargin = screenWidth < COMPACT_WIDTH_THRESHOLD ? 2 : screenWidth < 500 ? 10 : 20;
        int verticalMargin = screenHeight < COMPACT_HEIGHT_THRESHOLD ? 2 : screenHeight < 360 ? 8 : 14;
        int width = Math.min(preferredWidth, Math.max(1, screenWidth - horizontalMargin * 2));
        int height = Math.min(preferredHeight, Math.max(1, screenHeight - verticalMargin * 2));
        return new AdminPanelLayout((screenWidth - width) / 2, (screenHeight - height) / 2, width, height);
    }

    /** Returns whether reduced chrome is required to leave a usable content viewport. */
    boolean compactChrome() {
        return this.height < COMPACT_HEIGHT_THRESHOLD;
    }

    /** Returns the responsive horizontal inset between the panel and its content. */
    int contentPadding() {
        return this.width < COMPACT_WIDTH_THRESHOLD ? COMPACT_CONTENT_PADDING : STANDARD_CONTENT_PADDING;
    }

    /** Returns the responsive header height in logical GUI pixels. */
    int headerHeight() {
        return compactChrome() ? COMPACT_HEADER_HEIGHT : STANDARD_HEADER_HEIGHT;
    }

    /** Returns the responsive footer height in logical GUI pixels. */
    int footerHeight() {
        return compactChrome() ? COMPACT_FOOTER_HEIGHT : STANDARD_FOOTER_HEIGHT;
    }

    /** Returns the left content boundary. */
    int contentLeft() {
        return this.left + contentPadding();
    }

    /** Returns the exclusive right content boundary. */
    int contentRight() {
        return this.left + this.width - contentPadding();
    }

    /** Returns the top content boundary below the responsive header. */
    int contentTop() {
        return this.top + headerHeight() + (compactChrome() ? 2 : 10);
    }

    /** Returns the bottom content boundary above the responsive footer. */
    int contentBottom() {
        return this.top + this.height - footerHeight() - (compactChrome() ? 1 : 8);
    }

    /** Returns the common Y coordinate for footer buttons. */
    int footerButtonY() {
        return this.top + this.height - (compactChrome() ? 24 : 29);
    }

    /** Returns the title baseline inside the responsive header. */
    int titleY() {
        return this.top + (compactChrome() ? 5 : 16);
    }
}
