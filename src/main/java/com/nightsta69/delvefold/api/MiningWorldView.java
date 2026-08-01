package com.nightsta69.delvefold.api;

import net.minecraft.resources.ResourceLocation;

/**
 * Immutable public snapshot safe for integrations to retain.
 *
 * @param displayName administrator-selected player-facing world name
 * @param dimensionId registered dimension identifier; stable across recreation for the selected terrain variant
 * @param terrain serialized terrain mode
 * @param terrainVariant serialized terrain variant
 * @param activeProfileId server-authoritative active ore-profile identifier
 * @param generationEpoch non-negative recreation generation counter
 * @param renewalEnabled whether scheduled renewal is enabled in the projected settings
 */
public record MiningWorldView(
        String displayName,
        ResourceLocation dimensionId,
        String terrain,
        String terrainVariant,
        String activeProfileId,
        long generationEpoch,
        boolean renewalEnabled) {}
