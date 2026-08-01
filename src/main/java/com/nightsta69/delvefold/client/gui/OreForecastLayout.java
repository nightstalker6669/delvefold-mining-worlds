package com.nightsta69.delvefold.client.gui;

/**
 * Pure responsive geometry for the ore-forecast summary, graph, and scrollable rule list.
 *
 * <p>The compact summary deliberately gives the rule viewport priority. At Minecraft's minimum supported logical height
 * of 240 pixels, this leaves room for one complete rule row instead of allowing the fixed summary to consume the rules
 * card.
 */
record OreForecastLayout(
        int contentLeft,
        int contentTop,
        int contentWidth,
        int summaryHeight,
        boolean compactSummary,
        int bodyTop,
        int bodyBottom,
        int graphWidth,
        int rulesLeft,
        int rulesWidth,
        int ruleViewportLeft,
        int ruleViewportTop,
        int ruleViewportWidth,
        int ruleViewportBottom,
        int ruleRowHeight,
        boolean compactColumns) {
    private static final int SECTION_GAP = 6;
    private static final int RULE_HEADER_HEIGHT = 25;
    private static final int RULE_BOTTOM_INSET = 6;
    private static final int COMPACT_SUMMARY_HEIGHT = 42;
    private static final int STANDARD_SUMMARY_HEIGHT = 93;
    private static final int TRUNCATED_SUMMARY_HEIGHT = 104;
    private static final int COMPACT_RULE_ROW_HEIGHT = 43;
    private static final int STANDARD_RULE_ROW_HEIGHT = 45;

    /** Calculates forecast geometry from the already-responsive administration panel. */
    static OreForecastLayout calculate(AdminPanelLayout panel, boolean truncated) {
        int panelRight = panel.left() + Math.max(0, panel.width());
        int panelBottom = panel.top() + Math.max(0, panel.height());
        int contentLeft = Math.clamp(panel.contentLeft(), panel.left(), panelRight);
        int contentTop = Math.clamp(panel.contentTop(), panel.top(), panelBottom);
        int contentRight = Math.clamp(panel.contentRight(), contentLeft, panelRight);
        int contentBottom = Math.clamp(panel.contentBottom(), contentTop, panelBottom);
        int contentWidth = contentRight - contentLeft;
        int contentHeight = contentBottom - contentTop;

        boolean compactColumns = panel.width() < 610 || panel.height() < 340;
        int ruleRowHeight = compactColumns ? COMPACT_RULE_ROW_HEIGHT : STANDARD_RULE_ROW_HEIGHT;
        int fullSummaryHeight = truncated ? TRUNCATED_SUMMARY_HEIGHT : STANDARD_SUMMARY_HEIGHT;
        int minimumRuleBodyHeight = RULE_HEADER_HEIGHT + ruleRowHeight + RULE_BOTTOM_INSET;
        int summaryBudget = Math.max(0, contentHeight - SECTION_GAP - minimumRuleBodyHeight);
        boolean compactSummary = fullSummaryHeight > summaryBudget;
        int summaryHeight = compactSummary ? Math.min(COMPACT_SUMMARY_HEIGHT, summaryBudget) : fullSummaryHeight;

        int bodyTop = Math.min(contentBottom, contentTop + summaryHeight + (summaryHeight > 0 ? SECTION_GAP : 0));
        int bodyBottom = contentBottom;
        int graphWidth = compactColumns ? 0 : Math.min(276, Math.max(190, contentWidth * 2 / 5));
        int rulesLeft = compactColumns ? contentLeft : Math.min(contentRight, contentLeft + graphWidth + SECTION_GAP);
        int rulesWidth = Math.max(0, contentRight - rulesLeft);
        int ruleViewportLeft = Math.min(contentRight, rulesLeft + 8);
        int ruleViewportTop = Math.min(bodyBottom, bodyTop + RULE_HEADER_HEIGHT);
        int ruleViewportWidth = Math.max(0, rulesWidth - 16);
        int ruleViewportBottom = Math.max(ruleViewportTop, bodyBottom - RULE_BOTTOM_INSET);

        return new OreForecastLayout(
                contentLeft,
                contentTop,
                contentWidth,
                summaryHeight,
                compactSummary,
                bodyTop,
                bodyBottom,
                graphWidth,
                rulesLeft,
                rulesWidth,
                ruleViewportLeft,
                ruleViewportTop,
                ruleViewportWidth,
                ruleViewportBottom,
                ruleRowHeight,
                compactColumns);
    }

    /** Returns the nonnegative rules-card height. */
    int bodyHeight() {
        return Math.max(0, this.bodyBottom - this.bodyTop);
    }

    /** Returns the nonnegative scroll/scissor height available to rule rows. */
    int ruleViewportHeight() {
        return Math.max(0, this.ruleViewportBottom - this.ruleViewportTop);
    }

    /** Returns whether the height graph has a separate, drawable card. */
    boolean showsHeightGraph() {
        return !this.compactColumns && this.graphWidth > 0 && this.bodyHeight() > 0;
    }
}
