package com.nightsta69.delvefold.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** A compact themed button used throughout the Delvefold administration screens. */
public final class DelvefoldButton extends Button {
    private Style style;

    /**
     * Creates a themed button whose label and focus state remain available to vanilla narration.
     *
     * @param x left coordinate in GUI pixels
     * @param y top coordinate in GUI pixels
     * @param width button width in GUI pixels
     * @param height button height in GUI pixels
     * @param message localized, color-independent action label
     * @param onPress client-thread activation callback
     * @param style semantic visual style
     */
    public DelvefoldButton(int x, int y, int width, int height, Component message, OnPress onPress, Style style) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.style = style;
    }

    /**
     * Changes visual emphasis without changing the narrated action label.
     *
     * @param style replacement semantic style
     */
    public void setStyle(Style style) {
        this.style = style;
    }

    /**
     * Returns the current semantic style.
     *
     * @return current non-null style
     */
    public Style style() {
        return this.style;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean highlighted = this.isHoveredOrFocused();
        Palette palette = palette(highlighted);
        int left = this.getX();
        int top = this.getY();
        int right = left + this.getWidth();
        int bottom = top + this.getHeight();

        graphics.fill(left, top, right, bottom, palette.border());
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1, palette.background());

        if (this.style == Style.PRIMARY || this.style == Style.TOGGLE_ON) {
            graphics.fill(left + 1, top + 1, left + 3, bottom - 1, palette.accent());
        } else if (this.style == Style.DANGER) {
            graphics.fill(left + 1, top + 1, right - 1, top + 3, palette.accent());
        } else if (this.style == Style.TAB_SELECTED) {
            graphics.fill(left + 1, bottom - 3, right - 1, bottom - 1, palette.accent());
        }

        if (highlighted && this.active) {
            graphics.fill(left + 2, top + 2, right - 2, top + 3, 0x44FFFFFF);
        }
        this.renderScrollingString(graphics, Minecraft.getInstance().font, 5, palette.text());
    }

    private Palette palette(boolean highlighted) {
        if (!this.active && this.style != Style.TAB_SELECTED) {
            return new Palette(0xFF182026, 0xFF303B42, 0xFF728087, 0xFF303B42);
        }
        return switch (this.style) {
            case PRIMARY ->
                new Palette(
                        highlighted ? 0xFF245F53 : 0xFF1B4A42,
                        highlighted ? 0xFF78E1C0 : 0xFF55BFA2,
                        0xFFF2FFFB,
                        0xFF73E0BE);
            case DANGER ->
                new Palette(
                        highlighted ? 0xFF642F35 : 0xFF48262B,
                        highlighted ? 0xFFFF8B91 : 0xFFB85A62,
                        0xFFFFE9E9,
                        0xFFFF6B72);
            case TAB_SELECTED -> new Palette(0xFF23383A, 0xFF4F7773, 0xFFF0FFFA, 0xFF64D6B2);
            case TOGGLE_ON ->
                new Palette(
                        highlighted ? 0xFF24534B : 0xFF1D403A,
                        highlighted ? 0xFF68D2B1 : 0xFF488E7B,
                        0xFFE8FFF7,
                        0xFF5FD2AF);
            case TOGGLE_OFF ->
                new Palette(
                        highlighted ? 0xFF303D46 : 0xFF242F37,
                        highlighted ? 0xFF78909C : 0xFF485B66,
                        0xFFC5D0D6,
                        0xFF596A73);
            case GHOST ->
                new Palette(
                        highlighted ? 0xCC26343D : 0x66151D23,
                        highlighted ? 0xFF617A85 : 0xAA354751,
                        highlighted ? 0xFFE8F4F2 : 0xFFADBCC3,
                        0xFF526671);
            case SECONDARY ->
                new Palette(
                        highlighted ? 0xFF2C3B44 : 0xFF202B33,
                        highlighted ? 0xFF72A89E : 0xFF425761,
                        0xFFE7F0F2,
                        0xFF5A756F);
        };
    }

    /** Color-independent semantic roles used by themed administration buttons. */
    public enum Style {
        /** Standard non-destructive action. */
        SECONDARY,
        /** Primary forward or save action. */
        PRIMARY,
        /** Destructive or high-risk action whose label also conveys danger. */
        DANGER,
        /** Low-emphasis navigation action. */
        GHOST,
        /** Currently selected navigation tab. */
        TAB_SELECTED,
        /** Enabled state of a binary option. */
        TOGGLE_ON,
        /** Disabled state of a binary option. */
        TOGGLE_OFF
    }

    private record Palette(int background, int border, int text, int accent) {}
}
