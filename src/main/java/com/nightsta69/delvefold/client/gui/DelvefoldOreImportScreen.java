package com.nightsta69.delvefold.client.gui;

import com.nightsta69.delvefold.client.DelvefoldClientRequests;
import com.nightsta69.delvefold.client.gui.widget.DelvefoldButton.Style;
import com.nightsta69.delvefold.config.importer.OreImportModels.DiffStatus;
import com.nightsta69.delvefold.config.importer.OreImportModels.HostKind;
import com.nightsta69.delvefold.network.ProtocolLimits;
import com.nightsta69.delvefold.network.model.ActionStatus;
import com.nightsta69.delvefold.network.model.AdminSnapshot;
import com.nightsta69.delvefold.network.model.OreImportViews.DiffView;
import com.nightsta69.delvefold.network.model.OreImportViews.GroupView;
import com.nightsta69.delvefold.network.model.OreImportViews.PreviewView;
import com.nightsta69.delvefold.network.model.OreImportViews.ScanView;
import com.nightsta69.delvefold.network.model.OreImportViews.TerrainDeltaView;
import com.nightsta69.delvefold.network.model.OreImportViews.ValidationIssueView;
import com.nightsta69.delvefold.network.payload.ActionResultPayload;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** Guided, server-authoritative importer for conventional and ore-like registered blocks. */
public final class DelvefoldOreImportScreen extends DelvefoldScreen {
    private final Screen parent;
    private final OreImportScreenState state;
    private final List<RenderedGroup> renderedGroups = new ArrayList<>();
    private boolean includeVanilla;
    private boolean initialScanRequested;
    private boolean loading;
    private Component statusMessage = Component.empty();
    private @Nullable Button createButton;

    /**
     * Creates the importer before any server scan has arrived.
     *
     * @param parent screen restored when the importer closes
     * @param snapshot immutable administration capabilities and profile inventory
     */
    public DelvefoldOreImportScreen(Screen parent, AdminSnapshot snapshot) {
        super(Component.translatable("screen.delvefold.import.title"), snapshot);
        this.parent = parent;
        this.state = new OreImportScreenState(
                snapshot.profiles().stream().map(AdminSnapshot.ProfileDraft::id).toList());
    }

    /**
     * Accepts a bounded scan page on the client thread and rebuilds visible widgets when initialized.
     *
     * @param replacement immutable server-owned scan view
     */
    public void acceptScan(ScanView replacement) {
        this.state.acceptScan(replacement);
        this.loading = false;
        this.statusMessage = Component.empty();
        if (this.minecraft != null) {
            this.rebuildWidgets();
        }
    }

    /**
     * Accepts a non-mutating import preview on the client thread.
     *
     * @param replacement immutable validation, diff, and workload preview
     */
    public void acceptPreview(PreviewView replacement) {
        this.state.acceptPreview(replacement);
        this.loading = false;
        this.statusMessage = Component.empty();
        if (this.minecraft != null) {
            this.rebuildWidgets();
        }
    }

    @Override
    protected int preferredPanelWidth() {
        return 800;
    }

    @Override
    protected int preferredPanelHeight() {
        return 450;
    }

    @Override
    protected void initPanel() {
        this.renderedGroups.clear();
        this.createButton = null;
        if (this.state.preview() == null) {
            initScanPanel();
        } else {
            initPreviewPanel();
        }
    }

