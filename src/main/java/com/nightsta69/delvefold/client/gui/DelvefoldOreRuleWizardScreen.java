package com.nightsta69.delvefold.client.gui;

import com.nightsta69.delvefold.client.DelvefoldClientRequests;
import com.nightsta69.delvefold.client.gui.widget.DelvefoldButton.Style;
import com.nightsta69.delvefold.config.analysis.OreDistributionAnalysis;
import com.nightsta69.delvefold.config.model.HeightDistribution;
import com.nightsta69.delvefold.config.model.OreBandPlacement;
import com.nightsta69.delvefold.config.model.ProvinceSettings;
import com.nightsta69.delvefold.config.model.SpawnBand;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.network.ProtocolLimits;
import com.nightsta69.delvefold.network.model.ActionStatus;
import com.nightsta69.delvefold.network.model.AdminSnapshot;
import com.nightsta69.delvefold.network.payload.ActionResultPayload;
import com.nightsta69.delvefold.network.payload.DeleteOreRulePayload;
import com.nightsta69.delvefold.network.payload.SaveOreRulePayload;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
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

public final class DelvefoldOreRuleWizardScreen extends DelvefoldScreen {
    private static final int BODY_SCROLL_STEP = 22;

    private final Screen parent;
    private final boolean existingRule;
    private final String originalRuleId;
    private final String primaryBlockId;
    private final List<String> candidateVariants;
    private final LinkedHashMap<String, String> selectedVariants;
    private final LinkedHashMap<String, Map<String, String>> variantStates;
    private final LinkedHashMap<String, Integer> variantWeights;
    private final List<TerrainMode> terrainModes;
    private final List<String> biomeIncludes;
    private final List<String> biomeExcludes;
    private final List<AdminSnapshot.OreBandDraft> bands;
    private final boolean unsupportedDuplicateSources;
    private final List<AbstractWidget> bodyWidgets = new ArrayList<>();
    private final Map<AbstractWidget, Integer> bodyWidgetY = new IdentityHashMap<>();
    private final Page page;
    private int bandIndex;
    private int variantPage;
    private String focusedVariant;
    private String ruleId;
    private boolean enabled;
    private boolean required;
    private boolean deleteArmed;
    private Component validationMessage = Component.empty();
    private int validationColor = DANGER;
    private boolean closeOnNextSnapshot;
    private int bodyScrollOffset;
    private int bodyVirtualBottom;

    private EditBox ruleIdBox;
    private EditBox hostTagBox;
    private EditBox weightBox;
    private EditBox statePropertiesBox;
    private EditBox biomeIncludesBox;
    private EditBox biomeExcludesBox;
    private EditBox bandIdBox;
    private EditBox veinSizeBox;
    private EditBox attemptsBox;
    private EditBox minYBox;
    private EditBox maxYBox;
    private EditBox peakYBox;
    private EditBox plateauMinBox;
    private EditBox plateauMaxBox;
    private EditBox airDiscardBox;
    private Button saveButton;
    private Button deleteButton;
    private int rawBandIndex = -1;
    private String rawBandId;
    private String rawVeinSize;
    private String rawAttempts;
    private String rawMinY;
    private String rawMaxY;
    private String rawPeakY;
    private String rawPlateauMin;
    private String rawPlateauMax;
    private String rawAirDiscard;
    private String rawStateProperties;
    private String rawWeight;
    private String rawBiomeIncludes;
    private String rawBiomeExcludes;

    public DelvefoldOreRuleWizardScreen(Screen parent, AdminSnapshot snapshot, AdminSnapshot.OreRuleDraft draft) {
        this(parent, snapshot, draft, Page.TARGETS, 0,
                snapshot.oreRules().stream().anyMatch(rule -> rule.id().equals(draft.id())),
                draft.id(), draft.variants().isEmpty() ? draft.primaryBlockId() : draft.variants().get(0).sourceId(),
                null, 0, 0, hasDuplicateSources(draft));
    }

    private DelvefoldOreRuleWizardScreen(
            Screen parent,
            AdminSnapshot snapshot,
            AdminSnapshot.OreRuleDraft draft,
            Page page,
            int bandIndex,
            boolean existingRule,
            String originalRuleId,
            String focusedVariant,
            List<String> inheritedCandidates,
            int variantPage,
            int bodyScrollOffset,
            boolean unsupportedDuplicateSources) {
        super(Component.translatable("screen.delvefold.ore_wizard.title"), snapshot);
        this.parent = parent;
        this.page = page;
        this.bandIndex = Math.max(0, Math.min(bandIndex, Math.max(0, draft.bands().size() - 1)));
        this.variantPage = Math.max(0, variantPage);
        this.bodyScrollOffset = Math.max(0, bodyScrollOffset);
        this.existingRule = existingRule;
        this.originalRuleId = originalRuleId;
        this.primaryBlockId = draft.primaryBlockId();
        this.ruleId = draft.id();
        this.enabled = draft.enabled();
        this.required = draft.required();
        this.selectedVariants = new LinkedHashMap<>();
        this.variantStates = new LinkedHashMap<>();
        this.variantWeights = new LinkedHashMap<>();
        boolean duplicateSources = unsupportedDuplicateSources;
        for (AdminSnapshot.OreVariantDraft variant : draft.variants()) {
            if (this.selectedVariants.putIfAbsent(variant.sourceId(), variant.replaceTag()) != null) {
                duplicateSources = true;
                continue;
            }
            this.variantStates.put(variant.sourceId(), variant.state());
            this.variantWeights.put(variant.sourceId(), variant.weight());
        }
        this.unsupportedDuplicateSources = duplicateSources;
        if (this.selectedVariants.isEmpty()) {
            this.selectedVariants.put(this.primaryBlockId, inferredHost(this.primaryBlockId));
            this.variantStates.put(this.primaryBlockId, Map.of());
            this.variantWeights.put(this.primaryBlockId, AdminSnapshot.OreVariantDraft.MIN_WEIGHT);
        }
        this.terrainModes = new ArrayList<>(draft.terrainModes());
        if (this.terrainModes.isEmpty()) {
            this.terrainModes.addAll(List.of(TerrainMode.values()));
        }
        this.biomeIncludes = new ArrayList<>(draft.biomeIncludes());
        this.biomeExcludes = new ArrayList<>(draft.biomeExcludes());
        this.bands = new ArrayList<>(draft.bands());
        if (this.bands.isEmpty()) {
            this.bands.add(AdminSnapshot.OreBandDraft.defaultBand());
        }
        this.focusedVariant = focusedVariant != null && this.selectedVariants.containsKey(focusedVariant)
                ? focusedVariant
                : this.selectedVariants.keySet().iterator().next();
        this.candidateVariants = inheritedCandidates == null
                ? detectVariants(this.primaryBlockId, this.selectedVariants.keySet())
                : new ArrayList<>(inheritedCandidates);
        this.rawStateProperties = formatState(this.variantStates.getOrDefault(this.focusedVariant, Map.of()));
        this.rawWeight = Integer.toString(this.variantWeights.getOrDefault(
                this.focusedVariant, AdminSnapshot.OreVariantDraft.MIN_WEIGHT));
        this.rawBiomeIncludes = String.join(", ", this.biomeIncludes);
        this.rawBiomeExcludes = String.join(", ", this.biomeExcludes);
        resetValidationMessage();
    }

