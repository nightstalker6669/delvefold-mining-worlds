package com.nightsta69.delvefold.client.gui;

import com.nightsta69.delvefold.client.DelvefoldClientRequests;
import com.nightsta69.delvefold.client.gui.widget.DelvefoldButton.Style;
import com.nightsta69.delvefold.config.analysis.OreDistributionAnalysis;
import com.nightsta69.delvefold.config.model.HeightDistribution;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

public final class DelvefoldOreRuleWizardScreen extends DelvefoldScreen {
    private static final int TARGET_SCROLL_STEP = 22;

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
    private final List<AbstractWidget> targetBodyWidgets = new ArrayList<>();
    private final Map<AbstractWidget, Integer> targetBodyWidgetY = new IdentityHashMap<>();
    private final Page page;
    private int bandIndex;
    private int variantPage;
    private String focusedVariant;
    private String ruleId;
    private boolean enabled;
    private boolean required;
    private boolean deleteArmed;
    private String validationMessage = "";
    private int validationColor = DANGER;
    private boolean closeOnNextSnapshot;
    private int targetScrollOffset;
    private int targetContentHeight;

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
            int targetScrollOffset,
            boolean unsupportedDuplicateSources) {
        super(Component.translatable("screen.delvefold.ore_wizard.title"), snapshot);
        this.parent = parent;
        this.page = page;
        this.bandIndex = Math.max(0, Math.min(bandIndex, Math.max(0, draft.bands().size() - 1)));
        this.variantPage = Math.max(0, variantPage);
        this.targetScrollOffset = Math.max(0, targetScrollOffset);
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
        this.targetBodyWidgets.clear();
        this.targetBodyWidgetY.clear();
        this.targetContentHeight = 0;
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

        int footerY = this.panelTop + this.panelHeight - 29;
        this.addButton(this.contentLeft(), footerY, 76, 22,
                Component.translatable("gui.back"), Style.GHOST, button -> this.minecraft.setScreen(this.parent));
        this.saveButton = this.addButton(this.contentRight() - 106, footerY, 106, 22,
                Component.translatable("screen.delvefold.ore_wizard.save"), Style.PRIMARY, button -> save());
        this.saveButton.active = this.snapshot.backendReady() && !this.unsupportedDuplicateSources;
    }

    private void initFilters() {
        int x = this.contentLeft() + 12;
        int y = bodyTop() + 39;
        int width = this.contentWidth() - 24;
        this.statePropertiesBox = addWideEditBox(x, y, width, this.rawStateProperties,
                "property=value, property=value");
        this.statePropertiesBox.setResponder(value -> this.rawStateProperties = value);
        this.biomeIncludesBox = addWideEditBox(x, y + 48, width, this.rawBiomeIncludes,
                "#delvefold:mining_biomes");
        this.biomeIncludesBox.setResponder(value -> this.rawBiomeIncludes = value);
        this.biomeExcludesBox = addWideEditBox(x, y + 96, width, this.rawBiomeExcludes,
                "minecraft:plains, #namespace:tag");
        this.biomeExcludesBox.setResponder(value -> this.rawBiomeExcludes = value);
    }

    private void initTargets() {
        int x = this.contentLeft() + 12;
        boolean compact = compactLayout();
        int y = bodyTop() + (compact ? 31 : 39);
        int innerWidth = this.contentWidth() - 24;
        int toggleWidth = Math.min(102, Math.max(64, innerWidth / 5));
        int ruleWidth = innerWidth - toggleWidth * 2 - 12;
        this.ruleIdBox = registerTargetWidget(addEditBox(x, y, ruleWidth, this.ruleId, "Rule ID"));
        this.ruleIdBox.setResponder(value -> this.ruleId = value);
        this.ruleIdBox.active = !this.existingRule;
        this.addTargetButton(x + ruleWidth + 6, y, toggleWidth, 20,
                toggleLabel(Component.translatable("screen.delvefold.ore_wizard.enabled"), this.enabled),
                this.enabled ? Style.TOGGLE_ON : Style.TOGGLE_OFF, button -> {
            this.enabled = !this.enabled;
            button.setMessage(toggleLabel(Component.translatable("screen.delvefold.ore_wizard.enabled"), this.enabled));
            setButtonStyle(button, this.enabled ? Style.TOGGLE_ON : Style.TOGGLE_OFF);
        });
        this.addTargetButton(x + ruleWidth + toggleWidth + 12, y, toggleWidth, 20,
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
            String prefix = missing ? "MISSING  •  " : selected ? "ON  •  " : "OFF  •  ";
            Button button = this.addTargetButton(x + column * (variantWidth + variantGap), variantY + row * 22,
                    variantWidth, 20, Component.literal(prefix + blockId),
                    missing ? Style.DANGER : selected ? Style.TOGGLE_ON : Style.TOGGLE_OFF,
                    ignored -> toggleVariant(blockId));
            if (blockId.equals(this.focusedVariant)) {
                button.setMessage(Component.literal((missing ? "MISSING" : selected ? "ON" : "OFF")
                        + "  ›  " + blockId));
            }
        }
        if (pageCount > 1) {
            int pagerY = variantY - 16;
            Button previous = this.addTargetButton(x + innerWidth - 68, pagerY, 30, 14,
                    Component.literal("‹"), Style.GHOST, button -> changeVariantPage(-1));
            previous.active = this.variantPage > 0;
            Button next = this.addTargetButton(x + innerWidth - 32, pagerY, 30, 14,
                    Component.literal("›"), Style.GHOST, button -> changeVariantPage(1));
            next.active = this.variantPage + 1 < pageCount;
        }

        int hostY = variantY + variantRows * 22 + 10;
        int weightGap = 6;
        int weightWidth = Math.min(92, Math.max(62, innerWidth / 5));
        int hostWidth = innerWidth - weightWidth - weightGap;
        this.hostTagBox = registerTargetWidget(addEditBox(x, hostY, hostWidth,
                this.selectedVariants.getOrDefault(this.focusedVariant, inferredHost(this.focusedVariant)),
                "Replace tag for selected variant"));
        this.hostTagBox.setResponder(value -> {
            if (this.focusedVariant != null && this.selectedVariants.containsKey(this.focusedVariant)) {
                this.selectedVariants.put(this.focusedVariant, value);
            }
        });
        this.weightBox = registerTargetWidget(addEditBox(x + hostWidth + weightGap, hostY, weightWidth,
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
            this.addTargetButton(modeX, terrainY, terrainWidth, 20,
                    toggleLabel(DelvefoldText.option("terrain", mode.serializedName()),
                            this.terrainModes.contains(mode)),
                    selected ? Style.TOGGLE_ON : Style.TOGGLE_OFF,
                    button -> toggleTerrain(mode));
        }

        this.targetContentHeight = terrainY + 28 - bodyTop();
        applyTargetScroll();

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
        int distributionWidth = Math.min(190, innerWidth / 3);
        int bandIdWidth = innerWidth - distributionWidth - 8;
        this.bandIdBox = addEditBox(x, y, bandIdWidth, this.rawBandId, "Band ID");
        this.bandIdBox.setResponder(value -> this.rawBandId = value);
        this.addButton(x + bandIdWidth + 8, y, distributionWidth, 20,
                Component.literal(pretty(band.distribution().name()) + "  ›"), Style.SECONDARY,
                button -> cycleDistribution());

        int rowTwoY = y + (compact ? 31 : 42);
        int rowTwoGap = 8;
        int veinWidth = (innerWidth - rowTwoGap * 2) / 3;
        int attemptsWidth = veinWidth;
        int discardWidth = innerWidth - veinWidth - attemptsWidth - rowTwoGap * 2;
        this.veinSizeBox = addEditBox(x, rowTwoY, veinWidth, this.rawVeinSize, "Vein size");
        this.attemptsBox = addEditBox(x + veinWidth + rowTwoGap, rowTwoY, attemptsWidth,
                this.rawAttempts, "Attempts/chunk");
        this.airDiscardBox = addEditBox(x + veinWidth + attemptsWidth + rowTwoGap * 2, rowTwoY,
                discardWidth, this.rawAirDiscard, "Air discard 0-1");

        int rowThreeY = y + (compact ? 62 : 84);
        int heightGap = 6;
        int heightWidth = (innerWidth - heightGap * 4) / 5;
        this.minYBox = addEditBox(x, rowThreeY, heightWidth, this.rawMinY, "Min Y");
        this.maxYBox = addEditBox(x + (heightWidth + heightGap), rowThreeY, heightWidth, this.rawMaxY, "Max Y");
        this.peakYBox = addEditBox(x + (heightWidth + heightGap) * 2, rowThreeY,
                heightWidth, this.rawPeakY, "Peak Y");
        this.plateauMinBox = addEditBox(x + (heightWidth + heightGap) * 3, rowThreeY,
                heightWidth, this.rawPlateauMin, "Plateau min");
        this.plateauMaxBox = addEditBox(x + (heightWidth + heightGap) * 4, rowThreeY,
                innerWidth - (heightWidth + heightGap) * 4, this.rawPlateauMax, "Plateau max");
        bindRawBandResponders();
        this.peakYBox.active = band.distribution() == HeightDistribution.TRIANGLE;
        this.plateauMinBox.active = band.distribution() == HeightDistribution.TRAPEZOID;
        this.plateauMaxBox.active = band.distribution() == HeightDistribution.TRAPEZOID;

        int controlsY = y + (compact ? 95 : 156);
        int controlGap = 6;
        int controlWidth = (innerWidth - controlGap * 3) / 4;
        Button previous = this.addButton(x, controlsY, controlWidth, 20,
                Component.translatable("screen.delvefold.ore_wizard.previous_band"), Style.GHOST,
                button -> changeBand(-1));
        previous.active = this.bandIndex > 0;
        Button next = this.addButton(x + controlWidth + controlGap, controlsY, controlWidth, 20,
                Component.translatable("screen.delvefold.ore_wizard.next_band"), Style.GHOST,
                button -> changeBand(1));
        next.active = this.bandIndex + 1 < this.bands.size();
        Button add = this.addButton(x + (controlWidth + controlGap) * 2, controlsY, controlWidth, 20,
                Component.translatable("screen.delvefold.ore_wizard.add_band"), Style.PRIMARY,
                button -> addBand());
        add.active = this.bands.size() < ProtocolLimits.MAX_BANDS;
        Button remove = this.addButton(x + (controlWidth + controlGap) * 3, controlsY, controlWidth, 20,
                Component.translatable("screen.delvefold.ore_wizard.remove_band"), Style.DANGER,
                button -> removeBand());
        remove.active = this.bands.size() > 1;
    }

    private EditBox addEditBox(int x, int y, int width, String value, String hint) {
        return addEditBox(x, y, width, value, Component.literal(hint));
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

    private Button addTargetButton(
            int x, int y, int width, int height, Component label, Style style, Button.OnPress onPress) {
        return registerTargetWidget(this.addButton(x, y, width, height, label, style, onPress));
    }

    private <T extends AbstractWidget> T registerTargetWidget(T widget) {
        this.targetBodyWidgets.add(widget);
        this.targetBodyWidgetY.put(widget, widget.getY());
        return widget;
    }

    private EditBox addWideEditBox(int x, int y, int width, String value, String hint) {
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
        this.veinSizeBox.setResponder(value -> this.rawVeinSize = value);
        this.attemptsBox.setResponder(value -> this.rawAttempts = value);
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
                current.plateauMaxY(), current.discardOnAirExposure()));
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
                defaults.plateauMinY(), defaults.plateauMaxY(), defaults.discardOnAirExposure()));
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
            HeightDistribution distribution = this.bands.get(this.bandIndex).distribution();
            boolean shapeValuesValid = switch (distribution) {
                case UNIFORM -> true;
                case TRIANGLE -> peakY >= minY && peakY <= maxY;
                case TRAPEZOID -> plateauMin >= minY && plateauMax <= maxY && plateauMin <= plateauMax;
            };

            if (!id.matches("[a-z0-9_.-]{1,128}") || veinSize < 1 || veinSize > 64 || !Double.isFinite(attempts)
                    || attempts < 0.0D || attempts > 256.0D || minY < -64 || maxY > 320 || minY > maxY
                    || !shapeValuesValid || !Double.isFinite(airDiscard)
                    || airDiscard < 0.0D || airDiscard > 1.0D) {
                this.validationMessage = "Check band ID, heights, vein 1-64, attempts 0-256, and air discard 0-1.";
                this.validationColor = DANGER;
                return false;
            }

            this.bands.set(this.bandIndex, new AdminSnapshot.OreBandDraft(
                    id, veinSize, attempts, distribution, minY, maxY, peakY,
                    plateauMin, plateauMax, airDiscard));
            resetValidationMessage();
            return true;
        } catch (NumberFormatException exception) {
            this.validationMessage = "Every numeric field must contain a valid number.";
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
                    this.targetScrollOffset,
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
        this.validationMessage = "Saving on the server…";
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
            this.validationMessage = "Rule ID may contain lowercase letters, digits, _, . and - only.";
            this.validationColor = DANGER;
            return false;
        }
        if (this.selectedVariants.isEmpty() || this.terrainModes.isEmpty() || this.bands.isEmpty()) {
            this.validationMessage = "Select at least one variant, terrain mode, and spawn band.";
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
                this.validationMessage = "Every block and replacement tag must be a valid namespace:path ID.";
                this.validationColor = DANGER;
                return false;
            }
            entry.setValue(replacementTag);
            int weight = this.variantWeights.getOrDefault(
                    entry.getKey(), AdminSnapshot.OreVariantDraft.MIN_WEIGHT);
            if (weight < AdminSnapshot.OreVariantDraft.MIN_WEIGHT
                    || weight > AdminSnapshot.OreVariantDraft.MAX_WEIGHT) {
                this.validationMessage = Component.translatable(
                        "screen.delvefold.ore_wizard.validation.weight_all").getString();
                this.validationColor = DANGER;
                return false;
            }
        }
        List<String> bandIds = new ArrayList<>();
        for (AdminSnapshot.OreBandDraft band : this.bands) {
            if (!band.id().matches("[a-z0-9_.-]{1,128}")) {
                this.validationMessage = "Band IDs may contain lowercase letters, digits, _, . and - only.";
                this.validationColor = DANGER;
                return false;
            }
            if (bandIds.contains(band.id())) {
                this.validationMessage = "Each spawn band needs a unique ID.";
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
                    "screen.delvefold.ore_wizard.validation.weight").getString();
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
                throw new IllegalArgumentException("Too many state properties or biome selectors");
            }
            this.variantStates.put(this.focusedVariant, state);
            this.biomeIncludes.clear();
            this.biomeIncludes.addAll(includes);
            this.biomeExcludes.clear();
            this.biomeExcludes.addAll(excludes);
            resetValidationMessage();
            return true;
        } catch (IllegalArgumentException exception) {
            this.validationMessage = exception.getMessage();
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
                throw new IllegalArgumentException("State properties must use property=value pairs.");
            }
            if (result.putIfAbsent(parts[0], parts[1].trim()) != null) {
                throw new IllegalArgumentException("Duplicate state property: " + parts[0]);
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
                throw new IllegalArgumentException("Biome selectors must be namespace:path IDs or #tags.");
            }
            if (!result.contains(value)) {
                result.add(value);
            }
        }
        return result;
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
        if (this.page != Page.TARGETS) {
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        for (Renderable renderable : this.renderables) {
            if (!this.targetBodyWidgets.contains(renderable)) {
                renderable.render(graphics, mouseX, mouseY, partialTick);
            }
        }
        graphics.enableScissor(this.contentLeft() + 1, targetViewportTop(),
                this.contentRight() - 1, targetViewportBottom());
        for (AbstractWidget widget : this.targetBodyWidgets) {
            widget.render(graphics, mouseX, mouseY, partialTick);
        }
        graphics.disableScissor();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.page == Page.TARGETS
                && mouseX >= this.contentLeft() && mouseX < this.contentRight()
                && mouseY >= targetViewportTop() && mouseY < targetViewportBottom()
                && maximumTargetScroll() > 0) {
            setTargetScrollOffset(this.targetScrollOffset
                    - (int) Math.signum(scrollY) * TARGET_SCROLL_STEP);
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
        if (this.page == Page.TARGETS) {
            graphics.enableScissor(x + 1, targetViewportTop(), x + width - 1, targetViewportBottom());
            int scrolledY = y - this.targetScrollOffset;
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
            Component range = Component.literal(start + "–" + end + " / " + this.candidateVariants.size());
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
            String clippedFocusLabel = this.font.plainSubstrByWidth(focusLabel.getString(), hostWidth);
            this.drawFieldLabel(graphics,
                    Component.literal(clippedFocusLabel), x + 12, hostY - 12);
            this.drawFieldLabel(graphics, Component.translatable("screen.delvefold.ore_wizard.weight"),
                    x + 12 + hostWidth + weightGap, hostY - 12);
            this.drawFieldLabel(graphics, Component.translatable("screen.delvefold.ore_wizard.terrain"), x + 12, terrainY - 12);
            graphics.disableScissor();
            drawTargetScrollbar(graphics, x + width - 4);
        } else if (this.page == Page.FILTERS) {
            this.drawSectionTitle(graphics, Component.translatable("screen.delvefold.ore_wizard.filters.title"), x + 10, y + 7);
            int fieldY = y + 39;
            this.drawFieldLabel(graphics, Component.translatable("screen.delvefold.ore_wizard.state", this.focusedVariant), x + 12, fieldY - 12);
            this.drawFieldLabel(graphics, Component.translatable("screen.delvefold.ore_wizard.biomes.include"), x + 12, fieldY + 36);
            this.drawFieldLabel(graphics, Component.translatable("screen.delvefold.ore_wizard.biomes.exclude"), x + 12, fieldY + 84);
            graphics.drawWordWrap(this.font, Component.translatable("screen.delvefold.ore_wizard.filters.help"),
                    x + 12, fieldY + 121, width - 24, DIM_TEXT);
        } else {
            AdminSnapshot.OreBandDraft band = this.bands.get(this.bandIndex);
            int fieldY = y + (compact ? 31 : 39);
            int rowTwoY = fieldY + (compact ? 31 : 42);
            int rowThreeY = fieldY + (compact ? 62 : 84);
            this.drawSectionTitle(graphics,
                    Component.translatable("screen.delvefold.ore_wizard.band.title",
                            this.bandIndex + 1, this.bands.size()),
                    x + 10, y + 7);
            this.drawFieldLabel(graphics, Component.translatable("screen.delvefold.ore_wizard.band_id"), x + 12, fieldY - 12);
            this.drawFieldLabel(graphics, Component.translatable("screen.delvefold.ore_wizard.distribution"),
                    x + width - Math.min(190, (width - 24) / 3) - 12, fieldY - 12);
            this.drawFieldLabel(graphics, Component.translatable("screen.delvefold.ore_wizard.vein_size"), x + 12, rowTwoY - 12);
            this.drawFieldLabel(graphics, Component.translatable("screen.delvefold.ore_wizard.attempts"),
                    x + 12 + ((width - 24 - 16) / 3) + 8, rowTwoY - 12);
            this.drawFieldLabel(graphics, Component.translatable("screen.delvefold.ore_wizard.air_discard"),
                    x + 12 + (((width - 24 - 16) / 3) + 8) * 2, rowTwoY - 12);

            int innerWidth = width - 24;
            int gap = 6;
            int fieldWidth = (innerWidth - gap * 4) / 5;
            String[] heightLabels = width >= 430
                    ? new String[]{"MIN Y", "MAX Y", "PEAK Y", "PLATEAU MIN", "PLATEAU MAX"}
                    : new String[]{"MIN", "MAX", "PEAK", "PLAT MIN", "PLAT MAX"};
            for (int index = 0; index < heightLabels.length; index++) {
                this.drawFieldLabel(graphics, Component.literal(heightLabels[index]),
                        x + 12 + index * (fieldWidth + gap), rowThreeY - 12);
            }

            if (!compact) {
                int helpY = y + 151;
                drawBandPreview(graphics, band, x + 12, helpY, width - 24, 34);
            }
        }
        if (!this.validationMessage.isEmpty()) {
            int maximumWidth = Math.max(55, this.contentWidth() - 220);
            String clipped = this.font.plainSubstrByWidth(this.validationMessage, maximumWidth);
            int messageX = x + width - 12 - this.font.width(clipped);
            graphics.fill(messageX - 4, y + 5, x + width - 8, y + 18, 0xD9111A20);
            graphics.drawString(this.font, clipped, messageX, y + 7, this.validationColor, false);
        }
    }

    private void drawBandPreview(
            GuiGraphics graphics, AdminSnapshot.OreBandDraft draft, int x, int y, int width, int height) {
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
        String lineOne = String.format(java.util.Locale.ROOT, "%.2f attempts  •  %.1f work", analysis.attemptsPerChunk(), analysis.workUnits());
        String lineTwo = pretty(analysis.density().name()) + "  •  Y " + draft.minY() + "…" + draft.maxY();
        int textX = graphRight + 8;
        graphics.drawString(this.font, this.font.plainSubstrByWidth(lineOne, x + width - textX - 4),
                textX, y + 7, TEXT, false);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(lineTwo, x + width - textX - 4),
                textX, y + 19, analysis.density() == OreDistributionAnalysis.Density.EXTREME ? WARNING : MUTED_TEXT, false);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void handleActionResult(ActionResultPayload payload) {
        this.validationMessage = payload.message();
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
                this.targetScrollOffset,
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

    private int targetViewportTop() {
        return bodyTop() + 1;
    }

    private int targetViewportBottom() {
        return Math.max(targetViewportTop(), this.contentBottom() - 1);
    }

    private int maximumTargetScroll() {
        return Math.max(0, this.targetContentHeight - (targetViewportBottom() - targetViewportTop()));
    }

    private void setTargetScrollOffset(int requestedOffset) {
        this.targetScrollOffset = Math.clamp(requestedOffset, 0, maximumTargetScroll());
        applyTargetScroll();
    }

    private void applyTargetScroll() {
        this.targetScrollOffset = Math.clamp(this.targetScrollOffset, 0, maximumTargetScroll());
        int viewportTop = targetViewportTop();
        int viewportBottom = targetViewportBottom();
        for (AbstractWidget widget : this.targetBodyWidgets) {
            int y = this.targetBodyWidgetY.getOrDefault(widget, widget.getY()) - this.targetScrollOffset;
            widget.setY(y);
            widget.visible = y >= viewportTop && y + widget.getHeight() <= viewportBottom;
        }
    }

    private void scrollTargetWidgetIntoView(AbstractWidget widget) {
        Integer virtualY = this.targetBodyWidgetY.get(widget);
        if (virtualY == null) {
            return;
        }
        int requestedOffset = this.targetScrollOffset;
        if (virtualY - requestedOffset < targetViewportTop()) {
            requestedOffset = virtualY - targetViewportTop();
        } else if (virtualY + widget.getHeight() - requestedOffset > targetViewportBottom()) {
            requestedOffset = virtualY + widget.getHeight() - targetViewportBottom();
        }
        setTargetScrollOffset(requestedOffset);
    }

    private void focusWeightField() {
        if (this.weightBox == null || !this.weightBox.active) {
            return;
        }
        scrollTargetWidgetIntoView(this.weightBox);
        if (this.weightBox.visible) {
            this.setInitialFocus(this.weightBox);
        }
    }

    private void drawTargetScrollbar(GuiGraphics graphics, int x) {
        int maximum = maximumTargetScroll();
        if (maximum <= 0) {
            return;
        }
        int top = targetViewportTop();
        int bottom = targetViewportBottom();
        int trackHeight = bottom - top;
        int thumbHeight = Math.max(14, trackHeight * trackHeight / Math.max(trackHeight, this.targetContentHeight));
        int thumbTravel = Math.max(1, trackHeight - thumbHeight);
        int thumbY = top + this.targetScrollOffset * thumbTravel / maximum;
        graphics.fill(x, top, x + 3, bottom, 0xAA0B1318);
        graphics.fill(x, thumbY, x + 3, thumbY + thumbHeight, ACCENT);
    }

    private void resetValidationMessage() {
        if (this.unsupportedDuplicateSources) {
            showDuplicateSourceMessage();
        } else {
            this.validationMessage = "";
        }
    }

    private void showDuplicateSourceMessage() {
        this.validationMessage = Component.translatable(
                "screen.delvefold.ore_wizard.validation.duplicate_sources").getString();
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

    private static String pretty(String value) {
        String text = value.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private enum Page {
        TARGETS,
        FILTERS,
        BANDS
    }
}
