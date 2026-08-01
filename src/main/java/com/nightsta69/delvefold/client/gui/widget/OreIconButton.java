package com.nightsta69.delvefold.client.gui.widget;

import com.nightsta69.delvefold.client.gui.OrePickerEntry;
import java.time.Duration;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public final class OreIconButton extends AbstractButton {
    private final Consumer<OrePickerEntry> onPress;
    private OrePickerEntry entry;

    public OreIconButton(int x, int y, int width, int height, Consumer<OrePickerEntry> onPress) {
        super(x, y, width, height, Component.empty());
        this.onPress = onPress;
        this.visible = false;
        this.active = false;
    }

    public void setEntry(OrePickerEntry entry) {
        this.entry = entry;
        this.visible = entry != null;
        this.active = entry != null;
        this.setMessage(entry == null
                ? Component.empty()
                : Component.translatable("screen.delvefold.ore_picker.entry",
                        entry.translatedName(), entry.id()));
        this.setTooltip(entry == null ? null : Tooltip.create(Component.translatable(
                entry.commonTagged()
                        ? "screen.delvefold.ore_picker.entry.tooltip.common"
                        : "screen.delvefold.ore_picker.entry.tooltip",
                entry.translatedName(), entry.id())));
        this.setTooltipDelay(Duration.ofMillis(250));
    }

    public OrePickerEntry entry() {
        return this.entry;
    }

    @Override
    public void onPress() {
        if (this.entry != null) {
            this.onPress.accept(this.entry);
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
        if (this.entry != null) {
            if (this.entry.commonTagged()) {
                graphics.fill(left + 2, top + 2, right - 2, top + 4, 0xFF64D6B2);
            }
            int itemX = this.getX() + (this.getWidth() - 16) / 2;
            int itemY = this.getY() + (this.getHeight() - 16) / 2;
            graphics.renderItem(this.entry.icon(), itemX, itemY);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
