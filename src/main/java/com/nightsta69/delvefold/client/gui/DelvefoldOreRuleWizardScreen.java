package com.nightsta69.delvefold.client.gui;

import com.nightsta69.delvefold.client.DelvefoldClientRequests;
import com.nightsta69.delvefold.client.gui.widget.DelvefoldButton.Style;
import com.nightsta69.delvefold.config.analysis.OreDistributionAnalysis;
import com.nightsta69.delvefold.config.model.HeightDistribution;
import com.nightsta69.delvefold.config.model.OreBandPlacement;
import com.nightsta69.delvefold.config.model.ProvinceSettings;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.network.ProtocolLimits;
import com.nightsta69.delvefold.network.model.ActionStatus;
import com.nightsta69.delvefold.network.model.AdminSnapshot;
import com.nightsta69.delvefold.network.payload.ActionResultPayload;
import com.nightsta69.delvefold.network.payload.DeleteOreRulePayload;
import com.nightsta69.delvefold.network.payload.SaveOreRulePayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** Multi-page ore-rule editor that sends one validated, revision-guarded server mutation. */
public final class DelvefoldOreRuleWizardScreen extends DelvefoldScreen {
    private static final int BODY_SCROLL_STEP = 22;

    private final Screen parent;
    private final boolean existingRule;
    private final String originalRuleId;
    private final OreRuleWizardDraftState draftState;
    private final List<String> candidateVariants;
    private final ScrollableWidgetGroup bodyScroll = new ScrollableWidgetGroup();
    private final Page page;
    private int bandIndex;
    private int variantPage;
    private boolean deleteArmed;
    private Component validationMessage = Component.empty();
    private int validationColor = DANGER;
    private boolean closeOnNextSnapshot;
    private OreBandPreviewModel bandPreview;

    private @Nullable EditBox ruleIdBox;
    private @Nullable EditBox hostTagBox;
    private @Nullable EditBox weightBox;
    private @Nullable EditBox statePropertiesBox;
    private @Nullable EditBox biomeIncludesBox;
    private @Nullable EditBox biomeExcludesBox;
    private @Nullable EditBox bandIdBox;
    private @Nullable EditBox veinSizeBox;
    private @Nullable EditBox attemptsBox;
    private @Nullable EditBox minYBox;
    private @Nullable EditBox maxYBox;
    private @Nullable EditBox peakYBox;
    private @Nullable EditBox plateauMinBox;
    private @Nullable EditBox plateauMaxBox;
    private @Nullable EditBox airDiscardBox;
    private @Nullable Button saveButton;
    private @Nullable Button deleteButton;

    /**
     * Creates an editor from a copied rule draft without mutating the supplied snapshot.
     *
     * @param parent screen restored on cancellation or completion
     * @param snapshot immutable administration state and revision
     * @param draft bounded rule draft to create or edit
     */
    public DelvefoldOreRuleWizardScreen(Screen parent, AdminSnapshot snapshot, AdminSnapshot.OreRuleDraft draft) {
        this(
                parent,
                snapshot,
                draft,
                Page.TARGETS,
                0,
                snapshot.oreRules().stream().anyMatch(rule -> rule.id().equals(draft.id())),
                draft.id(),
                draft.variants().isEmpty()
                        ? draft.primaryBlockId()
                        : draft.variants().get(0).sourceId(),
                null,
                0,
                0,
                OreRuleWizardDraftState.hasDuplicateSources(draft));
    }

    private DelvefoldOreRuleWizardScreen(
            Screen parent,
            AdminSnapshot snapshot,
            AdminSnapshot.OreRuleDraft draft,
            Page page,
            int bandIndex,
            boolean existingRule,
            String originalRuleId,
            @Nullable String focusedVariant,
            @Nullable List<String> inheritedCandidates,
            int variantPage,
            int bodyScrollOffset,
            boolean unsupportedDuplicateSources) {
        super(Component.translatable("screen.delvefold.ore_wizard.title"), snapshot);
        this.parent = parent;
        this.page = page;
        this.draftState = new OreRuleWizardDraftState(draft, focusedVariant, unsupportedDuplicateSources);
        this.bandIndex = Math.max(0, Math.min(bandIndex, this.draftState.bands.size() - 1));
        this.variantPage = Math.max(0, variantPage);
        this.bodyScroll.restoreOffset(bodyScrollOffset);
        this.existingRule = existingRule;
        this.originalRuleId = originalRuleId;
        this.bandPreview = OreBandPreviewModel.from(this.draftState.bands.get(this.bandIndex));
        this.candidateVariants = inheritedCandidates == null
                ? detectVariants(this.draftState.primaryBlockId, this.draftState.selectedVariants.keySet())
                : new ArrayList<>(inheritedCandidates);
        resetValidationMessage();
    }

    @Override
    protected void initPanel() {
        this.bodyScroll.reset(bodyTop());
        int tabY = this.contentTop();
        int tabWidth = (this.contentWidth() - 12) / 3;
        this.addButton(
                this.contentLeft(),
                tabY,
                tabWidth,
                24,
                Component.translatable("screen.delvefold.ore_wizard.tab.blocks"),
                this.page == Page.TARGETS ? Style.TAB_SELECTED : Style.GHOST,
                button -> {
                    if (this.page != Page.TARGETS) {
                        openPage(Page.TARGETS, this.bandIndex);
                    }
                });
        this.addButton(
                this.contentLeft() + tabWidth + 6,
                tabY,
                tabWidth,
                24,
                Component.translatable("screen.delvefold.ore_wizard.tab.filters"),
                this.page == Page.FILTERS ? Style.TAB_SELECTED : Style.GHOST,
                button -> {
                    if (this.page != Page.FILTERS) {
                        openPage(Page.FILTERS, this.bandIndex);
                    }
                });
        this.addButton(
                this.contentLeft() + (tabWidth + 6) * 2,
                tabY,
                tabWidth,
                24,
                Component.translatable("screen.delvefold.ore_wizard.tab.bands"),
                this.page == Page.BANDS ? Style.TAB_SELECTED : Style.GHOST,
                button -> {
                    if (this.page != Page.BANDS) {
                        openPage(Page.BANDS, this.bandIndex);
                    }
                });

        if (this.page == Page.TARGETS) {
            initTargets();
        } else if (this.page == Page.FILTERS) {
            initFilters();
        } else {
            initBands();
        }
        applyBodyScroll();

        int footerY = this.footerButtonY();
        this.addButton(
                this.contentLeft(),
                footerY,
                76,
                22,
                Component.translatable("gui.back"),
                Style.GHOST,
                button -> this.minecraft.setScreen(this.parent));
        this.saveButton = this.addButton(
                this.contentRight() - 106,
                footerY,
                106,
                22,
                Component.translatable("screen.delvefold.ore_wizard.save"),
                Style.PRIMARY,
                button -> save());
        this.saveButton.active = this.snapshot.backendReady() && !this.draftState.unsupportedDuplicateSources;
        focusFirstBodyWidget();
    }

