package com.nightsta69.delvefold.client.gui;

import com.nightsta69.delvefold.client.DelvefoldClientRequests;
import com.nightsta69.delvefold.client.gui.widget.DelvefoldButton.Style;
import com.nightsta69.delvefold.config.importer.OreImportModels.Group;
import com.nightsta69.delvefold.network.ProtocolLimits;
import com.nightsta69.delvefold.network.model.ActionStatus;
import com.nightsta69.delvefold.network.model.AdminSnapshot;
import com.nightsta69.delvefold.network.model.OreLibraryView;
import com.nightsta69.delvefold.network.payload.ActionResultPayload;
import com.nightsta69.delvefold.network.payload.AddOreFamiliesPayload;
import com.nightsta69.delvefold.network.payload.OreLibraryRequestPayload;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/** Server-paged material-family picker with batch selection and exact-ID fallback. */
public final class DelvefoldOrePickerScreen extends DelvefoldScreen {
    private static final int SEARCH_DELAY_TICKS = 6;
    private static final int ROW_HEIGHT = 26;
    private static final int ROW_GAP = 2;

    private final Screen parent;
    private final OreLibraryPickerState state = new OreLibraryPickerState();
    private final List<RenderedFamily> renderedFamilies = new ArrayList<>();
    private final List<Button> variantButtons = new ArrayList<>();
    private Map<String, Group> localGroups = Map.of();
    private @Nullable OreLibraryView library;
    private @Nullable EditBox searchBox;
    private @Nullable Button addSelectedButton;
    private @Nullable Button exactIdButton;
    private boolean initialRequestSent;
    private boolean applyingServerView;
    private boolean loading;
    private boolean catalogRequestPending;
    private boolean catalogRecoveryAttempted;
    private boolean retainSelectionsAcrossCatalogRefresh;
    private boolean enterRequestedPageAtEnd;
    private boolean queuedEnterAtEnd;
    private boolean restoreInitialLocalOffset;
    private int searchDelay;
    private int localOffset;
    private int queuedPage = -1;
    private int visibleRows = 1;
    private int requestedInitialPage;
    private int requestedInitialLocalOffset;
    private Component localStatus = Component.empty();

    /**
     * Creates a picker that requests its first authoritative family page after responsive initialization.
     *
     * @param parent screen restored when the player leaves the picker
     * @param snapshot immutable administration state and expected ore revision
     */
    public DelvefoldOrePickerScreen(Screen parent, AdminSnapshot snapshot) {
        super(Component.translatable("screen.delvefold.ore_picker.title"), snapshot);
        this.parent = parent;
    }

    @Override
    protected int preferredPanelWidth() {
        return 720;
    }

    @Override
    protected int preferredPanelHeight() {
        return 430;
    }

