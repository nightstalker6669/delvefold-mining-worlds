package com.nightsta69.delvefold.client.gui;

import com.nightsta69.delvefold.client.gui.widget.DelvefoldButton;
import com.nightsta69.delvefold.client.gui.widget.DelvefoldButton.Style;
import com.nightsta69.delvefold.network.model.AdminSnapshot;
import com.nightsta69.delvefold.network.payload.ActionResultPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class DelvefoldScreen extends Screen {
    protected static final int PANEL_BACKGROUND = 0xF20E151A;
    protected static final int PANEL_SURFACE = 0xF21A242B;
    protected static final int PANEL_SURFACE_ALT = 0xE6162026;
    protected static final int PANEL_BORDER = 0xFF4B626B;
    protected static final int CARD_BORDER = 0xFF34464F;
    protected static final int TEXT = 0xFFF0F6F7;
    protected static final int MUTED_TEXT = 0xFFA6B5BC;
    protected static final int DIM_TEXT = 0xFF73858E;
    protected static final int ACCENT = 0xFF64D6B2;
    protected static final int ACCENT_DARK = 0xFF234D45;
    protected static final int WARNING = 0xFFFFC766;
    protected static final int DANGER = 0xFFFF7279;
    protected static final int SUCCESS = 0xFF72D6A7;

    protected static final int HEADER_HEIGHT = 46;
    protected static final int FOOTER_HEIGHT = 38;
    protected static final int CONTENT_PADDING = 16;

    protected final AdminSnapshot snapshot;
    protected int panelLeft;
    protected int panelTop;
    protected int panelWidth;
    protected int panelHeight;

    protected DelvefoldScreen(Component title, AdminSnapshot snapshot) {
        super(title);
        this.snapshot = snapshot;
    }

    /** Immutable administration context retained by nested read-only screens. */
    public final AdminSnapshot adminSnapshot() {
        return this.snapshot;
    }

    @Override
    protected final void init() {
        super.init();
        int horizontalMargin = this.width < 500 ? 10 : 20;
        int verticalMargin = this.height < 360 ? 8 : 14;
        this.panelWidth = Math.min(this.preferredPanelWidth(), Math.max(1, this.width - horizontalMargin * 2));
        this.panelHeight = Math.min(this.preferredPanelHeight(), Math.max(1, this.height - verticalMargin * 2));
        this.panelLeft = (this.width - this.panelWidth) / 2;
        this.panelTop = (this.height - this.panelHeight) / 2;
        this.initPanel();
        if (this.getFocused() == null) {
            for (var child : this.children()) {
                if (child instanceof AbstractWidget widget && widget.active && widget.visible) {
                    this.setInitialFocus(widget);
                    break;
                }
            }
        }
    }

    protected int preferredPanelWidth() {
        return 600;
    }

    protected int preferredPanelHeight() {
        return 390;
    }

    protected abstract void initPanel();

    public void handleActionResult(ActionResultPayload payload) {
    }

    protected void renderPanelContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    protected DelvefoldButton addButton(
            int x, int y, int width, int height, Component label, Button.OnPress onPress) {
        return this.addButton(x, y, width, height, label, Style.SECONDARY, onPress);
    }

    protected DelvefoldButton addButton(
            int x,
            int y,
            int width,
            int height,
            Component label,
            Style style,
            Button.OnPress onPress) {
        return this.addRenderableWidget(new DelvefoldButton(x, y, width, height, label, onPress, style));
    }

    protected static void setButtonStyle(Button button, Style style) {
        if (button instanceof DelvefoldButton themed) {
            themed.setStyle(style);
        }
    }

    protected int contentLeft() {
        return this.panelLeft + CONTENT_PADDING;
    }

    protected int contentRight() {
        return this.panelLeft + this.panelWidth - CONTENT_PADDING;
    }

    protected int contentTop() {
        return this.panelTop + HEADER_HEIGHT + 10;
    }

    protected int contentBottom() {
        return this.panelTop + this.panelHeight - FOOTER_HEIGHT - 8;
    }

    protected int contentWidth() {
        return this.contentRight() - this.contentLeft();
    }

    protected void drawCard(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL_SURFACE_ALT);
        graphics.renderOutline(x, y, width, height, CARD_BORDER);
    }

    protected void drawSectionTitle(GuiGraphics graphics, Component label, int x, int y) {
        graphics.fill(x, y + 1, x + 3, y + 10, ACCENT);
        graphics.drawString(this.font, label, x + 8, y + 1, TEXT, false);
    }

    protected void drawFieldLabel(GuiGraphics graphics, Component label, int x, int y) {
        graphics.drawString(this.font, label, x, y, MUTED_TEXT, false);
    }

    protected void drawBadge(GuiGraphics graphics, Component label, int x, int y, int color) {
        int badgeWidth = this.font.width(label) + 10;
        graphics.fill(x, y, x + badgeWidth, y + 15, 0xCC111A20);
        graphics.renderOutline(x, y, badgeWidth, 15, color);
        graphics.drawString(this.font, label, x + 5, y + 4, color, false);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fillGradient(0, 0, this.width, this.height, 0xA80B1116, 0xDC05080B);

        graphics.fill(this.panelLeft + 5, this.panelTop + 6,
                this.panelLeft + this.panelWidth + 5, this.panelTop + this.panelHeight + 6, 0x88000000);
        graphics.fill(this.panelLeft, this.panelTop, this.panelLeft + this.panelWidth,
                this.panelTop + this.panelHeight, PANEL_BACKGROUND);
        graphics.fillGradient(this.panelLeft + 1, this.panelTop + 1,
                this.panelLeft + this.panelWidth - 1, this.panelTop + HEADER_HEIGHT,
                0xFF1E3035, 0xFF142128);
        graphics.fill(this.panelLeft + 1, this.panelTop + this.panelHeight - FOOTER_HEIGHT,
                this.panelLeft + this.panelWidth - 1, this.panelTop + this.panelHeight - 1, 0xFF10191F);
        graphics.fill(this.panelLeft + 1, this.panelTop + 1, this.panelLeft + this.panelWidth - 1,
                this.panelTop + 4, ACCENT);
        graphics.renderOutline(this.panelLeft, this.panelTop, this.panelWidth, this.panelHeight, PANEL_BORDER);
        graphics.drawString(this.font, this.title, this.panelLeft + CONTENT_PADDING, this.panelTop + 16, TEXT, false);

        if (this.panelWidth >= 430) {
            Component revision = Component.translatable("screen.delvefold.revisions",
                    this.snapshot.oreRevision(), this.snapshot.settingsRevision());
            int revisionWidth = this.font.width(revision) + 12;
            int revisionX = this.panelLeft + this.panelWidth - CONTENT_PADDING - revisionWidth;
            graphics.fill(revisionX, this.panelTop + 12, revisionX + revisionWidth, this.panelTop + 31, 0xAA0B1318);
            graphics.renderOutline(revisionX, this.panelTop + 12, revisionWidth, 19, 0xFF334A52);
            graphics.drawString(this.font, revision, revisionX + 6, this.panelTop + 18, MUTED_TEXT, false);
        }
        this.renderPanelContents(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
