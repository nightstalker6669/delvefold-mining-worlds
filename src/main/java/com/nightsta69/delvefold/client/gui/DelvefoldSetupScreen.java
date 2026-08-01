package com.nightsta69.delvefold.client.gui;

import com.nightsta69.delvefold.client.DelvefoldClientRequests;
import com.nightsta69.delvefold.client.gui.widget.DelvefoldButton.Style;
import com.nightsta69.delvefold.config.model.GameplayPreset;
import com.nightsta69.delvefold.config.model.GameplaySettings;
import com.nightsta69.delvefold.config.model.GeologyTheme;
import com.nightsta69.delvefold.config.model.LandmarkPreset;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.RenewalSeedMode;
import com.nightsta69.delvefold.config.model.RenewalSettings;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.TerrainVariant;
import com.nightsta69.delvefold.config.model.WorldIdentitySettings;
import com.nightsta69.delvefold.network.model.ActionStatus;
import com.nightsta69.delvefold.network.model.AdminSnapshot;
import com.nightsta69.delvefold.network.payload.ActionResultPayload;
import com.nightsta69.delvefold.network.payload.InitializeWorldPayload;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Three-step first-time mining-world setup screen with explicit recreation-locked confirmation. */
public final class DelvefoldSetupScreen extends DelvefoldScreen {
    private static final int BODY_SCROLL_STEP = 24;
    private static final int RESOURCE_OPTIONS_TOP = 39;
    private static final int RESOURCE_GAMEPLAY_OPTIONS_TOP = 115;
    private static final int RESOURCE_GAMEPLAY_HELP_TOP = 153;
    private static final int RESOURCE_LANDMARK_TITLE_TOP = 176;
    private static final int RESOURCE_LANDMARK_OPTIONS_TOP = 191;
    private static final int RESOURCE_LANDMARK_HELP_TOP = 225;

    private TerrainMode terrainMode;
    private OrePreset orePreset;
    private GameplayPreset gameplayPreset;
    private TerrainVariant terrainVariant;
    private GeologyTheme geologyTheme;
    private LandmarkPreset landmarkPreset;
    private RenewalSeedMode renewalSeedMode;
    private Step step = Step.TERRAIN;
    private boolean lockConfirmed;
    private @Nullable Button confirmationButton;
    private @Nullable Button initializeButton;
    private Component localStatus = Component.empty();
    private int localStatusColor = ACCENT;
    private final List<AbstractWidget> bodyWidgets = new ArrayList<>();
    private final IdentityHashMap<AbstractWidget, Integer> bodyWidgetY = new IdentityHashMap<>();
    private int bodyScrollOffset;
    private int bodyVirtualBottom;
    private int terrainModeOptionsTop;
    private int terrainVariantLabelTop;
    private int terrainVariantOptionsTop;
    private int terrainGeologyLabelTop;
    private int terrainGeologyOptionsTop;
    private int terrainSeedLabelTop;
    private int terrainSeedOptionsTop;
    private int terrainNoticeTop;
    private int terrainSeedHelpTop;
    private int terrainGeologyHelpTop;
    private int resourceGameplayTitleTop;
    private int resourceGameplayOptionsTop;
    private int resourceGameplayHelpTop;
    private int resourceLandmarkTitleTop;
    private int resourceLandmarkOptionsTop;
    private int resourceLandmarkHelpTop;

    /**
     * Creates setup controls initialized from the server's proposed settings.
     *
     * @param snapshot immutable uninitialized administration snapshot and capability set
     */
    public DelvefoldSetupScreen(AdminSnapshot snapshot) {
        super(Component.translatable("screen.delvefold.setup.title"), snapshot);
        this.terrainMode = snapshot.terrainMode();
        this.orePreset = snapshot.orePreset();
        this.gameplayPreset = snapshot.gameplay().preset();
        this.terrainVariant = snapshot.identity().terrainVariant();
        this.geologyTheme = snapshot.identity().geologyTheme();
        this.landmarkPreset = snapshot.identity().landmarkPreset();
        this.renewalSeedMode = snapshot.identity().renewal().seedMode();
    }

