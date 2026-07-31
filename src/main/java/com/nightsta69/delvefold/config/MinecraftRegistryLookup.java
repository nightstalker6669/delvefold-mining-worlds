package com.nightsta69.delvefold.config;

import com.nightsta69.delvefold.config.validation.RegistryLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class MinecraftRegistryLookup implements RegistryLookup {
    @Override
    public boolean blockExists(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location != null && BuiltInRegistries.BLOCK.containsKey(location);
    }

    @Override
    public boolean blockTagExists(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            return false;
        }
        TagKey<Block> tag = TagKey.create(Registries.BLOCK, location);
        return BuiltInRegistries.BLOCK.getTag(tag).map(set -> set.size() > 0).orElse(false);
    }
}
