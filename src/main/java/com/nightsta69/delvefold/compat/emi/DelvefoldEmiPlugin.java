package com.nightsta69.delvefold.compat.emi;

import com.nightsta69.delvefold.Delvefold;
import com.nightsta69.delvefold.portal.PortalRegistries;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.stack.EmiStack;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Optional EMI information page; this class is discovered only when EMI is installed. */
@EmiEntrypoint
public final class DelvefoldEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        registry.addRecipe(new EmiInfoRecipe(
                List.of(EmiStack.of(PortalRegistries.PORTAL_FRAME_ITEM.get())),
                List.of(
                        Component.translatable("compat.delvefold.recipe_viewer.portal.1"),
                        Component.translatable("compat.delvefold.recipe_viewer.portal.2"),
                        Component.translatable("compat.delvefold.recipe_viewer.portal.3")),
                ResourceLocation.fromNamespaceAndPath(Delvefold.MOD_ID, "info/portal_activation")));
    }
}
