package com.nightsta69.delvefold.config;

import com.nightsta69.delvefold.config.validation.RegistryLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** Read-only {@link RegistryLookup} backed by the current built-in Minecraft block registry and bound block tags. */
public final class MinecraftRegistryLookup implements RegistryLookup {
    /** Creates a stateless lookup over the currently bound built-in block registry. */
    public MinecraftRegistryLookup() {}

    /**
     * Resolves a block ID against the current built-in registry.
     *
     * @param id candidate block registry ID
     * @return {@code true} only when the ID parses and the block is registered
     */
    @Override
    public boolean blockExists(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location != null && BuiltInRegistries.BLOCK.containsKey(location);
    }

    /**
     * Resolves a block-tag ID against the current bound registry snapshot.
     *
     * @param id candidate block-tag ID without a leading {@code #}
     * @return {@code true} only when the ID parses and the bound tag contains at least one block
     */
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
