package com.nightsta69.delvefold.compat;

import java.util.ArrayList;
import java.util.List;

/** Viewer-independent data for Delvefold's in-world portal construction recipe. */
public final class PortalConstructionGuide {
    /** Canonical immutable guide matching Delvefold's supported portal dimensions. */
    public static final PortalConstructionGuide INSTANCE = new PortalConstructionGuide(2, 3, 21, 21);
    /** Namespace used by recipe-viewer integrations for the synthetic portal recipe. */
    public static final String RECIPE_NAMESPACE = "delvefold";
    /** Path used by recipe-viewer integrations for the synthetic portal recipe. */
    public static final String RECIPE_PATH = "portal_construction";

    /** Translation key for the recipe-viewer category title. */
    public static final String TITLE_KEY = "compat.delvefold.recipe_viewer.portal.category";
    /** Translation key describing supported portal dimensions. */
    public static final String DIMENSIONS_KEY = "compat.delvefold.recipe_viewer.portal.dimensions";
    /** Translation key describing the required frame blocks. */
    public static final String FRAMES_KEY = "compat.delvefold.recipe_viewer.portal.frames";
    /** Translation key explaining mining-world initialization. */
    public static final String INITIALIZE_KEY = "compat.delvefold.recipe_viewer.portal.initialize";
    /** Translation key explaining flint-and-steel ignition. */
    public static final String IGNITE_KEY = "compat.delvefold.recipe_viewer.portal.ignite";

    private final int minimumInteriorWidth;
    private final int minimumInteriorHeight;
    private final int maximumInteriorWidth;
    private final int maximumInteriorHeight;
    private final List<FrameCell> minimumFrameCells;

    private PortalConstructionGuide(
            int minimumInteriorWidth, int minimumInteriorHeight, int maximumInteriorWidth, int maximumInteriorHeight) {
        if (minimumInteriorWidth < 1
                || minimumInteriorHeight < 1
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

    /**
     * Returns the minimum interior width.
     *
     * @return minimum supported interior width in blocks
     */
    public int minimumInteriorWidth() {
        return this.minimumInteriorWidth;
    }

    /**
     * Returns the minimum interior height.
     *
     * @return minimum supported interior height in blocks
     */
    public int minimumInteriorHeight() {
        return this.minimumInteriorHeight;
    }

    /**
     * Returns the maximum interior width.
     *
     * @return maximum supported interior width in blocks
     */
    public int maximumInteriorWidth() {
        return this.maximumInteriorWidth;
    }

    /**
     * Returns the maximum interior height.
     *
     * @return maximum supported interior height in blocks
     */
    public int maximumInteriorHeight() {
        return this.maximumInteriorHeight;
    }

    /**
     * Returns the minimum outer width.
     *
     * @return minimum outer frame width in blocks, including both frame columns
     */
    public int minimumOuterWidth() {
        return this.minimumInteriorWidth + 2;
    }

    /**
     * Returns the minimum outer height.
     *
     * @return minimum outer frame height in blocks, including both frame rows
     */
    public int minimumOuterHeight() {
        return this.minimumInteriorHeight + 2;
    }

    /**
     * Returns the minimum frame-block count.
     *
     * @return number of frame blocks required for the minimum supported portal
     */
    public int minimumFrameCount() {
        return frameCount(this.minimumInteriorWidth, this.minimumInteriorHeight);
    }

    /**
     * Returns the maximum frame-block count.
     *
     * @return number of frame blocks required for the maximum supported portal
     */
    public int maximumFrameCount() {
        return frameCount(this.maximumInteriorWidth, this.maximumInteriorHeight);
    }

    /**
     * Returns the minimum frame geometry.
     *
     * @return immutable row-major cells forming the minimum portal's outer frame
     */
    public List<FrameCell> minimumFrameCells() {
        return this.minimumFrameCells;
    }

    /**
     * Calculates the frame-block count for a rectangular portal interior.
     *
     * @param interiorWidth positive interior width in blocks
     * @param interiorHeight positive interior height in blocks
     * @return number of blocks in the one-block-thick rectangular perimeter
     * @throws IllegalArgumentException if either interior dimension is less than one
     */
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

    /**
     * Zero-based outer-frame cell used by recipe viewers to draw the construction layout.
     *
     * @param column horizontal cell offset from the frame's left edge
     * @param row vertical cell offset from the frame's top edge
     */
    public record FrameCell(int column, int row) {}
}
