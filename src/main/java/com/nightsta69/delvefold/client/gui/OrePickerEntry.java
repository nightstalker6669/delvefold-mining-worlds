package com.nightsta69.delvefold.client.gui;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public record OrePickerEntry(
        ResourceLocation id,
        Block block,
        ItemStack icon,
        String translatedName,
        boolean commonTagged,
        boolean oreLike) {
}