    @Override
    protected void initPanel() {
        this.bodyWidgets.clear();
        this.bodyWidgetY.clear();
        int x = this.contentLeft();
        int width = this.contentWidth();
        int gap = 6;
        int tabWidth = (width - gap * 2) / 3;
        for (Step value : Step.values()) {
            int tabX = x + GuiEnumOrder.index(value) * (tabWidth + gap);
            this.addButton(
                    tabX,
                    this.contentTop(),
                    tabWidth,
                    22,
                    Component.translatable(
                            "screen.delvefold.setup.step",
                            GuiEnumOrder.index(value) + 1,
                            Component.translatable(value.translationKey)),
                    this.step == value ? Style.TAB_SELECTED : Style.GHOST,
                    button -> moveTo(value));
        }

        switch (this.step) {
            case TERRAIN -> initTerrain();
            case RESOURCES -> initResources();
            case REVIEW -> initReview();
        }
        applyBodyScroll();

        int footerY = this.panelTop + this.panelHeight - 29;
        if (this.step == Step.TERRAIN) {
            this.addButton(
                    x, footerY, 76, 22, Component.translatable("gui.cancel"), Style.GHOST, button -> this.onClose());
        } else {
            this.addButton(
                    x,
                    footerY,
                    76,
                    22,
                    Component.translatable("screen.delvefold.back"),
                    Style.GHOST,
                    button -> moveTo(Step.values()[GuiEnumOrder.index(this.step) - 1]));
        }

        if (this.step != Step.REVIEW) {
            this.addButton(
                    this.contentRight() - 104,
                    footerY,
                    104,
                    22,
                    Component.translatable("screen.delvefold.continue"),
                    Style.PRIMARY,
                    button -> moveTo(Step.values()[GuiEnumOrder.index(this.step) + 1]));
        } else {
            Button initialize = this.addButton(
                    this.contentRight() - 172,
                    footerY,
                    172,
                    22,
                    Component.translatable("screen.delvefold.setup.initialize"),
                    Style.PRIMARY,
                    button -> initialize());
            this.initializeButton = initialize;
            initialize.active = this.snapshot.backendReady() && this.lockConfirmed;
        }
    }