    @Override
    protected void initPanel() {
        this.renderedFamilies.clear();
        this.variantButtons.clear();
        int x = this.contentLeft();
        int y = this.contentTop() + 12;
        int width = this.contentWidth();
        boolean compact = width < 500 || this.contentBottom() - this.contentTop() < 260;
        int gap = 6;
        int exactWidth = compact ? 88 : 104;
        int configuredWidth = compact ? width - exactWidth - gap : 154;
        int searchWidth = compact ? width : width - configuredWidth - exactWidth - gap * 2;

        EditBox search = this.addRenderableWidget(new EditBox(
                this.font, x, y, searchWidth, 20, Component.translatable("screen.delvefold.ore_picker.search")));
        this.searchBox = search;
        search.setMaxLength(ProtocolLimits.MAX_ORE_LIBRARY_QUERY_LENGTH);
        search.setHint(Component.translatable("screen.delvefold.ore_picker.search_hint"));
        search.setValue(this.state.searchQuery());
        search.setResponder(value -> {
            if (this.applyingServerView) {
                return;
            }
            this.state.setSearchQuery(value);
            this.searchDelay = SEARCH_DELAY_TICKS;
            this.localStatus = Component.empty();
        });

        int toggleY = compact ? y + 22 : y;
        int toggleX = compact ? x : x + searchWidth + gap;
        this.addButton(
                toggleX,
                toggleY,
                configuredWidth,
                20,
                showConfiguredLabel(),
                this.state.showConfigured() ? Style.TOGGLE_ON : Style.TOGGLE_OFF,
                button -> {
                    this.state.toggleShowConfigured();
                    button.setMessage(showConfiguredLabel());
                    setButtonStyle(button, this.state.showConfigured() ? Style.TOGGLE_ON : Style.TOGGLE_OFF);
                    requestLibrary(0);
                });
        this.exactIdButton = this.addButton(
                toggleX + configuredWidth + gap,
                toggleY,
                exactWidth,
                20,
                Component.translatable("screen.delvefold.ore_picker.exact_id"),
                Style.SECONDARY,
                button -> chooseTypedId());
        this.exactIdButton.active = !this.loading;

        int selectionY = toggleY + (compact ? 22 : 26);
        int selectionWidth = Math.max(72, (width - gap) / 2);
        this.addButton(
                x,
                selectionY,
                selectionWidth,
                20,
                Component.translatable("screen.delvefold.ore_picker.select_visible"),
                Style.SECONDARY,
                button -> {
                    this.state.selectVisible(visiblePageFamilies());
                    rebuildWidgets();
                });
        this.addButton(
                x + selectionWidth + gap,
                selectionY,
                width - selectionWidth - gap,
                20,
                Component.translatable("screen.delvefold.ore_picker.clear"),
                Style.GHOST,
                button -> {
                    this.state.clearSelection();
                    rebuildWidgets();
                });

        int rowsTop = selectionY + (compact ? 23 : 27);
        int pagerY = this.footerButtonY() - 24;
        int availableRows = Math.max(ROW_HEIGHT, pagerY - rowsTop - 2);
        this.visibleRows = Math.max(1, availableRows / (ROW_HEIGHT + ROW_GAP));
        List<OreLibraryPickerState.Family> pageFamilies = this.state.pageFamilies();
        this.localOffset = Math.clamp(
                this.localOffset,
                0,
                OreLibraryPickerState.lastLocalWindowOffset(pageFamilies.size(), this.visibleRows));
        int end = Math.min(pageFamilies.size(), this.localOffset + this.visibleRows);
        for (int index = this.localOffset; index < end; index++) {
            addFamilyRow(pageFamilies.get(index), rowsTop + (index - this.localOffset) * (ROW_HEIGHT + ROW_GAP));
        }

        int pagerWidth = Math.min(84, Math.max(56, width / 4));
        Button previous = this.addButton(
                x,
                pagerY,
                pagerWidth,
                20,
                Component.translatable("screen.delvefold.previous"),
                Style.GHOST,
                button -> previousPage());
        Button next = this.addButton(
                x + width - pagerWidth,
                pagerY,
                pagerWidth,
                20,
                Component.translatable("screen.delvefold.next"),
                Style.GHOST,
                button -> nextPage());
        OreLibraryView active = this.library;
        previous.active = active != null && (this.localOffset > 0 || active.page() > 0) && !this.loading;
        next.active = active != null
                && (end < pageFamilies.size() || active.page() + 1 < active.pageCount())
                && !this.loading;

        this.addButton(
                x,
                this.footerButtonY(),
                76,
                22,
                Component.translatable("gui.back"),
                Style.GHOST,
                button -> this.minecraft.setScreen(this.parent));
        this.addSelectedButton = this.addButton(
                this.contentRight() - 148,
                this.footerButtonY(),
                148,
                22,
                addSelectedLabel(),
                Style.PRIMARY,
                button -> addSelected());
        this.addSelectedButton.active = active != null && this.state.selectedCount() > 0 && !this.loading;

        if (!this.initialRequestSent) {
            this.initialRequestSent = true;
            requestLibrary(this.requestedInitialPage);
        }
        this.setInitialFocus(search);
    }

