package com.nightsta69.delvefold.config.model;

public enum TerrainVariant {
    CLASSIC,
    EXPANSIVE;

    public String serializedName() {
        return name().toLowerCase();
    }
}