    @Override
    protected void initPanel() {
        this.bodyWidgets.clear();
        this.bodyWidgetY.clear();
        this.bodyVirtualBottom = bodyTop();
        int tabY = this.contentTop();
        int tabWidth = (this.contentWidth() - 12) / 3;
        this.addButton(this.contentLeft(), tabY, tabWidth, 24,
                Component.translatable("screen.delvefold.ore_wizard.tab.blocks"),
                this.page == Page.TARGETS ? Style.TAB_SELECTED : Style.GHOST,
                button -> {
                    if (this.page != Page.TARGETS) {
                        openPage(Page.TARGETS, this.bandIndex);
                    }
                });
        this.addButton(this.contentLeft() + tabWidth + 6, tabY, tabWidth, 24,
                Component.translatable("screen.delvefold.ore_wizard.tab.filters"),
                this.page == Page.FILTERS ? Style.TAB_SELECTED : Style.GHOST,
                button -> {
                    if (this.page != Page.FILTERS) {
                        openPage(Page.FILTERS, this.bandIndex);
                    }
                });
        this.addButton(this.contentLeft() + (tabWidth + 6) * 2, tabY, tabWidth, 24,
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

        int footerY = this.panelTop + this.panelHeight - 29;
        this.addButton(this.contentLeft(), footerY, 76, 22,
                Component.translatable("gui.back"), Style.GHOST, button -> this.minecraft.setScreen(this.parent));
        this.saveButton = this.addButton(this.contentRight() - 106, footerY, 106, 22,
                Component.translatable("screen.delvefold.ore_wizard.save"), Style.PRIMARY, button -> save());
        this.saveButton.active = this.snapshot.backendReady() && !this.unsupportedDuplicateSources;
        focusFirstBodyWidget();
    }

    private void initFilters() {
        int x = this.contentLeft() + 12;
        int y = bodyTop() + 39;
        int width = this.contentWidth() - 24;
        this.statePropertiesBox = registerBodyWidget(addWideEditBox(
                x, y, width, this.rawStateProperties,
                Component.translatable("screen.delvefold.ore_wizard.hint.state")));
        this.statePropertiesBox.setResponder(value -> this.rawStateProperties = value);
        this.biomeIncludesBox = registerBodyWidget(addWideEditBox(
                x, y + 48, width, this.rawBiomeIncludes,
                Component.translatable("screen.delvefold.ore_wizard.hint.biomes.include")));
        this.biomeIncludesBox.setResponder(value -> this.rawBiomeIncludes = value);
        this.biomeExcludesBox = registerBodyWidget(addWideEditBox(
                x, y + 96, width, this.rawBiomeExcludes,
                Component.translatable("screen.delvefold.ore_wizard.hint.biomes.exclude")));
        this.biomeExcludesBox.setResponder(value -> this.rawBiomeExcludes = value);
        int helpY = y + 121;
        this.bodyVirtualBottom = helpY + wrappedTextHeight(
                Component.translatable("screen.delvefold.ore_wizard.filters.help"), width) + 8;
    }

    private void initTargets() {
        int x = this.contentLeft() + 12;
        boolean compact = compactLayout();
        int y = bodyTop() + (compact ? 31 : 39);
        int innerWidth = this.contentWidth() - 24;
        int toggleWidth = Math.min(102, Math.max(64, innerWidth / 5));
        int ruleWidth = innerWidth - toggleWidth * 2 - 12;
        this.ruleIdBox = registerBodyWidget(addEditBox(x, y, ruleWidth, this.ruleId,
                Component.translatable("screen.delvefold.ore_wizard.hint.rule_id")));
        this.ruleIdBox.setResponder(value -> this.ruleId = value);
        this.ruleIdBox.active = !this.existingRule;
        this.addBodyButton(x + ruleWidth + 6, y, toggleWidth, 20,
                toggleLabel(Component.translatable("screen.delvefold.ore_wizard.enabled"), this.enabled),
                this.enabled ? Style.TOGGLE_ON : Style.TOGGLE_OFF, button -> {
            this.enabled = !this.enabled;
            button.setMessage(toggleLabel(Component.translatable("screen.delvefold.ore_wizard.enabled"), this.enabled));
            setButtonStyle(button, this.enabled ? Style.TOGGLE_ON : Style.TOGGLE_OFF);
        });
        this.addBodyButton(x + ruleWidth + toggleWidth + 12, y, toggleWidth, 20,
                toggleLabel(Component.translatable("screen.delvefold.ore_wizard.required"), this.required),
                this.required ? Style.TOGGLE_ON : Style.TOGGLE_OFF, button -> {
            this.required = !this.required;
            button.setMessage(toggleLabel(Component.translatable("screen.delvefold.ore_wizard.required"), this.required));
            setButtonStyle(button, this.required ? Style.TOGGLE_ON : Style.TOGGLE_OFF);
        });

        int variantY = y + (compact ? 34 : 40);
        int variantColumns = variantColumns();
        int variantRows = compact ? 2 : 4;
        int variantGap = 5;
        int variantWidth = (innerWidth - variantGap * (variantColumns - 1)) / variantColumns;
        int pageSize = variantColumns * variantRows;
        int pageCount = Math.max(1, (this.candidateVariants.size() + pageSize - 1) / pageSize);
        this.variantPage = Math.min(this.variantPage, pageCount - 1);
        int candidateStart = this.variantPage * pageSize;
        int candidateEnd = Math.min(candidateStart + pageSize, this.candidateVariants.size());
        for (int index = candidateStart; index < candidateEnd; index++) {
            String blockId = this.candidateVariants.get(index);
            int pageIndex = index - candidateStart;
            int column = pageIndex % variantColumns;
            int row = pageIndex / variantColumns;
            boolean selected = this.selectedVariants.containsKey(blockId);
            ResourceLocation registryId = ResourceLocation.tryParse(blockId);
            boolean outputTag = blockId.startsWith("#");
            ResourceLocation tagId = ResourceLocation.tryParse(outputTag ? blockId.substring(1) : blockId);
            boolean missing = outputTag
                    ? tagId == null || BuiltInRegistries.BLOCK.getTag(
                            net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, tagId)).isEmpty()
                    : registryId == null || BuiltInRegistries.BLOCK.getOptional(registryId).isEmpty();
            Component status = Component.translatable(missing
                    ? "screen.delvefold.status.missing"
                    : selected ? "screen.delvefold.status.on" : "screen.delvefold.status.off");
            Component label = Component.translatable(blockId.equals(this.focusedVariant)
                            ? "screen.delvefold.ore_wizard.variant.focused"
                            : "screen.delvefold.ore_wizard.variant.label",
                    status, blockId);
            this.addBodyButton(x + column * (variantWidth + variantGap), variantY + row * 22,
                    variantWidth, 20, label,
                    missing ? Style.DANGER : selected ? Style.TOGGLE_ON : Style.TOGGLE_OFF,
                    ignored -> toggleVariant(blockId));
        }
        if (pageCount > 1) {
            int pagerY = variantY - 16;
            Button previous = this.addBodyButton(x + innerWidth - 68, pagerY, 30, 14,
                    Component.translatable("screen.delvefold.previous"), Style.GHOST,
                    button -> changeVariantPage(-1));
            previous.active = this.variantPage > 0;
            Button next = this.addBodyButton(x + innerWidth - 32, pagerY, 30, 14,
                    Component.translatable("screen.delvefold.next"), Style.GHOST,
                    button -> changeVariantPage(1));
            next.active = this.variantPage + 1 < pageCount;
        }

        int hostY = variantY + variantRows * 22 + 10;
        int weightGap = 6;
        int weightWidth = Math.min(92, Math.max(62, innerWidth / 5));
        int hostWidth = innerWidth - weightWidth - weightGap;
        this.hostTagBox = registerBodyWidget(addEditBox(x, hostY, hostWidth,
                this.selectedVariants.getOrDefault(this.focusedVariant, inferredHost(this.focusedVariant)),
                Component.translatable("screen.delvefold.ore_wizard.hint.replacement_tag")));
        this.hostTagBox.setResponder(value -> {
            if (this.focusedVariant != null && this.selectedVariants.containsKey(this.focusedVariant)) {
                this.selectedVariants.put(this.focusedVariant, value);
            }
        });
        this.weightBox = registerBodyWidget(addEditBox(x + hostWidth + weightGap, hostY, weightWidth,
                this.rawWeight, Component.translatable("screen.delvefold.ore_wizard.weight.hint")));
        this.weightBox.setMaxLength(4);
        this.weightBox.setResponder(value -> this.rawWeight = value);

        boolean focusedVariantVisible = isFocusedVariantVisible(candidateStart, candidateEnd);
        this.hostTagBox.active = focusedVariantVisible;
        this.weightBox.active = focusedVariantVisible;

        int terrainY = hostY + (compact ? 34 : 40);
        int terrainGap = 6;
        int terrainWidth = (innerWidth - terrainGap * 2) / 3;
        for (TerrainMode mode : TerrainMode.values()) {
            int modeX = x + mode.ordinal() * (terrainWidth + terrainGap);
            boolean selected = this.terrainModes.contains(mode);
            this.addBodyButton(modeX, terrainY, terrainWidth, 20,
                    toggleLabel(DelvefoldText.option("terrain", mode.serializedName()),
                            this.terrainModes.contains(mode)),
                    selected ? Style.TOGGLE_ON : Style.TOGGLE_OFF,
                    button -> toggleTerrain(mode));
        }

        this.bodyVirtualBottom = terrainY + 28;

        if (this.existingRule) {
            int deleteX = this.contentLeft() + 84;
            int maximumDeleteRight = this.contentRight() - 112;
            int deleteWidth = Math.min(122, Math.max(52, maximumDeleteRight - deleteX));
            this.deleteButton = this.addButton(deleteX,
                    this.panelTop + this.panelHeight - 29, deleteWidth, 22,
                    Component.translatable("screen.delvefold.ore_wizard.delete"), Style.DANGER,
                    button -> delete());
        }
    }

