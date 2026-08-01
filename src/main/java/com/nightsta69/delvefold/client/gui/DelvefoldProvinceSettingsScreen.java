package com.nightsta69.delvefold.client.gui;

import com.nightsta69.delvefold.client.gui.widget.DelvefoldButton.Style;
import com.nightsta69.delvefold.config.model.ProvinceSettings;
import com.nightsta69.delvefold.config.validation.OreConfigValidator;
import com.nightsta69.delvefold.network.model.AdminSnapshot;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Focused editor for the bounded regional controls of one province spawn band. */
public final class DelvefoldProvinceSettingsScreen extends DelvefoldScreen {
    private final Screen parent;
    private final Consumer<ProvinceSettings> onSave;
    private String regionSize;
    private String radius;
    private String verticalThickness;
    private String density;
    private String workCap;
    private Component error = Component.empty();

    public DelvefoldProvinceSettingsScreen(
            Screen parent,
            AdminSnapshot snapshot,
            ProvinceSettings initial,
            Consumer<ProvinceSettings> onSave) {
        super(Component.translatable("screen.delvefold.province.title"), snapshot);
        this.parent = parent;
        this.onSave = onSave;
        ProvinceSettings safe = initial == null ? ProvinceSettings.defaults() : initial;
        this.regionSize = Integer.toString(safe.regionSize());
        this.radius = Integer.toString(safe.radius());
        this.verticalThickness = Integer.toString(safe.verticalThickness());
        this.density = Double.toString(safe.density());
        this.workCap = Integer.toString(safe.perChunkWorkCap());
    }

    @Override
    protected int preferredPanelHeight() {
        return 330;
    }

    @Override
    protected void initPanel() {
        ProvinceSettingsLayout layout = layout();
        EditBox region = editBox(layout.region(), this.regionSize,
                Component.translatable("screen.delvefold.province.region_size"));
        region.setResponder(value -> this.regionSize = value);
        EditBox radiusBox = editBox(layout.radius(), this.radius,
                Component.translatable("screen.delvefold.province.radius"));
        radiusBox.setResponder(value -> this.radius = value);

        EditBox thickness = editBox(layout.verticalThickness(), this.verticalThickness,
                Component.translatable("screen.delvefold.province.vertical_thickness"));
        thickness.setResponder(value -> this.verticalThickness = value);
        EditBox densityBox = editBox(layout.density(), this.density,
                Component.translatable("screen.delvefold.province.density"));
        densityBox.setResponder(value -> this.density = value);

        EditBox cap = editBox(layout.workCap(), this.workCap,
                Component.translatable("screen.delvefold.province.work_cap"));
        cap.setResponder(value -> this.workCap = value);

        int footerY = this.panelTop + this.panelHeight - 29;
        this.addButton(this.contentLeft(), footerY, 80, 22,
                Component.translatable("gui.back"), Style.GHOST, button -> onClose());
        this.addButton(this.contentRight() - 108, footerY, 108, 22,
                Component.translatable("screen.delvefold.province.apply"), Style.PRIMARY,
                button -> save());
    }

    private EditBox editBox(ProvinceSettingsLayout.Field field, String value, Component hint) {
        EditBox box = this.addRenderableWidget(new EditBox(
                this.font, field.x(), field.y(), field.width(), field.height(), hint));
        box.setMaxLength(16);
        box.setValue(value);
        box.setHint(hint);
        return box;
    }

    private ProvinceSettingsLayout layout() {
        return ProvinceSettingsLayout.calculate(
                this.contentLeft(), this.contentTop(), this.contentWidth(), this.contentBottom());
    }

    private void save() {
        try {
            int parsedRegion = Integer.parseInt(this.regionSize.trim());
            int parsedRadius = Integer.parseInt(this.radius.trim());
            int parsedThickness = Integer.parseInt(this.verticalThickness.trim());
            double parsedDensity = Double.parseDouble(this.density.trim());
            int parsedCap = Integer.parseInt(this.workCap.trim());
            boolean valid = parsedRegion >= OreConfigValidator.MIN_PROVINCE_REGION_SIZE
                    && parsedRegion <= OreConfigValidator.MAX_PROVINCE_REGION_SIZE
                    && parsedRegion % 16 == 0
                    && parsedRadius >= 1 && parsedRadius <= parsedRegion
                    && parsedThickness >= 1
                    && parsedThickness <= OreConfigValidator.MAX_WORLD_Y - OreConfigValidator.MIN_WORLD_Y + 1
                    && Double.isFinite(parsedDensity) && parsedDensity > 0.0D && parsedDensity <= 1.0D
                    && parsedCap >= 1 && parsedCap <= OreConfigValidator.MAX_PROVINCE_WORK_PER_CHUNK;
            if (!valid) {
                throw new NumberFormatException();
            }
            this.error = Component.empty();
            this.onSave.accept(new ProvinceSettings(
                    parsedRegion, parsedRadius, parsedThickness, parsedDensity, parsedCap));
        } catch (NumberFormatException exception) {
            this.error = Component.translatable("screen.delvefold.province.invalid");
        }
    }