    /**
     * Installs a validated server page while retaining selections from other pages of the same catalog.
     *
     * @param replacement authoritative bounded family page
     */
    public void acceptLibrary(OreLibraryView replacement) {
        this.library = replacement;
        this.loading = false;
        this.catalogRequestPending = false;
        this.catalogRecoveryAttempted = false;
        boolean enterAtEnd = this.enterRequestedPageAtEnd;
        this.enterRequestedPageAtEnd = false;
        int deferredPage = this.queuedPage;
        boolean deferredEnterAtEnd = this.queuedEnterAtEnd;
        this.queuedPage = -1;
        this.queuedEnterAtEnd = false;
        if (!OreLibraryPickerState.responseMatchesFilters(
                this.state.searchQuery(),
                this.state.showConfigured(),
                replacement.query(),
                replacement.showConfigured())) {
            this.searchDelay = 0;
            requestLibrary(0);
            return;
        }
        if (deferredPage >= 0
                && replacement.page() != OreLibraryPickerState.clampServerPage(deferredPage, replacement.pageCount())) {
            requestLibrary(deferredPage, deferredEnterAtEnd);
            return;
        }
        if (deferredPage >= 0) {
            enterAtEnd = deferredEnterAtEnd;
        }
        if (this.localGroups.isEmpty()) {
            this.localGroups = OreFamilyCatalog.discoverInstalled();
        }
        List<OreLibraryPickerState.Family> presented = replacement.families().stream()
                .map(summary -> OreFamilyCatalog.present(summary, this.localGroups.get(summary.id())))
                .toList();
        this.state.acceptPage(replacement.catalogToken(), presented, this.retainSelectionsAcrossCatalogRefresh);
        this.retainSelectionsAcrossCatalogRefresh = false;
        if (this.restoreInitialLocalOffset) {
            this.localOffset = OreLibraryPickerState.clampLocalWindowOffset(
                    this.requestedInitialLocalOffset, presented.size(), this.visibleRows);
            this.restoreInitialLocalOffset = false;
        } else {
            this.localOffset =
                    enterAtEnd ? OreLibraryPickerState.lastLocalWindowOffset(presented.size(), this.visibleRows) : 0;
        }
        this.applyingServerView = true;
        this.state.setSearchQuery(replacement.query());
        this.state.setPage(replacement.page(), replacement.pageCount());
        if (this.state.showConfigured() != replacement.showConfigured()) {
            this.state.toggleShowConfigured();
        }
        EditBox search = this.searchBox;
        if (search != null) {
            search.setValue(replacement.query());
        }
        this.applyingServerView = false;
        this.localStatus = replacement.truncated()
                ? Component.translatable("screen.delvefold.ore_picker.truncated")
                : Component.empty();
        if (this.minecraft != null) {
            this.rebuildWidgets();
        }
    }

