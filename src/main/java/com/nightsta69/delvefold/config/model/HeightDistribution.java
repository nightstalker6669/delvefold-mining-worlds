package com.nightsta69.delvefold.config.model;

import com.google.gson.annotations.SerializedName;

/** Defines how a spawn band's vertical sample is distributed across its inclusive height range. */
public enum HeightDistribution {
    /** Gives every integer height from {@code min_y} through {@code max_y} equal probability. */
    @SerializedName("uniform")
    UNIFORM,
    /** Linearly increases probability toward {@code peak_y} and decreases it beyond the peak. */
    @SerializedName("triangle")
    TRIANGLE,
    /** Uses linear shoulders around an equally weighted {@code plateau_min_y} through {@code plateau_max_y}. */
    @SerializedName("trapezoid")
    TRAPEZOID
}
