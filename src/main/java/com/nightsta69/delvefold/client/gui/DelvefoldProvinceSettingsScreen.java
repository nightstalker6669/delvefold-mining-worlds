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
import org.jspecify.annotations.Nullable;

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

    /**
     * Creates an editor for bounded regional province settings.
     *
     * @param parent ore-rule editor restored after save or cancellation
     * @param snapshot immutable administration context
     * @param initial settings copied into editable text fields; defaults are used if absent
     * @param onSave client-thread callback receiving a newly validated immutable value
     */
    public DelvefoldProvinceSettingsScreen(
            Screen parent,
            AdminSnapshot snapshot,
            @Nullable ProvinceSettings initial,
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
        EditBox region = editBox(
                layout.region(), this.regionSize, Component.translatable("screen.delvefold.province.region_size"));
        region.setResponder(value -> this.regionSize = value);
        EditBox radiusBox =
                editBox(layout.radius(), this.radius, Component.translatable("screen.delvefold.province.radius"));
        radiusBox.setResponder(value -> this.radius = value);

        EditBox thickness = editBox(
                layout.verticalThickness(),
                this.verticalThickness,
                Component.translatable("screen.delvefold.province.vertical_thickness"));
        thickness.setResponder(value -> this.verticalThickness = value);
        EditBox densityBox =
                editBox(layout.density(), this.density, Component.translatable("screen.delvefold.province.density"));
        densityBox.setResponder(value -> this.density = value);

        EditBox cap =
                editBox(layout.workCap(), this.workCap, Component.translatable("screen.delvefold.province.work_cap"));
        cap.setResponder(value -> this.workCap = value);

        int footerY = this.footerButtonY();
        this.addButton(
                this.contentLeft(),
                footerY,
                80,
                22,
                Component.translatable("gui.back"),
                Style.GHOST,
                button -> onClose());
        this.addButton(
                this.contentRight() - 108,
                footerY,
                108,
                22,
                Component.translatable("screen.delvefold.province.apply"),
                Style.PRIMARY,
                button -> save());
    }

    private EditBox editBox(ProvinceSettingsLayout.Field field, String value, Component hint) {
        EditBox box = this.addRenderableWidget(
                new EditBox(this.font, field.x(), field.y(), field.width(), field.height(), hint));
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
                    && parsedRadius >= 1
                    && parsedRadius <= parsedRegion
                    && parsedThickness >= 1
                    && parsedThickness <= OreConfigValidator.MAX_WORLD_Y - OreConfigValidator.MIN_WORLD_Y + 1
                    && Double.isFinite(parsedDensity)
                    && parsedDensity > 0.0D
                    && parsedDensity <= 1.0D
                    && parsedCap >= 1
                    && parsedCap <= OreConfigValidator.MAX_PROVINCE_WORK_PER_CHUNK;
            if (!valid) {
                throw new NumberFormatException();
            }
            this.error = Component.empty();
            this.onSave.accept(
                    new ProvinceSettings(parsedRegion, parsedRadius, parsedThickness, parsedDensity, parsedCap));
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
        this.drawSectionTitle(
                graphics, Component.translatable("screen.delvefold.province.section"), x + 10, layout.sectionTitleY());
        this.drawFieldLabel(
                graphics,
                Component.translatable("screen.delvefold.province.region_size"),
                layout.region().x(),
                layout.region().labelY());
        this.drawFieldLabel(
                graphics,
                Component.translatable("screen.delvefold.province.radius"),
                layout.radius().x(),
                layout.radius().labelY());
        this.drawFieldLabel(
                graphics,
                Component.translatable("screen.delvefold.province.vertical_thickness"),
                layout.verticalThickness().x(),
                layout.verticalThickness().labelY());
        this.drawFieldLabel(
                graphics,
                Component.translatable("screen.delvefold.province.density"),
                layout.density().x(),
                layout.density().labelY());
        this.drawFieldLabel(
                graphics,
                Component.translatable("screen.delvefold.province.work_cap"),
                layout.workCap().x(),
                layout.workCap().labelY());
        if (layout.showHelp()) {
            graphics.drawWordWrap(
                    this.font,
                    Component.translatable("screen.delvefold.province.help"),
                    x + 12,
                    layout.helpY(),
                    width - 24,
                    DIM_TEXT);
        }
        if (!this.error.getString().isEmpty()) {
            if (layout.compact()) {
                graphics.enableScissor(x + 1, layout.errorY(), x + width - 1, layout.contentBottom());
            }
            graphics.drawWordWrap(this.font, this.error, x + 12, layout.errorY(), width - 24, DANGER);
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
