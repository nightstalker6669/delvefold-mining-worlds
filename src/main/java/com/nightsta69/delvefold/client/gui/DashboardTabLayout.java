package com.nightsta69.delvefold.client.gui;

/** Pure responsive chrome, body-viewport, and pagination calculations for dashboard tabs. */
record DashboardTabLayout(int contentWidth, int panelHeight, int contentTop, int contentBottom, boolean compactChrome) {
    private static final int MIN_BODY_VIEWPORT_HEIGHT = 22;

    int tabHeight() {
        return compactChrome ? 18 : 24;
    }

    int bodyTop() {
        return contentTop + (compactChrome ? 20 : 32);
    }

    int bodyViewportTop() {
        int preferred = bodyTop() + (compactChrome ? 18 : 22);
        int latest = Math.max(bodyTop() + 1, contentBottom - 1 - MIN_BODY_VIEWPORT_HEIGHT);
        return Math.min(preferred, latest);
    }

    int bodyViewportHeight() {
        return Math.max(0, contentBottom - 1 - bodyViewportTop());
    }

    boolean compactHeight() {
        return panelHeight < 350;
    }

    boolean worldContentStacked() {
        return contentWidth < 430 || compactHeight();
    }

    int footerDoneWidth() {
        return Math.min(76, Math.max(54, contentWidth / 5));
    }

    int footerPagerButtonWidth() {
        int spaceBeforeDone = Math.max(2, contentWidth - footerDoneWidth() - 8);
        return Math.min(64, Math.max(1, (spaceBeforeDone - 6) / 2));
    }

    ProfilePanelLayout profilePanel() {
        return ProfilePanelLayout.calculate(bodyTop(), contentBottom, compactHeight());
    }

    Page page(int totalEntries, int requestedPage, int pageSize) {
        if (pageSize < 1) {
            throw new IllegalArgumentException("Dashboard page size must be positive");
        }
        int safeTotal = Math.max(0, totalEntries);
        int pageCount = (int) Math.max(1L, (safeTotal + (long) pageSize - 1L) / pageSize);
        int page = Math.clamp(requestedPage, 0, pageCount - 1);
        int start = (int) Math.min(safeTotal, (long) page * pageSize);
        int end = (int) Math.min(safeTotal, start + (long) pageSize);
        return new Page(page, pageCount, start, end);
    }

    /** Clamped zero-based page and its half-open entry range. */
    record Page(int index, int count, int start, int end) {}
}
