package com.nightsta69.delvefold.client.gui;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Pure responsive geometry for the paged backup-management screen. */
record BackupScreenLayout(
        int listX,
        int listTop,
        int listWidth,
        int listBottom,
        int footerTop,
        int footerY,
        int rowHeight,
        int rowStep,
        int pageSize,
        ActionMode actionMode) {
    static final int MAX_PAGE_SIZE = 6;
    static final int BUTTON_HEIGHT = 20;
    static final int GAP = 4;
    static final int MIN_INLINE_INFO_WIDTH = 140;
    static final int MIN_STACKED_ACTION_WIDTH = 64;
    static final int FOOTER_BUTTON_HEIGHT = 22;

    private static final int PREFERRED_PANEL_WIDTH = 600;
    private static final int PREFERRED_PANEL_HEIGHT = 390;
    private static final int CONTENT_PADDING = 16;
    private static final int HEADER_HEIGHT = 46;
    private static final int FOOTER_HEIGHT = 38;
    private static final int[] INLINE_ACTION_WIDTHS = {50, 58, 64, 54};
    private static final int[] FOOTER_NATURAL_WIDTHS = {72, 58, 58, 126};
    private static final int[] FOOTER_MINIMUM_WIDTHS = {44, 42, 42, 92};

    static BackupScreenLayout forPanel(int panelLeft, int panelTop, int panelWidth, int panelHeight) {
        int contentLeft = panelLeft + CONTENT_PADDING;
        int contentRight = panelLeft + panelWidth - CONTENT_PADDING;
        int contentTop = panelTop + HEADER_HEIGHT + 10;
        int listX = contentLeft + 10;
        int listWidth = Math.max(1, contentRight - contentLeft - 20);
        int listTop = contentTop + 31;
        int footerTop = panelTop + panelHeight - FOOTER_HEIGHT;
        int footerY = panelTop + panelHeight - 29;
        // Keep a visible gutter between the final row and the fixed footer.
        int listBottom = footerTop - 7;

        int inlineWidth = MIN_INLINE_INFO_WIDTH + GAP * INLINE_ACTION_WIDTHS.length;
        for (int width : INLINE_ACTION_WIDTHS) {
            inlineWidth += width;
        }
        ActionMode mode;
        int rowHeight;
        int rowStep;
        if (listWidth >= inlineWidth) {
            mode = ActionMode.INLINE;
            rowHeight = BUTTON_HEIGHT;
            rowStep = 24;
        } else if (listWidth >= MIN_STACKED_ACTION_WIDTH * 4 + GAP * 3) {
            mode = ActionMode.STACKED;
            rowHeight = 43;
            rowStep = 47;
        } else {
            mode = ActionMode.GRID;
            rowHeight = 67;
            rowStep = 71;
        }

        int available = Math.max(0, listBottom - listTop);
        int pageSize = available < rowHeight ? 1 : Math.min(MAX_PAGE_SIZE, 1 + (available - rowHeight) / rowStep);
        return new BackupScreenLayout(
                listX, listTop, listWidth, listBottom, footerTop, footerY, rowHeight, rowStep, pageSize, mode);
    }

    /** Mirrors {@link DelvefoldScreen}'s panel sizing without requiring a Minecraft client. */
    static BackupScreenLayout forScreen(int screenWidth, int screenHeight) {
        int horizontalMargin = screenWidth < 500 ? 10 : 20;
        int verticalMargin = screenHeight < 360 ? 8 : 14;
        int panelWidth = Math.min(PREFERRED_PANEL_WIDTH, Math.max(1, screenWidth - horizontalMargin * 2));
        int panelHeight = Math.min(PREFERRED_PANEL_HEIGHT, Math.max(1, screenHeight - verticalMargin * 2));
        int panelLeft = (screenWidth - panelWidth) / 2;
        int panelTop = (screenHeight - panelHeight) / 2;
        return forPanel(panelLeft, panelTop, panelWidth, panelHeight);
    }

    Bounds infoBounds(int row) {
        int width = this.actionMode == ActionMode.INLINE
                ? this.listWidth - inlineActionsWidth() - GAP * INLINE_ACTION_WIDTHS.length
                : this.listWidth;
        return new Bounds(this.listX, rowY(row), width, BUTTON_HEIGHT);
    }

    Bounds actionBounds(int row, int action) {
        if (action < 0 || action >= INLINE_ACTION_WIDTHS.length) {
            throw new IllegalArgumentException("Backup action index must be between 0 and 3");
        }
        return switch (this.actionMode) {
            case INLINE -> inlineActionBounds(row, action);
            case STACKED -> distributedActionBounds(action, 4, rowY(row) + 23);
            case GRID -> distributedActionBounds(action % 2, 2, rowY(row) + 23 + (action / 2) * 24);
        };
    }

    int rowY(int row) {
        if (row < 0 || row >= this.pageSize) {
            throw new IllegalArgumentException("Backup row is outside the current page");
        }
        return this.listTop + row * this.rowStep;
    }

    Footer footer(boolean showPagination, boolean showCancel) {
        int left = this.listX - 10;
        int width = this.listWidth + 20;
        int right = left + width;
        Bounds back = new Bounds(left, this.footerY, FOOTER_NATURAL_WIDTHS[0], FOOTER_BUTTON_HEIGHT);
        Bounds previous = showPagination
                ? new Bounds(left + 78, this.footerY, FOOTER_NATURAL_WIDTHS[1], FOOTER_BUTTON_HEIGHT)
                : null;
        Bounds next = showPagination
                ? new Bounds(left + 142, this.footerY, FOOTER_NATURAL_WIDTHS[2], FOOTER_BUTTON_HEIGHT)
                : null;
        Bounds cancel = showCancel
                ? new Bounds(
                        right - FOOTER_NATURAL_WIDTHS[3], this.footerY, FOOTER_NATURAL_WIDTHS[3], FOOTER_BUTTON_HEIGHT)
                : null;

        Bounds lastNavigation = showPagination ? Objects.requireNonNull(next) : back;
        boolean naturalFits =
                lastNavigation.right() <= right && (cancel == null || lastNavigation.right() + 6 <= cancel.x());
        if (naturalFits) {
            return new Footer(back, previous, next, cancel, false, false);
        }

        int[] actions = showPagination
                ? (showCancel ? new int[] {0, 1, 2, 3} : new int[] {0, 1, 2})
                : (showCancel ? new int[] {0, 3} : new int[] {0});
        int compactGap = 4;
        int compactMinimum = compactGap * (actions.length - 1);
        for (int action : actions) {
            compactMinimum += FOOTER_MINIMUM_WIDTHS[action];
        }
        if (compactMinimum <= width) {
            Bounds[] compact = compactFooterBounds(left, width, this.footerY, actions, compactGap);
            return footerFrom(actions, compact, false, true);
        }

        Bounds[] navigation = showPagination
                ? distributedBounds(left, width, this.footerY, 3, 4, FOOTER_BUTTON_HEIGHT)
                : new Bounds[] {
                    new Bounds(left, this.footerY, Math.min(width, FOOTER_NATURAL_WIDTHS[0]), FOOTER_BUTTON_HEIGHT)
                };
        Bounds wrappedCancel = showCancel
                ? new Bounds(
                        right - Math.min(width, FOOTER_NATURAL_WIDTHS[3]),
                        this.footerY - 24,
                        Math.min(width, FOOTER_NATURAL_WIDTHS[3]),
                        FOOTER_BUTTON_HEIGHT)
                : null;
        return new Footer(
                navigation[0],
                showPagination ? navigation[1] : null,
                showPagination ? navigation[2] : null,
                wrappedCancel,
                showCancel,
                true);
    }

    private Bounds inlineActionBounds(int row, int action) {
        int x = this.infoBounds(row).right() + GAP;
        for (int index = 0; index < action; index++) {
            x += INLINE_ACTION_WIDTHS[index] + GAP;
        }
        return new Bounds(x, rowY(row), INLINE_ACTION_WIDTHS[action], BUTTON_HEIGHT);
    }

    private Bounds distributedActionBounds(int column, int columns, int y) {
        return distributedBounds(this.listX, this.listWidth, y, columns, GAP, BUTTON_HEIGHT)[column];
    }

    private static Bounds[] compactFooterBounds(int left, int width, int y, int[] actions, int gap) {
        int[] widths = new int[actions.length];
        int used = gap * (actions.length - 1);
        for (int index = 0; index < actions.length; index++) {
            widths[index] = FOOTER_MINIMUM_WIDTHS[actions[index]];
            used += widths[index];
        }
        int remaining = width - used;
        while (remaining > 0) {
            boolean expanded = false;
            for (int index = 0; index < actions.length && remaining > 0; index++) {
                if (widths[index] < FOOTER_NATURAL_WIDTHS[actions[index]]) {
                    widths[index]++;
                    remaining--;
                    expanded = true;
                }
            }
            if (!expanded) {
                widths[widths.length - 1] += remaining;
                remaining = 0;
            }
        }
        Bounds[] result = new Bounds[actions.length];
        int x = left;
        for (int index = 0; index < actions.length; index++) {
            result[index] = new Bounds(x, y, widths[index], FOOTER_BUTTON_HEIGHT);
            x += widths[index] + gap;
        }
        return result;
    }

    private static Footer footerFrom(int[] actions, Bounds[] bounds, boolean wrapped, boolean compact) {
        Bounds[] byAction = new Bounds[FOOTER_NATURAL_WIDTHS.length];
        for (int index = 0; index < actions.length; index++) {
            byAction[actions[index]] = bounds[index];
        }
        return new Footer(Objects.requireNonNull(byAction[0]), byAction[1], byAction[2], byAction[3], wrapped, compact);
    }

    private static Bounds[] distributedBounds(int left, int width, int y, int columns, int gap, int height) {
        int available = width - gap * (columns - 1);
        int baseWidth = available / columns;
        int remainder = available % columns;
        Bounds[] result = new Bounds[columns];
        for (int column = 0; column < columns; column++) {
            int x = left + column * baseWidth + Math.min(column, remainder) + column * gap;
            int columnWidth = baseWidth + (column < remainder ? 1 : 0);
            result[column] = new Bounds(x, y, columnWidth, height);
        }
        return result;
    }

    private static int inlineActionsWidth() {
        int width = 0;
        for (int actionWidth : INLINE_ACTION_WIDTHS) {
            width += actionWidth;
        }
        return width;
    }

    enum ActionMode {
        INLINE,
        STACKED,
        GRID
    }

    record Footer(
            Bounds back,
            @Nullable Bounds previous,
            @Nullable Bounds next,
            @Nullable Bounds cancel,
            boolean wrapped,
            boolean compact) {}

    record Bounds(int x, int y, int width, int height) {
        int right() {
            return this.x + this.width;
        }

        int bottom() {
            return this.y + this.height;
        }

        boolean overlaps(Bounds other) {
            return this.x < other.right()
                    && this.right() > other.x
                    && this.y < other.bottom()
                    && this.bottom() > other.y;
        }
    }
}