    private void initFilters() {
        int x = this.contentLeft() + 12;
        int y = bodyTop() + 39;
        int width = this.contentWidth() - 24;
        this.statePropertiesBox = registerBodyWidget(addWideEditBox(
                x,
                y,
                width,
                this.draftState.rawStateProperties,
                Component.translatable("screen.delvefold.ore_wizard.hint.state")));
        this.statePropertiesBox.setResponder(value -> this.draftState.rawStateProperties = value);
        this.biomeIncludesBox = registerBodyWidget(addWideEditBox(
                x,
                y + 48,
                width,
                this.draftState.rawBiomeIncludes,
                Component.translatable("screen.delvefold.ore_wizard.hint.biomes.include")));
        this.biomeIncludesBox.setResponder(value -> this.draftState.rawBiomeIncludes = value);
        this.biomeExcludesBox = registerBodyWidget(addWideEditBox(
                x,
                y + 96,
                width,
                this.draftState.rawBiomeExcludes,
                Component.translatable("screen.delvefold.ore_wizard.hint.biomes.exclude")));
        this.biomeExcludesBox.setResponder(value -> this.draftState.rawBiomeExcludes = value);
        int helpY = y + 121;
        this.bodyScroll.setVirtualBottom(helpY
                + wrappedTextHeight(Component.translatable("screen.delvefold.ore_wizard.filters.help"), width)
                + 8);
    }

    private void initTargets() {
        int x = this.contentLeft() + 12;
        boolean compact = compactLayout();
        int y = bodyTop() + (compact ? 31 : 39);
        int innerWidth = this.contentWidth() - 24;
        int toggleWidth = Math.min(102, Math.max(64, innerWidth / 5));
        int ruleWidth = innerWidth - toggleWidth * 2 - 12;
        this.ruleIdBox = registerBodyWidget(addEditBox(
                x,
                y,
                ruleWidth,
                this.draftState.ruleId,
                Component.translatable("screen.delvefold.ore_wizard.hint.rule_id")));
        this.ruleIdBox.setResponder(value -> this.draftState.ruleId = value);
        this.ruleIdBox.active = !this.existingRule;
        this.addBodyButton(
                x + ruleWidth + 6,
                y,
                toggleWidth,
                20,
                toggleLabel(Component.translatable("screen.delvefold.ore_wizard.enabled"), this.draftState.enabled),
                this.draftState.enabled ? Style.TOGGLE_ON : Style.TOGGLE_OFF,
                button -> {
                    this.draftState.enabled = !this.draftState.enabled;
                    button.setMessage(toggleLabel(
                            Component.translatable("screen.delvefold.ore_wizard.enabled"), this.draftState.enabled));
                    setButtonStyle(button, this.draftState.enabled ? Style.TOGGLE_ON : Style.TOGGLE_OFF);
                });
        this.addBodyButton(
                x + ruleWidth + toggleWidth + 12,
                y,
                toggleWidth,
                20,
                toggleLabel(Component.translatable("screen.delvefold.ore_wizard.required"), this.draftState.required),
                this.draftState.required ? Style.TOGGLE_ON : Style.TOGGLE_OFF,
                button -> {
                    this.draftState.required = !this.draftState.required;
                    button.setMessage(toggleLabel(
                            Component.translatable("screen.delvefold.ore_wizard.required"), this.draftState.required));
                    setButtonStyle(button, this.draftState.required ? Style.TOGGLE_ON : Style.TOGGLE_OFF);
                });

        int variantY = y + (compact ? 34 : 40);
        OreRuleWizardLayout variantLayout = OreRuleWizardLayout.calculate(
                this.panelWidth, compact, this.candidateVariants.size(), this.variantPage);
        this.variantPage = variantLayout.page();
        int variantColumns = variantLayout.columns();
        int variantRows = variantLayout.rows();
        int variantGap = 5;
        int variantWidth = (innerWidth - variantGap * (variantColumns - 1)) / variantColumns;
        int pageCount = variantLayout.pageCount();
        int candidateStart = variantLayout.start();
        int candidateEnd = variantLayout.end();
        for (int index = candidateStart; index < candidateEnd; index++) {
            String blockId = this.candidateVariants.get(index);
            int pageIndex = index - candidateStart;
            int column = pageIndex % variantColumns;
            int row = pageIndex / variantColumns;
            boolean selected = this.draftState.selectedVariants.containsKey(blockId);
            ResourceLocation registryId = ResourceLocation.tryParse(blockId);
            boolean outputTag = blockId.startsWith("#");
            ResourceLocation tagId = ResourceLocation.tryParse(outputTag ? blockId.substring(1) : blockId);
            boolean missing = outputTag
                    ? tagId == null
                            || BuiltInRegistries.BLOCK
                                    .getTag(net.minecraft.tags.TagKey.create(
                                            net.minecraft.core.registries.Registries.BLOCK, tagId))
                                    .isEmpty()
                    : registryId == null
                            || BuiltInRegistries.BLOCK.getOptional(registryId).isEmpty();
            Component status = Component.translatable(
                    missing
                            ? "screen.delvefold.status.missing"
                            : selected ? "screen.delvefold.status.on" : "screen.delvefold.status.off");
            Component label = Component.translatable(
                    blockId.equals(this.draftState.focusedVariant)
                            ? "screen.delvefold.ore_wizard.variant.focused"
                            : "screen.delvefold.ore_wizard.variant.label",
                    status,
                    blockId);
            this.addBodyButton(
                    x + column * (variantWidth + variantGap),
                    variantY + row * 22,
                    variantWidth,
                    20,
                    label,
                    missing ? Style.DANGER : selected ? Style.TOGGLE_ON : Style.TOGGLE_OFF,
                    ignored -> toggleVariant(blockId));
        }
        if (pageCount > 1) {
            int pagerY = variantY - 16;
            Button previous = this.addBodyButton(
                    x + innerWidth - 68,
                    pagerY,
                    30,
                    14,
                    Component.translatable("screen.delvefold.previous"),
                    Style.GHOST,
                    button -> changeVariantPage(-1));
            previous.active = this.variantPage > 0;
            Button next = this.addBodyButton(
                    x + innerWidth - 32,
                    pagerY,
                    30,
                    14,
                    Component.translatable("screen.delvefold.next"),
                    Style.GHOST,
                    button -> changeVariantPage(1));
            next.active = this.variantPage + 1 < pageCount;
        }

        int hostY = variantY + variantRows * 22 + 10;
        int weightGap = 6;
        int weightWidth = Math.min(92, Math.max(62, innerWidth / 5));
        int hostWidth = innerWidth - weightWidth - weightGap;
        this.hostTagBox = registerBodyWidget(addEditBox(
                x,
                hostY,
                hostWidth,
                this.draftState.selectedVariants.getOrDefault(
                        this.draftState.focusedVariant,
                        OreRuleWizardDraftState.inferredHost(this.draftState.focusedVariant)),
                Component.translatable("screen.delvefold.ore_wizard.hint.replacement_tag")));
        this.hostTagBox.setResponder(value -> {
            if (this.draftState.selectedVariants.containsKey(this.draftState.focusedVariant)) {
                this.draftState.selectedVariants.put(this.draftState.focusedVariant, value);
            }
        });
        this.weightBox = registerBodyWidget(addEditBox(
                x + hostWidth + weightGap,
                hostY,
                weightWidth,
                this.draftState.rawWeight,
                Component.translatable("screen.delvefold.ore_wizard.weight.hint")));
        this.weightBox.setMaxLength(4);
        this.weightBox.setResponder(value -> this.draftState.rawWeight = value);

        boolean focusedVariantVisible = isFocusedVariantVisible(candidateStart, candidateEnd);
        this.hostTagBox.active = focusedVariantVisible;
        this.weightBox.active = focusedVariantVisible;

        int terrainY = hostY + (compact ? 34 : 40);
        int terrainGap = 6;
        int terrainWidth = (innerWidth - terrainGap * 2) / 3;
        for (TerrainMode mode : TerrainMode.values()) {
            int modeX = x + GuiEnumOrder.index(mode) * (terrainWidth + terrainGap);
            boolean selected = this.draftState.terrainModes.contains(mode);
            this.addBodyButton(
                    modeX,
                    terrainY,
                    terrainWidth,
                    20,
                    toggleLabel(
                            DelvefoldText.option("terrain", mode.serializedName()),
                            this.draftState.terrainModes.contains(mode)),
                    selected ? Style.TOGGLE_ON : Style.TOGGLE_OFF,
                    button -> toggleTerrain(mode));
        }

        this.bodyScroll.setVirtualBottom(terrainY + 28);

        if (this.existingRule) {
            int deleteX = this.contentLeft() + 84;
            int maximumDeleteRight = this.contentRight() - 112;
            int deleteWidth = Math.min(122, Math.max(52, maximumDeleteRight - deleteX));
            this.deleteButton = this.addButton(
                    deleteX,
                    this.footerButtonY(),
                    deleteWidth,
                    22,
                    Component.translatable("screen.delvefold.ore_wizard.delete"),
                    Style.DANGER,
                    button -> delete());
        }
    }

