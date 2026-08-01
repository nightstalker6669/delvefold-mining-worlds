package com.nightsta69.delvefold.compat.jei;

import com.nightsta69.delvefold.Delvefold;
import com.nightsta69.delvefold.compat.PortalConstructionGuide;
import mezz.jei.api.recipe.RecipeType;

/** JEI recipe types owned by Delvefold. */
public final class DelvefoldJeiRecipeTypes {
    public static final RecipeType<PortalConstructionGuide> PORTAL_CONSTRUCTION = RecipeType.create(
            Delvefold.MOD_ID, "portal_construction", PortalConstructionGuide.class);

    private DelvefoldJeiRecipeTypes() {
    }
}