    @Override
    protected void renderPanelContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ProvinceSettingsLayout layout = layout();
        int x = layout.contentLeft();
        int y = layout.contentTop();
        int width = layout.contentWidth();
        this.drawCard(graphics, x, y, width, layout.contentBottom() - y);
        this.drawSectionTitle(graphics, Component.translatable("screen.delvefold.province.section"),
                x + 10, layout.sectionTitleY());
        this.drawFieldLabel(graphics, Component.translatable("screen.delvefold.province.region_size"),
                layout.region().x(), layout.region().labelY());
        this.drawFieldLabel(graphics, Component.translatable("screen.delvefold.province.radius"),
                layout.radius().x(), layout.radius().labelY());
        this.drawFieldLabel(graphics, Component.translatable("screen.delvefold.province.vertical_thickness"),
                layout.verticalThickness().x(), layout.verticalThickness().labelY());
        this.drawFieldLabel(graphics, Component.translatable("screen.delvefold.province.density"),
                layout.density().x(), layout.density().labelY());
        this.drawFieldLabel(graphics, Component.translatable("screen.delvefold.province.work_cap"),
                layout.workCap().x(), layout.workCap().labelY());
        if (layout.showHelp()) {
            graphics.drawWordWrap(this.font, Component.translatable("screen.delvefold.province.help"),
                    x + 12, layout.helpY(), width - 24, DIM_TEXT);
        }
        if (!this.error.getString().isEmpty()) {
            if (layout.compact()) {
                graphics.enableScissor(x + 1, layout.errorY(), x + width - 1, layout.contentBottom());
            }
            graphics.drawWordWrap(this.font, this.error,
                    x + 12, layout.errorY(), width - 24, DANGER);
            if (layout.compact()) {
                graphics.disableScissor();
            }
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public Component getNarrationMessage() {
        return this.error.getString().isEmpty()
                ? Component.translatable("screen.delvefold.province.narration")
                : Component.translatable("screen.delvefold.province.narration.error", this.error);
    }
}

/** Pure responsive geometry kept in this source so it can be verified without loading Minecraft. */
record ProvinceSettingsLayout(
        boolean compact,
        int contentLeft,
        int contentTop,
        int contentWidth,
        int contentBottom,
        int sectionTitleY,
        Field region,
        Field radius,
        Field verticalThickness,
        Field density,
        Field workCap,
        boolean showHelp,
        int helpY,
        int errorY) {
    private static final int NORMAL_BODY_HEIGHT = 214;

    static ProvinceSettingsLayout calculate(
            int contentLeft, int contentTop, int contentWidth, int contentBottom) {
        int innerX = contentLeft + 12;
        int innerWidth = Math.max(1, contentWidth - 24);
        boolean compact = contentBottom - contentTop < NORMAL_BODY_HEIGHT;
        int gap = compact ? 6 : 8;
        int half = Math.max(1, (innerWidth - gap) / 2);
        int rightWidth = Math.max(1, innerWidth - half - gap);
        int fieldHeight = compact ? 18 : 20;
        int firstY = contentTop + (compact ? 26 : 37);
        int rowStep = compact ? 29 : 43;
        Field region = new Field(innerX, firstY, half, fieldHeight);
        Field radius = new Field(innerX + half + gap, firstY, rightWidth, fieldHeight);
        Field thickness = new Field(innerX, firstY + rowStep, half, fieldHeight);
        Field density = new Field(innerX + half + gap, firstY + rowStep, rightWidth, fieldHeight);
        Field workCap = new Field(innerX, firstY + rowStep * 2, innerWidth, fieldHeight);
        return new ProvinceSettingsLayout(
                compact,
                contentLeft,
                contentTop,
                contentWidth,
                contentBottom,
                contentTop + (compact ? 4 : 7),
                region,
                radius,
                thickness,
                density,
                workCap,
                !compact,
                compact ? contentBottom : firstY + 113,
                compact ? workCap.bottom() + 2 : firstY + 150);
    }

    static ProvinceSettingsLayout forScreen(int screenWidth, int screenHeight) {
        int horizontalMargin = screenWidth < 500 ? 10 : 20;
        int verticalMargin = screenHeight < 360 ? 8 : 14;
        int panelWidth = Math.min(600, Math.max(1, screenWidth - horizontalMargin * 2));
        int panelHeight = Math.min(330, Math.max(1, screenHeight - verticalMargin * 2));
        int panelLeft = (screenWidth - panelWidth) / 2;
        int panelTop = (screenHeight - panelHeight) / 2;
        int contentLeft = panelLeft + 16;
        int contentTop = panelTop + 56;
        int contentWidth = panelWidth - 32;
        int contentBottom = panelTop + panelHeight - 46;
        return calculate(contentLeft, contentTop, contentWidth, contentBottom);
    }

    int contentRight() {
        return contentLeft + contentWidth;
    }

    record Field(int x, int y, int width, int height) {
        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }

        int labelY() {
            return y - (height == 18 ? 11 : 12);
        }
    }
}