    private void initTerrain() {
        int x = this.contentLeft() + 12;
        int bodyTop = bodyTop();
        int width = this.contentWidth() - 24;
        int gap = 8;
        int helpHeight = wrappedTextHeight(Component.translatable("screen.delvefold.setup.terrain.help"), width);
        this.terrainModeOptionsTop = Math.max(38, 23 + helpHeight + 6);
        this.terrainVariantLabelTop = this.terrainModeOptionsTop + 45;
        this.terrainVariantOptionsTop = this.terrainModeOptionsTop + 54;
        this.terrainGeologyLabelTop = this.terrainVariantOptionsTop + 30;
        this.terrainGeologyOptionsTop = this.terrainGeologyLabelTop + 15;
        this.terrainSeedLabelTop = this.terrainGeologyOptionsTop + 39;
        this.terrainSeedOptionsTop = this.terrainSeedLabelTop + 15;
        this.terrainNoticeTop = this.terrainSeedOptionsTop + 31;
        int detailHeight = noticeHeight(terrainDetail(), width);
        this.terrainSeedHelpTop = this.terrainNoticeTop + detailHeight + 6;
        int seedHelpHeight = wrappedTextHeight(seedModeHelp(), width);
        this.terrainGeologyHelpTop = Math.max(this.terrainNoticeTop + 72, this.terrainSeedHelpTop + seedHelpHeight + 6);
        int geologyHelpHeight = wrappedTextHeight(geologyThemeHelp(), width);
        this.bodyVirtualBottom = bodyTop + this.terrainGeologyHelpTop + geologyHelpHeight + 8;

        int y = bodyTop + this.terrainModeOptionsTop;
        int optionWidth = (width - gap * 2) / 3;
        for (TerrainMode mode : TerrainMode.values()) {
            int optionX = x + GuiEnumOrder.index(mode) * (optionWidth + gap);
            this.addBodyButton(
                    optionX,
                    y,
                    optionWidth,
                    42,
                    DelvefoldText.choice(
                            this.terrainMode == mode, DelvefoldText.option("terrain", mode.serializedName())),
                    this.terrainMode == mode ? Style.TOGGLE_ON : Style.SECONDARY,
                    button -> {
                        this.terrainMode = mode;
                        resetConfirmation();
                        rebuildWidgets();
                    });
        }
        int variantY = bodyTop + this.terrainVariantOptionsTop;
        int variantWidth = (width - gap) / 2;
        for (TerrainVariant variant : TerrainVariant.values()) {
            int optionX = x + GuiEnumOrder.index(variant) * (variantWidth + gap);
            this.addBodyButton(
                    optionX,
                    variantY,
                    variantWidth,
                    24,
                    DelvefoldText.choice(
                            this.terrainVariant == variant,
                            DelvefoldText.option("terrain_variant", variant.serializedName())),
                    this.terrainVariant == variant ? Style.TOGGLE_ON : Style.SECONDARY,
                    button -> {
                        this.terrainVariant = variant;
                        resetConfirmation();
                        rebuildWidgets();
                    });
        }
        int geologyY = bodyTop + this.terrainGeologyOptionsTop;
        int geologyGap = 4;
        int geologyWidth =
                Math.max(1, (width - geologyGap * (GeologyTheme.values().length - 1)) / GeologyTheme.values().length);
        for (GeologyTheme theme : GeologyTheme.values()) {
            int optionX = x + GuiEnumOrder.index(theme) * (geologyWidth + geologyGap);
            this.addBodyButton(
                    optionX,
                    geologyY,
                    geologyWidth,
                    24,
                    DelvefoldText.choice(
                            this.geologyTheme == theme, DelvefoldText.option("geology_theme", theme.serializedName())),
                    this.geologyTheme == theme ? Style.TOGGLE_ON : Style.SECONDARY,
                    button -> {
                        this.geologyTheme = theme;
                        resetConfirmation();
                        rebuildWidgets();
                    });
        }
        int seedModeY = bodyTop + this.terrainSeedOptionsTop;
        int seedModeWidth = (width - gap) / 2;
        for (RenewalSeedMode mode : RenewalSeedMode.values()) {
            int optionX = x + GuiEnumOrder.index(mode) * (seedModeWidth + gap);
            this.addBodyButton(
                    optionX,
                    seedModeY,
                    seedModeWidth,
                    24,
                    DelvefoldText.choice(
                            this.renewalSeedMode == mode,
                            DelvefoldText.option("renewal_seed_mode", mode.serializedName())),
                    this.renewalSeedMode == mode ? Style.TOGGLE_ON : Style.SECONDARY,
                    button -> {
                        this.renewalSeedMode = mode;
                        resetConfirmation();
                        rebuildWidgets();
                    });
        }
    }

