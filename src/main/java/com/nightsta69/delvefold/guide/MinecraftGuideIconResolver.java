package com.nightsta69.delvefold.guide;

import java.util.Comparator;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/** Registry-backed representative icons used by the server guide snapshot builder. */
final class MinecraftGuideIconResolver implements GuideIconResolver {
    static final MinecraftGuideIconResolver INSTANCE = new MinecraftGuideIconResolver();

    private MinecraftGuideIconResolver() {
    }

    @Override
    public Optional<String> representativeBlock(GuideSnapshot.OutputKind kind, String sourceId) {
        ResourceLocation id = ResourceLocation.tryParse(sourceId);
        if (id == null) {
            return Optional.empty();
        }
        if (kind == GuideSnapshot.OutputKind.BLOCK) {
            return BuiltInRegistries.BLOCK.getOptional(id)
                    .filter(block -> block.asItem() != Items.AIR)
                    .map(block -> BuiltInRegistries.BLOCK.getKey(block).toString());
        }
        TagKey<Block> tag = TagKey.create(Registries.BLOCK, id);
        return BuiltInRegistries.BLOCK.getTag(tag).stream()
                .flatMap(holders -> holders.stream())
                .map(holder -> holder.value())
                .filter(block -> block.asItem() != Items.AIR)
                .map(block -> BuiltInRegistries.BLOCK.getKey(block))
                .sorted(Comparator.naturalOrder())
                .map(ResourceLocation::toString)
                .findFirst();
    }
}
