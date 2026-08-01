package com.nightsta69.delvefold.reset;

import java.util.List;
import java.util.Set;

/** Canonical save-folder names for every Delvefold 1.x mining dimension. */
public final class DelvefoldDimensionFolders {
    /** Immutable, stable-order list containing the Classic and Expansive folder for each terrain mode. */
    public static final List<String> ALL = List.of(
            "delve_cavern",
            "delve_cavern_expansive",
            "delve_flat",
            "delve_flat_expansive",
            "delve_wild",
            "delve_wild_expansive");

    /** Immutable membership view of {@link #ALL}; iteration order is not a persisted contract. */
    public static final Set<String> ALL_SET = Set.copyOf(ALL);

    private DelvefoldDimensionFolders() {}
}