    private void initResources() {
        int x = this.contentLeft() + 12;
        int y = bodyTop();
        int width = this.contentWidth() - 24;
        int gap = 8;
        int oreHelpTop = RESOURCE_OPTIONS_TOP + 37;
        int oreHelpHeight = wrappedTextHeight(oreDescription(), width);
        int gameplayTitleMinimum = RESOURCE_GAMEPLAY_OPTIONS_TOP - 16;
        this.resourceGameplayTitleTop = Math.max(gameplayTitleMinimum, oreHelpTop + oreHelpHeight + 8);
        this.resourceGameplayOptionsTop =
                this.resourceGameplayTitleTop + (RESOURCE_GAMEPLAY_OPTIONS_TOP - gameplayTitleMinimum);
        this.resourceGameplayHelpTop =
                this.resourceGameplayOptionsTop + (RESOURCE_GAMEPLAY_HELP_TOP - RESOURCE_GAMEPLAY_OPTIONS_TOP);
        int gameplayHelpHeight = wrappedTextHeight(gameplayDescription(), width);
        this.resourceLandmarkTitleTop = Math.max(
                this.resourceGameplayTitleTop + (RESOURCE_LANDMARK_TITLE_TOP - gameplayTitleMinimum),
                this.resourceGameplayHelpTop + gameplayHelpHeight + 8);
        this.resourceLandmarkOptionsTop =
                this.resourceLandmarkTitleTop + (RESOURCE_LANDMARK_OPTIONS_TOP - RESOURCE_LANDMARK_TITLE_TOP);
        this.resourceLandmarkHelpTop =
                this.resourceLandmarkOptionsTop + (RESOURCE_LANDMARK_HELP_TOP - RESOURCE_LANDMARK_OPTIONS_TOP);
        int landmarkHelpHeight = wrappedTextHeight(landmarkDescription(), width);
        this.bodyVirtualBottom = y + this.resourceLandmarkHelpTop + landmarkHelpHeight + 8;

        int optionWidth = (width - gap * 2) / 3;
        for (OrePreset preset : OrePreset.values()) {
            int optionX = x + GuiEnumOrder.index(preset) * (optionWidth + gap);
            this.addBodyButton(
                    optionX,
                    y + RESOURCE_OPTIONS_TOP,
                    optionWidth,
                    32,
                    DelvefoldText.choice(
                            this.orePreset == preset, DelvefoldText.option("ore_preset", preset.serializedName())),
                    this.orePreset == preset ? Style.TOGGLE_ON : Style.SECONDARY,
                    button -> {
                        this.orePreset = preset;
                        resetConfirmation();
                        rebuildWidgets();
                    });
        }
        int gameplayY = y + this.resourceGameplayOptionsTop;
        for (GameplayPreset preset : GameplayPreset.values()) {
            int optionX = x + GuiEnumOrder.index(preset) * (optionWidth + gap);
            this.addBodyButton(
                    optionX,
                    gameplayY,
                    optionWidth,
                    32,
                    DelvefoldText.choice(
                            this.gameplayPreset == preset, DelvefoldText.option("gameplay", preset.serializedName())),
                    this.gameplayPreset == preset ? Style.TOGGLE_ON : Style.SECONDARY,
                    button -> {
                        this.gameplayPreset = preset;
                        resetConfirmation();
                        rebuildWidgets();
                    });
        }
        int landmarkY = y + this.resourceLandmarkOptionsTop;
        for (LandmarkPreset preset : LandmarkPreset.values()) {
            int optionX = x + GuiEnumOrder.index(preset) * (optionWidth + gap);
            this.addBodyButton(
                    optionX,
                    landmarkY,
                    optionWidth,
                    28,
                    DelvefoldText.choice(
                            this.landmarkPreset == preset, DelvefoldText.option("landmark", preset.serializedName())),
                    this.landmarkPreset == preset ? Style.TOGGLE_ON : Style.SECONDARY,
                    button -> {
                        this.landmarkPreset = preset;
                        resetConfirmation();
                        rebuildWidgets();
                    });
        }
    }

    private void initReview() {
        int x = this.contentLeft() + 10;
        int y = bodyTop() + 153;
        int width = this.contentWidth() - 20;
        this.confirmationButton = this.addBodyButton(
                x,
                y,
                width,
                24,
                confirmationLabel(),
                this.lockConfirmed ? Style.TOGGLE_ON : Style.TOGGLE_OFF,
                button -> toggleConfirmation());
        this.bodyVirtualBottom = reviewVirtualBottom();
    }

    private void moveTo(Step requested) {
        this.step = requested;
        this.bodyScrollOffset = 0;
        rebuildWidgets();
    }

    private void toggleConfirmation() {
        Button confirmation = this.confirmationButton;
        Button initialize = this.initializeButton;
        if (confirmation == null || initialize == null) {
            return;
        }
        this.lockConfirmed = !this.lockConfirmed;
        confirmation.setMessage(confirmationLabel());
        setButtonStyle(confirmation, this.lockConfirmed ? Style.TOGGLE_ON : Style.TOGGLE_OFF);
        initialize.active = this.snapshot.backendReady() && this.lockConfirmed;
    }

