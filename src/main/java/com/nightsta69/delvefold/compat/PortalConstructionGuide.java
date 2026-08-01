package com.nightsta69.delvefold.compat;

import java.util.ArrayList;
import java.util.List;

/** Viewer-independent data for Delvefold's in-world portal construction recipe. */
public final class PortalConstructionGuide {
    public static final PortalConstructionGuide INSTANCE = new PortalConstructionGuide(2, 3, 21, 21);
    public static final String RECIPE_NAMESPACE = "delvefold";
    public static final String RECIPE_PATH = "portal_construction";

    public static final String TITLE_KEY = "compat.delvefold.recipe_viewer.portal.category";
    public static final String DIMENSIONS_KEY = "compat.delvefold.recipe_viewer.portal.dimensions";
    public static final String FRAMES_KEY = "compat.delvefold.recipe_viewer.portal.frames";
    public static final String INITIALIZE_KEY = "compat.delvefold.recipe_viewer.portal.initialize";
    public static final String IGNITE_KEY = "compat.delvefold.recipe_viewer.portal.ignite";

    private final int minimumInteriorWidth;
    private final int minimumInteriorHeight;
    private final int maximumInteriorWidth;
    private final int maximumInteriorHeight;
    private final List<FrameCell> minimumFrameCells;

    private PortalConstructionGuide(
            int minimumInteriorWidth,
            int minimumInteriorHeight,
            int maximumInteriorWidth,
            int maximumInteriorHeight) {
        if (minimumInteriorWidth < 1 || minimumInteriorHeight < 1
                || maximumInteriorWidth < minimumInteriorWidth
                || maximumInteriorHeight < minimumInteriorHeight) {
            throw new IllegalArgumentException("Invalid portal guide dimensions");
        }
        this.minimumInteriorWidth = minimumInteriorWidth;
        this.minimumInteriorHeight = minimumInteriorHeight;
        this.maximumInteriorWidth = maximumInteriorWidth;
        this.maximumInteriorHeight = maximumInteriorHeight;
        this.minimumFrameCells = buildFrameCells(minimumOuterWidth(), minimumOuterHeight());
    }

    public int minimumInteriorWidth() {
        return this.minimumInteriorWidth;
    }

    public int minimumInteriorHeight() {
        return this.minimumInteriorHeight;
    }

    public int maximumInteriorWidth() {
        return this.maximumInteriorWidth;
    }

    public int maximumInteriorHeight() {
        return this.maximumInteriorHeight;
    }

    public int minimumOuterWidth() {
        return this.minimumInteriorWidth + 2;
    }

    public int minimumOuterHeight() {
        return this.minimumInteriorHeight + 2;
    }

    public int minimumFrameCount() {
        return frameCount(this.minimumInteriorWidth, this.minimumInteriorHeight);
    }

    public int maximumFrameCount() {
        return frameCount(this.maximumInteriorWidth, this.maximumInteriorHeight);
    }

    public List<FrameCell> minimumFrameCells() {
        return this.minimumFrameCells;
    }

    public static int frameCount(int interiorWidth, int interiorHeight) {
        if (interiorWidth < 1 || interiorHeight < 1) {
            throw new IllegalArgumentException("Portal interior dimensions must be positive");
        }
        return (interiorWidth + 2) * 2 + interiorHeight * 2;
    }

    private static List<FrameCell> buildFrameCells(int outerWidth, int outerHeight) {
        List<FrameCell> cells = new ArrayList<>();
        for (int row = 0; row < outerHeight; row++) {
            for (int column = 0; column < outerWidth; column++) {
                if (row == 0 || row == outerHeight - 1 || column == 0 || column == outerWidth - 1) {
                    cells.add(new FrameCell(column, row));
                }
            }
        }
        return List.copyOf(cells);
    }

    public record FrameCell(int column, int row) {
    }
}
