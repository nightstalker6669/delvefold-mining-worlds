package com.nightsta69.delvefold.client.gui;

/** Pure responsive grid and pager geometry for the registry-backed ore picker. */
record OrePickerLayout(int columns, int rows, int gridLeft, int gridTop, int gridWidth, int pagerY) {
    static final int TILE_STEP = 27;
    static final int PAGER_GAP = 8;
    static final int PAGER_HEIGHT = 20;
    static final int FOOTER_GAP = 2;

    /**
     * Calculates a bounded tile grid whose pager remains above the fixed administration footer.
     *
     * @param panelLeft complete panel left edge
     * @param panelWidth complete panel width
     * @param contentWidth responsive content width
     * @param gridTop top edge reserved for the tile grid
     * @param footerY top edge of the fixed footer buttons
     * @return deterministic picker grid and pager geometry
     */
    static OrePickerLayout calculate(int panelLeft, int panelWidth, int contentWidth, int gridTop, int footerY) {
        int columns = Math.max(6, Math.min(14, (contentWidth - 20) / TILE_STEP));
        int availableForTiles = footerY - FOOTER_GAP - PAGER_HEIGHT - PAGER_GAP - gridTop;
        int rows = Math.max(1, Math.min(5, availableForTiles / TILE_STEP));
        int gridWidth = columns * TILE_STEP;
        int gridLeft = panelLeft + (panelWidth - gridWidth) / 2;
        int pagerY = gridTop + rows * TILE_STEP + PAGER_GAP;
        return new OrePickerLayout(columns, rows, gridLeft, gridTop, gridWidth, pagerY);
    }

    /** Returns the number of entries displayed on one picker page. */
    int pageSize() {
        return this.columns * this.rows;
    }

    /** Returns the exclusive bottom edge of the pager controls. */
    int pagerBottom() {
        return this.pagerY + PAGER_HEIGHT;
    }
}
