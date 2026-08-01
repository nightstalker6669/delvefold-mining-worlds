package com.nightsta69.delvefold.client.gui;

/** Pure responsive geometry kept in this source so it can be verified without loading Minecraft. */
record ProvinceSettingsLayout(
        boolean compact,
        int contentLeft,
        int contentTop,
        int contentWidth,
        int contentBottom,
        int sectionTitleY,
        Field region,
        Field radius,
        Field verticalThickness,
        Field density,
        Field workCap,
        boolean showHelp,
        int helpY,
        int errorY) {
    private static final int NORMAL_BODY_HEIGHT = 214;

    static ProvinceSettingsLayout calculate(int contentLeft, int contentTop, int contentWidth, int contentBottom) {
        int innerX = contentLeft + 12;
        int innerWidth = Math.max(1, contentWidth - 24);
        boolean compact = contentBottom - contentTop < NORMAL_BODY_HEIGHT;
        int gap = compact ? 6 : 8;
        int half = Math.max(1, (innerWidth - gap) / 2);
        int rightWidth = Math.max(1, innerWidth - half - gap);
        int fieldHeight = compact ? 18 : 20;
        int firstY = contentTop + (compact ? 26 : 37);
        int rowStep = compact ? 29 : 43;
        Field region = new Field(innerX, firstY, half, fieldHeight);
        Field radius = new Field(innerX + half + gap, firstY, rightWidth, fieldHeight);
        Field thickness = new Field(innerX, firstY + rowStep, half, fieldHeight);
        Field density = new Field(innerX + half + gap, firstY + rowStep, rightWidth, fieldHeight);
        Field workCap = new Field(innerX, firstY + rowStep * 2, innerWidth, fieldHeight);
        return new ProvinceSettingsLayout(
                compact,
                contentLeft,
                contentTop,
                contentWidth,
                contentBottom,
                contentTop + (compact ? 4 : 7),
                region,
                radius,
                thickness,
                density,
                workCap,
                !compact,
                compact ? contentBottom : firstY + 113,
                compact ? workCap.bottom() + 2 : firstY + 150);
    }

    static ProvinceSettingsLayout forScreen(int screenWidth, int screenHeight) {
        int horizontalMargin = screenWidth < 500 ? 10 : 20;
        int verticalMargin = screenHeight < 360 ? 8 : 14;
        int panelWidth = Math.min(600, Math.max(1, screenWidth - horizontalMargin * 2));
        int panelHeight = Math.min(330, Math.max(1, screenHeight - verticalMargin * 2));
        int panelLeft = (screenWidth - panelWidth) / 2;
        int panelTop = (screenHeight - panelHeight) / 2;
        int contentLeft = panelLeft + 16;
        int contentTop = panelTop + 56;
        int contentWidth = panelWidth - 32;
        int contentBottom = panelTop + panelHeight - 46;
        return calculate(contentLeft, contentTop, contentWidth, contentBottom);
    }

    int contentRight() {
        return contentLeft + contentWidth;
    }

    record Field(int x, int y, int width, int height) {
        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }

        int labelY() {
            return y - (height == 18 ? 11 : 12);
        }
    }
}
