package com.nightsta69.delvefold.client.gui;

/**
 * Immutable pagination geometry for the ore-target candidate grid.
 *
 * @param columns responsive grid column count
 * @param rows grid row count for the current compact mode
 * @param pageSize maximum candidates displayed per page
 * @param pageCount total page count, always at least one
 * @param page clamped zero-based page
 * @param start inclusive candidate index
 * @param end exclusive candidate index
 */
record OreRuleWizardLayout(int columns, int rows, int pageSize, int pageCount, int page, int start, int end) {
    /**
     * Calculates the established target-grid breakpoints and bounded page window.
     *
     * @param panelWidth complete screen-panel width
     * @param compact whether the height-constrained two-row layout is active
     * @param candidateCount nonnegative candidate count
     * @param requestedPage requested zero-based page
     * @return deterministic responsive layout
     */
    static OreRuleWizardLayout calculate(int panelWidth, boolean compact, int candidateCount, int requestedPage) {
        int safeCount = Math.max(0, candidateCount);
        int columns = columns(panelWidth);
        int rows = compact ? 2 : 4;
        int pageSize = columns * rows;
        int pageCount = safeCount == 0 ? 1 : (safeCount - 1) / pageSize + 1;
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        int start = (int) Math.min((long) page * pageSize, safeCount);
        int end = (int) Math.min((long) start + pageSize, safeCount);
        return new OreRuleWizardLayout(columns, rows, pageSize, pageCount, page, start, end);
    }

    /**
     * Resolves the target-grid column breakpoint used by initialization and rendering.
     *
     * @param panelWidth complete screen-panel width
     * @return two, three, or four columns
     */
    static int columns(int panelWidth) {
        if (panelWidth >= 520) {
            return 4;
        }
        return panelWidth >= 390 ? 3 : 2;
    }

    /**
     * Tests whether a candidate index belongs to this page window.
     *
     * @param candidateIndex zero-based candidate index
     * @return whether the index lies in {@code [start, end)}
     */
    boolean contains(int candidateIndex) {
        return candidateIndex >= this.start && candidateIndex < this.end;
    }
}