    private void initBands() {
        AdminSnapshot.OreBandDraft band = this.bands.get(this.bandIndex);
        primeBandInputs(band);
        int x = this.contentLeft() + 12;
        boolean compact = compactLayout();
        int y = bodyTop() + (compact ? 31 : 39);
        int innerWidth = this.contentWidth() - 24;
        int selectorGap = 6;
        int selectorWidth = Math.min(132, Math.max(64, innerWidth / 4));
        int bandIdWidth = Math.max(72, innerWidth - selectorWidth * 2 - selectorGap * 2);
        this.bandIdBox = registerBodyWidget(addEditBox(x, y, bandIdWidth, this.rawBandId,
                Component.translatable("screen.delvefold.ore_wizard.hint.band_id")));
        this.bandIdBox.setResponder(value -> this.rawBandId = value);
        this.addBodyButton(x + bandIdWidth + selectorGap, y, selectorWidth, 20,
                Component.translatable("screen.delvefold.ore_wizard.selector",
                        Component.translatable("option.delvefold.ore_band_placement."
                                + band.placement().name().toLowerCase(Locale.ROOT))),
                Style.SECONDARY,
                button -> cyclePlacement());
        int distributionX = x + bandIdWidth + selectorGap + selectorWidth + selectorGap;
        this.addBodyButton(distributionX, y, innerWidth - (distributionX - x), 20,
                Component.translatable("screen.delvefold.ore_wizard.selector",
                        Component.translatable("option.delvefold.height_distribution."
                                + band.distribution().name().toLowerCase(Locale.ROOT))),
                Style.SECONDARY,
                button -> cycleDistribution());

        int rowTwoY = y + (compact ? 31 : 42);
        int rowTwoGap = 8;
        int thirdWidth = (innerWidth - rowTwoGap * 2) / 3;
        int finalThirdWidth = innerWidth - thirdWidth * 2 - rowTwoGap * 2;
        if (band.placement() == OreBandPlacement.PROVINCE) {
            ProvinceSettings province = band.province() == null ? ProvinceSettings.defaults() : band.province();
            this.addBodyButton(x, rowTwoY, thirdWidth * 2 + rowTwoGap, 20,
                    Component.translatable("screen.delvefold.ore_wizard.province.edit",
                            province.regionSize(), province.radius(), province.verticalThickness(),
                            (int) Math.round(province.density() * 100.0D), province.perChunkWorkCap()),
                    Style.SECONDARY, button -> openProvinceSettings());
            this.airDiscardBox = registerBodyWidget(addEditBox(
                    x + thirdWidth * 2 + rowTwoGap * 2, rowTwoY,
                    finalThirdWidth, this.rawAirDiscard,
                    Component.translatable("screen.delvefold.ore_wizard.hint.air_discard")));
            this.veinSizeBox = null;
            this.attemptsBox = null;
        } else {
            this.veinSizeBox = registerBodyWidget(addEditBox(
                    x, rowTwoY, thirdWidth, this.rawVeinSize,
                    Component.translatable("screen.delvefold.ore_wizard.hint.vein_size")));
            this.attemptsBox = registerBodyWidget(addEditBox(
                    x + thirdWidth + rowTwoGap, rowTwoY, thirdWidth,
                    this.rawAttempts,
                    Component.translatable("screen.delvefold.ore_wizard.hint.attempts")));
            this.airDiscardBox = registerBodyWidget(addEditBox(
                    x + thirdWidth * 2 + rowTwoGap * 2, rowTwoY,
                    finalThirdWidth, this.rawAirDiscard,
                    Component.translatable("screen.delvefold.ore_wizard.hint.air_discard")));
        }

        int rowThreeY = y + (compact ? 62 : 84);
        int heightGap = 6;
        int heightWidth = (innerWidth - heightGap * 4) / 5;
        this.minYBox = registerBodyWidget(addEditBox(x, rowThreeY, heightWidth, this.rawMinY,
                Component.translatable("screen.delvefold.ore_wizard.hint.min_y")));
        this.maxYBox = registerBodyWidget(addEditBox(
                x + (heightWidth + heightGap), rowThreeY, heightWidth, this.rawMaxY,
                Component.translatable("screen.delvefold.ore_wizard.hint.max_y")));
        this.peakYBox = registerBodyWidget(addEditBox(
                x + (heightWidth + heightGap) * 2, rowThreeY,
                heightWidth, this.rawPeakY,
                Component.translatable("screen.delvefold.ore_wizard.hint.peak_y")));
        this.plateauMinBox = registerBodyWidget(addEditBox(
                x + (heightWidth + heightGap) * 3, rowThreeY,
                heightWidth, this.rawPlateauMin,
                Component.translatable("screen.delvefold.ore_wizard.hint.plateau_min")));
        this.plateauMaxBox = registerBodyWidget(addEditBox(
                x + (heightWidth + heightGap) * 4, rowThreeY,
                innerWidth - (heightWidth + heightGap) * 4, this.rawPlateauMax,
                Component.translatable("screen.delvefold.ore_wizard.hint.plateau_max")));
        bindRawBandResponders();
        this.peakYBox.active = band.distribution() == HeightDistribution.TRIANGLE;
        this.plateauMinBox.active = band.distribution() == HeightDistribution.TRAPEZOID;
        this.plateauMaxBox.active = band.distribution() == HeightDistribution.TRAPEZOID;

        int controlsY = y + (compact ? 95 : 156);
        int controlGap = 6;
        int controlWidth = (innerWidth - controlGap * 3) / 4;
        Button previous = this.addBodyButton(x, controlsY, controlWidth, 20,
                Component.translatable("screen.delvefold.ore_wizard.previous_band"), Style.GHOST,
                button -> changeBand(-1));
        previous.active = this.bandIndex > 0;
        Button next = this.addBodyButton(x + controlWidth + controlGap, controlsY, controlWidth, 20,
                Component.translatable("screen.delvefold.ore_wizard.next_band"), Style.GHOST,
                button -> changeBand(1));
        next.active = this.bandIndex + 1 < this.bands.size();
        Button add = this.addBodyButton(x + (controlWidth + controlGap) * 2, controlsY, controlWidth, 20,
                Component.translatable("screen.delvefold.ore_wizard.add_band"), Style.PRIMARY,
                button -> addBand());
        add.active = this.bands.size() < ProtocolLimits.MAX_BANDS;
        Button remove = this.addBodyButton(x + (controlWidth + controlGap) * 3, controlsY, controlWidth, 20,
                Component.translatable("screen.delvefold.ore_wizard.remove_band"), Style.DANGER,
                button -> removeBand());
        remove.active = this.bands.size() > 1;

        int previewBottom = compact ? rowThreeY + 20 : bodyTop() + 185;
        this.bodyVirtualBottom = Math.max(controlsY + 28, previewBottom + 8);
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
        this.bodyWidgets.add(widget);
        this.bodyWidgetY.put(widget, widget.getY());
        return widget;
    }

