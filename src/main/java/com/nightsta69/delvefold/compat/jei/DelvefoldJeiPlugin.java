package com.nightsta69.delvefold.compat.jei;

import com.nightsta69.delvefold.Delvefold;
import com.nightsta69.delvefold.portal.PortalRegistries;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Optional JEI information page; this class is discovered only when JEI is installed. */
@JeiPlugin
public final class DelvefoldJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(
            Delvefold.MOD_ID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addIngredientInfo(PortalRegistries.PORTAL_FRAME_ITEM.get(),
                Component.translatable("compat.delvefold.recipe_viewer.portal.1"),
                Component.translatable("compat.delvefold.recipe_viewer.portal.2"),
                Component.translatable("compat.delvefold.recipe_viewer.portal.3"));
    }
}
