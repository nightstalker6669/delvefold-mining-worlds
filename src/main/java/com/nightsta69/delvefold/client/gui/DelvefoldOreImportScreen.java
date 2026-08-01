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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/** Guided, server-authoritative importer for conventional and ore-like registered blocks. */
public final class DelvefoldOreImportScreen extends DelvefoldScreen {
    private static final Pattern PROFILE_ID = Pattern.compile("[a-z0-9_.-]+");
    private static final int ROW_HEIGHT = 28;

    private final Screen parent;
    private final Set<String> selectedGroupIds = new LinkedHashSet<>();
    private final List<RenderedGroup> renderedGroups = new ArrayList<>();
    private ScanView scan;
    private PreviewView preview;
    private boolean includeVanilla;
    private boolean initialScanRequested;
    private boolean loading;
    private int localOffset;
    private int visibleRows = 1;
    private String targetProfileId = "modded_ores";
    private Component statusMessage = Component.empty();
    private Button createButton;

    public DelvefoldOreImportScreen(Screen parent, AdminSnapshot snapshot) {
        super(Component.translatable("screen.delvefold.import.title"), snapshot);
        this.parent = parent;
    }

    public void acceptScan(ScanView replacement) {
        boolean newSession = this.scan == null || !this.scan.scanToken().equals(replacement.scanToken());
        this.scan = replacement;
        this.preview = null;
        this.loading = false;
        this.statusMessage = Component.empty();
        this.localOffset = 0;
        if (newSession) {
            this.selectedGroupIds.clear();
        }
        if (this.minecraft != null) {
            this.rebuildWidgets();
        }
    }

    public void acceptPreview(PreviewView replacement) {
        this.preview = replacement;
        this.loading = false;
        this.statusMessage = Component.empty();
        this.localOffset = 0;
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
        if (this.preview == null) {
            initScanPanel();
        } else {
            initPreviewPanel();
        }
    }

    private void initScanPanel() {
        int footerY = this.panelTop + this.panelHeight - 29;
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

        int controlsY = this.contentTop() + 24;
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

        if (this.scan == null) {
            previous.active = false;
            next.active = false;
            previewButton.active = false;
            if (!this.initialScanRequested) {
                this.initialScanRequested = true;
                requestScan();
            }
            return;
        }

        int groupTop = controlsY + (this.scan.truncated() ? 54 : 39);
        int available = Math.max(ROW_HEIGHT, this.contentBottom() - groupTop - 14);
        this.visibleRows = Math.max(1, Math.min(this.scan.groups().size(), available / ROW_HEIGHT));
        this.localOffset =
                Math.clamp(this.localOffset, 0, Math.max(0, this.scan.groups().size() - this.visibleRows));
        int end = Math.min(this.scan.groups().size(), this.localOffset + this.visibleRows);
        for (int index = this.localOffset; index < end; index++) {
            GroupView group = this.scan.groups().get(index);
            int rowY = groupTop + (index - this.localOffset) * ROW_HEIGHT;
            boolean selected = this.selectedGroupIds.contains(group.id());
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
            this.renderedGroups.add(new RenderedGroup(group, this.contentLeft() + 5, rowY + 4));
        }

        previous.active = this.localOffset > 0 || this.scan.page() > 0;
        next.active = this.localOffset + this.visibleRows < this.scan.groups().size()
                || this.scan.page() + 1 < this.scan.pageCount();
        previewButton.active = !this.loading && !this.selectedGroupIds.isEmpty();
    }