    private int wrappedTextHeight(Component text, int width) {
        return Math.max(this.font.lineHeight, this.font.split(text, Math.max(1, width)).size() * this.font.lineHeight);
    }

    private EditBox addWideEditBox(int x, int y, int width, String value, Component hint) {
        EditBox box = addEditBox(x, y, width, value, hint);
        box.setMaxLength(ProtocolLimits.SHORT_TEXT_LENGTH);
        return box;
    }

    private void primeBandInputs(AdminSnapshot.OreBandDraft band) {
        if (this.rawBandIndex == this.bandIndex && this.rawBandId != null) {
            return;
        }
        this.rawBandIndex = this.bandIndex;
        this.rawBandId = band.id();
        this.rawVeinSize = Integer.toString(band.veinSize());
        this.rawAttempts = Double.toString(band.attemptsPerChunk());
        this.rawMinY = Integer.toString(band.minY());
        this.rawMaxY = Integer.toString(band.maxY());
        this.rawPeakY = Integer.toString(band.peakY());
        this.rawPlateauMin = Integer.toString(band.plateauMinY());
        this.rawPlateauMax = Integer.toString(band.plateauMaxY());
        this.rawAirDiscard = Double.toString(band.discardOnAirExposure());
    }

    private void bindRawBandResponders() {
        if (this.veinSizeBox != null) {
            this.veinSizeBox.setResponder(value -> this.rawVeinSize = value);
        }
        if (this.attemptsBox != null) {
            this.attemptsBox.setResponder(value -> this.rawAttempts = value);
        }
        this.minYBox.setResponder(value -> this.rawMinY = value);
        this.maxYBox.setResponder(value -> this.rawMaxY = value);
        this.peakYBox.setResponder(value -> this.rawPeakY = value);
        this.plateauMinBox.setResponder(value -> this.rawPlateauMin = value);
        this.plateauMaxBox.setResponder(value -> this.rawPlateauMax = value);
        this.airDiscardBox.setResponder(value -> this.rawAirDiscard = value);
    }

    private void toggleVariant(String blockId) {
        boolean removesFocusedVariant = blockId.equals(this.focusedVariant)
                && this.selectedVariants.containsKey(blockId)
                && this.selectedVariants.size() > 1;
        if (!removesFocusedVariant && !commitFocusedWeight()) {
            return;
        }
        if (this.selectedVariants.containsKey(blockId)) {
            if (!blockId.equals(this.focusedVariant)) {
                this.focusedVariant = blockId;
                reopen(this.page, this.bandIndex);
                return;
            }
            if (this.selectedVariants.size() > 1) {
                this.selectedVariants.remove(blockId);
                this.variantStates.remove(blockId);
                this.variantWeights.remove(blockId);
                resetValidationMessage();
            }
        } else if (this.selectedVariants.size() < ProtocolLimits.MAX_VARIANTS) {
            this.selectedVariants.put(blockId, inferredHost(blockId));
            this.variantStates.put(blockId, Map.of());
            this.variantWeights.put(blockId, AdminSnapshot.OreVariantDraft.MIN_WEIGHT);
        }
        this.focusedVariant = this.selectedVariants.containsKey(blockId)
                ? blockId
                : this.selectedVariants.keySet().iterator().next();
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
        if (this.terrainModes.contains(mode)) {
            if (this.terrainModes.size() > 1) {
                this.terrainModes.remove(mode);
            }
        } else {
            this.terrainModes.add(mode);
        }
        reopen(this.page, this.bandIndex);
    }

    private void cycleDistribution() {
        if (!commitBand()) {
            return;
        }
        AdminSnapshot.OreBandDraft current = this.bands.get(this.bandIndex);
        HeightDistribution[] values = HeightDistribution.values();
        HeightDistribution next = values[(current.distribution().ordinal() + 1) % values.length];
        this.bands.set(this.bandIndex, new AdminSnapshot.OreBandDraft(
                current.id(), current.veinSize(), current.attemptsPerChunk(), next,
                current.minY(), current.maxY(), current.peakY(), current.plateauMinY(),
                current.plateauMaxY(), current.discardOnAirExposure(),
                current.placement(), current.province()));
        reopen(Page.BANDS, this.bandIndex);
    }

    private void cyclePlacement() {
        if (!commitBand()) {
            return;
        }
        AdminSnapshot.OreBandDraft current = this.bands.get(this.bandIndex);
        boolean province = current.placement() != OreBandPlacement.PROVINCE;
        this.bands.set(this.bandIndex, new AdminSnapshot.OreBandDraft(
                current.id(), province ? 1 : 8, province ? 0.0D : 8.0D,
                current.distribution(), current.minY(), current.maxY(), current.peakY(),
                current.plateauMinY(), current.plateauMaxY(), current.discardOnAirExposure(),
                province ? OreBandPlacement.PROVINCE : OreBandPlacement.VEIN,
                province ? ProvinceSettings.defaults() : null));
        this.rawBandIndex = -1;
        reopen(Page.BANDS, this.bandIndex);
    }

    private void openProvinceSettings() {
        if (!commitBand() || this.minecraft == null) {
            return;
        }
        AdminSnapshot.OreBandDraft current = this.bands.get(this.bandIndex);
        ProvinceSettings province = current.province() == null ? ProvinceSettings.defaults() : current.province();
        this.minecraft.setScreen(new DelvefoldProvinceSettingsScreen(
                this, this.snapshot, province, this::applyProvinceSettings));
    }