    private void initScanPanel() {
        @Nullable ScanView activeScan = this.state.scan();
        OreImportPanelLayout panelLayout = OreImportPanelLayout.scan(
                this.contentTop(), this.contentBottom(), activeScan != null && activeScan.truncated());
        int footerY = this.footerButtonY();
        int slot = Math.max(1, (this.contentWidth() - 12) / 4);
        this.addButton(
                this.contentLeft(),
                footerY,
                slot,
                22,
                Component.translatable("screen.delvefold.back"),
                Style.GHOST,
                button -> this.onClose());
        Button previous = this.addButton(
                this.contentLeft() + slot + 4,
                footerY,
                slot,
                22,
                Component.translatable("screen.delvefold.previous"),
                Style.GHOST,
                button -> previousScanPage());
        Button next = this.addButton(
                this.contentLeft() + (slot + 4) * 2,
                footerY,
                slot,
                22,
                Component.translatable("screen.delvefold.next"),
                Style.GHOST,
                button -> nextScanPage());
        Button previewButton = this.addButton(
                this.contentLeft() + (slot + 4) * 3,
                footerY,
                this.contentWidth() - slot * 3 - 12,
                22,
                Component.translatable("screen.delvefold.import.preview"),
                Style.PRIMARY,
                button -> requestPreview());

        int controlsY = panelLayout.controlsY();
        int toggleWidth = Math.max(1, this.contentWidth() * 2 / 3 - 3);
        this.addButton(
                this.contentLeft(),
                controlsY,
                toggleWidth,
                20,
                Component.translatable(
                        "screen.delvefold.import.include_vanilla",
                        Component.translatable(
                                this.includeVanilla ? "screen.delvefold.status.on" : "screen.delvefold.status.off")),
                this.includeVanilla ? Style.TOGGLE_ON : Style.TOGGLE_OFF,
                button -> {
                    this.includeVanilla = !this.includeVanilla;
                    button.setMessage(Component.translatable(
                            "screen.delvefold.import.include_vanilla",
                            Component.translatable(
                                    this.includeVanilla
                                            ? "screen.delvefold.status.on"
                                            : "screen.delvefold.status.off")));
                    setButtonStyle(button, this.includeVanilla ? Style.TOGGLE_ON : Style.TOGGLE_OFF);
                });
        this.addButton(
                this.contentLeft() + toggleWidth + 6,
                controlsY,
                this.contentWidth() - toggleWidth - 6,
                20,
                Component.translatable("screen.delvefold.import.rescan"),
                Style.SECONDARY,
                button -> requestScan());

        if (activeScan == null) {
            previous.active = false;
            next.active = false;
            previewButton.active = false;
            if (!this.initialScanRequested) {
                this.initialScanRequested = true;
                requestScan();
            }
            return;
        }

        int groupTop = panelLayout.rowsTop();
        int available = Math.max(OreImportScreenState.SCAN_ROW_HEIGHT, panelLayout.availableRowsHeight());
        OreImportScreenState.PageWindow window = this.state.resizeScanWindow(available);
        for (int index = window.startInclusive(); index < window.endExclusive(); index++) {
            GroupView group = activeScan.groups().get(index);
            int rowY = groupTop + (index - window.startInclusive()) * OreImportScreenState.SCAN_ROW_HEIGHT;
            boolean selected = this.state.selected(group.id());
            int importable = importableCandidateCount(group);
            int review = reviewCandidateCount(group);
            Component marker = Component.translatable(
                    selected ? "screen.delvefold.status.selected" : "screen.delvefold.status.not_selected");
            Component label = Component.translatable(
                    "screen.delvefold.import.group_summary",
                    marker,
                    group.namespace() + ":" + group.material(),
                    importable,
                    review);
            Button groupButton = this.addButton(
                    this.contentLeft(),
                    rowY,
                    this.contentWidth(),
                    24,
                    label,
                    selected ? Style.TOGGLE_ON : Style.TOGGLE_OFF,
                    button -> toggleGroup(group));
            groupButton.active = importable > 0;
            groupButton.setTooltip(Tooltip.create(groupTooltip(group)));
            groupButton.setTooltipDelay(Duration.ofMillis(250));
            this.renderedGroups.add(new RenderedGroup(icon(group), this.contentLeft() + 5, rowY + 4));
        }

        previous.active = this.state.localOffset() > 0 || activeScan.page() > 0;
        next.active = this.state.localOffset() + this.state.visibleRows()
                        < activeScan.groups().size()
                || activeScan.page() + 1 < activeScan.pageCount();
        previewButton.active = !this.loading && this.state.selectedCount() > 0;
    }