    private void initBands() {
        AdminSnapshot.OreBandDraft band = this.draftState.bands.get(this.bandIndex);
        primeBandInputs(band);
        int x = this.contentLeft() + 12;
        boolean compact = compactLayout();
        int y = bodyTop() + (compact ? 31 : 39);
        int innerWidth = this.contentWidth() - 24;
        int selectorGap = 6;
        int selectorWidth = Math.min(132, Math.max(64, innerWidth / 4));
        int bandIdWidth = Math.max(72, innerWidth - selectorWidth * 2 - selectorGap * 2);
        this.bandIdBox = registerBodyWidget(addEditBox(
                x,
                y,
                bandIdWidth,
                this.draftState.bandInputs.bandId(),
                Component.translatable("screen.delvefold.ore_wizard.hint.band_id")));
        this.bandIdBox.setResponder(this.draftState.bandInputs::setBandId);
        this.addBodyButton(
                x + bandIdWidth + selectorGap,
                y,
                selectorWidth,
                20,
                Component.translatable(
                        "screen.delvefold.ore_wizard.selector",
                        Component.translatable("option.delvefold.ore_band_placement."
                                + band.placement().name().toLowerCase(Locale.ROOT))),
                Style.SECONDARY,
                button -> cyclePlacement());
        int distributionX = x + bandIdWidth + selectorGap + selectorWidth + selectorGap;
        this.addBodyButton(
                distributionX,
                y,
                innerWidth - (distributionX - x),
                20,
                Component.translatable(
                        "screen.delvefold.ore_wizard.selector",
                        Component.translatable("option.delvefold.height_distribution."
                                + band.distribution().name().toLowerCase(Locale.ROOT))),
                Style.SECONDARY,
                button -> cycleDistribution());

        int rowTwoY = y + (compact ? 31 : 42);
        int rowTwoGap = 8;
        int thirdWidth = (innerWidth - rowTwoGap * 2) / 3;
        int finalThirdWidth = innerWidth - thirdWidth * 2 - rowTwoGap * 2;
        if (band.placement() == OreBandPlacement.PROVINCE) {
            ProvinceSettings province = OreRuleWizardValidation.provinceOrDefault(band.province());
            this.addBodyButton(
                    x,
                    rowTwoY,
                    thirdWidth * 2 + rowTwoGap,
                    20,
                    Component.translatable(
                            "screen.delvefold.ore_wizard.province.edit",
                            province.regionSize(),
                            province.radius(),
                            province.verticalThickness(),
                            (int) Math.round(province.density() * 100.0D),
                            province.perChunkWorkCap()),
                    Style.SECONDARY,
                    button -> openProvinceSettings());
            this.airDiscardBox = registerBodyWidget(addEditBox(
                    x + thirdWidth * 2 + rowTwoGap * 2,
                    rowTwoY,
                    finalThirdWidth,
                    this.draftState.bandInputs.airDiscard(),
                    Component.translatable("screen.delvefold.ore_wizard.hint.air_discard")));
            this.veinSizeBox = null;
            this.attemptsBox = null;
        } else {
            this.veinSizeBox = registerBodyWidget(addEditBox(
                    x,
                    rowTwoY,
                    thirdWidth,
                    this.draftState.bandInputs.veinSize(),
                    Component.translatable("screen.delvefold.ore_wizard.hint.vein_size")));
            this.attemptsBox = registerBodyWidget(addEditBox(
                    x + thirdWidth + rowTwoGap,
                    rowTwoY,
                    thirdWidth,
                    this.draftState.bandInputs.attempts(),
                    Component.translatable("screen.delvefold.ore_wizard.hint.attempts")));
            this.airDiscardBox = registerBodyWidget(addEditBox(
                    x + thirdWidth * 2 + rowTwoGap * 2,
                    rowTwoY,
                    finalThirdWidth,
                    this.draftState.bandInputs.airDiscard(),
                    Component.translatable("screen.delvefold.ore_wizard.hint.air_discard")));
        }

        int rowThreeY = y + (compact ? 62 : 84);
        int heightGap = 6;
        int heightWidth = (innerWidth - heightGap * 4) / 5;
        EditBox minY = registerBodyWidget(addEditBox(
                x,
                rowThreeY,
                heightWidth,
                this.draftState.bandInputs.minY(),
                Component.translatable("screen.delvefold.ore_wizard.hint.min_y")));
        this.minYBox = minY;
        EditBox maxY = registerBodyWidget(addEditBox(
                x + (heightWidth + heightGap),
                rowThreeY,
                heightWidth,
                this.draftState.bandInputs.maxY(),
                Component.translatable("screen.delvefold.ore_wizard.hint.max_y")));
        this.maxYBox = maxY;
        EditBox peakY = registerBodyWidget(addEditBox(
                x + (heightWidth + heightGap) * 2,
                rowThreeY,
                heightWidth,
                this.draftState.bandInputs.peakY(),
                Component.translatable("screen.delvefold.ore_wizard.hint.peak_y")));
        this.peakYBox = peakY;
        EditBox plateauMin = registerBodyWidget(addEditBox(
                x + (heightWidth + heightGap) * 3,
                rowThreeY,
                heightWidth,
                this.draftState.bandInputs.plateauMin(),
                Component.translatable("screen.delvefold.ore_wizard.hint.plateau_min")));
        this.plateauMinBox = plateauMin;
        EditBox plateauMax = registerBodyWidget(addEditBox(
                x + (heightWidth + heightGap) * 4,
                rowThreeY,
                innerWidth - (heightWidth + heightGap) * 4,
                this.draftState.bandInputs.plateauMax(),
                Component.translatable("screen.delvefold.ore_wizard.hint.plateau_max")));
        this.plateauMaxBox = plateauMax;
        bindRawBandResponders();
        peakY.active = band.distribution() == HeightDistribution.TRIANGLE;
        plateauMin.active = band.distribution() == HeightDistribution.TRAPEZOID;
        plateauMax.active = band.distribution() == HeightDistribution.TRAPEZOID;

        int controlsY = y + (compact ? 95 : 156);
        int controlGap = 6;
        int controlWidth = (innerWidth - controlGap * 3) / 4;
        Button previous = this.addBodyButton(
                x,
                controlsY,
                controlWidth,
                20,
                Component.translatable("screen.delvefold.ore_wizard.previous_band"),
                Style.GHOST,
                button -> changeBand(-1));
        previous.active = this.bandIndex > 0;
        Button next = this.addBodyButton(
                x + controlWidth + controlGap,
                controlsY,
                controlWidth,
                20,
                Component.translatable("screen.delvefold.ore_wizard.next_band"),
                Style.GHOST,
                button -> changeBand(1));
        next.active = this.bandIndex + 1 < this.draftState.bands.size();
        Button add = this.addBodyButton(
                x + (controlWidth + controlGap) * 2,
                controlsY,
                controlWidth,
                20,
                Component.translatable("screen.delvefold.ore_wizard.add_band"),
                Style.PRIMARY,
                button -> addBand());
        add.active = this.draftState.bands.size() < ProtocolLimits.MAX_BANDS;
        Button remove = this.addBodyButton(
                x + (controlWidth + controlGap) * 3,
                controlsY,
                controlWidth,
                20,
                Component.translatable("screen.delvefold.ore_wizard.remove_band"),
                Style.DANGER,
                button -> removeBand());
        remove.active = this.draftState.bands.size() > 1;

        int previewBottom = compact ? rowThreeY + 20 : bodyTop() + 185;
        this.bodyScroll.setVirtualBottom(Math.max(controlsY + 28, previewBottom + 8));
    }

