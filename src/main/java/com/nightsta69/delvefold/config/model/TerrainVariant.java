package com.nightsta69.delvefold.config.model;

import java.util.Locale;

/** Selects the recreation-locked vertical and horizontal scale variant for a terrain family. */
public enum TerrainVariant {
    /** Preserves the original registered terrain scale and generation contract. */
    CLASSIC,
    /** Uses the deeper flat/cavern or amplified wild registered dimension variant. */
    EXPANSIVE;

    /**
     * Returns the lowercase schema-2 representation using a locale-independent conversion.
     *
     * @return serialized variant name
     */
    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
