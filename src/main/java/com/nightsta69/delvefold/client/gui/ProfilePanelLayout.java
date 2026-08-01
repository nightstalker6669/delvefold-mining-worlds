package com.nightsta69.delvefold.client.gui;

/** Pure responsive layout for the dashboard's profile list and creation controls. */
record ProfilePanelLayout(int controlsY, int pageSize, int rowStep, int rowHeight) {
    static ProfilePanelLayout calculate(int bodyTop, int contentBottom, boolean compact) {
        int preferred = bodyTop + (compact ? 145 : 154);
        int controlsY = Math.max(bodyTop + 25, Math.min(preferred, contentBottom - 45));
        int rowStep = compact ? 21 : 23;
        int rowHeight = compact ? 18 : 20;
        int available = Math.max(0, controlsY - (bodyTop + 25));
        int pageSize = Math.clamp(available / rowStep, 1, 5);
        return new ProfilePanelLayout(controlsY, pageSize, rowStep, rowHeight);
    }
}
