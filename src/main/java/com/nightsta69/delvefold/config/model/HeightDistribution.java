package com.nightsta69.delvefold.config.model;

import com.google.gson.annotations.SerializedName;

public enum HeightDistribution {
    @SerializedName("uniform")
    UNIFORM,
    @SerializedName("triangle")
    TRIANGLE,
    @SerializedName("trapezoid")
    TRAPEZOID
}
