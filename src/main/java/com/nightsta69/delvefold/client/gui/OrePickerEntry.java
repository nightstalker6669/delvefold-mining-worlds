package com.nightsta69.delvefold.client.gui;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * Immutable client-registry entry rendered by the ore picker.
 *
 * @param id exact registered block identifier
 * @param block registered block instance
 * @param icon item stack owned by this entry for rendering
 * @param translatedName client-localized display name captured for filtering and sorting
 * @param commonTagged whether the block belongs to the conventional {@code c:ores} tag
 * @param oreLike whether discovery heuristics classify the block as an ore candidate
 */
public record OrePickerEntry(
        ResourceLocation id,
        Block block,
        ItemStack icon,
        String translatedName,
        boolean commonTagged,
        boolean oreLike) {}
