package com.nightsta69.delvefold.world.landmark.catalog;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** Existing world-identity toggles that can narrow the reloadable landmark catalog. */
public enum LandmarkCategory implements StringRepresentable {
    /** Survey camps and lift stations governed by the survey-station identity toggle. */
    SURVEY_STATION("survey_station"),
    /** Geode vaults and motherlode chambers governed by the motherlode identity toggle. */
    MOTHERLODE("motherlode"),
    /** Collapsed entrances and fault-line grottos governed by the fault-line identity toggle. */
    FAULT_LINE("fault_line");

    /** String codec used by landmark datapack definitions. */
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
