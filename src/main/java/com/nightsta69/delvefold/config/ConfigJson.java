package com.nightsta69.delvefold.config;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/** Provides the canonical, thread-safe Gson codec used for Delvefold configuration and exported JSON. */
public final class ConfigJson {
    /**
     * Shared codec using snake-case fields, explicit nulls, pretty printing, and unescaped safe Unicode/HTML
     * characters.
     *
     * <p>Structural field allowlisting, byte limits, schema checks, and path safety are repository responsibilities and
     * are not implied by direct use of this codec.
     */
    public static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .serializeNulls()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private ConfigJson() {}
}