    private void initPreviewPanel() {
        @Nullable PreviewView activePreview = this.state.preview();
        if (activePreview == null) {
            return;
        }
        OreImportPanelLayout panelLayout =
                OreImportPanelLayout.preview(this.contentTop(), this.contentBottom(), activePreview.truncated());
        int footerY = this.footerButtonY();
        int slot = Math.max(1, (this.contentWidth() - 12) / 4);
        this.addButton(
                this.contentLeft(),
                footerY,
                slot,
                22,
                Component.translatable("screen.delvefold.back"),
                Style.GHOST,
                button -> backToScan());
        Button previous = this.addButton(
                this.contentLeft() + slot + 4,
                footerY,
                slot,
                22,
                Component.translatable("screen.delvefold.previous"),
                Style.GHOST,
                button -> previousPreviewPage());
        Button next = this.addButton(
                this.contentLeft() + (slot + 4) * 2,
                footerY,
                slot,
                22,
                Component.translatable("screen.delvefold.next"),
                Style.GHOST,
                button -> nextPreviewPage());
        Button create = this.addButton(
                this.contentLeft() + (slot + 4) * 3,
                footerY,
                this.contentWidth() - slot * 3 - 12,
                22,
                Component.translatable("screen.delvefold.import.create"),
                Style.PRIMARY,
                button -> createProfile());
        this.createButton = create;

        int fieldY = panelLayout.controlsY();
        EditBox profile = this.addRenderableWidget(new EditBox(
                this.font,
                this.contentLeft(),
                fieldY,
                Math.max(1, this.contentWidth() / 2),
                20,
                Component.translatable("screen.delvefold.import.profile_id")));
        profile.setMaxLength(ProtocolLimits.ID_LENGTH);
        profile.setValue(this.state.targetProfileId());
        profile.setHint(Component.translatable("screen.delvefold.import.profile_hint"));
        profile.setTextColor(TEXT);
        profile.setResponder(value -> {
            this.state.setTargetProfileId(value);
            updateCreateButton();
        });

        int diffTop = panelLayout.rowsTop();
        OreImportScreenState.PageWindow window =
                this.state.resizePreviewWindowAllowEmpty(panelLayout.availableRowsHeight());
        for (int index = window.startInclusive(); index < window.endExclusive(); index++) {
            DiffView entry = activePreview.diff().get(index);
            int rowY = diffTop + (index - window.startInclusive()) * OreImportScreenState.PREVIEW_ROW_HEIGHT;
            String rule = entry.ruleId().isBlank() ? entry.groupId() : entry.ruleId();
            Component label = Component.translatable(
                    "screen.delvefold.import.diff.summary",
                    diffLabel(entry.status()),
                    rule,
                    entry.addedBlocks().size(),
                    entry.skippedBlocks().size());
            Button diff = this.addButton(
                    this.contentLeft(), rowY, this.contentWidth(), 20, label, diffStyle(entry.status()), button -> {});
            diff.setTooltip(Tooltip.create(diffTooltip(entry)));
            diff.setTooltipDelay(Duration.ofMillis(250));
        }

        if (window.visibleRows() == 0) {
            previous.active = activePreview.page() > 0;
            next.active = activePreview.page() + 1 < activePreview.pageCount();
        } else {
            previous.active = this.state.localOffset() > 0 || activePreview.page() > 0;
            next.active = this.state.localOffset() + this.state.visibleRows()
                            < activePreview.diff().size()
                    || activePreview.page() + 1 < activePreview.pageCount();
        }
        if (!activePreview.issues().isEmpty()) {
            var issueText = Component.empty();
            activePreview.issues().stream().limit(6).forEach(issue -> {
                if (!issueText.getString().isEmpty()) {
                    issueText.append("\n");
                }
                issueText.append(issueLine(issue));
            });
            create.setTooltip(Tooltip.create(issueText));
        }
        updateCreateButton();
    }

