package com.nightsta69.delvefold.world.landmark.catalog;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** How a landmark's template origin is anchored to generated terrain. */
public enum LandmarkPlacementStyle implements StringRepresentable {
    /** Anchors the template to the world-surface heightmap within the configured block-Y bounds. */
    SURFACE("surface"),
    /** Searches downward for a solid floor with three air blocks of clearance. */
    CAVE_FLOOR("cave_floor"),
    /** Chooses an inclusive random block-Y value inside the configured bounds. */
    BURIED("buried");

    /** String codec used by landmark datapack definitions. */
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