    private void applyProvinceSettings(ProvinceSettings province) {
        AdminSnapshot.OreBandDraft current = this.bands.get(this.bandIndex);
        this.bands.set(this.bandIndex, new AdminSnapshot.OreBandDraft(
                current.id(), 1, 0.0D, current.distribution(), current.minY(), current.maxY(),
                current.peakY(), current.plateauMinY(), current.plateauMaxY(),
                current.discardOnAirExposure(), OreBandPlacement.PROVINCE, province));
        this.rawBandIndex = -1;
        reopen(Page.BANDS, this.bandIndex);
    }

    private void changeBand(int amount) {
        if (commitBand()) {
            reopen(Page.BANDS, this.bandIndex + amount);
        }
    }

    private void addBand() {
        if (!commitBand() || this.bands.size() >= ProtocolLimits.MAX_BANDS) {
            return;
        }
        int nextIndex = this.bands.size();
        AdminSnapshot.OreBandDraft defaults = AdminSnapshot.OreBandDraft.defaultBand();
        this.bands.add(new AdminSnapshot.OreBandDraft(
                "band_" + (nextIndex + 1), defaults.veinSize(), defaults.attemptsPerChunk(),
                defaults.distribution(), defaults.minY(), defaults.maxY(), defaults.peakY(),
                defaults.plateauMinY(), defaults.plateauMaxY(), defaults.discardOnAirExposure(),
                defaults.placement(), defaults.province()));
        reopen(Page.BANDS, nextIndex);
    }

    private void removeBand() {
        if (this.bands.size() <= 1) {
            return;
        }
        this.bands.remove(this.bandIndex);
        reopen(Page.BANDS, Math.min(this.bandIndex, this.bands.size() - 1));
    }