    private void requestScan() {
        this.loading = true;
        this.statusMessage = Component.empty();
        DelvefoldClientRequests.requestOreImportScan(this.snapshot.oreRevision(), this.includeVanilla);
    }

    private void requestPreview() {
        @Nullable ScanView activeScan = this.state.scan();
        if (activeScan == null || this.state.selectedCount() == 0) {
            return;
        }
        this.loading = true;
        List<String> selected = this.state.sortedSelectedGroupIds();
        DelvefoldClientRequests.requestOreImportPreview(activeScan.scanToken(), selected);
    }

    private void toggleGroup(GroupView group) {
        if (this.state.toggleGroup(group.id(), importableCandidateCount(group) > 0)) {
            this.rebuildWidgets();
        }
    }

    private void previousScanPage() {
        @Nullable ScanView activeScan = this.state.scan();
        if (activeScan == null) {
            return;
        }
        applyScanTransition(activeScan, this.state.previousScanPage());
    }

    private void nextScanPage() {
        @Nullable ScanView activeScan = this.state.scan();
        if (activeScan == null) {
            return;
        }
        applyScanTransition(activeScan, this.state.nextScanPage());
    }

    private void applyScanTransition(ScanView activeScan, OreImportScreenState.PageTransition transition) {
        if (transition.localChanged()) {
            this.rebuildWidgets();
        } else if (transition.requestsServerPage()) {
            this.loading = true;
            DelvefoldClientRequests.requestOreImportScanPage(activeScan.scanToken(), transition.requestedPage());
        }
    }

    private void previousPreviewPage() {
        @Nullable PreviewView activePreview = this.state.preview();
        if (activePreview == null) {
            return;
        }
        applyPreviewTransition(activePreview, this.state.previousPreviewPage());
    }

    private void nextPreviewPage() {
        @Nullable PreviewView activePreview = this.state.preview();
        if (activePreview == null) {
            return;
        }
        applyPreviewTransition(activePreview, this.state.nextPreviewPage());
    }

    private void applyPreviewTransition(PreviewView activePreview, OreImportScreenState.PageTransition transition) {
        if (transition.localChanged()) {
            this.rebuildWidgets();
        } else if (transition.requestsServerPage()) {
            this.loading = true;
            DelvefoldClientRequests.requestOreImportPreviewPage(
                    activePreview.commitToken(), transition.requestedPage());
        }
    }

    private void backToScan() {
        this.state.backToScan();
        this.loading = false;
        this.statusMessage = Component.empty();
        this.rebuildWidgets();
    }

    private void createProfile() {
        @Nullable PreviewView activePreview = this.state.preview();
        if (activePreview == null || !canCreate()) {
            return;
        }
        this.loading = true;
        updateCreateButton();
        DelvefoldClientRequests.createImportedOreProfile(activePreview.commitToken(), this.state.targetProfileId());
    }

    private void updateCreateButton() {
        Button create = this.createButton;
        if (create != null) {
            create.active = !this.loading && canCreate();
        }
    }

    private boolean canCreate() {
        return this.state.canCreate();
    }

    private @Nullable Component profileIdIssue() {
        return switch (this.state.profileIdIssue()) {
            case NONE -> null;
            case EMPTY -> Component.translatable("screen.delvefold.import.profile_error.empty");
            case TOO_LONG ->
                Component.translatable("screen.delvefold.import.profile_error.too_long", ProtocolLimits.ID_LENGTH);
            case INVALID -> Component.translatable("screen.delvefold.import.profile_error.invalid");
            case EXISTS ->
                Component.translatable("screen.delvefold.import.profile_error.exists", this.state.targetProfileId());
        };
    }

