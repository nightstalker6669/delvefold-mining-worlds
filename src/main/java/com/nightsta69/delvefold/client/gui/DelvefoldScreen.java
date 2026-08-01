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

/**
 * Responsive base screen for server-authoritative Delvefold administration views.
 *
 * <p>Subclasses retain one immutable {@link AdminSnapshot}; mutations are sent as revision-guarded requests and never
 * applied directly to this snapshot.
 */
public abstract class DelvefoldScreen extends Screen {
    /** Translucent ARGB background used behind the complete administration panel. */
    protected static final int PANEL_BACKGROUND = 0xF20E151A;
    /** Primary opaque ARGB surface used by panel sections. */
    protected static final int PANEL_SURFACE = 0xF21A242B;
    /** Alternate translucent ARGB surface used to distinguish nested cards. */
    protected static final int PANEL_SURFACE_ALT = 0xE6162026;
    /** ARGB outline color for the outer panel. */
    protected static final int PANEL_BORDER = 0xFF4B626B;
    /** ARGB outline color for nested cards. */
    protected static final int CARD_BORDER = 0xFF34464F;
    /** Primary high-contrast ARGB text color. */
    protected static final int TEXT = 0xFFF0F6F7;
    /** Secondary ARGB text color for supporting information. */
    protected static final int MUTED_TEXT = 0xFFA6B5BC;
    /** Low-emphasis ARGB text color for disabled or tertiary information. */
    protected static final int DIM_TEXT = 0xFF73858E;
    /** Primary ARGB accent used for active controls and section markers. */
    protected static final int ACCENT = 0xFF64D6B2;
    /** Dark ARGB accent used beneath selected controls. */
    protected static final int ACCENT_DARK = 0xFF234D45;
    /** ARGB warning status color; status text and icons also carry meaning independently of color. */
    protected static final int WARNING = 0xFFFFC766;
    /** ARGB destructive/error status color; labels and narration also identify the state. */
    protected static final int DANGER = 0xFFFF7279;
    /** ARGB success status color; labels and narration also identify the state. */
    protected static final int SUCCESS = 0xFF72D6A7;

    /** Fixed header height in logical GUI pixels. */
    protected static final int HEADER_HEIGHT = 46;
    /** Fixed footer height in logical GUI pixels. */
    protected static final int FOOTER_HEIGHT = 38;
    /** Horizontal content inset in logical GUI pixels. */
    protected static final int CONTENT_PADDING = 16;

    /** Immutable server-authoritative snapshot rendered by this screen. */
    protected final AdminSnapshot snapshot;
    /** Calculated left edge of the responsive panel in logical GUI pixels. */
    protected int panelLeft;
    /** Calculated top edge of the responsive panel in logical GUI pixels. */
    protected int panelTop;
    /** Calculated responsive panel width in logical GUI pixels. */
    protected int panelWidth;
    /** Calculated responsive panel height in logical GUI pixels. */
    protected int panelHeight;

    /**
     * Creates a screen backed by one immutable server snapshot.
     *
     * @param title localized screen title
     * @param snapshot bounded administration state retained for the lifetime of this screen
     */
    protected DelvefoldScreen(Component title, AdminSnapshot snapshot) {
        super(title);
        this.snapshot = snapshot;
    }

    /**
     * Returns the immutable administration context retained by nested read-only screens.
     *
     * @return the server snapshot associated with this screen
     */
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

    /**
     * Returns the preferred panel width.
     *
     * @return preferred panel width in GUI pixels before responsive clamping
     */
    protected int preferredPanelWidth() {
        return 600;
    }

    /**
     * Returns the preferred panel height.
     *
     * @return preferred panel height in GUI pixels before responsive clamping
     */
    protected int preferredPanelHeight() {
        return 390;
    }

    /** Builds subclass widgets after responsive panel geometry has been calculated. */
    protected abstract void initPanel();

    /**
     * Receives a server-authoritative result on the client thread; subclasses may update transient status UI.
     *
     * @param payload immutable action result
     */
    public void handleActionResult(ActionResultPayload payload) {}

    /**
     * Draws subclass content inside the calculated panel bounds.
     *
     * @param graphics active GUI drawing context
     * @param mouseX mouse x-coordinate in GUI pixels
     * @param mouseY mouse y-coordinate in GUI pixels
     * @param partialTick partial client tick used for animation interpolation
     */
    protected void renderPanelContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    /**
     * Adds a secondary-style Delvefold button to this screen.
     *
     * @param x left coordinate in GUI pixels
     * @param y top coordinate in GUI pixels
     * @param width button width in GUI pixels
     * @param height button height in GUI pixels
     * @param label localized button label
     * @param onPress client-thread activation callback
     * @return the registered themed button
     */
    protected DelvefoldButton addButton(int x, int y, int width, int height, Component label, Button.OnPress onPress) {
        return this.addButton(x, y, width, height, label, Style.SECONDARY, onPress);
    }