    private boolean commitBand() {
        if (this.page != Page.BANDS || this.bandIdBox == null) {
            return true;
        }
        try {
            String id = this.rawBandId.trim();
            int veinSize = Integer.parseInt(this.rawVeinSize.trim());
            double attempts = Double.parseDouble(this.rawAttempts.trim());
            int minY = Integer.parseInt(this.rawMinY.trim());
            int maxY = Integer.parseInt(this.rawMaxY.trim());
            int peakY = Integer.parseInt(this.rawPeakY.trim());
            int plateauMin = Integer.parseInt(this.rawPlateauMin.trim());
            int plateauMax = Integer.parseInt(this.rawPlateauMax.trim());
            double airDiscard = Double.parseDouble(this.rawAirDiscard.trim());
            AdminSnapshot.OreBandDraft current = this.bands.get(this.bandIndex);
            HeightDistribution distribution = current.distribution();
            OreBandPlacement placement = current.placement();
            boolean shapeValuesValid = switch (distribution) {
                case UNIFORM -> true;
                case TRIANGLE -> peakY >= minY && peakY <= maxY;
                case TRAPEZOID -> plateauMin >= minY && plateauMax <= maxY && plateauMin <= plateauMax;
            };

            boolean veinValuesValid = placement == OreBandPlacement.PROVINCE
                    || veinSize >= 1 && veinSize <= 64 && Double.isFinite(attempts)
                    && attempts >= 0.0D && attempts <= 256.0D;
            if (!id.matches("[a-z0-9_.-]{1,128}") || !veinValuesValid
                    || minY < -64 || maxY > 320 || minY > maxY
                    || !shapeValuesValid || !Double.isFinite(airDiscard)
                    || airDiscard < 0.0D || airDiscard > 1.0D) {
                this.validationMessage = Component.translatable(
                        "screen.delvefold.ore_wizard.validation.band_values");
                this.validationColor = DANGER;
                return false;
            }

            this.bands.set(this.bandIndex, new AdminSnapshot.OreBandDraft(
                    id,
                    placement == OreBandPlacement.PROVINCE ? 1 : veinSize,
                    placement == OreBandPlacement.PROVINCE ? 0.0D : attempts,
                    distribution, minY, maxY, peakY, plateauMin, plateauMax, airDiscard,
                    placement, placement == OreBandPlacement.PROVINCE
                            ? (current.province() == null ? ProvinceSettings.defaults() : current.province())
                            : null));
            resetValidationMessage();
            return true;
        } catch (NumberFormatException exception) {
            this.validationMessage = Component.translatable(
                    "screen.delvefold.ore_wizard.validation.number");
            this.validationColor = DANGER;
            return false;
        }
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
                    this.focusedVariant,
                    this.candidateVariants,
                    this.variantPage,
                    requestedPage == this.page ? this.bodyScrollOffset : 0,
                    this.unsupportedDuplicateSources));
        }
    }

    private AdminSnapshot.OreRuleDraft currentDraft() {
        List<AdminSnapshot.OreVariantDraft> variants = this.selectedVariants.entrySet().stream()
                .map(entry -> new AdminSnapshot.OreVariantDraft(
                        entry.getKey().startsWith("#") ? "" : entry.getKey(),
                        entry.getKey().startsWith("#") ? entry.getKey().substring(1) : "",
                        entry.getValue(), this.variantStates.getOrDefault(entry.getKey(), Map.of()),
                        this.variantWeights.getOrDefault(
                                entry.getKey(), AdminSnapshot.OreVariantDraft.MIN_WEIGHT)))
                .toList();
        return new AdminSnapshot.OreRuleDraft(
                this.ruleId,
                this.enabled,
                this.required,
                this.primaryBlockId,
                variants,
                this.terrainModes,
                this.biomeIncludes,
                this.biomeExcludes,
                this.bands);
    }

    private void save() {
        if (this.unsupportedDuplicateSources) {
            showDuplicateSourceMessage();
            return;
        }
        if (!commitFocusedWeight() || !commitBand() || !commitAdvanced() || !validateRule()) {
            return;
        }
        this.saveButton.active = false;
        this.validationMessage = Component.translatable("screen.delvefold.ore_wizard.saving");
        this.validationColor = ACCENT;
        DelvefoldClientRequests.send(new SaveOreRulePayload(
                this.snapshot.oreRevision(), currentDraft(), !this.existingRule));
    }

    private boolean validateRule() {
        if (this.unsupportedDuplicateSources) {
            showDuplicateSourceMessage();
            return false;
        }
        this.ruleId = this.ruleId.trim();
        if (!this.ruleId.matches("[a-z0-9_.-]{1,128}")) {
            this.validationMessage = Component.translatable(
                    "screen.delvefold.ore_wizard.validation.rule_id");
            this.validationColor = DANGER;
            return false;
        }
        if (this.selectedVariants.isEmpty() || this.terrainModes.isEmpty() || this.bands.isEmpty()) {
            this.validationMessage = Component.translatable(
                    "screen.delvefold.ore_wizard.validation.selection");
            this.validationColor = DANGER;
            return false;
        }
        for (Map.Entry<String, String> entry : this.selectedVariants.entrySet()) {
            String replacementTag = entry.getValue().trim();
            if (replacementTag.startsWith("#")) {
                replacementTag = replacementTag.substring(1);
            }
            String outputId = entry.getKey().startsWith("#") ? entry.getKey().substring(1) : entry.getKey();
            if (ResourceLocation.tryParse(outputId) == null || ResourceLocation.tryParse(replacementTag) == null) {
                this.validationMessage = Component.translatable(
                        "screen.delvefold.ore_wizard.validation.registry_ids");
                this.validationColor = DANGER;
                return false;
            }
            entry.setValue(replacementTag);
            int weight = this.variantWeights.getOrDefault(
                    entry.getKey(), AdminSnapshot.OreVariantDraft.MIN_WEIGHT);
            if (weight < AdminSnapshot.OreVariantDraft.MIN_WEIGHT
                    || weight > AdminSnapshot.OreVariantDraft.MAX_WEIGHT) {
                this.validationMessage = Component.translatable(
                        "screen.delvefold.ore_wizard.validation.weight_all");
                this.validationColor = DANGER;
                return false;
            }
        }
        List<String> bandIds = new ArrayList<>();
        for (AdminSnapshot.OreBandDraft band : this.bands) {
            if (!band.id().matches("[a-z0-9_.-]{1,128}")) {
                this.validationMessage = Component.translatable(
                        "screen.delvefold.ore_wizard.validation.band_id");
                this.validationColor = DANGER;
                return false;
            }
            if (bandIds.contains(band.id())) {
                this.validationMessage = Component.translatable(
                        "screen.delvefold.ore_wizard.validation.unique_band_id");
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
            int weight = Integer.parseInt(this.rawWeight == null ? "" : this.rawWeight.trim());
            if (weight < AdminSnapshot.OreVariantDraft.MIN_WEIGHT
                    || weight > AdminSnapshot.OreVariantDraft.MAX_WEIGHT) {
                throw new NumberFormatException("out of range");
            }
            if (this.focusedVariant != null && this.selectedVariants.containsKey(this.focusedVariant)) {
                this.variantWeights.put(this.focusedVariant, weight);
            }
            resetValidationMessage();
            return true;
        } catch (NumberFormatException exception) {
            this.validationMessage = Component.translatable(
                    "screen.delvefold.ore_wizard.validation.weight");
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
            Map<String, String> state = parseState(this.rawStateProperties);
            List<String> includes = parseSelectors(this.rawBiomeIncludes);
            List<String> excludes = parseSelectors(this.rawBiomeExcludes);
            if (state.size() > ProtocolLimits.MAX_STATE_PROPERTIES
                    || includes.size() > ProtocolLimits.MAX_BIOME_SELECTORS_PER_LIST
                    || excludes.size() > ProtocolLimits.MAX_BIOME_SELECTORS_PER_LIST) {
                throw validation("screen.delvefold.ore_wizard.validation.filter_limit");
            }
            this.variantStates.put(this.focusedVariant, state);
            this.biomeIncludes.clear();
            this.biomeIncludes.addAll(includes);
            this.biomeExcludes.clear();
            this.biomeExcludes.addAll(excludes);
            resetValidationMessage();
            return true;
        } catch (LocalizedValidationException exception) {
            this.validationMessage = exception.displayMessage();
            this.validationColor = DANGER;
            return false;
        }
    }

    private static Map<String, String> parseState(String text) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return result;
        }
        for (String pair : text.split(",")) {
            String[] parts = pair.trim().split("=", 2);
            if (parts.length != 2 || !parts[0].matches("[a-z0-9_]+") || parts[1].isBlank()
                    || parts[1].length() > ProtocolLimits.ID_LENGTH) {
                throw validation("screen.delvefold.ore_wizard.validation.state_format");
            }
            if (result.putIfAbsent(parts[0], parts[1].trim()) != null) {
                throw validation("screen.delvefold.ore_wizard.validation.state_duplicate", parts[0]);
            }
        }
        return result;
    }

    private static List<String> parseSelectors(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String value : text.split("[,\\s]+")) {
            if (value.isBlank()) {
                continue;
            }
            String id = value.startsWith("#") ? value.substring(1) : value;
            if (ResourceLocation.tryParse(id) == null) {
                throw validation("screen.delvefold.ore_wizard.validation.biome_selector");
            }
            if (!result.contains(value)) {
                result.add(value);
            }
        }
        return result;
    }

    private static LocalizedValidationException validation(String key, Object... arguments) {
        return new LocalizedValidationException(Component.translatable(key, arguments));
    }

    private static final class LocalizedValidationException extends IllegalArgumentException {
        private final Component displayMessage;

        private LocalizedValidationException(Component displayMessage) {
            this.displayMessage = displayMessage;
        }

        private Component displayMessage() {
            return displayMessage;
        }
    }

    private static String formatState(Map<String, String> state) {
        return state.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private void delete() {
        if (!this.deleteArmed) {
            this.deleteArmed = true;
            this.deleteButton.setMessage(Component.translatable("screen.delvefold.ore_wizard.confirm_delete"));
            return;
        }
        this.deleteButton.active = false;
        DelvefoldClientRequests.send(new DeleteOreRulePayload(this.snapshot.oreRevision(), this.originalRuleId));
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
        graphics.enableScissor(this.contentLeft() + 1, layout.viewportTop(),
                this.contentRight() - 1, layout.viewportBottom());
        for (AbstractWidget widget : this.bodyWidgets) {
            widget.render(graphics, mouseX, mouseY, partialTick);
        }
        graphics.disableScissor();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        VerticalScrollLayout layout = bodyScrollLayout();
        if (mouseX >= this.contentLeft() && mouseX < this.contentRight()
                && mouseY >= layout.viewportTop() && mouseY < layout.viewportBottom()
                && layout.maximumScroll() > 0) {
            setBodyScrollOffset(this.bodyScrollOffset
                    - (int) Math.signum(scrollY) * BODY_SCROLL_STEP);
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
        boolean compact = compactLayout();
        this.drawCard(graphics, x, y, width, height);
        VerticalScrollLayout layout = bodyScrollLayout();
        graphics.enableScissor(x + 1, layout.viewportTop(), x + width - 1, layout.viewportBottom());
        int scrolledY = y - this.bodyScrollOffset;
        if (this.page == Page.TARGETS) {
            this.drawSectionTitle(graphics, Component.translatable("screen.delvefold.ore_wizard.targets.title"),
                    x + 10, scrolledY + 7);
            int fieldY = scrolledY + (compact ? 31 : 39);
            int variantY = fieldY + (compact ? 34 : 40);
            int variantRows = compact ? 2 : 4;
            int hostY = variantY + variantRows * 22 + 10;
            int terrainY = hostY + (compact ? 34 : 40);
            this.drawFieldLabel(graphics, Component.translatable("screen.delvefold.ore_wizard.rule_id"), x + 12, fieldY - 12);
            Component variantLabel = Component.translatable(width >= 500
                    ? "screen.delvefold.ore_wizard.variants.help"
                    : "screen.delvefold.ore_wizard.variants");
            this.drawFieldLabel(graphics, variantLabel, x + 12, variantY - 12);
            int pageSize = variantColumns() * variantRows;
            int pageCount = Math.max(1, (this.candidateVariants.size() + pageSize - 1) / pageSize);
            int start = this.candidateVariants.isEmpty() ? 0 : this.variantPage * pageSize + 1;
            int end = Math.min((this.variantPage + 1) * pageSize, this.candidateVariants.size());
            Component range = Component.translatable("screen.delvefold.ore_wizard.variant.range",
                    start, end, this.candidateVariants.size());
            int rangeRight = x + width - (pageCount > 1 ? 88 : 12);
            graphics.drawString(this.font, range, rangeRight - this.font.width(range),
                    variantY - 12, DIM_TEXT, false);
            boolean focusedVariantVisible = isFocusedVariantVisible(
                    this.variantPage * pageSize,
                    Math.min((this.variantPage + 1) * pageSize, this.candidateVariants.size()));
            Component focusLabel = Component.translatable(focusedVariantVisible
                            ? "screen.delvefold.ore_wizard.replacement_tag"
                            : "screen.delvefold.ore_wizard.replacement_tag.off_page",
                    this.focusedVariant);
            int innerWidth = width - 24;
            int weightGap = 6;
            int weightWidth = Math.min(92, Math.max(62, innerWidth / 5));
            int hostWidth = innerWidth - weightWidth - weightGap;
            graphics.drawString(this.font, clipped(focusLabel, hostWidth),
                    x + 12, hostY - 12, MUTED_TEXT, false);
            this.drawFieldLabel(graphics, Component.translatable("screen.delvefold.ore_wizard.weight"),
                    x + 12 + hostWidth + weightGap, hostY - 12);
            this.drawFieldLabel(graphics, Component.translatable("screen.delvefold.ore_wizard.terrain"), x + 12, terrainY - 12);
        } else if (this.page == Page.FILTERS) {
            this.drawSectionTitle(graphics, Component.translatable("screen.delvefold.ore_wizard.filters.title"),
                    x + 10, scrolledY + 7);
            int fieldY = scrolledY + 39;
            this.drawFieldLabel(graphics, Component.translatable("screen.delvefold.ore_wizard.state", this.focusedVariant), x + 12, fieldY - 12);
            this.drawFieldLabel(graphics, Component.translatable("screen.delvefold.ore_wizard.biomes.include"), x + 12, fieldY + 36);
            this.drawFieldLabel(graphics, Component.translatable("screen.delvefold.ore_wizard.biomes.exclude"), x + 12, fieldY + 84);
            graphics.drawWordWrap(this.font, Component.translatable("screen.delvefold.ore_wizard.filters.help"),
                    x + 12, fieldY + 121, width - 24, DIM_TEXT);
        } else {
            AdminSnapshot.OreBandDraft band = this.bands.get(this.bandIndex);
            int fieldY = scrolledY + (compact ? 31 : 39);
            int rowTwoY = fieldY + (compact ? 31 : 42);
            int rowThreeY = fieldY + (compact ? 62 : 84);
            this.drawSectionTitle(graphics,
                    Component.translatable("screen.delvefold.ore_wizard.band.title",
                            this.bandIndex + 1, this.bands.size()),
                    x + 10, scrolledY + 7);
            this.drawFieldLabel(graphics, Component.translatable("screen.delvefold.ore_wizard.band_id"), x + 12, fieldY - 12);
            int selectorGap = 6;
            int selectorWidth = Math.min(132, Math.max(64, (width - 24) / 4));
            int bandIdWidth = Math.max(72, width - 24 - selectorWidth * 2 - selectorGap * 2);
            this.drawFieldLabel(graphics, Component.translatable("screen.delvefold.ore_wizard.placement"),
                    x + 12 + bandIdWidth + selectorGap, fieldY - 12);
            this.drawFieldLabel(graphics, Component.translatable("screen.delvefold.ore_wizard.distribution"),
                    x + 12 + bandIdWidth + selectorGap + selectorWidth + selectorGap, fieldY - 12);
            if (band.placement() == OreBandPlacement.PROVINCE) {
                this.drawFieldLabel(graphics, Component.translatable("screen.delvefold.ore_wizard.province"),
                        x + 12, rowTwoY - 12);
            } else {
                this.drawFieldLabel(graphics, Component.translatable("screen.delvefold.ore_wizard.vein_size"), x + 12, rowTwoY - 12);
                this.drawFieldLabel(graphics, Component.translatable("screen.delvefold.ore_wizard.attempts"),
                        x + 12 + ((width - 24 - 16) / 3) + 8, rowTwoY - 12);
            }
            this.drawFieldLabel(graphics, Component.translatable("screen.delvefold.ore_wizard.air_discard"),
                    x + 12 + (((width - 24 - 16) / 3) + 8) * 2, rowTwoY - 12);

            int innerWidth = width - 24;
            int gap = 6;
            int fieldWidth = (innerWidth - gap * 4) / 5;
            String suffix = width >= 430 ? "" : ".short";
            Component[] heightLabels = new Component[]{
                    Component.translatable("screen.delvefold.ore_wizard.height.min" + suffix),
                    Component.translatable("screen.delvefold.ore_wizard.height.max" + suffix),
                    Component.translatable("screen.delvefold.ore_wizard.height.peak" + suffix),
                    Component.translatable("screen.delvefold.ore_wizard.height.plateau_min" + suffix),
                    Component.translatable("screen.delvefold.ore_wizard.height.plateau_max" + suffix)};
            for (int index = 0; index < heightLabels.length; index++) {
                this.drawFieldLabel(graphics, heightLabels[index],
                        x + 12 + index * (fieldWidth + gap), rowThreeY - 12);
            }

            if (!compact) {
                int helpY = scrolledY + 151;
                drawBandPreview(graphics, band, x + 12, helpY, width - 24, 34);
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
            GuiGraphics graphics, AdminSnapshot.OreBandDraft draft, int x, int y, int width, int height) {
        if (draft.placement() == OreBandPlacement.PROVINCE) {
            ProvinceSettings province = draft.province() == null ? ProvinceSettings.defaults() : draft.province();
            graphics.fill(x, y, x + width, y + height, 0xCC142127);
            graphics.renderOutline(x, y, width, height, CARD_BORDER);
            Component lineOne = Component.translatable("screen.delvefold.ore_wizard.preview.province.geometry",
                    province.radius(), province.verticalThickness(), province.regionSize());
            Component lineTwo = Component.translatable("screen.delvefold.ore_wizard.preview.province.work",
                    String.format(Locale.ROOT, "%.1f", province.density() * 100.0D),
                    province.perChunkWorkCap(), draft.minY(), draft.maxY());
            graphics.drawString(this.font, clipped(lineOne, width - 10),
                    x + 5, y + 7, TEXT, false);
            graphics.drawString(this.font, clipped(lineTwo, width - 10),
                    x + 5, y + 19, MUTED_TEXT, false);
            return;
        }
        SpawnBand band = new SpawnBand(draft.id(), draft.veinSize(), draft.attemptsPerChunk(),
                draft.distribution(), draft.minY(), draft.maxY(), draft.peakY(),
                draft.plateauMinY(), draft.plateauMaxY(), draft.discardOnAirExposure());
        OreDistributionAnalysis.Summary analysis = OreDistributionAnalysis.analyze(band);
        graphics.fill(x, y, x + width, y + height, 0xCC142127);
        graphics.renderOutline(x, y, width, height, CARD_BORDER);
        int graphLeft = x + 5;
        int graphRight = x + Math.max(36, width * 3 / 5);
        int graphBottom = y + height - 4;
        double maximum = analysis.maximumProbability();
        if (maximum > 0.0D && !analysis.samples().isEmpty()) {
            for (int pixel = graphLeft; pixel < graphRight; pixel++) {
                int sampleIndex = (pixel - graphLeft) * analysis.samples().size()
                        / Math.max(1, graphRight - graphLeft);
                double normalized = analysis.samples().get(Math.min(sampleIndex, analysis.samples().size() - 1)).probability()
                        / maximum;
                int barHeight = Math.max(1, (int) Math.round(normalized * (height - 9)));
                graphics.fill(pixel, graphBottom - barHeight, pixel + 1, graphBottom, ACCENT);
            }
        }
        Component lineOne = Component.translatable("screen.delvefold.ore_wizard.preview.vein.work",
                String.format(Locale.ROOT, "%.2f", analysis.attemptsPerChunk()),
                String.format(Locale.ROOT, "%.1f", analysis.workUnits()));
        Component lineTwo = Component.translatable("screen.delvefold.ore_wizard.preview.vein.density",
                Component.translatable("screen.delvefold.ore_wizard.density."
                        + analysis.density().name().toLowerCase(Locale.ROOT)),
                draft.minY(), draft.maxY());
        int textX = graphRight + 8;
        graphics.drawString(this.font, clipped(lineOne, x + width - textX - 4),
                textX, y + 7, TEXT, false);
        graphics.drawString(this.font, clipped(lineTwo, x + width - textX - 4),
                textX, y + 19, analysis.density() == OreDistributionAnalysis.Density.EXTREME ? WARNING : MUTED_TEXT, false);
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
            if (this.saveButton != null) {
                this.saveButton.active = this.snapshot.backendReady() && !this.unsupportedDuplicateSources;
            }
            if (this.deleteButton != null) {
                this.deleteButton.active = true;
                this.deleteArmed = false;
                this.deleteButton.setMessage(Component.translatable("screen.delvefold.ore_wizard.delete"));
            }
        }
    }

    public boolean closeOnNextSnapshot() {
        return this.closeOnNextSnapshot;
    }

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
                this.focusedVariant,
                this.candidateVariants,
                this.variantPage,
                this.bodyScrollOffset,
                this.unsupportedDuplicateSources);
        refreshed.rawBandIndex = this.rawBandIndex;
        refreshed.rawBandId = this.rawBandId;
        refreshed.rawVeinSize = this.rawVeinSize;
        refreshed.rawAttempts = this.rawAttempts;
        refreshed.rawMinY = this.rawMinY;
        refreshed.rawMaxY = this.rawMaxY;
        refreshed.rawPeakY = this.rawPeakY;
        refreshed.rawPlateauMin = this.rawPlateauMin;
        refreshed.rawPlateauMax = this.rawPlateauMax;
        refreshed.rawAirDiscard = this.rawAirDiscard;
        refreshed.rawStateProperties = this.rawStateProperties;
        refreshed.rawWeight = this.rawWeight;
        refreshed.rawBiomeIncludes = this.rawBiomeIncludes;
        refreshed.rawBiomeExcludes = this.rawBiomeExcludes;
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
            if (candidate == null || !candidate.getNamespace().equals(primary.getNamespace())
                    || block.asItem() == Items.AIR || !family(candidate.getPath()).equals(family)) {
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

    private static String inferredHost(String blockId) {
        ResourceLocation id = ResourceLocation.tryParse(blockId);
        return id != null && id.getPath().startsWith("deepslate_")
                ? "minecraft:deepslate_ore_replaceables"
                : "minecraft:stone_ore_replaceables";
    }

    private static Component toggleLabel(Component label, boolean value) {
        return DelvefoldText.toggle(value, label);
    }

    private void focusFirstSelectedVariantOnPage() {
        int pageSize = variantColumns() * (compactLayout() ? 2 : 4);
        int start = Math.min(this.variantPage * pageSize, this.candidateVariants.size());
        int end = Math.min(start + pageSize, this.candidateVariants.size());
        for (int index = start; index < end; index++) {
            String candidate = this.candidateVariants.get(index);
            if (this.selectedVariants.containsKey(candidate)) {
                this.focusedVariant = candidate;
                this.rawWeight = Integer.toString(this.variantWeights.getOrDefault(
                        candidate, AdminSnapshot.OreVariantDraft.MIN_WEIGHT));
                return;
            }
        }
    }

    private boolean isFocusedVariantVisible(int candidateStart, int candidateEnd) {
        if (this.focusedVariant == null) {
            return false;
        }
        int start = Math.max(0, candidateStart);
        int end = Math.min(candidateEnd, this.candidateVariants.size());
        for (int index = start; index < end; index++) {
            if (this.focusedVariant.equals(this.candidateVariants.get(index))) {
                return true;
            }
        }
        return false;
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
            int y = layout.screenY(
                    this.bodyWidgetY.getOrDefault(widget, widget.getY()), this.bodyScrollOffset);
            widget.setY(y);
            widget.visible = layout.fullyVisible(y, widget.getHeight());
        }
    }

    private void scrollBodyWidgetIntoView(AbstractWidget widget) {
        Integer virtualY = this.bodyWidgetY.get(widget);
        if (virtualY == null) {
            return;
        }
        VerticalScrollLayout layout = bodyScrollLayout();
        int requestedOffset = this.bodyScrollOffset;
        if (virtualY - requestedOffset < layout.viewportTop()) {
            requestedOffset = virtualY - layout.viewportTop();
        } else if (virtualY + widget.getHeight() - requestedOffset > layout.viewportBottom()) {
            requestedOffset = virtualY + widget.getHeight() - layout.viewportBottom();
        }
        setBodyScrollOffset(requestedOffset);
    }

    private void focusWeightField() {
        if (this.weightBox == null || !this.weightBox.active) {
            return;
        }
        scrollBodyWidgetIntoView(this.weightBox);
        if (this.weightBox.visible) {
            this.setInitialFocus(this.weightBox);
        }
    }

    private void focusFirstBodyWidget() {
        for (AbstractWidget widget : this.bodyWidgets) {
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
        String pageKey = switch (this.page) {
            case TARGETS -> "screen.delvefold.ore_wizard.tab.blocks";
            case FILTERS -> "screen.delvefold.ore_wizard.tab.filters";
            case BANDS -> "screen.delvefold.ore_wizard.tab.bands";
        };
        return Component.translatable("screen.delvefold.ore_wizard.narration",
                Component.translatable(pageKey), state);
    }

    private void drawBodyScrollbar(GuiGraphics graphics, int x, VerticalScrollLayout layout) {
        int maximum = layout.maximumScroll();
        if (maximum <= 0) {
            return;
        }
        int top = layout.viewportTop();
        int bottom = layout.viewportBottom();
        int trackHeight = bottom - top;
        int virtualHeight = Math.max(trackHeight, this.bodyVirtualBottom - top);
        int thumbHeight = Math.min(trackHeight, Math.max(14, trackHeight * trackHeight / virtualHeight));
        int thumbTravel = Math.max(1, trackHeight - thumbHeight);
        int thumbY = top + this.bodyScrollOffset * thumbTravel / maximum;
        graphics.fill(x, top, x + 3, bottom, 0xAA0B1318);
        graphics.fill(x, thumbY, x + 3, thumbY + thumbHeight, ACCENT);
    }

    private void resetValidationMessage() {
        if (this.unsupportedDuplicateSources) {
            showDuplicateSourceMessage();
        } else {
            this.validationMessage = Component.empty();
        }
    }

    private void showDuplicateSourceMessage() {
        this.validationMessage = Component.translatable(
                "screen.delvefold.ore_wizard.validation.duplicate_sources");
        this.validationColor = DANGER;
    }

    private static boolean hasDuplicateSources(AdminSnapshot.OreRuleDraft draft) {
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        for (AdminSnapshot.OreVariantDraft variant : draft.variants()) {
            if (!seen.add(variant.sourceId())) {
                return true;
            }
        }
        return false;
    }

    private int bodyTop() {
        return this.contentTop() + 32;
    }

    private boolean compactLayout() {
        return this.contentBottom() - bodyTop() < 220;
    }

    private int variantColumns() {
        if (this.panelWidth >= 520) {
            return 4;
        }
        return this.panelWidth >= 390 ? 3 : 2;
    }

    private enum Page {
        TARGETS,
        FILTERS,
        BANDS
    }
}