    private void initPreviewPanel() {
        int footerY = this.panelTop + this.panelHeight - 29;
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
        this.createButton = this.addButton(
                this.contentLeft() + (slot + 4) * 3,
                footerY,
                this.contentWidth() - slot * 3 - 12,
                22,
                Component.translatable("screen.delvefold.import.create"),
                Style.PRIMARY,
                button -> createProfile());

        int fieldY = this.contentTop() + 23;
        EditBox profile = this.addRenderableWidget(new EditBox(
                this.font,
                this.contentLeft(),
                fieldY,
                Math.max(1, this.contentWidth() / 2),
                20,
                Component.translatable("screen.delvefold.import.profile_id")));
        profile.setMaxLength(ProtocolLimits.ID_LENGTH);
        profile.setValue(this.targetProfileId);
        profile.setHint(Component.translatable("screen.delvefold.import.profile_hint"));
        profile.setTextColor(TEXT);
        profile.setResponder(value -> {
            this.targetProfileId = value.trim();
            updateCreateButton();
        });

        int diffTop = fieldY + (this.preview.truncated() ? 70 : 58);
        int available = Math.max(24, this.contentBottom() - diffTop - 14);
        this.visibleRows = Math.max(1, Math.min(this.preview.diff().size(), available / 24));
        this.localOffset =
                Math.clamp(this.localOffset, 0, Math.max(0, this.preview.diff().size() - this.visibleRows));
        int end = Math.min(this.preview.diff().size(), this.localOffset + this.visibleRows);
        for (int index = this.localOffset; index < end; index++) {
            DiffView entry = this.preview.diff().get(index);
            int rowY = diffTop + (index - this.localOffset) * 24;
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

        previous.active = this.localOffset > 0 || this.preview.page() > 0;
        next.active = this.localOffset + this.visibleRows < this.preview.diff().size()
                || this.preview.page() + 1 < this.preview.pageCount();
        if (!this.preview.issues().isEmpty()) {
            var issueText = Component.empty();
            this.preview.issues().stream().limit(6).forEach(issue -> {
                if (!issueText.getString().isEmpty()) {
                    issueText.append("\n");
                }
                issueText.append(issueLine(issue));
            });
            this.createButton.setTooltip(Tooltip.create(issueText));
        }
        updateCreateButton();
    }

    private void requestScan() {
        this.loading = true;
        this.statusMessage = Component.empty();
        DelvefoldClientRequests.requestOreImportScan(this.snapshot.oreRevision(), this.includeVanilla);
    }

    private void requestPreview() {
        if (this.scan == null || this.selectedGroupIds.isEmpty()) {
            return;
        }
        this.loading = true;
        List<String> selected = this.selectedGroupIds.stream().sorted().toList();
        DelvefoldClientRequests.requestOreImportPreview(this.scan.scanToken(), selected);
    }

    private void toggleGroup(GroupView group) {
        if (importableCandidateCount(group) == 0) {
            return;
        }
        if (!this.selectedGroupIds.remove(group.id())) {
            this.selectedGroupIds.add(group.id());
        }
        this.rebuildWidgets();
    }

    private void previousScanPage() {
        if (this.scan == null) return;
        if (this.localOffset > 0) {
            this.localOffset = Math.max(0, this.localOffset - this.visibleRows);
            this.rebuildWidgets();
        } else if (this.scan.page() > 0) {
            this.loading = true;
            DelvefoldClientRequests.requestOreImportScanPage(this.scan.scanToken(), this.scan.page() - 1);
        }
    }

    private void nextScanPage() {
        if (this.scan == null) return;
        if (this.localOffset + this.visibleRows < this.scan.groups().size()) {
            this.localOffset += this.visibleRows;
            this.rebuildWidgets();
        } else if (this.scan.page() + 1 < this.scan.pageCount()) {
            this.loading = true;
            DelvefoldClientRequests.requestOreImportScanPage(this.scan.scanToken(), this.scan.page() + 1);
        }
    }

    private void previousPreviewPage() {
        if (this.preview == null) return;
        if (this.localOffset > 0) {
            this.localOffset = Math.max(0, this.localOffset - this.visibleRows);
            this.rebuildWidgets();
        } else if (this.preview.page() > 0) {
            this.loading = true;
            DelvefoldClientRequests.requestOreImportPreviewPage(this.preview.commitToken(), this.preview.page() - 1);
        }
    }

    private void nextPreviewPage() {
        if (this.preview == null) return;
        if (this.localOffset + this.visibleRows < this.preview.diff().size()) {
            this.localOffset += this.visibleRows;
            this.rebuildWidgets();
        } else if (this.preview.page() + 1 < this.preview.pageCount()) {
            this.loading = true;
            DelvefoldClientRequests.requestOreImportPreviewPage(this.preview.commitToken(), this.preview.page() + 1);
        }
    }

    private void backToScan() {
        this.preview = null;
        this.localOffset = 0;
        this.loading = false;
        this.statusMessage = Component.empty();
        this.rebuildWidgets();
    }

    private void createProfile() {
        if (this.preview == null || !canCreate()) {
            return;
        }
        this.loading = true;
        updateCreateButton();
        DelvefoldClientRequests.createImportedOreProfile(this.preview.commitToken(), this.targetProfileId.trim());
    }

    private void updateCreateButton() {
        if (this.createButton != null) {
            this.createButton.active = !this.loading && canCreate();
        }
    }

    private boolean canCreate() {
        return this.preview != null
                && this.preview.valid()
                && this.preview.addedRuleCount() > 0
                && profileIdIssue() == null;
    }

    private Component profileIdIssue() {
        String id = this.targetProfileId.trim();
        if (id.isEmpty()) {
            return Component.translatable("screen.delvefold.import.profile_error.empty");
        }
        if (id.length() > ProtocolLimits.ID_LENGTH) {
            return Component.translatable("screen.delvefold.import.profile_error.too_long", ProtocolLimits.ID_LENGTH);
        }
        if (!PROFILE_ID.matcher(id).matches()) {
            return Component.translatable("screen.delvefold.import.profile_error.invalid");
        }
        if (this.snapshot.profiles().stream().anyMatch(profile -> profile.id().equals(id))) {
            return Component.translatable("screen.delvefold.import.profile_error.exists", id);
        }
        return null;
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
        if (this.preview == null) {
            renderScan(graphics, x, y);
        } else {
            renderPreview(graphics, x, y);
        }
    }

    private void renderScan(GuiGraphics graphics, int x, int y) {
        this.drawSectionTitle(graphics, Component.translatable("screen.delvefold.import.discovery"), x + 9, y + 7);
        if (this.scan == null) {
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
                this.scan.totalGroups(),
                this.scan.scannedBlocks(),
                this.scan.baseProfileId());
        graphics.drawString(
                this.font,
                this.font.plainSubstrByWidth(summary.getString(), this.contentWidth() - 20),
                x + 10,
                y + 48,
                DIM_TEXT,
                false);
        Component selected = Component.translatable("screen.delvefold.import.selected", this.selectedGroupIds.size());
        graphics.drawString(
                this.font, selected, x + this.contentWidth() - this.font.width(selected) - 10, y + 8, ACCENT, false);
        if (this.scan.groups().isEmpty()) {
            graphics.drawWordWrap(
                    this.font,
                    Component.translatable("screen.delvefold.import.no_groups"),
                    x + 10,
                    y + 72,
                    this.contentWidth() - 20,
                    MUTED_TEXT);
        }
        if (this.scan.truncated()) {
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
        this.drawSectionTitle(graphics, Component.translatable("screen.delvefold.import.preview_title"), x + 9, y + 7);
        Component page = Component.translatable(
                "screen.delvefold.import.page", this.preview.page() + 1, this.preview.pageCount());
        graphics.drawString(
                this.font, page, x + this.contentWidth() - this.font.width(page) - 9, y + 8, DIM_TEXT, false);

        Component profileIssue = profileIdIssue();
        Component validity = profileIssue != null
                ? profileIssue
                : this.preview.valid()
                        ? Component.translatable("screen.delvefold.import.valid", this.preview.addedRuleCount())
                        : Component.translatable("screen.delvefold.import.invalid");
        int validityColor = profileIssue == null && this.preview.valid() ? SUCCESS : DANGER;
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
                        / Math.max(1, this.preview.workloads().size()));
        for (int index = 0; index < this.preview.workloads().size(); index++) {
            TerrainDeltaView workload = this.preview.workloads().get(index);
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
        if (this.preview.truncated()) {
            Component warning = Component.translatable("screen.delvefold.import.preview_truncated");
            graphics.drawString(
                    this.font,
                    this.font.plainSubstrByWidth(warning.getString(), this.contentWidth() - 16),
                    x + 8,
                    y + 74,
                    WARNING,
                    false);
        }
        if (!this.preview.issues().isEmpty()) {
            Component issue = issueLine(this.preview.issues().getFirst());
            graphics.drawString(
                    this.font,
                    this.font.plainSubstrByWidth(issue.getString(), this.contentWidth() - 16),
                    x + 8,
                    this.contentBottom() - 11,
                    this.preview.valid() ? WARNING : DANGER,
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
            ItemStack icon = icon(rendered.group());
            if (!icon.isEmpty()) {
                graphics.renderItem(icon, rendered.x(), rendered.y());
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
            moveRows(this.visibleRows);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            moveRows(-this.visibleRows);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME) {
            setLocalOffset(0);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_END) {
            setLocalOffset(maximumLocalOffset());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void moveRows(int rows) {
        if (rows == 0 || this.loading) {
            return;
        }
        int next = Math.clamp(this.localOffset + rows, 0, maximumLocalOffset());
        if (next != this.localOffset) {
            setLocalOffset(next);
            return;
        }
        if (rows < 0) {
            if (this.preview == null) previousScanPage();
            else previousPreviewPage();
        } else {
            if (this.preview == null) nextScanPage();
            else nextPreviewPage();
        }
    }

    private int maximumLocalOffset() {
        int size = this.preview == null
                ? this.scan == null ? 0 : this.scan.groups().size()
                : this.preview.diff().size();
        return Math.max(0, size - this.visibleRows);
    }

    private void setLocalOffset(int offset) {
        int bounded = Math.clamp(offset, 0, maximumLocalOffset());
        if (bounded != this.localOffset) {
            this.localOffset = bounded;
            this.rebuildWidgets();
        }
    }

    @Override
    public Component getNarrationMessage() {
        if (this.preview != null) {
            Component state = profileIdIssue();
            if (state == null) {
                state = this.preview.valid()
                        ? Component.translatable("screen.delvefold.import.valid", this.preview.addedRuleCount())
                        : Component.translatable("screen.delvefold.import.invalid");
            }
            return Component.translatable(
                    "screen.delvefold.import.narration.preview", this.preview.totalDiffEntries(), state);
        }
        int total = this.scan == null ? 0 : this.scan.totalGroups();
        return Component.translatable("screen.delvefold.import.narration.scan", total, this.selectedGroupIds.size());
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

    private record RenderedGroup(GroupView group, int x, int y) {}
}