    private EditBox addEditBox(int x, int y, int width, String value, Component hint) {
        EditBox box = this.addRenderableWidget(new EditBox(this.font, x, y, width, 20, hint));
        box.setMaxLength(ProtocolLimits.ID_LENGTH);
        box.setValue(value);
        box.setHint(hint);
        box.setTextColor(TEXT);
        box.setTextColorUneditable(DIM_TEXT);
        return box;
    }

    private Button addBodyButton(
            int x, int y, int width, int height, Component label, Style style, Button.OnPress onPress) {
        return registerBodyWidget(this.addButton(x, y, width, height, label, style, onPress));
    }

    private <T extends AbstractWidget> T registerBodyWidget(T widget) {
        return this.bodyScroll.register(widget);
    }

    private int wrappedTextHeight(Component text, int width) {
        return Math.max(
                this.font.lineHeight, this.font.split(text, Math.max(1, width)).size() * this.font.lineHeight);
    }

    private EditBox addWideEditBox(int x, int y, int width, String value, Component hint) {
        EditBox box = addEditBox(x, y, width, value, hint);
        box.setMaxLength(ProtocolLimits.SHORT_TEXT_LENGTH);
        return box;
    }

    private void primeBandInputs(AdminSnapshot.OreBandDraft band) {
        this.draftState.bandInputs.prime(this.bandIndex, band);
    }

    private void bindRawBandResponders() {
        EditBox veinSize = this.veinSizeBox;
        EditBox attempts = this.attemptsBox;
        EditBox minY = this.minYBox;
        EditBox maxY = this.maxYBox;
        EditBox peakY = this.peakYBox;
        EditBox plateauMin = this.plateauMinBox;
        EditBox plateauMax = this.plateauMaxBox;
        EditBox airDiscard = this.airDiscardBox;
        if (minY == null
                || maxY == null
                || peakY == null
                || plateauMin == null
                || plateauMax == null
                || airDiscard == null) {
            return;
        }
        if (veinSize != null) {
            veinSize.setResponder(this.draftState.bandInputs::setVeinSize);
        }
        if (attempts != null) {
            attempts.setResponder(this.draftState.bandInputs::setAttempts);
        }
        minY.setResponder(this.draftState.bandInputs::setMinY);
        maxY.setResponder(this.draftState.bandInputs::setMaxY);
        peakY.setResponder(this.draftState.bandInputs::setPeakY);
        plateauMin.setResponder(this.draftState.bandInputs::setPlateauMin);
        plateauMax.setResponder(this.draftState.bandInputs::setPlateauMax);
        airDiscard.setResponder(this.draftState.bandInputs::setAirDiscard);
    }

    private void toggleVariant(String blockId) {
        boolean removesFocusedVariant = blockId.equals(this.draftState.focusedVariant)
                && this.draftState.selectedVariants.containsKey(blockId)
                && this.draftState.selectedVariants.size() > 1;
        if (!removesFocusedVariant && !commitFocusedWeight()) {
            return;
        }
        if (this.draftState.selectedVariants.containsKey(blockId)) {
            if (!blockId.equals(this.draftState.focusedVariant)) {
                this.draftState.focusedVariant = blockId;
                reopen(this.page, this.bandIndex);
                return;
            }
            if (this.draftState.selectedVariants.size() > 1) {
                this.draftState.selectedVariants.remove(blockId);
                this.draftState.variantStates.remove(blockId);
                this.draftState.variantWeights.remove(blockId);
                resetValidationMessage();
            }
        } else if (this.draftState.selectedVariants.size() < ProtocolLimits.MAX_VARIANTS) {
            this.draftState.selectedVariants.put(blockId, OreRuleWizardDraftState.inferredHost(blockId));
            this.draftState.variantStates.put(blockId, Map.of());
            this.draftState.variantWeights.put(blockId, AdminSnapshot.OreVariantDraft.MIN_WEIGHT);
        }
        this.draftState.focusedVariant = this.draftState.selectedVariants.containsKey(blockId)
                ? blockId
                : this.draftState.selectedVariants.keySet().iterator().next();
        reopen(this.page, this.bandIndex);
    }

    private void changeVariantPage(int amount) {
        if (!commitFocusedWeight()) {
            return;
        }
        this.variantPage = Math.max(0, this.variantPage + amount);
        focusFirstSelectedVariantOnPage();
        reopen(this.page, this.bandIndex);
    }

    private void toggleTerrain(TerrainMode mode) {
        if (!commitFocusedWeight()) {
            return;
        }
        if (this.draftState.terrainModes.contains(mode)) {
            if (this.draftState.terrainModes.size() > 1) {
                this.draftState.terrainModes.remove(mode);
            }
        } else {
            this.draftState.terrainModes.add(mode);
        }
        reopen(this.page, this.bandIndex);
    }

    private void cycleDistribution() {
        if (!commitBand()) {
            return;
        }
        AdminSnapshot.OreBandDraft current = this.draftState.bands.get(this.bandIndex);
        HeightDistribution[] values = HeightDistribution.values();
        HeightDistribution next = values[(GuiEnumOrder.index(current.distribution()) + 1) % values.length];
        this.draftState.bands.set(
                this.bandIndex,
                new AdminSnapshot.OreBandDraft(
                        current.id(),
                        current.veinSize(),
                        current.attemptsPerChunk(),
                        next,
                        current.minY(),
                        current.maxY(),
                        current.peakY(),
                        current.plateauMinY(),
                        current.plateauMaxY(),
                        current.discardOnAirExposure(),
                        current.placement(),
                        current.province()));
        reopen(Page.BANDS, this.bandIndex);
    }

    private void cyclePlacement() {
        if (!commitBand()) {
            return;
        }
        AdminSnapshot.OreBandDraft current = this.draftState.bands.get(this.bandIndex);
        boolean province = current.placement() != OreBandPlacement.PROVINCE;
        this.draftState.bands.set(
                this.bandIndex,
                new AdminSnapshot.OreBandDraft(
                        current.id(),
                        province ? 1 : 8,
                        province ? 0.0D : 8.0D,
                        current.distribution(),
                        current.minY(),
                        current.maxY(),
                        current.peakY(),
                        current.plateauMinY(),
                        current.plateauMaxY(),
                        current.discardOnAirExposure(),
                        province ? OreBandPlacement.PROVINCE : OreBandPlacement.VEIN,
                        province ? ProvinceSettings.defaults() : null));
        this.draftState.bandInputs.invalidateBand();
        reopen(Page.BANDS, this.bandIndex);
    }