    private void resetConfirmation() {
        this.lockConfirmed = false;
    }

    private void initialize() {
        Button initialize = this.initializeButton;
        if (initialize == null || !this.lockConfirmed || !this.snapshot.backendReady()) {
            return;
        }
        initialize.active = false;
        this.localStatus = Component.translatable("screen.delvefold.setup.initializing");
        this.localStatusColor = ACCENT;
        this.bodyVirtualBottom = reviewVirtualBottom();
        setBodyScrollOffset(this.bodyScrollOffset);
        DelvefoldClientRequests.send(new InitializeWorldPayload(
                this.snapshot.oreRevision(),
                this.snapshot.settingsRevision(),
                this.terrainMode,
                this.orePreset,
                GameplaySettings.fromPreset(this.gameplayPreset),
                selectedIdentity(),
                true));
    }

    private WorldIdentitySettings selectedIdentity() {
        WorldIdentitySettings source = this.snapshot.identity();
        boolean enabled = this.landmarkPreset != LandmarkPreset.PURE_MINING;
        RenewalSettings renewal = source.renewal();
        return new WorldIdentitySettings(
                source.displayName(),
                this.terrainVariant,
                this.landmarkPreset,
                enabled && source.surveyStations(),
                enabled && source.motherlodes(),
                enabled && source.faultLines(),
                this.geologyTheme,
                new RenewalSettings(
                        renewal.enabled(),
                        renewal.intervalDays(),
                        renewal.warningMinutes(),
                        renewal.nextRenewalAtEpochMillis(),
                        this.renewalSeedMode));
    }

    private Component confirmationLabel() {
        return Component.translatable(
                this.lockConfirmed ? "screen.delvefold.setup.confirmed" : "screen.delvefold.setup.confirm");
    }

    private int bodyTop() {
        return this.contentTop() + 31;
    }

    private int wrappedTextHeight(Component text, int width) {
        return Math.max(
                this.font.lineHeight, this.font.split(text, Math.max(1, width)).size() * this.font.lineHeight);
    }

    private int noticeHeight(Component text, int width) {
        return Math.max(34, wrappedTextHeight(text, Math.max(1, width - 16)) + 14);
    }

    private int reviewVirtualBottom() {
        int contentBottom = 153 + 24 + 8;
        Component status = reviewStatus();
        if (!status.getString().isEmpty()) {
            contentBottom = Math.max(contentBottom, 190 + noticeHeight(status, this.contentWidth() - 20) + 8);
        }
        return bodyTop() + contentBottom;
    }

    private Component terrainDetail() {
        return Component.translatable(
                "screen.delvefold.setup.terrain.detail",
                Component.translatable("screen.delvefold.setup.terrain.detail." + this.terrainMode.serializedName()),
                Component.translatable("screen.delvefold.setup.terrain.scale." + this.terrainVariant.serializedName()));
    }

    private Component seedModeHelp() {
        return Component.translatable(
                "screen.delvefold.setup.terrain.seed_mode.help." + this.renewalSeedMode.serializedName());
    }

    private Component geologyThemeHelp() {
        return Component.translatable(
                "screen.delvefold.setup.terrain.geology_theme.help." + this.geologyTheme.serializedName());
    }