    @Override
    public void handleActionResult(ActionResultPayload payload) {
        this.loading = false;
        this.statusMessage = DelvefoldText.serverMessage(payload.message());
        if (payload.status() != ActionStatus.ACCEPTED && this.minecraft != null) {
            this.rebuildWidgets();
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    protected void renderPanelContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = this.contentLeft();
        int y = this.contentTop();
        this.drawCard(graphics, x, y, this.contentWidth(), this.contentBottom() - y);
        if (this.state.preview() == null) {
            renderScan(graphics, x, y);
        } else {
            renderPreview(graphics, x, y);
        }
    }

    private void renderScan(GuiGraphics graphics, int x, int y) {
        this.drawSectionTitle(graphics, Component.translatable("screen.delvefold.import.discovery"), x + 9, y + 7);
        @Nullable ScanView activeScan = this.state.scan();
        if (activeScan == null) {
            graphics.drawWordWrap(
                    this.font,
                    Component.translatable("screen.delvefold.import.scanning"),
                    x + 10,
                    y + 58,
                    this.contentWidth() - 20,
                    MUTED_TEXT);
            return;
        }
        Component summary = Component.translatable(
                "screen.delvefold.import.scan_summary",
                activeScan.totalGroups(),
                activeScan.scannedBlocks(),
                activeScan.baseProfileId());
        graphics.drawString(
                this.font,
                this.font.plainSubstrByWidth(summary.getString(), this.contentWidth() - 20),
                x + 10,
                y + 48,
                DIM_TEXT,
                false);
        Component selected = Component.translatable("screen.delvefold.import.selected", this.state.selectedCount());
        graphics.drawString(
                this.font, selected, x + this.contentWidth() - this.font.width(selected) - 10, y + 8, ACCENT, false);
        if (activeScan.groups().isEmpty()) {
            graphics.drawWordWrap(
                    this.font,
                    Component.translatable("screen.delvefold.import.no_groups"),
                    x + 10,
                    y + 72,
                    this.contentWidth() - 20,
                    MUTED_TEXT);
        }
        if (activeScan.truncated()) {
            Component warning = Component.translatable("screen.delvefold.import.scan_truncated");
            graphics.drawString(
                    this.font,
                    this.font.plainSubstrByWidth(warning.getString(), this.contentWidth() - 20),
                    x + 10,
                    y + 61,
                    WARNING,
                    false);
        }
        renderStatus(graphics);
    }

    private void renderPreview(GuiGraphics graphics, int x, int y) {
        @Nullable PreviewView activePreview = this.state.preview();
        if (activePreview == null) {
            return;
        }
        this.drawSectionTitle(graphics, Component.translatable("screen.delvefold.import.preview_title"), x + 9, y + 7);
        Component page = Component.translatable(
                "screen.delvefold.import.page", activePreview.page() + 1, activePreview.pageCount());
        graphics.drawString(
                this.font, page, x + this.contentWidth() - this.font.width(page) - 9, y + 8, DIM_TEXT, false);

        Component profileIssue = profileIdIssue();
        Component validity = profileIssue != null
                ? profileIssue
                : activePreview.valid()
                        ? Component.translatable("screen.delvefold.import.valid", activePreview.addedRuleCount())
                        : Component.translatable("screen.delvefold.import.invalid");
        int validityColor = profileIssue == null && activePreview.valid() ? SUCCESS : DANGER;
        graphics.drawString(
                this.font,
                this.font.plainSubstrByWidth(validity.getString(), Math.max(1, this.contentWidth() / 2 - 16)),
                x + this.contentWidth() / 2 + 8,
                y + 29,
                validityColor,
                false);

        int metricX = x + 8;
        int metricY = y + 48;
        int column = Math.max(
                1,
                (this.contentWidth() - 16)
                        / Math.max(1, activePreview.workloads().size()));
        for (int index = 0; index < activePreview.workloads().size(); index++) {
            TerrainDeltaView workload = activePreview.workloads().get(index);
            String terrain = Component.translatable(
                            "option.delvefold.terrain." + workload.terrain().serializedName())
                    .getString();
            Component metric = Component.translatable(
                    "screen.delvefold.import.workload",
                    terrain,
                    format(workload.addedAttempts()),
                    format(workload.addedWorkUnits()));
            graphics.drawString(
                    this.font,
                    this.font.plainSubstrByWidth(metric.getString(), column - 5),
                    metricX + index * column,
                    metricY,
                    DIM_TEXT,
                    false);
        }
        Component authority = Component.translatable("screen.delvefold.import.server_authoritative");
        graphics.drawString(
                this.font,
                this.font.plainSubstrByWidth(authority.getString(), this.contentWidth() - 16),
                x + 8,
                y + 62,
                MUTED_TEXT,
                false);
        if (activePreview.truncated()) {
            Component warning = Component.translatable("screen.delvefold.import.preview_truncated");
            graphics.drawString(
                    this.font,
                    this.font.plainSubstrByWidth(warning.getString(), this.contentWidth() - 16),
                    x + 8,
                    y + 74,
                    WARNING,
                    false);
        }
        if (!activePreview.issues().isEmpty()) {
            Component issue = issueLine(activePreview.issues().getFirst());
            graphics.drawString(
                    this.font,
                    this.font.plainSubstrByWidth(issue.getString(), this.contentWidth() - 16),
                    x + 8,
                    this.contentBottom() - 11,
                    activePreview.valid() ? WARNING : DANGER,
                    false);
        }
        renderStatus(graphics);
    }

    private void renderStatus(GuiGraphics graphics) {
        if (!this.statusMessage.getString().isBlank()) {
            graphics.drawString(
                    this.font,
                    this.font.plainSubstrByWidth(this.statusMessage.getString(), this.contentWidth() - 16),
                    this.contentLeft() + 8,
                    this.contentBottom() - 11,
                    DANGER,
                    false);
        } else if (this.loading) {
            graphics.drawString(
                    this.font,
                    Component.translatable("screen.delvefold.import.working"),
                    this.contentLeft() + 8,
                    this.contentBottom() - 11,
                    ACCENT,
                    false);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        for (RenderedGroup rendered : this.renderedGroups) {
            if (!rendered.icon().isEmpty()) {
                graphics.renderItem(rendered.icon(), rendered.x(), rendered.y());
            }
        }
    }

    private static ItemStack icon(GroupView group) {
        if (group.candidates().isEmpty()) return ItemStack.EMPTY;
        ResourceLocation id =
                ResourceLocation.tryParse(group.candidates().getFirst().blockId());
        if (id == null) return ItemStack.EMPTY;
        return BuiltInRegistries.BLOCK
                .getOptional(id)
                .map(block -> new ItemStack(block.asItem()))
                .orElse(ItemStack.EMPTY);
    }

    private static Component groupTooltip(GroupView group) {
        int importable = importableCandidateCount(group);
        int review = reviewCandidateCount(group);
        Component heading = importable == 0
                ? Component.translatable("screen.delvefold.import.group_all_review", review)
                : review > 0
                        ? Component.translatable("screen.delvefold.import.group_mixed", importable, review)
                        : Component.translatable("screen.delvefold.import.group_all_safe", importable);
        var tooltip = Component.empty().append(heading);
        for (var candidate : group.candidates()) {
            boolean skipped = candidate.hostKind() == HostKind.REVIEW_REQUIRED;
            tooltip.append(Component.literal("\n" + (skipped ? "× " : "+ ") + candidate.blockId()));
            if (!candidate.replaceTag().isBlank()) {
                tooltip.append(Component.literal(" → #" + candidate.replaceTag()));
            }
        }
        return tooltip;
    }

    private static Component diffTooltip(DiffView entry) {
        var tooltip = Component.empty();
        if (!entry.message().isBlank()) {
            tooltip.append(DelvefoldText.serverMessage(entry.message()));
        }
        appendBlockList(tooltip, "screen.delvefold.import.diff.added_blocks", entry.addedBlocks());
        appendBlockList(tooltip, "screen.delvefold.import.diff.skipped_blocks", entry.skippedBlocks());
        return tooltip;
    }

    private static void appendBlockList(
            net.minecraft.network.chat.MutableComponent tooltip, String headingKey, List<String> blockIds) {
        if (blockIds.isEmpty()) {
            return;
        }
        if (!tooltip.getString().isBlank()) {
            tooltip.append(Component.literal("\n"));
        }
        tooltip.append(Component.translatable(headingKey));
        for (String blockId : blockIds) {
            tooltip.append(Component.literal("\n  " + blockId));
        }
    }

    private static int importableCandidateCount(GroupView group) {
        return (int) group.candidates().stream()
                .filter(candidate -> candidate.hostKind() != HostKind.REVIEW_REQUIRED)
                .count();
    }

    private static int reviewCandidateCount(GroupView group) {
        return group.candidates().size() - importableCandidateCount(group);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= this.contentLeft()
                && mouseX < this.contentRight()
                && mouseY >= this.contentTop()
                && mouseY < this.contentBottom()
                && scrollY != 0.0D) {
            moveRows(scrollY > 0.0D ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.getFocused() instanceof EditBox) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            moveRows(1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            moveRows(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            moveRows(this.state.visibleRows());
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            moveRows(-this.state.visibleRows());
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME) {
            setLocalOffset(0);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_END) {
            setLocalOffset(this.state.maximumLocalOffset());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void moveRows(int rows) {
        if (rows == 0 || this.loading) {
            return;
        }
        boolean previewActive = this.state.preview() != null;
        OreImportScreenState.PageTransition transition = this.state.moveRows(rows);
        if (previewActive) {
            @Nullable PreviewView activePreview = this.state.preview();
            if (activePreview != null) {
                applyPreviewTransition(activePreview, transition);
            }
        } else {
            @Nullable ScanView activeScan = this.state.scan();
            if (activeScan != null) {
                applyScanTransition(activeScan, transition);
            }
        }
    }

    private void setLocalOffset(int offset) {
        if (this.state.setLocalOffset(offset)) {
            this.rebuildWidgets();
        }
    }

    @Override
    public Component getNarrationMessage() {
        @Nullable PreviewView activePreview = this.state.preview();
        if (activePreview != null) {
            Component state = profileIdIssue();
            if (state == null) {
                state = activePreview.valid()
                        ? Component.translatable("screen.delvefold.import.valid", activePreview.addedRuleCount())
                        : Component.translatable("screen.delvefold.import.invalid");
            }
            return Component.translatable(
                    "screen.delvefold.import.narration.preview", activePreview.totalDiffEntries(), state);
        }
        @Nullable ScanView activeScan = this.state.scan();
        int total = activeScan == null ? 0 : activeScan.totalGroups();
        return Component.translatable("screen.delvefold.import.narration.scan", total, this.state.selectedCount());
    }

    private static Component diffLabel(DiffStatus status) {
        return Component.translatable(
                "screen.delvefold.import.diff." + status.name().toLowerCase(Locale.ROOT));
    }

    private static Component issueLine(ValidationIssueView issue) {
        return DelvefoldText.serverMessage(issue.message());
    }

    private static Style diffStyle(DiffStatus status) {
        return switch (status) {
            case ADDED, PARTIALLY_ADDED -> Style.TOGGLE_ON;
            case SKIPPED_COVERED -> Style.GHOST;
            case SKIPPED_REVIEW_REQUIRED -> Style.TOGGLE_OFF;
        };
    }

    private static String format(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.005D) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private record RenderedGroup(ItemStack icon, int x, int y) {}
}
