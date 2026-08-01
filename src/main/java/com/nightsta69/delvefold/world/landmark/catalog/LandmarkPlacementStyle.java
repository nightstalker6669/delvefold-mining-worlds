package com.nightsta69.delvefold.world.landmark.catalog;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** How a landmark's template origin is anchored to generated terrain. */
public enum LandmarkPlacementStyle implements StringRepresentable {
    SURFACE("surface"),
    CAVE_FLOOR("cave_floor"),
    BURIED("buried");

    public static final Codec<LandmarkPlacementStyle> CODEC =
            StringRepresentable.fromEnum(LandmarkPlacementStyle::values);

    private final String serializedName;

    LandmarkPlacementStyle(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