    /**
     * Adds a styled Delvefold button to this screen.
     *
     * @param x left coordinate in GUI pixels
     * @param y top coordinate in GUI pixels
     * @param width button width in GUI pixels
     * @param height button height in GUI pixels
     * @param label localized button label
     * @param style visual and color-independent semantic style
     * @param onPress client-thread activation callback
     * @return the registered themed button
     */
    protected DelvefoldButton addButton(
            int x, int y, int width, int height, Component label, Style style, Button.OnPress onPress) {
        return this.addRenderableWidget(new DelvefoldButton(x, y, width, height, label, onPress, style));
    }

    /**
     * Updates a button's themed style when it is a Delvefold button.
     *
     * @param button target button, including compatible vanilla buttons
     * @param style requested semantic style
     */
    protected static void setButtonStyle(Button button, Style style) {
        if (button instanceof DelvefoldButton themed) {
            themed.setStyle(style);
        }
    }

    /**
     * Returns the left content boundary.
     *
     * @return left content boundary in GUI pixels
     */
    protected int contentLeft() {
        return this.panelLeft + CONTENT_PADDING;
    }

    /**
     * Returns the right content boundary.
     *
     * @return exclusive right content boundary in GUI pixels
     */
    protected int contentRight() {
        return this.panelLeft + this.panelWidth - CONTENT_PADDING;
    }

    /**
     * Returns the top content boundary.
     *
     * @return top content boundary below the panel header, in GUI pixels
     */
    protected int contentTop() {
        return this.panelTop + HEADER_HEIGHT + 10;
    }

    /**
     * Returns the bottom content boundary.
     *
     * @return bottom content boundary above the fixed footer, in GUI pixels
     */
    protected int contentBottom() {
        return this.panelTop + this.panelHeight - FOOTER_HEIGHT - 8;
    }

    /**
     * Returns the available content width.
     *
     * @return non-negative content width in GUI pixels
     */
    protected int contentWidth() {
        return this.contentRight() - this.contentLeft();
    }

    /**
     * Draws a bordered card in the panel's alternate surface colors.
     *
     * @param graphics active GUI drawing context
     * @param x left coordinate in GUI pixels
     * @param y top coordinate in GUI pixels
     * @param width card width in GUI pixels
     * @param height card height in GUI pixels
     */
    protected void drawCard(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL_SURFACE_ALT);
        graphics.renderOutline(x, y, width, height, CARD_BORDER);
    }

    /**
     * Draws a localized section heading with an accent marker.
     *
     * @param graphics active GUI drawing context
     * @param label localized section label
     * @param x left coordinate in GUI pixels
     * @param y top coordinate in GUI pixels
     */
    protected void drawSectionTitle(GuiGraphics graphics, Component label, int x, int y) {
        graphics.fill(x, y + 1, x + 3, y + 10, ACCENT);
        graphics.drawString(this.font, label, x + 8, y + 1, TEXT, false);
    }

    /**
     * Draws a muted label for an adjacent input field.
     *
     * @param graphics active GUI drawing context
     * @param label localized field label
     * @param x left coordinate in GUI pixels
     * @param y baseline coordinate in GUI pixels
     */
    protected void drawFieldLabel(GuiGraphics graphics, Component label, int x, int y) {
        graphics.drawString(this.font, label, x, y, MUTED_TEXT, false);
    }

    /**
     * Draws a compact outlined status badge whose label conveys status independently of color.
     *
     * @param graphics active GUI drawing context
     * @param label localized badge text
     * @param x left coordinate in GUI pixels
     * @param y top coordinate in GUI pixels
     * @param color ARGB border and text color
     */
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

        graphics.fill(
                this.panelLeft + 5,
                this.panelTop + 6,
                this.panelLeft + this.panelWidth + 5,
                this.panelTop + this.panelHeight + 6,
                0x88000000);
        graphics.fill(
                this.panelLeft,
                this.panelTop,
                this.panelLeft + this.panelWidth,
                this.panelTop + this.panelHeight,
                PANEL_BACKGROUND);
        graphics.fillGradient(
                this.panelLeft + 1,
                this.panelTop + 1,
                this.panelLeft + this.panelWidth - 1,
                this.panelTop + HEADER_HEIGHT,
                0xFF1E3035,
                0xFF142128);
        graphics.fill(
                this.panelLeft + 1,
                this.panelTop + this.panelHeight - FOOTER_HEIGHT,
                this.panelLeft + this.panelWidth - 1,
                this.panelTop + this.panelHeight - 1,
                0xFF10191F);
        graphics.fill(
                this.panelLeft + 1, this.panelTop + 1, this.panelLeft + this.panelWidth - 1, this.panelTop + 4, ACCENT);
        graphics.renderOutline(this.panelLeft, this.panelTop, this.panelWidth, this.panelHeight, PANEL_BORDER);
        graphics.drawString(this.font, this.title, this.panelLeft + CONTENT_PADDING, this.panelTop + 16, TEXT, false);

        if (this.panelWidth >= 430) {
            Component revision = Component.translatable(
                    "screen.delvefold.revisions", this.snapshot.oreRevision(), this.snapshot.settingsRevision());
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
