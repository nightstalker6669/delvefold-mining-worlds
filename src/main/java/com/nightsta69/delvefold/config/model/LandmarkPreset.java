package com.nightsta69.delvefold.config.model;

public enum LandmarkPreset {
    PURE_MINING,
    BALANCED,
    ABUNDANT;

    public String serializedName() {
        return name().toLowerCase();
    }
}