    /**
     * Returns a replacement picker after a configuration refresh while retaining search, filter, and page context.
     *
     * @param updatedSnapshot authoritative post-mutation administration snapshot
     * @return replacement picker ready to request the corresponding refreshed catalog page
     */
    public DelvefoldOrePickerScreen refreshed(AdminSnapshot updatedSnapshot) {
        Screen refreshedParent = this.parent instanceof DelvefoldDashboardScreen dashboard
                ? dashboard.refreshed(updatedSnapshot)
                : this.parent;
        DelvefoldOrePickerScreen refreshed = new DelvefoldOrePickerScreen(refreshedParent, updatedSnapshot);
        refreshed.state.setSearchQuery(this.state.searchQuery());
        if (this.state.showConfigured()) {
            refreshed.state.toggleShowConfigured();
        }
        OreLibraryView active = this.library;
        refreshed.requestedInitialPage = active == null ? 0 : active.page();
        refreshed.requestedInitialLocalOffset = this.localOffset;
        refreshed.restoreInitialLocalOffset = true;
        return refreshed;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.searchDelay > 0 && --this.searchDelay == 0) {
            requestLibrary(0);
        }
    }

    @Override
    public void handleActionResult(ActionResultPayload payload) {
        OreLibraryPickerState.CatalogRecovery recovery = this.catalogRequestPending
                        && payload.status() != ActionStatus.ACCEPTED
                        && !this.catalogRecoveryAttempted
                ? OreLibraryPickerState.catalogRecovery(payload.message())
                : OreLibraryPickerState.CatalogRecovery.NONE;
        this.catalogRequestPending = false;
        this.loading = false;
        this.enterRequestedPageAtEnd = false;
        this.localStatus = DelvefoldText.serverMessage(payload.message());
        if (recovery.recoverable()) {
            restartCatalog(recovery.retainSelections());
            return;
        }
        if (payload.status() != ActionStatus.ACCEPTED) {
            this.retainSelectionsAcrossCatalogRefresh = false;
            this.queuedPage = -1;
            this.queuedEnterAtEnd = false;
            if (this.minecraft != null) {
                this.rebuildWidgets();
            }
        }
    }

    private void addFamilyRow(OreLibraryPickerState.Family family, int y) {
        int x = this.contentLeft();
        int width = this.contentWidth();
        int iconWidth = 24;
        int detailsWidth = Math.min(96, Math.max(66, width / 5));
        int gap = 4;
        boolean selected = this.state.selected(family.id());
        Component marker = Component.translatable(
                selected ? "screen.delvefold.status.selected" : "screen.delvefold.status.not_selected");
        Component label = Component.translatable(
                "screen.delvefold.ore_picker.family_row",
                marker,
                family.displayName(),
                family.providerNamespaces().size(),
                family.candidates().size());
        Button familyButton = this.addButton(
                x + iconWidth,
                y,
                width - iconWidth - detailsWidth - gap,
                ROW_HEIGHT,
                label,
                family.configured() ? Style.GHOST : selected ? Style.TOGGLE_ON : Style.TOGGLE_OFF,
                button -> {
                    if (this.state.toggleFamily(family.id())) {
                        rebuildWidgets();
                    }
                });
        familyButton.active = !family.configured();
        OreLibraryView.Family summary = summary(family.id());
        if (summary != null) {
            familyButton.setTooltip(Tooltip.create(Component.translatable(
                    "screen.delvefold.ore_picker.family_tooltip",
                    summary.preferredBlockId(),
                    summary.providerCount(),
                    summary.candidateCount(),
                    summary.importableCandidateCount(),
                    familyReviewStatus(summary),
                    Component.translatable(
                            summary.overflow()
                                    ? "screen.delvefold.status.overflow"
                                    : "screen.delvefold.status.complete"))));
            familyButton.setTooltipDelay(Duration.ofMillis(250));
        }
        Button details = this.addButton(
                x + width - detailsWidth,
                y,
                detailsWidth,
                ROW_HEIGHT,
                Component.translatable("screen.delvefold.ore_picker.edit_variants"),
                Style.SECONDARY,
                button -> editFamily(family));
        details.active = !this.loading && !family.configured() && this.state.draft(family.id()) != null;
        this.variantButtons.add(details);
        this.renderedFamilies.add(new RenderedFamily(icon(family), x + 3, y + 5));
    }

    private static Component familyReviewStatus(OreLibraryView.Family family) {
        if (family.reviewRequired()) {
            return Component.translatable("screen.delvefold.status.review_required");
        }
        if (family.importableCandidateCount() < family.candidateCount()) {
            return Component.translatable("screen.delvefold.status.variants_need_review");
        }
        return Component.translatable("screen.delvefold.status.ready");
    }

    private void editFamily(OreLibraryPickerState.Family family) {
        AdminSnapshot.@Nullable OreRuleDraft draft = this.state.draft(family.id());
        if (draft == null) {
            this.localStatus = Component.translatable("screen.delvefold.ore_picker.no_importable_variants");
            return;
        }
        this.minecraft.setScreen(new DelvefoldOreRuleWizardScreen(this, this.snapshot, draft, family.candidates()));
    }

    private void addSelected() {
        OreLibraryView active = this.library;
        List<String> selected = this.state.selectedFamilyIds();
        if (active == null || selected.isEmpty()) {
            return;
        }
        this.loading = true;
        this.catalogRequestPending = true;
        this.localStatus = Component.translatable("screen.delvefold.ore_picker.adding", selected.size());
        if (this.addSelectedButton != null) {
            this.addSelectedButton.active = false;
        }
        DelvefoldClientRequests.send(
                new AddOreFamiliesPayload(active.expectedOreRevision(), active.catalogToken(), selected));
    }

    private void chooseTypedId() {
        EditBox search = this.searchBox;
        ResourceLocation id = ResourceLocation.tryParse(
                search == null ? "" : search.getValue().trim());
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            this.localStatus = Component.translatable("screen.delvefold.ore_picker.no_exact_match");
            return;
        }
        AdminSnapshot.OreRuleDraft draft = AdminSnapshot.OreRuleDraft.createDefault(
                id.toString(), OreRuleWizardDraftState.inferredHost(id.toString()));
        this.minecraft.setScreen(new DelvefoldOreRuleWizardScreen(this, this.snapshot, draft));
    }

    private void previousPage() {
        if (this.loading) {
            return;
        }
        if (this.localOffset > 0) {
            this.localOffset = Math.max(0, this.localOffset - this.visibleRows);
            rebuildWidgets();
            return;
        }
        OreLibraryView active = this.library;
        if (active != null && active.page() > 0) {
            requestLibrary(active.page() - 1, true);
        }
    }

    private void nextPage() {
        if (this.loading) {
            return;
        }
        List<OreLibraryPickerState.Family> pageFamilies = this.state.pageFamilies();
        if (this.localOffset + this.visibleRows < pageFamilies.size()) {
            this.localOffset += this.visibleRows;
            rebuildWidgets();
            return;
        }
        OreLibraryView active = this.library;
        if (active != null && active.page() + 1 < active.pageCount()) {
            requestLibrary(active.page() + 1);
        }
    }

    private void requestLibrary(int page) {
        requestLibrary(page, false);
    }

    private void requestLibrary(int page, boolean enterAtEnd) {
        if (this.loading) {
            this.queuedPage = Math.max(0, page);
            this.queuedEnterAtEnd = enterAtEnd;
            return;
        }
        OreLibraryView active = this.library;
        this.loading = true;
        this.catalogRequestPending = true;
        this.enterRequestedPageAtEnd = enterAtEnd;
        this.localStatus = Component.translatable("screen.delvefold.ore_picker.loading");
        if (this.exactIdButton != null) {
            this.exactIdButton.active = false;
        }
        this.variantButtons.forEach(button -> button.active = false);
        DelvefoldClientRequests.requestOreLibrary(new OreLibraryRequestPayload(
                this.snapshot.oreRevision(),
                active == null ? "" : active.catalogToken(),
                Math.max(0, page),
                this.state.searchQuery(),
                this.state.showConfigured()));
    }

    private void restartCatalog(boolean retainSelections) {
        OreLibraryView active = this.library;
        int restartPage = active == null ? this.state.page() : active.page();
        this.catalogRecoveryAttempted = true;
        this.retainSelectionsAcrossCatalogRefresh = retainSelections;
        this.library = null;
        this.localGroups = Map.of();
        this.queuedPage = -1;
        this.queuedEnterAtEnd = false;
        this.requestedInitialLocalOffset = this.localOffset;
        this.restoreInitialLocalOffset = true;
        requestLibrary(restartPage);
    }

    private List<OreLibraryPickerState.Family> visiblePageFamilies() {
        List<OreLibraryPickerState.Family> pageFamilies = this.state.pageFamilies();
        int end = Math.min(pageFamilies.size(), this.localOffset + this.visibleRows);
        return pageFamilies.subList(Math.min(this.localOffset, end), end);
    }

    private OreLibraryView.@Nullable Family summary(String familyId) {
        OreLibraryView active = this.library;
        return active == null
                ? null
                : active.families().stream()
                        .filter(family -> family.id().equals(familyId))
                        .findFirst()
                        .orElse(null);
    }

    private ItemStack icon(OreLibraryPickerState.Family family) {
        OreLibraryView.Family summary = summary(family.id());
        ResourceLocation id = ResourceLocation.tryParse(
                summary == null ? family.candidates().getFirst().blockId() : summary.preferredBlockId());
        Block block = id == null ? null : BuiltInRegistries.BLOCK.get(id);
        return block == null || Items.AIR.equals(block.asItem()) ? new ItemStack(Items.IRON_ORE) : new ItemStack(block);
    }

    private Component showConfiguredLabel() {
        return Component.translatable(
                "screen.delvefold.ore_picker.show_configured",
                Component.translatable(
                        this.state.showConfigured() ? "screen.delvefold.status.on" : "screen.delvefold.status.off"));
    }

    private Component addSelectedLabel() {
        return Component.translatable("screen.delvefold.ore_picker.add_selected", this.state.selectedCount());
    }

    @Override
    protected void renderPanelContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.drawString(
                this.font,
                Component.translatable("screen.delvefold.ore_picker.material_families"),
                this.contentLeft(),
                this.contentTop(),
                MUTED_TEXT,
                false);
        for (RenderedFamily rendered : this.renderedFamilies) {
            graphics.renderItem(rendered.icon(), rendered.x(), rendered.y());
        }
        OreLibraryView active = this.library;
        List<OreLibraryPickerState.Family> pageFamilies = this.state.pageFamilies();
        int visibleStart = pageFamilies.isEmpty()
                ? 0
                : active == null
                        ? this.localOffset + 1
                        : active.page() * ProtocolLimits.MAX_ORE_LIBRARY_FAMILIES_PER_PAGE + this.localOffset + 1;
        int visibleEnd = active == null
                ? Math.min(pageFamilies.size(), this.localOffset + this.visibleRows)
                : active.page() * ProtocolLimits.MAX_ORE_LIBRARY_FAMILIES_PER_PAGE
                        + Math.min(pageFamilies.size(), this.localOffset + this.visibleRows);
        Component page = active == null
                ? Component.translatable("screen.delvefold.ore_picker.loading")
                : Component.translatable(
                        "screen.delvefold.ore_picker.family_results",
                        active.totalFamilies(),
                        visibleStart,
                        visibleEnd,
                        this.state.selectedCount());
        Component footerLine = this.localStatus.getString().isEmpty() ? page : this.localStatus;
        var clipped = this.font
                .split(footerLine, Math.max(1, this.contentWidth() - 8))
                .getFirst();
        graphics.drawString(
                this.font,
                clipped,
                this.panelLeft + (this.panelWidth - this.font.width(clipped)) / 2,
                this.footerButtonY() - 19,
                this.localStatus.getString().isEmpty() ? MUTED_TEXT : this.loading ? ACCENT : WARNING,
                false);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public Component getNarrationMessage() {
        OreLibraryView active = this.library;
        return Component.translatable(
                "screen.delvefold.ore_picker.family_narration",
                active == null ? 0 : active.totalFamilies(),
                active == null ? 1 : active.page() + 1,
                active == null ? 1 : active.pageCount(),
                this.state.selectedCount(),
                Component.translatable(
                        this.state.showConfigured() ? "screen.delvefold.status.on" : "screen.delvefold.status.off"));
    }

    private record RenderedFamily(ItemStack icon, int x, int y) {}
}
