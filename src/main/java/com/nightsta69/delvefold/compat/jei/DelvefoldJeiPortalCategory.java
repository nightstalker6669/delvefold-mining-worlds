package com.nightsta69.delvefold.compat.jei;

import com.nightsta69.delvefold.compat.PortalConstructionGuide;
import com.nightsta69.delvefold.portal.PortalRegistries;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Visual JEI category for the in-world Delvefold portal construction process. */
public final class DelvefoldJeiPortalCategory implements IRecipeCategory<PortalConstructionGuide> {
    private static final int WIDTH = 190;
    private static final int HEIGHT = 100;
    private static final int GRID_X = 4;
    private static final int GRID_Y = 4;
    private static final int SLOT_STEP = 18;
    private static final int TEXT_X = 84;
    private static final int TEXT_WIDTH = WIDTH - TEXT_X - 3;

    private final IDrawable background;
    private final IDrawable icon;

    public DelvefoldJeiPortalCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createBlankDrawable(WIDTH, HEIGHT);
        this.icon = guiHelper.createDrawableItemLike(PortalRegistries.PORTAL_FRAME_ITEM.get());
    }

    @Override
    public RecipeType<PortalConstructionGuide> getRecipeType() {
        return DelvefoldJeiRecipeTypes.PORTAL_CONSTRUCTION;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("compat.delvefold.recipe_viewer.portal.category");
    }

    /** Retained for compatibility with earlier supported JEI 19.x builds. */
    @Override
    @SuppressWarnings("removal")
    public IDrawable getBackground() {
        return this.background;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return this.icon;
    }

    @Override
    public void setRecipe(
            IRecipeLayoutBuilder builder,
            PortalConstructionGuide guide,
            IFocusGroup focuses) {
        ItemStack frame = new ItemStack(PortalRegistries.PORTAL_FRAME_ITEM.get());
        for (PortalConstructionGuide.FrameCell cell : guide.minimumFrameCells()) {
            builder.addSlot(
                            RecipeIngredientRole.INPUT,
                            GRID_X + cell.column() * SLOT_STEP,
                            GRID_Y + cell.row() * SLOT_STEP)
                    .setStandardSlotBackground()
                    .addItemStack(frame.copy());
        }
        builder.addSlot(RecipeIngredientRole.CATALYST, GRID_X + 27, GRID_Y + 36)
                .setStandardSlotBackground()
                .addItemStack(new ItemStack(Items.FLINT_AND_STEEL));
    }

    @Override
    public void draw(
            PortalConstructionGuide guide,
            IRecipeSlotsView recipeSlotsView,
            GuiGraphics graphics,
            double mouseX,
            double mouseY) {
        Font font = Minecraft.getInstance().font;
        int y = 4;
        y = drawWrapped(graphics, font,
                Component.translatable("compat.delvefold.recipe_viewer.portal.dimensions",
                        guide.minimumInteriorWidth(), guide.minimumInteriorHeight(),
                        guide.maximumInteriorWidth(), guide.maximumInteriorHeight()), y);
        y = drawWrapped(graphics, font,
                Component.translatable("compat.delvefold.recipe_viewer.portal.frames",
                        guide.minimumFrameCount()), y + 2);
        y = drawWrapped(graphics, font,
                Component.translatable("compat.delvefold.recipe_viewer.portal.initialize"), y + 2);
        drawWrapped(graphics, font,
                Component.translatable("compat.delvefold.recipe_viewer.portal.ignite"), y + 2);
    }

    private static int drawWrapped(
            GuiGraphics graphics,
            Font font,
            Component text,
            int y) {
        List<FormattedCharSequence> lines = font.split(text, TEXT_WIDTH);
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, TEXT_X, y, 0xFF404040, false);
            y += font.lineHeight;
        }
        return y;
    }
}
