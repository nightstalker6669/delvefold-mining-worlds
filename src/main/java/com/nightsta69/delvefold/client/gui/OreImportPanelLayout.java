package com.nightsta69.delvefold.client.gui;

/** Pure vertical geometry for scan and preview rows in the guided ore importer. */
record OreImportPanelLayout(int controlsY, int rowsTop, int availableRowsHeight) {
    private static final int ROWS_BOTTOM_GUTTER = 14;

    /** Calculates the scan controls and group-row viewport without changing the established spacing. */
    static OreImportPanelLayout scan(int contentTop, int contentBottom, boolean truncated) {
        int controlsY = contentTop + 24;
        int rowsTop = controlsY + (truncated ? 54 : 39);
        return new OreImportPanelLayout(controlsY, rowsTop, Math.max(0, contentBottom - rowsTop - ROWS_BOTTOM_GUTTER));
    }

    /**
     * Calculates the preview field and diff-row viewport while reserving the bottom feedback line.
     *
     * <p>A height-constrained truncated preview can intentionally have no complete diff row; the fixed footer and
     * validation/status line remain usable instead of being covered by a partial row.
     */
    static OreImportPanelLayout preview(int contentTop, int contentBottom, boolean truncated) {
        int fieldY = contentTop + 23;
        int rowsTop = fieldY + (truncated ? 70 : 58);
        return new OreImportPanelLayout(fieldY, rowsTop, Math.max(0, contentBottom - rowsTop - ROWS_BOTTOM_GUTTER));
    }

    /** Returns how many complete rows of the requested height fit in the reserved viewport. */
    int completeRows(int rowHeight) {
        if (rowHeight <= 0) {
            throw new IllegalArgumentException("Row height must be positive");
        }
        return this.availableRowsHeight / rowHeight;
    }
}