    private void openProvinceSettings() {
        if (!commitBand() || this.minecraft == null) {
            return;
        }
        AdminSnapshot.OreBandDraft current = this.draftState.bands.get(this.bandIndex);
        ProvinceSettings province = OreRuleWizardValidation.provinceOrDefault(current.province());
        this.minecraft.setScreen(
                new DelvefoldProvinceSettingsScreen(this, this.snapshot, province, this::applyProvinceSettings));
    }

    private void applyProvinceSettings(ProvinceSettings province) {
        AdminSnapshot.OreBandDraft current = this.draftState.bands.get(this.bandIndex);
        this.draftState.bands.set(
                this.bandIndex,
                new AdminSnapshot.OreBandDraft(
                        current.id(),
                        1,
                        0.0D,
                        current.distribution(),
                        current.minY(),
                        current.maxY(),
                        current.peakY(),
                        current.plateauMinY(),
                        current.plateauMaxY(),
                        current.discardOnAirExposure(),
                        OreBandPlacement.PROVINCE,
                        province));
        this.draftState.bandInputs.invalidateBand();
        reopen(Page.BANDS, this.bandIndex);
    }

    private void changeBand(int amount) {
        if (commitBand()) {
            reopen(Page.BANDS, this.bandIndex + amount);
        }
    }

    private void addBand() {
        if (!commitBand() || this.draftState.bands.size() >= ProtocolLimits.MAX_BANDS) {
            return;
        }
        int nextIndex = this.draftState.bands.size();
        AdminSnapshot.OreBandDraft defaults = AdminSnapshot.OreBandDraft.defaultBand();
        this.draftState.bands.add(new AdminSnapshot.OreBandDraft(
                "band_" + (nextIndex + 1),
                defaults.veinSize(),
                defaults.attemptsPerChunk(),
                defaults.distribution(),
                defaults.minY(),
                defaults.maxY(),
                defaults.peakY(),
                defaults.plateauMinY(),
                defaults.plateauMaxY(),
                defaults.discardOnAirExposure(),
                defaults.placement(),
                defaults.province()));
        reopen(Page.BANDS, nextIndex);
    }

    private void removeBand() {
        if (this.draftState.bands.size() <= 1) {
            return;
        }
        this.draftState.bands.remove(this.bandIndex);
        reopen(Page.BANDS, Math.min(this.bandIndex, this.draftState.bands.size() - 1));
    }

    private boolean commitBand() {
        if (this.page != Page.BANDS || this.bandIdBox == null) {
            return true;
        }
        AdminSnapshot.OreBandDraft current = this.draftState.bands.get(this.bandIndex);
        OreRuleWizardValidation.BandParseResult result =
                OreRuleWizardValidation.parseBand(this.draftState.bandInputs.values(), current);
        AdminSnapshot.OreBandDraft accepted = result.band();
        if (accepted == null) {
            this.validationMessage = Component.translatable(result.messageKey());
            this.validationColor = DANGER;
            return false;
        }
        this.draftState.bands.set(this.bandIndex, accepted);
        this.bandPreview = OreBandPreviewModel.from(this.draftState.bands.get(this.bandIndex));
        resetValidationMessage();
        return true;
    }

    private void openPage(Page requestedPage, int requestedBand) {
        if (this.page == Page.TARGETS && !commitFocusedWeight()) {
            return;
        }
        if (this.page == Page.BANDS && !commitBand()) {
            return;
        }
        if (this.page == Page.FILTERS && !commitAdvanced()) {
            return;
        }
        reopen(requestedPage, requestedBand);
    }

