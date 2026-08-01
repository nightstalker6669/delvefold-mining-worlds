package com.nightsta69.delvefold.world.landmark.catalog;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** Existing world-identity toggles that can narrow the reloadable landmark catalog. */
public enum LandmarkCategory implements StringRepresentable {
    SURVEY_STATION("survey_station"),
    MOTHERLODE("motherlode"),
    FAULT_LINE("fault_line");

    public static final Codec<LandmarkCategory> CODEC = StringRepresentable.fromEnum(LandmarkCategory::values);

    private final String serializedName;

    LandmarkCategory(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
