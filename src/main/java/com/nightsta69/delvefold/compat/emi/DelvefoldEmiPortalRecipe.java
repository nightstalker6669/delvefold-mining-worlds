package com.nightsta69.delvefold.compat.emi;

import com.nightsta69.delvefold.compat.PortalConstructionGuide;
import com.nightsta69.delvefold.portal.PortalRegistries;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Items;

/** Visual EMI recipe for the in-world Delvefold portal construction process. */
public final class DelvefoldEmiPortalRecipe extends BasicEmiRecipe {
    private static final int GRID_X = 4;
    private static final int GRID_Y = 4;
    private static final int SLOT_STEP = 18;
    private static final int TEXT_X = 84;
    private static final int TEXT_WIDTH = 103;

    private final PortalConstructionGuide guide;
    private final EmiStack frame;
    private final EmiStack ignition;

    /**
     * Creates EMI's immutable synthetic view of the in-world construction process.
     *
     * @param category EMI category that owns this recipe
     * @param guide viewer-independent portal geometry and instruction contract
     */
    public DelvefoldEmiPortalRecipe(EmiRecipeCategory category, PortalConstructionGuide guide) {
        super(
                category,
                ResourceLocation.fromNamespaceAndPath(
                        PortalConstructionGuide.RECIPE_NAMESPACE, PortalConstructionGuide.RECIPE_PATH),
                190,
                100);
        this.guide = guide;
        this.frame = EmiStack.of(PortalRegistries.PORTAL_FRAME_ITEM.get());
        this.ignition = EmiStack.of(Items.FLINT_AND_STEEL);
        this.inputs.add(this.frame.copy().setAmount(guide.minimumFrameCount()));
        this.catalysts.add(this.ignition.copy());
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        for (PortalConstructionGuide.FrameCell cell : this.guide.minimumFrameCells()) {
            widgets.addSlot(this.frame.copy(), GRID_X + cell.column() * SLOT_STEP, GRID_Y + cell.row() * SLOT_STEP);
        }
        widgets.addSlot(this.ignition.copy(), GRID_X + 27, GRID_Y + 36).catalyst(true);

        Font font = Minecraft.getInstance().font;
        int y = 4;
        y = addWrapped(
                widgets,
                font,
                Component.translatable(
                        "compat.delvefold.recipe_viewer.portal.dimensions",
                        this.guide.minimumInteriorWidth(),
                        this.guide.minimumInteriorHeight(),
                        this.guide.maximumInteriorWidth(),
                        this.guide.maximumInteriorHeight()),
                y);
        y = addWrapped(
                widgets,
                font,
                Component.translatable("compat.delvefold.recipe_viewer.portal.frames", this.guide.minimumFrameCount()),
                y + 2);
        y = addWrapped(
                widgets, font, Component.translatable("compat.delvefold.recipe_viewer.portal.initialize"), y + 2);
        addWrapped(widgets, font, Component.translatable("compat.delvefold.recipe_viewer.portal.ignite"), y + 2);
    }

    private static int addWrapped(WidgetHolder widgets, Font font, Component text, int y) {
        List<FormattedCharSequence> lines = font.split(text, TEXT_WIDTH);
        for (FormattedCharSequence line : lines) {
            widgets.addText(line, TEXT_X, y, 0x404040, false);
            y += font.lineHeight;
        }
        return y;
    }
}
