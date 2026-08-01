package com.nightsta69.delvefold.compat.emi;

import net.minecraft.resources.ResourceLocation;

/** Creates EMI identifiers for recipes that do not exist in Minecraft's recipe manager. */
final class DelvefoldEmiIds {
    private static final String SYNTHETIC_PATH_PREFIX = "/";

    private DelvefoldEmiIds() {}

    static ResourceLocation syntheticRecipe(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, SYNTHETIC_PATH_PREFIX + path);
    }
}
