package com.nightsta69.delvefold.compat.emi;

import com.nightsta69.delvefold.Delvefold;
import com.nightsta69.delvefold.compat.PortalConstructionGuide;
import com.nightsta69.delvefold.portal.PortalRegistries;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;

/** Optional EMI information page; this class is discovered only when EMI is installed. */
@EmiEntrypoint
public final class DelvefoldEmiPlugin implements EmiPlugin {
    /** Creates the EMI entry point discovered through EMI's client plugin metadata. */
    public DelvefoldEmiPlugin() {}

    @Override
    public void register(EmiRegistry registry) {
        EmiRecipeCategory portalConstruction = new EmiRecipeCategory(
                ResourceLocation.fromNamespaceAndPath(
                        PortalConstructionGuide.RECIPE_NAMESPACE, PortalConstructionGuide.RECIPE_PATH),
                EmiStack.of(PortalRegistries.PORTAL_FRAME_ITEM.get()));
        registry.addCategory(portalConstruction);
        registry.addWorkstation(portalConstruction, EmiStack.of(PortalRegistries.PORTAL_FRAME_ITEM.get()));
        registry.addWorkstation(portalConstruction, EmiStack.of(Items.FLINT_AND_STEEL));
        registry.addRecipe(new DelvefoldEmiPortalRecipe(portalConstruction, PortalConstructionGuide.INSTANCE));
        if (!ModList.get().isLoaded("jei")) {
            registry.addRecipe(new EmiInfoRecipe(
                    List.of(EmiStack.of(PortalRegistries.PORTAL_FRAME_ITEM.get())),
                    List.of(
                            Component.translatable("compat.delvefold.recipe_viewer.portal.1"),
                            Component.translatable("compat.delvefold.recipe_viewer.portal.2"),
                            Component.translatable("compat.delvefold.recipe_viewer.portal.3")),
                    ResourceLocation.fromNamespaceAndPath(Delvefold.MOD_ID, "info/portal_activation")));
        }
    }
}
