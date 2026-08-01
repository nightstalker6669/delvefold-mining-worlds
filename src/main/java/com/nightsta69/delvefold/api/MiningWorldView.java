package com.nightsta69.delvefold.api;

import net.minecraft.resources.ResourceLocation;

/** Immutable public snapshot safe for integrations to retain. */
public record MiningWorldView(
        String displayName,
        ResourceLocation dimensionId,
        String terrain,
        String terrainVariant,
        String activeProfileId,
        long generationEpoch,
        boolean renewalEnabled) {}
