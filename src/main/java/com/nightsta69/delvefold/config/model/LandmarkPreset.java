package com.nightsta69.delvefold.config.model;

import java.util.Locale;

/** Controls the deterministic acceptance density of reloadable landmark candidates in newly generated chunks. */
public enum LandmarkPreset {
    /** Rejects all landmark candidates, producing a resource-focused mining world. */
    PURE_MINING,
    /** Accepts 25 percent of otherwise eligible deterministic candidates. */
    BALANCED,
    /** Accepts 75 percent of otherwise eligible deterministic candidates. */
    ABUNDANT;

    /**
     * Returns the lowercase schema-2 representation using a locale-independent conversion.
     *
     * @return serialized preset name
     */
    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
