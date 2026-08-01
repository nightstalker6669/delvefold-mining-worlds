package com.nightsta69.delvefold.client.gui;

/** Pure layout calculation kept testable without loading Minecraft client classes. */
record GuideLayout(
        int panelLeft,
        int panelTop,
        int panelWidth,
        int panelHeight,
        int listTop,
        int listBottom,
        int doneX,
        int doneY,
        int doneWidth,
        int doneHeight,
        int footerTextWidth) {

    static GuideLayout calculate(int screenWidth, int screenHeight) {
        int horizontalMargin = screenWidth < 500 ? 8 : 16;
        int verticalMargin = screenHeight < 360 ? 6 : 10;
        int panelWidth = Math.min(760, Math.max(1, screenWidth - horizontalMargin * 2));
        int panelHeight = Math.min(450, Math.max(1, screenHeight - verticalMargin * 2));
        int panelLeft = (screenWidth - panelWidth) / 2;
        int panelTop = (screenHeight - panelHeight) / 2;
        int listTop = panelTop + 112;
        int listBottom = Math.max(listTop, panelTop + panelHeight - 42);
        int doneWidth = Math.min(110, Math.max(70, panelWidth / 4));
        int doneX = panelLeft + panelWidth - doneWidth - 12;
        int doneY = panelTop + panelHeight - 31;
        int footerTextWidth = Math.max(0, doneX - 8 - (panelLeft + 14));
        return new GuideLayout(panelLeft, panelTop, panelWidth, panelHeight,
                listTop, listBottom, doneX, doneY, doneWidth, 20, footerTextWidth);
    }
}