    private Component reviewStatus() {
        return !this.snapshot.backendReady()
                ? DelvefoldText.serverMessage(this.snapshot.worldStatus())
                : this.localStatus;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        for (Renderable renderable : this.renderables) {
            if (!this.bodyWidgets.contains(renderable)) {
                renderable.render(graphics, mouseX, mouseY, partialTick);
            }
        }
        VerticalScrollLayout layout = bodyScrollLayout();
        graphics.enableScissor(
                this.contentLeft() + 1, layout.viewportTop(), this.contentRight() - 1, layout.viewportBottom());
        for (AbstractWidget widget : this.bodyWidgets) {
            widget.render(graphics, mouseX, mouseY, partialTick);
        }
        graphics.disableScissor();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        VerticalScrollLayout layout = bodyScrollLayout();
        if (mouseX >= this.contentLeft()
                && mouseX < this.contentRight()
                && mouseY >= layout.viewportTop()
                && mouseY < layout.viewportBottom()
                && layout.maximumScroll() > 0) {
            setBodyScrollOffset(this.bodyScrollOffset - (int) Math.signum(scrollY) * BODY_SCROLL_STEP);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void renderPanelContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = this.contentLeft();
        int y = bodyTop();
        int width = this.contentWidth();
        int height = this.contentBottom() - y;
        this.drawCard(graphics, x, y, width, height);
        VerticalScrollLayout layout = bodyScrollLayout();
        graphics.enableScissor(x + 1, layout.viewportTop(), x + width - 1, layout.viewportBottom());
        int scrolledY = y - this.bodyScrollOffset;
        switch (this.step) {
            case TERRAIN -> renderTerrain(graphics, x, scrolledY, width);
            case RESOURCES -> renderResources(graphics, x, scrolledY, width);
            case REVIEW -> renderReview(graphics, x, scrolledY, width);
        }
        graphics.disableScissor();
        drawBodyScrollbar(graphics, x + width - 5, layout);
    }

    private Button addBodyButton(
            int x, int y, int width, int height, Component label, Style style, Button.OnPress onPress) {
        Button widget = this.addButton(x, y, width, height, label, style, onPress);
        this.bodyWidgets.add(widget);
        this.bodyWidgetY.put(widget, y);
        return widget;
    }

    private VerticalScrollLayout bodyScrollLayout() {
        int top = bodyTop() + 1;
        int bottom = Math.max(top + 1, this.contentBottom() - 1);
        return new VerticalScrollLayout(top, bottom, this.bodyVirtualBottom);
    }

    private void setBodyScrollOffset(int requestedOffset) {
        this.bodyScrollOffset = bodyScrollLayout().clamp(requestedOffset);
        applyBodyScroll();
    }

    private void applyBodyScroll() {
        VerticalScrollLayout layout = bodyScrollLayout();
        this.bodyScrollOffset = layout.clamp(this.bodyScrollOffset);
        for (AbstractWidget widget : this.bodyWidgets) {
            int y = layout.screenY(this.bodyWidgetY.getOrDefault(widget, widget.getY()), this.bodyScrollOffset);
            widget.setY(y);
            widget.visible = layout.fullyVisible(y, widget.getHeight());
        }
    }

    private void drawBodyScrollbar(GuiGraphics graphics, int x, VerticalScrollLayout layout) {
        int maximum = layout.maximumScroll();
        if (maximum <= 0) {
            return;
        }
        int trackHeight = layout.viewportHeight();
        int virtualHeight = Math.max(trackHeight, this.bodyVirtualBottom - layout.viewportTop());
        int thumbHeight = Math.min(trackHeight, Math.max(14, trackHeight * trackHeight / virtualHeight));
        int thumbTravel = Math.max(1, trackHeight - thumbHeight);
        int thumbY = layout.viewportTop() + this.bodyScrollOffset * thumbTravel / maximum;
        graphics.fill(x, layout.viewportTop(), x + 3, layout.viewportBottom(), 0xAA0B1318);
        graphics.fill(x, thumbY, x + 3, thumbY + thumbHeight, ACCENT);
    }

    private void renderTerrain(GuiGraphics graphics, int x, int y, int width) {
        this.drawSectionTitle(graphics, Component.translatable("screen.delvefold.setup.terrain.title"), x + 10, y + 8);
        graphics.drawWordWrap(
                this.font,
                Component.translatable("screen.delvefold.setup.terrain.help"),
                x + 12,
                y + 23,
                width - 24,
                MUTED_TEXT);
        Component detail = terrainDetail();
        graphics.drawString(
                this.font,
                Component.translatable("screen.delvefold.setup.terrain.scale"),
                x + 12,
                y + this.terrainVariantLabelTop,
                MUTED_TEXT,
                false);
        graphics.drawString(
                this.font,
                Component.translatable("screen.delvefold.setup.terrain.geology_theme"),
                x + 12,
                y + this.terrainGeologyLabelTop,
                MUTED_TEXT,
                false);
        graphics.drawString(
                this.font,
                Component.translatable("screen.delvefold.setup.terrain.seed_mode"),
                x + 12,
                y + this.terrainSeedLabelTop,
                MUTED_TEXT,
                false);
        drawNotice(graphics, x + 12, y + this.terrainNoticeTop, width - 24, detail, ACCENT);
        graphics.drawWordWrap(this.font, seedModeHelp(), x + 12, y + this.terrainSeedHelpTop, width - 24, DIM_TEXT);
        graphics.drawWordWrap(
                this.font, geologyThemeHelp(), x + 12, y + this.terrainGeologyHelpTop, width - 24, DIM_TEXT);
    }

    private void renderResources(GuiGraphics graphics, int x, int y, int width) {
        this.drawSectionTitle(graphics, Component.translatable("screen.delvefold.setup.resources.ores"), x + 10, y + 8);
        graphics.drawWordWrap(this.font, oreDescription(), x + 12, y + 76, width - 24, MUTED_TEXT);
        this.drawSectionTitle(
                graphics,
                Component.translatable("screen.delvefold.setup.resources.gameplay"),
                x + 10,
                y + this.resourceGameplayTitleTop);
        graphics.drawWordWrap(
                this.font, gameplayDescription(), x + 12, y + this.resourceGameplayHelpTop, width - 24, DIM_TEXT);
        this.drawSectionTitle(
                graphics,
                Component.translatable("screen.delvefold.setup.resources.landmarks"),
                x + 10,
                y + this.resourceLandmarkTitleTop);
        graphics.drawWordWrap(
                this.font, landmarkDescription(), x + 12, y + this.resourceLandmarkHelpTop, width - 24, DIM_TEXT);
    }

    private void renderReview(GuiGraphics graphics, int x, int y, int width) {
        this.drawSectionTitle(graphics, Component.translatable("screen.delvefold.setup.review.title"), x + 10, y + 8);
        graphics.drawString(
                this.font,
                Component.translatable("screen.delvefold.setup.review.terrain"),
                x + 14,
                y + 29,
                MUTED_TEXT,
                false);
        graphics.drawString(
                this.font,
                DelvefoldText.option("terrain", this.terrainMode.serializedName()),
                x + width / 2,
                y + 29,
                TEXT,
                false);
        graphics.drawString(
                this.font,
                Component.translatable("screen.delvefold.setup.review.ore"),
                x + 14,
                y + 45,
                MUTED_TEXT,
                false);
        graphics.drawString(
                this.font,
                DelvefoldText.option("ore_preset", this.orePreset.serializedName()),
                x + width / 2,
                y + 45,
                TEXT,
                false);
        graphics.drawString(
                this.font,
                Component.translatable("screen.delvefold.setup.review.gameplay"),
                x + 14,
                y + 61,
                MUTED_TEXT,
                false);
        graphics.drawString(
                this.font,
                DelvefoldText.option("gameplay", this.gameplayPreset.serializedName()),
                x + width / 2,
                y + 61,
                TEXT,
                false);
        graphics.drawString(
                this.font,
                Component.translatable("screen.delvefold.setup.review.scale"),
                x + 14,
                y + 77,
                MUTED_TEXT,
                false);
        graphics.drawString(
                this.font,
                DelvefoldText.option("terrain_variant", this.terrainVariant.serializedName()),
                x + width / 2,
                y + 77,
                TEXT,
                false);
        graphics.drawString(
                this.font,
                Component.translatable("screen.delvefold.setup.review.geology_theme"),
                x + 14,
                y + 93,
                MUTED_TEXT,
                false);
        graphics.drawString(
                this.font,
                DelvefoldText.option("geology_theme", this.geologyTheme.serializedName()),
                x + width / 2,
                y + 93,
                TEXT,
                false);
        graphics.drawString(
                this.font,
                Component.translatable("screen.delvefold.setup.review.landmarks"),
                x + 14,
                y + 109,
                MUTED_TEXT,
                false);
        graphics.drawString(
                this.font,
                DelvefoldText.option("landmark", this.landmarkPreset.serializedName()),
                x + width / 2,
                y + 109,
                TEXT,
                false);
        graphics.drawString(
                this.font,
                Component.translatable("screen.delvefold.setup.review.seed_mode"),
                x + 14,
                y + 125,
                MUTED_TEXT,
                false);
        graphics.drawString(
                this.font,
                DelvefoldText.option("renewal_seed_mode", this.renewalSeedMode.serializedName()),
                x + width / 2,
                y + 125,
                TEXT,
                false);

        Component status = reviewStatus();
        int color = !this.snapshot.backendReady() ? DANGER : this.localStatusColor;
        if (!status.getString().isEmpty()) {
            drawNotice(graphics, x + 10, y + 190, width - 20, status, color);
        }
    }

    private void drawNotice(GuiGraphics graphics, int x, int y, int width, Component message, int color) {
        int height = noticeHeight(message, width);
        graphics.fill(x, y, x + width, y + height, 0xCC111A20);
        graphics.fill(x, y, x + 3, y + height, color);
        graphics.drawWordWrap(this.font, message, x + 9, y + 7, width - 16, color);
    }

    private Component oreDescription() {
        return switch (this.orePreset) {
            case VANILLA_BALANCED -> Component.translatable("screen.delvefold.setup.ore_help.vanilla_balanced");
            case RICH -> Component.translatable("screen.delvefold.setup.ore_help.rich");
            case EMPTY -> Component.translatable("screen.delvefold.setup.ore_help.empty");
        };
    }

    private Component gameplayDescription() {
        return switch (this.gameplayPreset) {
            case SAFE -> Component.translatable("screen.delvefold.setup.gameplay_help.safe");
            case HOSTILE -> Component.translatable("screen.delvefold.setup.gameplay_help.hostile");
            case NORMAL -> Component.translatable("screen.delvefold.setup.gameplay_help.normal");
        };
    }

    private Component landmarkDescription() {
        return switch (this.landmarkPreset) {
            case PURE_MINING -> Component.translatable("screen.delvefold.setup.landmark_help.pure_mining");
            case BALANCED -> Component.translatable("screen.delvefold.setup.landmark_help.balanced");
            case ABUNDANT -> Component.translatable("screen.delvefold.setup.landmark_help.abundant");
        };
    }

    @Override
    public void handleActionResult(ActionResultPayload payload) {
        this.localStatus = DelvefoldText.serverMessage(payload.message());
        this.localStatusColor = payload.status() == ActionStatus.ACCEPTED ? SUCCESS : DANGER;
        if (this.step == Step.REVIEW) {
            this.bodyVirtualBottom = reviewVirtualBottom();
            setBodyScrollOffset(this.bodyScrollOffset);
        }
        Button initialize = this.initializeButton;
        if (payload.status() != ActionStatus.ACCEPTED && initialize != null) {
            initialize.active = this.snapshot.backendReady() && this.lockConfirmed;
        }
    }

    @Override
    public Component getNarrationMessage() {
        Component status = reviewStatus();
        if (status.getString().isEmpty()) {
            status = Component.translatable("screen.delvefold.status.ready");
        }
        return Component.translatable(
                "screen.delvefold.setup.narration", Component.translatable(this.step.translationKey), status);
    }

    private enum Step {
        TERRAIN("screen.delvefold.setup.step.terrain"),
        RESOURCES("screen.delvefold.setup.step.resources"),
        REVIEW("screen.delvefold.setup.step.review");

        private final String translationKey;

        Step(String translationKey) {
            this.translationKey = translationKey;
        }
    }
}