    private void reopen(Page requestedPage, int requestedBand) {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new DelvefoldOreRuleWizardScreen(
                    this.parent,
                    this.snapshot,
                    currentDraft(),
                    requestedPage,
                    requestedBand,
                    this.existingRule,
                    this.originalRuleId,
                    this.draftState.focusedVariant,
                    this.candidateVariants,
                    this.variantPage,
                    requestedPage == this.page ? this.bodyScroll.scrollOffset() : 0,
                    this.draftState.unsupportedDuplicateSources));
        }
    }

    private AdminSnapshot.OreRuleDraft currentDraft() {
        return this.draftState.toDraft();
    }

    private void save() {
        if (this.draftState.unsupportedDuplicateSources) {
            showDuplicateSourceMessage();
            return;
        }
        if (!commitFocusedWeight() || !commitBand() || !commitAdvanced() || !validateRule()) {
            return;
        }
        Button save = this.saveButton;
        if (save == null) {
            return;
        }
        save.active = false;
        this.validationMessage = Component.translatable("screen.delvefold.ore_wizard.saving");
        this.validationColor = ACCENT;
        DelvefoldClientRequests.send(
                new SaveOreRulePayload(this.snapshot.oreRevision(), currentDraft(), !this.existingRule));
    }

    private boolean validateRule() {
        if (this.draftState.unsupportedDuplicateSources) {
            showDuplicateSourceMessage();
            return false;
        }
        this.draftState.ruleId = this.draftState.ruleId.trim();
        if (!this.draftState.ruleId.matches("[a-z0-9_.-]{1,128}")) {
            this.validationMessage = Component.translatable("screen.delvefold.ore_wizard.validation.rule_id");
            this.validationColor = DANGER;
            return false;
        }
        if (this.draftState.selectedVariants.isEmpty()
                || this.draftState.terrainModes.isEmpty()
                || this.draftState.bands.isEmpty()) {
            this.validationMessage = Component.translatable("screen.delvefold.ore_wizard.validation.selection");
            this.validationColor = DANGER;
            return false;
        }
        for (Map.Entry<String, String> entry : this.draftState.selectedVariants.entrySet()) {
            String replacementTag = entry.getValue().trim();
            if (replacementTag.startsWith("#")) {
                replacementTag = replacementTag.substring(1);
            }
            String outputId = entry.getKey().startsWith("#") ? entry.getKey().substring(1) : entry.getKey();
            if (ResourceLocation.tryParse(outputId) == null || ResourceLocation.tryParse(replacementTag) == null) {
                this.validationMessage = Component.translatable("screen.delvefold.ore_wizard.validation.registry_ids");
                this.validationColor = DANGER;
                return false;
            }
            entry.setValue(replacementTag);
            int weight = this.draftState.variantWeights.getOrDefault(
                    entry.getKey(), AdminSnapshot.OreVariantDraft.MIN_WEIGHT);
            if (weight < AdminSnapshot.OreVariantDraft.MIN_WEIGHT
                    || weight > AdminSnapshot.OreVariantDraft.MAX_WEIGHT) {
                this.validationMessage = Component.translatable("screen.delvefold.ore_wizard.validation.weight_all");
                this.validationColor = DANGER;
                return false;
            }
        }
        List<String> bandIds = new ArrayList<>();
        for (AdminSnapshot.OreBandDraft band : this.draftState.bands) {
            if (!band.id().matches("[a-z0-9_.-]{1,128}")) {
                this.validationMessage = Component.translatable("screen.delvefold.ore_wizard.validation.band_id");
                this.validationColor = DANGER;
                return false;
            }
            if (bandIds.contains(band.id())) {
                this.validationMessage =
                        Component.translatable("screen.delvefold.ore_wizard.validation.unique_band_id");
                this.validationColor = DANGER;
                return false;
            }
            bandIds.add(band.id());
        }
        resetValidationMessage();
        return true;
    }

    private boolean commitFocusedWeight() {
        if (this.page != Page.TARGETS && this.weightBox == null) {
            return true;
        }
        try {
            int weight = OreRuleWizardValidation.parseWeight(this.draftState.rawWeight);
            if (this.draftState.selectedVariants.containsKey(this.draftState.focusedVariant)) {
                this.draftState.variantWeights.put(this.draftState.focusedVariant, weight);
            }
            resetValidationMessage();
            return true;
        } catch (NumberFormatException exception) {
            this.validationMessage = Component.translatable("screen.delvefold.ore_wizard.validation.weight");
            this.validationColor = DANGER;
            focusWeightField();
            return false;
        }
    }

    private boolean commitAdvanced() {
        if (this.page != Page.FILTERS && this.statePropertiesBox == null) {
            return true;
        }
        try {
            OreRuleWizardValidation.FilterValues filters = OreRuleWizardValidation.parseFilters(
                    this.draftState.rawStateProperties,
                    this.draftState.rawBiomeIncludes,
                    this.draftState.rawBiomeExcludes);
            this.draftState.variantStates.put(this.draftState.focusedVariant, filters.state());
            this.draftState.biomeIncludes.clear();
            this.draftState.biomeIncludes.addAll(filters.biomeIncludes());
            this.draftState.biomeExcludes.clear();
            this.draftState.biomeExcludes.addAll(filters.biomeExcludes());
            resetValidationMessage();
            return true;
        } catch (OreRuleWizardValidation.LocalizedValidationException exception) {
            this.validationMessage = Component.translatable(
                    exception.translationKey(), exception.arguments().toArray());
            this.validationColor = DANGER;
            return false;
        }
    }

    private void delete() {
        Button delete = this.deleteButton;
        if (delete == null) {
            return;
        }
        if (!this.deleteArmed) {
            this.deleteArmed = true;
            delete.setMessage(Component.translatable("screen.delvefold.ore_wizard.confirm_delete"));
            return;
        }
        delete.active = false;
        DelvefoldClientRequests.send(new DeleteOreRulePayload(this.snapshot.oreRevision(), this.originalRuleId));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        for (Renderable renderable : this.renderables) {
            if (!this.bodyScroll.contains(renderable)) {
                renderable.render(graphics, mouseX, mouseY, partialTick);
            }
        }
        VerticalScrollLayout layout = bodyScrollLayout();
        graphics.enableScissor(
                this.contentLeft() + 1, layout.viewportTop(), this.contentRight() - 1, layout.viewportBottom());
        for (AbstractWidget widget : this.bodyScroll.widgets()) {
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
            setBodyScrollOffset(this.bodyScroll.scrollOffset() - (int) Math.signum(scrollY) * BODY_SCROLL_STEP);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_TAB && this.getFocused() instanceof AbstractWidget focused) {
            AbstractWidget next = this.bodyScroll.nextFocusable(focused, !hasShiftDown(), bodyScrollLayout());
            if (next != null) {
                this.setInitialFocus(next);
                return true;
            }
        }
        if (this.getFocused() instanceof AbstractWidget focused
                && !(focused instanceof EditBox)
                && this.bodyScroll.contains(focused)
                && this.bodyScroll.handleScrollKey(keyCode, bodyScrollLayout(), BODY_SCROLL_STEP)) {
            if (!focused.visible) {
                AbstractWidget replacement = this.bodyScroll.firstVisibleFocusable();
                if (replacement != null) {
                    this.setInitialFocus(replacement);
                }
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderPanelContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = this.contentLeft();
        int y = bodyTop();
        int width = this.contentWidth();
        int height = this.contentBottom() - y;
        boolean compact = compactLayout();
        this.drawCard(graphics, x, y, width, height);
        VerticalScrollLayout layout = bodyScrollLayout();
        graphics.enableScissor(x + 1, layout.viewportTop(), x + width - 1, layout.viewportBottom());
        int scrolledY = y - this.bodyScroll.scrollOffset();
        if (this.page == Page.TARGETS) {
            this.drawSectionTitle(
                    graphics,
                    Component.translatable("screen.delvefold.ore_wizard.targets.title"),
                    x + 10,
                    scrolledY + 7);
            int fieldY = scrolledY + (compact ? 31 : 39);
            int variantY = fieldY + (compact ? 34 : 40);
            OreRuleWizardLayout variantLayout = OreRuleWizardLayout.calculate(
                    this.panelWidth, compact, this.candidateVariants.size(), this.variantPage);
            int variantRows = variantLayout.rows();
            int hostY = variantY + variantRows * 22 + 10;
            int terrainY = hostY + (compact ? 34 : 40);
            this.drawFieldLabel(
                    graphics, Component.translatable("screen.delvefold.ore_wizard.rule_id"), x + 12, fieldY - 12);
            Component variantLabel = Component.translatable(
                    width >= 500
                            ? "screen.delvefold.ore_wizard.variants.help"
                            : "screen.delvefold.ore_wizard.variants");
            this.drawFieldLabel(graphics, variantLabel, x + 12, variantY - 12);
            int pageCount = variantLayout.pageCount();
            int start = this.candidateVariants.isEmpty() ? 0 : variantLayout.start() + 1;
            int end = variantLayout.end();
            Component range = Component.translatable(
                    "screen.delvefold.ore_wizard.variant.range", start, end, this.candidateVariants.size());
            int rangeRight = x + width - (pageCount > 1 ? 88 : 12);
            graphics.drawString(this.font, range, rangeRight - this.font.width(range), variantY - 12, DIM_TEXT, false);
            boolean focusedVariantVisible = isFocusedVariantVisible(variantLayout.start(), variantLayout.end());
            Component focusLabel = Component.translatable(
                    focusedVariantVisible
                            ? "screen.delvefold.ore_wizard.replacement_tag"
                            : "screen.delvefold.ore_wizard.replacement_tag.off_page",
                    this.draftState.focusedVariant);
            int innerWidth = width - 24;
            int weightGap = 6;
            int weightWidth = Math.min(92, Math.max(62, innerWidth / 5));
            int hostWidth = innerWidth - weightWidth - weightGap;
            graphics.drawString(this.font, clipped(focusLabel, hostWidth), x + 12, hostY - 12, MUTED_TEXT, false);
            this.drawFieldLabel(
                    graphics,
                    Component.translatable("screen.delvefold.ore_wizard.weight"),
                    x + 12 + hostWidth + weightGap,
                    hostY - 12);
            this.drawFieldLabel(
                    graphics, Component.translatable("screen.delvefold.ore_wizard.terrain"), x + 12, terrainY - 12);
        } else if (this.page == Page.FILTERS) {
            this.drawSectionTitle(
                    graphics,
                    Component.translatable("screen.delvefold.ore_wizard.filters.title"),
                    x + 10,
                    scrolledY + 7);
            int fieldY = scrolledY + 39;
            this.drawFieldLabel(
                    graphics,
                    Component.translatable("screen.delvefold.ore_wizard.state", this.draftState.focusedVariant),
                    x + 12,
                    fieldY - 12);
            this.drawFieldLabel(
                    graphics,
                    Component.translatable("screen.delvefold.ore_wizard.biomes.include"),
                    x + 12,
                    fieldY + 36);
            this.drawFieldLabel(
                    graphics,
                    Component.translatable("screen.delvefold.ore_wizard.biomes.exclude"),
                    x + 12,
                    fieldY + 84);
            graphics.drawWordWrap(
                    this.font,
                    Component.translatable("screen.delvefold.ore_wizard.filters.help"),
                    x + 12,
                    fieldY + 121,
                    width - 24,
                    DIM_TEXT);
        } else {
            AdminSnapshot.OreBandDraft band = this.draftState.bands.get(this.bandIndex);
            int fieldY = scrolledY + (compact ? 31 : 39);
            int rowTwoY = fieldY + (compact ? 31 : 42);
            int rowThreeY = fieldY + (compact ? 62 : 84);
            this.drawSectionTitle(
                    graphics,
                    Component.translatable(
                            "screen.delvefold.ore_wizard.band.title", this.bandIndex + 1, this.draftState.bands.size()),
                    x + 10,
                    scrolledY + 7);
            this.drawFieldLabel(
                    graphics, Component.translatable("screen.delvefold.ore_wizard.band_id"), x + 12, fieldY - 12);
            int selectorGap = 6;
            int selectorWidth = Math.min(132, Math.max(64, (width - 24) / 4));
            int bandIdWidth = Math.max(72, width - 24 - selectorWidth * 2 - selectorGap * 2);
            this.drawFieldLabel(
                    graphics,
                    Component.translatable("screen.delvefold.ore_wizard.placement"),
                    x + 12 + bandIdWidth + selectorGap,
                    fieldY - 12);
            this.drawFieldLabel(
                    graphics,
                    Component.translatable("screen.delvefold.ore_wizard.distribution"),
                    x + 12 + bandIdWidth + selectorGap + selectorWidth + selectorGap,
                    fieldY - 12);
            if (band.placement() == OreBandPlacement.PROVINCE) {
                this.drawFieldLabel(
                        graphics, Component.translatable("screen.delvefold.ore_wizard.province"), x + 12, rowTwoY - 12);
            } else {
                this.drawFieldLabel(
                        graphics,
                        Component.translatable("screen.delvefold.ore_wizard.vein_size"),
                        x + 12,
                        rowTwoY - 12);
                this.drawFieldLabel(
                        graphics,
                        Component.translatable("screen.delvefold.ore_wizard.attempts"),
                        x + 12 + ((width - 24 - 16) / 3) + 8,
                        rowTwoY - 12);
            }
            this.drawFieldLabel(
                    graphics,
                    Component.translatable("screen.delvefold.ore_wizard.air_discard"),
                    x + 12 + (((width - 24 - 16) / 3) + 8) * 2,
                    rowTwoY - 12);

            int innerWidth = width - 24;
            int gap = 6;
            int fieldWidth = (innerWidth - gap * 4) / 5;
            String suffix = width >= 430 ? "" : ".short";
            Component[] heightLabels = new Component[] {
                Component.translatable("screen.delvefold.ore_wizard.height.min" + suffix),
                Component.translatable("screen.delvefold.ore_wizard.height.max" + suffix),
                Component.translatable("screen.delvefold.ore_wizard.height.peak" + suffix),
                Component.translatable("screen.delvefold.ore_wizard.height.plateau_min" + suffix),
                Component.translatable("screen.delvefold.ore_wizard.height.plateau_max" + suffix)
            };
            for (int index = 0; index < heightLabels.length; index++) {
                this.drawFieldLabel(graphics, heightLabels[index], x + 12 + index * (fieldWidth + gap), rowThreeY - 12);
            }

            if (!compact) {
                int helpY = scrolledY + 151;
                drawBandPreview(graphics, this.bandPreview, x + 12, helpY, width - 24, 34);
            }
        }
        if (!this.validationMessage.getString().isEmpty()) {
            int maximumWidth = Math.max(55, this.contentWidth() - 220);
            FormattedCharSequence clipped = clipped(this.validationMessage, maximumWidth);
            int messageX = x + width - 12 - this.font.width(clipped);
            graphics.fill(messageX - 4, y + 5, x + width - 8, y + 18, 0xD9111A20);
            graphics.drawString(this.font, clipped, messageX, y + 7, this.validationColor, false);
        }
        graphics.disableScissor();
        drawBodyScrollbar(graphics, x + width - 4, layout);
    }

    private void drawBandPreview(
            GuiGraphics graphics, OreBandPreviewModel preview, int x, int y, int width, int height) {
        if (preview instanceof OreBandPreviewModel.Province provincePreview) {
            ProvinceSettings province = provincePreview.settings();
            graphics.fill(x, y, x + width, y + height, 0xCC142127);
            graphics.renderOutline(x, y, width, height, CARD_BORDER);
            Component lineOne = Component.translatable(
                    "screen.delvefold.ore_wizard.preview.province.geometry",
                    province.radius(),
                    province.verticalThickness(),
                    province.regionSize());
            Component lineTwo = Component.translatable(
                    "screen.delvefold.ore_wizard.preview.province.work",
                    String.format(Locale.ROOT, "%.1f", province.density() * 100.0D),
                    province.perChunkWorkCap(),
                    provincePreview.minY(),
                    provincePreview.maxY());
            graphics.drawString(this.font, clipped(lineOne, width - 10), x + 5, y + 7, TEXT, false);
            graphics.drawString(this.font, clipped(lineTwo, width - 10), x + 5, y + 19, MUTED_TEXT, false);
            return;
        }
        OreBandPreviewModel.Vein veinPreview = (OreBandPreviewModel.Vein) preview;
        var analysis = veinPreview.analysis();
        graphics.fill(x, y, x + width, y + height, 0xCC142127);
        graphics.renderOutline(x, y, width, height, CARD_BORDER);
        int graphLeft = x + 5;
        int graphRight = x + Math.max(36, width * 3 / 5);
        int graphBottom = y + height - 4;
        double maximum = analysis.maximumProbability();
        if (maximum > 0.0D && !analysis.samples().isEmpty()) {
            for (int pixel = graphLeft; pixel < graphRight; pixel++) {
                int sampleIndex = (pixel - graphLeft) * analysis.samples().size() / Math.max(1, graphRight - graphLeft);
                double normalized = analysis.samples()
                                .get(Math.min(sampleIndex, analysis.samples().size() - 1))
                                .probability()
                        / maximum;
                int barHeight = Math.max(1, (int) Math.round(normalized * (height - 9)));
                graphics.fill(pixel, graphBottom - barHeight, pixel + 1, graphBottom, ACCENT);
            }
        }
        Component lineOne = Component.translatable(
                "screen.delvefold.ore_wizard.preview.vein.work",
                String.format(Locale.ROOT, "%.2f", analysis.attemptsPerChunk()),
                String.format(Locale.ROOT, "%.1f", analysis.workUnits()));
        Component lineTwo = Component.translatable(
                "screen.delvefold.ore_wizard.preview.vein.density",
                Component.translatable("screen.delvefold.ore_wizard.density."
                        + analysis.density().name().toLowerCase(Locale.ROOT)),
                veinPreview.minY(),
                veinPreview.maxY());
        int textX = graphRight + 8;
        graphics.drawString(this.font, clipped(lineOne, x + width - textX - 4), textX, y + 7, TEXT, false);
        graphics.drawString(
                this.font,
                clipped(lineTwo, x + width - textX - 4),
                textX,
                y + 19,
                analysis.density() == OreDistributionAnalysis.Density.EXTREME ? WARNING : MUTED_TEXT,
                false);
    }

    private FormattedCharSequence clipped(Component text, int maximumWidth) {
        return this.font.split(text, Math.max(1, maximumWidth)).stream()
                .findFirst()
                .orElse(text.getVisualOrderText());
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void handleActionResult(ActionResultPayload payload) {
        this.validationMessage = DelvefoldText.serverMessage(payload.message());
        this.closeOnNextSnapshot = payload.status() == ActionStatus.ACCEPTED;
        this.validationColor = switch (payload.status()) {
            case ACCEPTED -> SUCCESS;
            case STALE -> WARNING;
            case REJECTED, ERROR -> DANGER;
        };
        if (payload.status() != ActionStatus.ACCEPTED) {
            Button save = this.saveButton;
            if (save != null) {
                save.active = this.snapshot.backendReady() && !this.draftState.unsupportedDuplicateSources;
            }
            Button delete = this.deleteButton;
            if (delete != null) {
                delete.active = true;
                this.deleteArmed = false;
                delete.setMessage(Component.translatable("screen.delvefold.ore_wizard.delete"));
            }
        }
    }

    /**
     * Indicates whether an accepted save should return to the refreshed dashboard.
     *
     * @return {@code true} after the server accepts the pending mutation
     */
    public boolean closeOnNextSnapshot() {
        return this.closeOnNextSnapshot;
    }

    /**
     * Creates a replacement editor retaining unsaved local page and field state.
     *
     * @param updatedSnapshot newer immutable server snapshot
     * @return a new editor instance with preserved local draft state
     */
    public DelvefoldOreRuleWizardScreen refreshed(AdminSnapshot updatedSnapshot) {
        Screen refreshedParent = this.parent instanceof DelvefoldDashboardScreen dashboard
                ? dashboard.refreshed(updatedSnapshot)
                : this.parent;
        DelvefoldOreRuleWizardScreen refreshed = new DelvefoldOreRuleWizardScreen(
                refreshedParent,
                updatedSnapshot,
                currentDraft(),
                this.page,
                this.bandIndex,
                this.existingRule,
                this.originalRuleId,
                this.draftState.focusedVariant,
                this.candidateVariants,
                this.variantPage,
                this.bodyScroll.scrollOffset(),
                this.draftState.unsupportedDuplicateSources);
        refreshed.draftState.copyRawInputsFrom(this.draftState);
        refreshed.validationMessage = this.validationMessage;
        refreshed.validationColor = this.validationColor;
        return refreshed;
    }

    private static List<String> detectVariants(String primaryBlockId, Iterable<String> selected) {
        List<String> result = new ArrayList<>();
        for (String blockId : selected) {
            if (!result.contains(blockId)) {
                result.add(blockId);
            }
        }
        ResourceLocation primary = ResourceLocation.tryParse(primaryBlockId);
        if (primary == null) {
            return result;
        }
        String family = family(primary.getPath());
        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation candidate = BuiltInRegistries.BLOCK.getKey(block);
            if (candidate == null
                    || !candidate.getNamespace().equals(primary.getNamespace())
                    || Items.AIR.equals(block.asItem())
                    || !family(candidate.getPath()).equals(family)) {
                continue;
            }
            if (!result.contains(candidate.toString())) {
                result.add(candidate.toString());
            }
            if (result.size() >= ProtocolLimits.MAX_VARIANTS) {
                break;
            }
        }
        return result;
    }

    private static String family(String path) {
        if (path.startsWith("deepslate_")) {
            return path.substring("deepslate_".length());
        }
        if (path.startsWith("stone_")) {
            return path.substring("stone_".length());
        }
        return path;
    }

    private static Component toggleLabel(Component label, boolean value) {
        return DelvefoldText.toggle(value, label);
    }

    private void focusFirstSelectedVariantOnPage() {
        OreRuleWizardLayout layout = OreRuleWizardLayout.calculate(
                this.panelWidth, compactLayout(), this.candidateVariants.size(), this.variantPage);
        for (int index = layout.start(); index < layout.end(); index++) {
            String candidate = this.candidateVariants.get(index);
            if (this.draftState.selectedVariants.containsKey(candidate)) {
                this.draftState.focusedVariant = candidate;
                this.draftState.rawWeight = Integer.toString(this.draftState.variantWeights.getOrDefault(
                        candidate, AdminSnapshot.OreVariantDraft.MIN_WEIGHT));
                return;
            }
        }
    }

    private boolean isFocusedVariantVisible(int candidateStart, int candidateEnd) {
        int start = Math.max(0, candidateStart);
        int end = Math.min(candidateEnd, this.candidateVariants.size());
        for (int index = start; index < end; index++) {
            if (this.draftState.focusedVariant.equals(this.candidateVariants.get(index))) {
                return true;
            }
        }
        return false;
    }

    private VerticalScrollLayout bodyScrollLayout() {
        int top = bodyTop() + 1;
        int bottom = Math.max(top + 1, this.contentBottom() - 1);
        return new VerticalScrollLayout(top, bottom, this.bodyScroll.virtualBottom());
    }

    private void setBodyScrollOffset(int requestedOffset) {
        this.bodyScroll.setScrollOffset(requestedOffset, bodyScrollLayout());
    }

    private void applyBodyScroll() {
        VerticalScrollLayout layout = bodyScrollLayout();
        this.bodyScroll.apply(layout);
        this.bodyScroll.ensureFocusableVisible(layout);
    }

    private void scrollBodyWidgetIntoView(AbstractWidget widget) {
        this.bodyScroll.scrollIntoView(widget, bodyScrollLayout());
    }

    private void focusWeightField() {
        EditBox weight = this.weightBox;
        if (weight == null || !weight.active) {
            return;
        }
        scrollBodyWidgetIntoView(weight);
        if (weight.visible) {
            this.setInitialFocus(weight);
        }
    }

    private void focusFirstBodyWidget() {
        for (AbstractWidget widget : this.bodyScroll.widgets()) {
            if (widget.active && widget.visible) {
                this.setInitialFocus(widget);
                return;
            }
        }
    }

    @Override
    public Component getNarrationMessage() {
        Component state = this.validationMessage.getString().isEmpty()
                ? Component.translatable("screen.delvefold.status.ready")
                : this.validationMessage;
        String pageKey =
                switch (this.page) {
                    case TARGETS -> "screen.delvefold.ore_wizard.tab.blocks";
                    case FILTERS -> "screen.delvefold.ore_wizard.tab.filters";
                    case BANDS -> "screen.delvefold.ore_wizard.tab.bands";
                };
        return Component.translatable("screen.delvefold.ore_wizard.narration", Component.translatable(pageKey), state);
    }

    private void drawBodyScrollbar(GuiGraphics graphics, int x, VerticalScrollLayout layout) {
        int maximum = layout.maximumScroll();
        if (maximum <= 0) {
            return;
        }
        int top = layout.viewportTop();
        int bottom = layout.viewportBottom();
        int trackHeight = bottom - top;
        int virtualHeight = Math.max(trackHeight, this.bodyScroll.virtualBottom() - top);
        int thumbHeight = Math.min(trackHeight, Math.max(14, trackHeight * trackHeight / virtualHeight));
        int thumbTravel = Math.max(1, trackHeight - thumbHeight);
        int thumbY = top + this.bodyScroll.scrollOffset() * thumbTravel / maximum;
        graphics.fill(x, top, x + 3, bottom, 0xAA0B1318);
        graphics.fill(x, thumbY, x + 3, thumbY + thumbHeight, ACCENT);
    }

    private void resetValidationMessage() {
        if (this.draftState.unsupportedDuplicateSources) {
            showDuplicateSourceMessage();
        } else {
            this.validationMessage = Component.empty();
        }
    }

    private void showDuplicateSourceMessage() {
        this.validationMessage = Component.translatable("screen.delvefold.ore_wizard.validation.duplicate_sources");
        this.validationColor = DANGER;
    }

    private int bodyTop() {
        return this.contentTop() + 32;
    }

    private boolean compactLayout() {
        return this.contentBottom() - bodyTop() < 220;
    }

    private enum Page {
        TARGETS,
        FILTERS,
        BANDS
    }
}
