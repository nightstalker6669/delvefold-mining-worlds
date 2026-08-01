package com.nightsta69.delvefold.client.gui.widget;

import com.nightsta69.delvefold.client.gui.OrePickerEntry;
import java.time.Duration;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Reusable ore-picker tile whose empty state is hidden, inactive, and safe to narrate. */
public final class OreIconButton extends AbstractButton {
    private final Consumer<OrePickerEntry> onPress;
    private @Nullable OrePickerEntry entry;

    /**
     * Creates an initially empty tile.
     *
     * @param x left coordinate in GUI pixels
     * @param y top coordinate in GUI pixels
     * @param width tile width in GUI pixels
     * @param height tile height in GUI pixels
     * @param onPress callback receiving the non-null entry selected by the player
     */
    public OreIconButton(int x, int y, int width, int height, Consumer<OrePickerEntry> onPress) {
        super(x, y, width, height, Component.empty());
        this.onPress = onPress;
        this.visible = false;
        this.active = false;
    }

    /**
     * Rebinds the tile to a registry entry or clears it for an unused grid slot.
     *
     * @param entry entry to render and select, or {@code null} to hide and deactivate the tile
     */
    public void setEntry(@Nullable OrePickerEntry entry) {
        this.entry = entry;
        this.visible = entry != null;
        this.active = entry != null;
        this.setMessage(
                entry == null
                        ? Component.empty()
                        : Component.translatable(
                                "screen.delvefold.ore_picker.entry", entry.translatedName(), entry.id()));
        this.setTooltip(
                entry == null
                        ? null
                        : Tooltip.create(Component.translatable(
                                entry.commonTagged()
                                        ? "screen.delvefold.ore_picker.entry.tooltip.common"
                                        : "screen.delvefold.ore_picker.entry.tooltip",
                                entry.translatedName(),
                                entry.id())));
        this.setTooltipDelay(Duration.ofMillis(250));
    }

    /**
     * Returns the currently bound entry.
     *
     * @return the entry, or {@code null} while the grid slot is empty
     */
    public @Nullable OrePickerEntry entry() {
        return this.entry;
    }

    @Override
    public void onPress() {
        OrePickerEntry activeEntry = this.entry;
        if (activeEntry != null) {
            this.onPress.accept(activeEntry);
        }
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = this.getX();
        int top = this.getY();
        int right = left + this.getWidth();
        int bottom = top + this.getHeight();
        int border = this.isHoveredOrFocused() ? 0xFF76DDBB : 0xFF42545D;
        int background = this.isHoveredOrFocused() ? 0xFF293B3D : 0xFF182329;
        graphics.fill(left, top, right, bottom, border);
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1, background);
        OrePickerEntry activeEntry = this.entry;
        if (activeEntry != null) {
            if (activeEntry.commonTagged()) {
                graphics.fill(left + 2, top + 2, right - 2, top + 4, 0xFF64D6B2);
            }
            int itemX = this.getX() + (this.getWidth() - 16) / 2;
            int itemY = this.getY() + (this.getHeight() - 16) / 2;
            graphics.renderItem(activeEntry.icon(), itemX, itemY);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
