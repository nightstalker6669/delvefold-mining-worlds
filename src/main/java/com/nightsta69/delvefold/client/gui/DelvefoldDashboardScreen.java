package com.nightsta69.delvefold.client.gui;

import com.nightsta69.delvefold.client.DelvefoldClientRequests;
import com.nightsta69.delvefold.client.gui.widget.DelvefoldButton.Style;
import com.nightsta69.delvefold.config.model.GameplayPreset;
import com.nightsta69.delvefold.config.model.GameplaySettings;
import com.nightsta69.delvefold.config.model.GeologyTheme;
import com.nightsta69.delvefold.config.model.LandmarkPreset;
import com.nightsta69.delvefold.config.model.PortalRoutingMode;
import com.nightsta69.delvefold.config.model.RenewalSeedMode;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.TerrainVariant;
import com.nightsta69.delvefold.network.model.AdminOperation;
import com.nightsta69.delvefold.network.model.AdminSnapshot;
import com.nightsta69.delvefold.network.model.ProfileOperation;
import com.nightsta69.delvefold.network.payload.AdminActionPayload;
import com.nightsta69.delvefold.network.payload.GameplayUpdatePayload;
import com.nightsta69.delvefold.network.payload.IdentityUpdatePayload;
import com.nightsta69.delvefold.network.payload.PortalUpdatePayload;
import com.nightsta69.delvefold.network.payload.ProfileActionPayload;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** Main administration dashboard for revision-guarded configuration and lifecycle operations. */
public final class DelvefoldDashboardScreen extends DelvefoldScreen {
    private static final int BODY_SCROLL_STEP = 24;
    private final Tab selectedTab;
    private final int orePage;
    private final int profilePage;
    private final DashboardPresentation<Component> presentation;
    private DashboardDraftState drafts;
    private Component portalError = Component.empty();
    private Component profileError = Component.empty();
    private Component identityError = Component.empty();
    private final ScrollableWidgetGroup bodyScroll = new ScrollableWidgetGroup();
    private @Nullable DashboardTabLayout tabLayout;
    private int portalStatusVirtualY;
    private int portalErrorVirtualY;
    private int identityMessageVirtualY;
    private int worldStatusVirtualY;
    private int worldWarningVirtualY;
    private int worldWarningHeight;
    private boolean worldContentStacked;
    private @Nullable Button destructiveButton;
    private @Nullable Button secondaryDestructiveButton;
    private @Nullable Button cancelPendingButton;

    /**
     * Creates the default ore-tab dashboard.
     *
     * @param snapshot immutable server-authoritative administration state
     */
    public DelvefoldDashboardScreen(AdminSnapshot snapshot) {
        this(snapshot, Tab.ORES, snapshot.orePage(), 0);
    }

    /**
     * Creates a replacement dashboard retaining local tab and profile-page navigation.
     *
     * @param updatedSnapshot newer immutable server snapshot
     * @return a new dashboard instance
     */
    public DelvefoldDashboardScreen refreshed(AdminSnapshot updatedSnapshot) {
        return new DelvefoldDashboardScreen(
                updatedSnapshot, this.selectedTab, updatedSnapshot.orePage(), this.profilePage);
    }

    private DelvefoldDashboardScreen(AdminSnapshot snapshot, Tab selectedTab, int orePage, int profilePage) {
        super(Component.translatable("screen.delvefold.dashboard.title"), snapshot);
        this.selectedTab = selectedTab;
        this.orePage = Math.max(0, orePage);
        this.profilePage = Math.max(0, profilePage);
        this.presentation = new DashboardPresentation<>(
                DelvefoldText.serverMessage(this.snapshot.portalStatus()),
                DelvefoldText.serverMessage(this.snapshot.worldStatus()),
                this.snapshot.diagnostics().stream()
                        .map(DelvefoldText::serverMessage)
                        .toList());
        this.drafts = DashboardDraftState.from(snapshot);
    }

    @Override
    protected void initPanel() {
        this.tabLayout = new DashboardTabLayout(
                this.contentWidth(), this.panelHeight, this.contentTop(), this.contentBottom(), this.compactChrome());
        this.bodyScroll.restoreOffset(0);
        this.bodyScroll.reset(bodyViewportTop());
        int available = this.contentWidth();
        int gap = 4;
        int tabWidth = Math.max(1, (available - gap * (Tab.values().length - 1)) / Tab.values().length);
        int x = this.contentLeft();
        int y = this.contentTop();
        int tabHeight = dashboardLayout().tabHeight();
        for (Tab tab : Tab.values()) {
            this.addButton(
                    x,
                    y,
                    tabWidth,
                    tabHeight,
                    Component.translatable(tab.translationKey),
                    tab == this.selectedTab ? Style.TAB_SELECTED : Style.GHOST,
                    ignored -> {
                        if (tab != this.selectedTab) {
                            selectTab(tab);
                        }
                    });
            x += tabWidth + gap;
        }

        switch (this.selectedTab) {
            case ORES -> initOres();
            case PROFILES -> initProfiles();
            case GAMEPLAY -> initGameplay();
            case PORTAL -> initPortal();
            case IDENTITY -> initIdentity();
            case DIAGNOSTICS -> initDiagnostics();
            case WORLD_MANAGEMENT -> initWorldManagement();
        }
        if (hasScrollableBody()) {
            applyBodyScroll();
        }

        int doneWidth = footerDoneWidth();
        this.addButton(
                this.contentRight() - doneWidth,
                this.footerButtonY(),
                doneWidth,
                22,
                Component.translatable("gui.done"),
                Style.GHOST,
                button -> this.onClose());
    }

    private void selectTab(Tab tab) {
        if (this.minecraft != null) {
            this.minecraft.setScreen(
                    new DelvefoldDashboardScreen(this.snapshot, tab, this.snapshot.orePage(), this.profilePage));
        }
    }

    private void initOres() {
        int innerWidth = Math.max(1, this.contentWidth() - 20);
        int actionGap = Math.min(6, Math.max(0, (innerWidth - 3) / 2));
        int usable = Math.max(3, innerWidth - actionGap * 2);
        int addWidth = Math.max(1, usable * 2 / 5);
        int forecastWidth = Math.max(1, usable * 2 / 5);
        int refreshWidth = Math.max(1, innerWidth - addWidth - forecastWidth - actionGap * 2);
        int y = bodyTop() + (compactHeight() ? 23 : 25);
        this.addBodyButton(
                this.contentLeft() + 10,
                y,
                addWidth,
                22,
                Component.translatable("screen.delvefold.ores.add"),
                Style.PRIMARY,
                button -> this.minecraft.setScreen(new DelvefoldOrePickerScreen(this, this.snapshot)));
        this.addBodyButton(
                this.contentLeft() + 10 + addWidth + actionGap,
                y,
                forecastWidth,
                22,
                Component.translatable("screen.delvefold.forecast.open"),
                Style.SECONDARY,
                button -> DelvefoldClientRequests.requestForecast(this.snapshot.activeProfileId(), 0));
        this.addBodyButton(
                this.contentLeft() + 10 + addWidth + forecastWidth + actionGap * 2,
                y,
                refreshWidth,
                22,
                Component.translatable("screen.delvefold.refresh"),
                Style.GHOST,
                button -> refresh());

        List<AdminSnapshot.OreRuleDraft> rules = this.snapshot.oreRules();
        DashboardTabLayout.Page page = dashboardLayout()
                .page(
                        this.snapshot.oreRuleTotal(),
                        this.orePage,
                        com.nightsta69.delvefold.network.ProtocolLimits.GUI_ORE_RULES_PER_PAGE);
        int pageCount = page.count();
        int safePage = page.index();
        int start = 0;
        int end = rules.size();
        int rowY = y + (compactHeight() ? 28 : 30);
        int rowStep = 25;
        int rowHeight = 22;
        for (int index = start; index < end; index++) {
            AdminSnapshot.OreRuleDraft rule = rules.get(index);
            Component label = Component.translatable("screen.delvefold.ores.row", rule.id(), rule.primaryBlockId());
            this.addBodyButton(
                    this.contentLeft() + 10,
                    rowY,
                    innerWidth,
                    rowHeight,
                    DelvefoldText.toggle(rule.enabled(), label),
                    rule.enabled() ? Style.TOGGLE_ON : Style.TOGGLE_OFF,
                    button -> this.minecraft.setScreen(new DelvefoldOreRuleWizardScreen(this, this.snapshot, rule)));
            rowY += rowStep;
        }
        if (rules.isEmpty()) {
            includeBodyText(Component.translatable("screen.delvefold.ores.empty"), innerWidth - 4, bodyTop() + 63);
        }

        int pagerY = this.footerButtonY();
        int pagerWidth = footerPagerButtonWidth();
        int pagerGap = Math.min(6, Math.max(0, this.contentWidth() - footerDoneWidth() - pagerWidth * 2));
        Button previous = this.addButton(
                this.contentLeft(),
                pagerY,
                pagerWidth,
                22,
                Component.translatable("screen.delvefold.previous"),
                Style.GHOST,
                button -> setOrePage(safePage - 1));
        previous.active = safePage > 0;
        Button next = this.addButton(
                this.contentLeft() + pagerWidth + pagerGap,
                pagerY,
                pagerWidth,
                22,
                Component.translatable("screen.delvefold.next"),
                Style.GHOST,
                button -> setOrePage(safePage + 1));
        next.active = safePage + 1 < pageCount;
    }

    private void initProfiles() {
        int x = this.contentLeft() + 10;
        int y = bodyTop() + 25;
        int innerWidth = Math.max(1, this.contentWidth() - 20);
        int deleteWidth = 74;
        int gap = 5;
        int rowWidth = Math.max(1, innerWidth - deleteWidth - gap);
        ProfilePanelLayout layout = profilePanelLayout();
        int pageSize = layout.pageSize();
        DashboardTabLayout.Page page =
                dashboardLayout().page(this.snapshot.profiles().size(), this.profilePage, pageSize);
        int pageCount = page.count();
        int safePage = page.index();
        int start = page.start();
        int end = page.end();
        for (int index = start; index < end; index++) {
            AdminSnapshot.ProfileDraft profile = this.snapshot.profiles().get(index);
            boolean active = profile.id().equals(this.snapshot.activeProfileId());
            String rowKey = active
                    ? "screen.delvefold.profiles.row.active"
                    : profile.valid()
                            ? "screen.delvefold.profiles.row.available"
                            : "screen.delvefold.profiles.row.invalid";
            this.addButton(
                    x,
                    y,
                    rowWidth,
                    layout.rowHeight(),
                    Component.translatable(rowKey, profile.id(), profile.ruleCount()),
                    active ? Style.TOGGLE_ON : Style.GHOST,
                    button -> performProfile(ProfileOperation.SELECT, profile.id(), "", "", false));
            Button delete = this.addButton(
                    x + rowWidth + gap,
                    y,
                    deleteWidth,
                    layout.rowHeight(),
                    Component.translatable(
                            profile.id().equals(this.drafts.profile().armedDeleteId())
                                    ? "screen.delvefold.confirm"
                                    : "screen.delvefold.delete"),
                    Style.DANGER,
                    button -> deleteProfile(profile));
            delete.active = !active && profile.localOverride();
            y += layout.rowStep();
        }

        if (pageCount > 1) {
            Button previous = this.addButton(
                    this.contentLeft(),
                    this.footerButtonY(),
                    58,
                    22,
                    Component.translatable("screen.delvefold.previous"),
                    Style.GHOST,
                    button -> setProfilePage(safePage - 1));
            previous.active = safePage > 0;
            Button next = this.addButton(
                    this.contentLeft() + 64,
                    this.footerButtonY(),
                    58,
                    22,
                    Component.translatable("screen.delvefold.next"),
                    Style.GHOST,
                    button -> setProfilePage(safePage + 1));
            next.active = safePage + 1 < pageCount;
        }

        int controlsY = layout.controlsY();
        int nameWidth = Math.max(100, innerWidth / 2);
        EditBox name = this.addRenderableWidget(new EditBox(
                this.font, x, controlsY, nameWidth, 20, Component.translatable("screen.delvefold.profiles.id")));
        name.setMaxLength(com.nightsta69.delvefold.network.ProtocolLimits.ID_LENGTH);
        name.setValue(this.drafts.profile().name());
        name.setHint(Component.translatable("screen.delvefold.profiles.id_hint"));
        name.setResponder(value ->
                this.drafts = this.drafts.withProfile(this.drafts.profile().withName(value)));
        name.setTextColor(TEXT);
        this.addButton(
                x + nameWidth + gap,
                controlsY,
                innerWidth - nameWidth - gap,
                20,
                portalToggleLabel(
                        "screen.delvefold.profiles.overwrite",
                        this.drafts.profile().overwrite()),
                this.drafts.profile().overwrite() ? Style.TOGGLE_ON : Style.TOGGLE_OFF,
                button -> {
                    boolean overwrite = !this.drafts.profile().overwrite();
                    this.drafts = this.drafts.withProfile(this.drafts.profile().withOverwrite(overwrite));
                    button.setMessage(portalToggleLabel("screen.delvefold.profiles.overwrite", overwrite));
                    setButtonStyle(button, overwrite ? Style.TOGGLE_ON : Style.TOGGLE_OFF);
                });

        int actionY = controlsY + 25;
        int actionGap = 5;
        int actionWidth = Math.max(1, (innerWidth - actionGap * 3) / 4);
        this.addButton(
                x,
                actionY,
                actionWidth,
                20,
                Component.translatable("screen.delvefold.profiles.save_current"),
                Style.PRIMARY,
                button -> performProfile(
                        ProfileOperation.SAVE_CURRENT,
                        "",
                        this.drafts.profile().name(),
                        "",
                        this.drafts.profile().overwrite()));
        this.addButton(
                x + actionWidth + actionGap,
                actionY,
                actionWidth,
                20,
                Component.translatable("screen.delvefold.profiles.import_clipboard"),
                Style.SECONDARY,
                button -> importClipboard());
        this.addButton(
                x + (actionWidth + actionGap) * 2,
                actionY,
                actionWidth,
                20,
                Component.translatable("screen.delvefold.profiles.copy_active"),
                Style.GHOST,
                button -> DelvefoldClientRequests.requestProfileExport(this.snapshot.activeProfileId()));
        this.addButton(
                x + (actionWidth + actionGap) * 3,
                actionY,
                innerWidth - actionWidth * 3 - actionGap * 3,
                20,
                Component.translatable("screen.delvefold.import.open"),
                Style.SECONDARY,
                button -> this.minecraft.setScreen(new DelvefoldOreImportScreen(this, this.snapshot)));
    }

    private void setProfilePage(int page) {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new DelvefoldDashboardScreen(
                    this.snapshot, Tab.PROFILES, this.snapshot.orePage(), Math.max(0, page)));
        }
    }

    private int profilePageSize() {
        return profilePanelLayout().pageSize();
    }

    private ProfilePanelLayout profilePanelLayout() {
        return dashboardLayout().profilePanel();
    }

    private void deleteProfile(AdminSnapshot.ProfileDraft profile) {
        DashboardDraftState.DeleteDecision decision = this.drafts.profile().armDelete(profile.id());
        this.drafts = this.drafts.withProfile(decision.draft());
        if (!decision.confirmed()) {
            if (this.minecraft != null) {
                this.rebuildWidgets();
            }
            return;
        }
        performProfile(ProfileOperation.DELETE, profile.id(), "", "", false);
    }

    private void importClipboard() {
        if (this.minecraft == null) {
            return;
        }
        String json = this.minecraft.keyboardHandler.getClipboard();
        if (json == null
                || json.isBlank()
                || json.length() > com.nightsta69.delvefold.network.ProtocolLimits.MAX_PROFILE_CLIPBOARD_CHARS) {
            this.profileError = Component.translatable("screen.delvefold.profiles.clipboard_invalid");
            return;
        }
        performProfile(
                ProfileOperation.IMPORT_CLIPBOARD,
                "",
                this.drafts.profile().name(),
                json,
                this.drafts.profile().overwrite());
    }

    private void performProfile(
            ProfileOperation operation, String sourceId, String targetId, String json, boolean overwrite) {
        this.profileError = Component.empty();
        DelvefoldClientRequests.send(
                new ProfileActionPayload(this.snapshot.oreRevision(), operation, sourceId, targetId, json, overwrite));
    }

    private void setOrePage(int page) {
        DelvefoldClientRequests.requestOrePage(page, this.snapshot.oreRevision());
    }

    private void initGameplay() {
        boolean compact = compactHeight();
        int x = this.contentLeft() + 10;
        int y = bodyTop() + (compact ? 23 : 27);
        int controlsWidth = Math.max(1, Math.min(354, this.contentWidth() - 20));
        int gap = Math.min(6, Math.max(0, controlsWidth - 2));
        int toggleWidth = (controlsWidth - gap) / 2;
        this.addBodyButton(
                x, y, controlsWidth, 22, gameplayPresetLabel(), Style.SECONDARY, button -> cycleGameplayPreset());
        int firstRow = compact ? 26 : 30;
        int secondRow = compact ? 50 : 58;
        int thirdRow = compact ? 74 : 86;
        addGameplayToggle(
                x,
                y + firstRow,
                toggleWidth,
                Component.translatable("screen.delvefold.gameplay.monsters"),
                DashboardDraftState.GameplayToggle.MONSTERS);
        addGameplayToggle(
                x + toggleWidth + gap,
                y + firstRow,
                toggleWidth,
                Component.translatable("screen.delvefold.gameplay.creatures"),
                DashboardDraftState.GameplayToggle.CREATURES);
        addGameplayToggle(
                x,
                y + secondRow,
                toggleWidth,
                Component.translatable("screen.delvefold.gameplay.ambient"),
                DashboardDraftState.GameplayToggle.AMBIENT);
        addGameplayToggle(
                x + toggleWidth + gap,
                y + secondRow,
                toggleWidth,
                Component.translatable("screen.delvefold.gameplay.water_mobs"),
                DashboardDraftState.GameplayToggle.WATER_CREATURES);
        addGameplayToggle(
                x,
                y + thirdRow,
                toggleWidth,
                Component.translatable("screen.delvefold.gameplay.patrols"),
                DashboardDraftState.GameplayToggle.PATROLS);
        addGameplayToggle(
                x + toggleWidth + gap,
                y + thirdRow,
                toggleWidth,
                Component.translatable("screen.delvefold.gameplay.phantoms"),
                DashboardDraftState.GameplayToggle.PHANTOMS);
        this.addBodyButton(
                x,
                y + (compact ? 105 : 124),
                controlsWidth,
                22,
                Component.translatable("screen.delvefold.gameplay.save"),
                Style.PRIMARY,
                button -> saveGameplay());

        if (!compactHeight() && this.contentWidth() - Math.min(374, this.contentWidth()) >= 150) {
            int infoWidth = this.contentWidth() - Math.min(374, this.contentWidth()) - 8;
            includeBodyText(
                    Component.translatable("screen.delvefold.gameplay.description"), infoWidth - 20, bodyTop() + 28);
            includeBodyText(
                    Component.translatable("screen.delvefold.gameplay.unaffected"), infoWidth - 20, bodyTop() + 92);
        }
    }

    private void addGameplayToggle(
            int x, int y, int width, Component label, DashboardDraftState.GameplayToggle toggle) {
        boolean initialValue = this.drafts.gameplay().value(toggle);
        boolean[] state = {initialValue};
        this.addBodyButton(
                x,
                y,
                width,
                22,
                toggleLabel(label, initialValue),
                initialValue ? Style.TOGGLE_ON : Style.TOGGLE_OFF,
                button -> {
                    boolean next = !state[0];
                    state[0] = next;
                    this.drafts =
                            this.drafts.withGameplay(this.drafts.gameplay().with(toggle, next));
                    button.setMessage(toggleLabel(label, next));
                    setButtonStyle(button, next ? Style.TOGGLE_ON : Style.TOGGLE_OFF);
                });
    }

    private void cycleGameplayPreset() {
        GameplayPreset[] values = GameplayPreset.values();
        GameplayPreset next = values[(GuiEnumOrder.index(this.drafts.gameplay().preset()) + 1) % values.length];
        this.drafts = this.drafts.withGameplay(this.drafts.gameplay().withPreset(next));
        if (this.minecraft != null) {
            this.minecraft.setScreen(new DelvefoldDashboardScreen(
                    new AdminSnapshot(
                            this.snapshot.oreRevision(),
                            this.snapshot.settingsRevision(),
                            this.snapshot.backendReady(),
                            this.snapshot.initialized(),
                            this.snapshot.terrainMode(),
                            this.snapshot.orePreset(),
                            currentGameplay(),
                            this.snapshot.portal(),
                            this.snapshot.identity(),
                            this.snapshot.capabilities(),
                            this.snapshot.activeProfileId(),
                            this.snapshot.profiles(),
                            this.snapshot.backups(),
                            this.snapshot.portalStatus(),
                            this.snapshot.worldStatus(),
                            this.snapshot.pendingOperation(),
                            this.snapshot.diagnostics(),
                            this.snapshot.oreRuleTotal(),
                            this.snapshot.orePage(),
                            this.snapshot.oreRules()),
                    Tab.GAMEPLAY,
                    0,
                    this.profilePage));
        }
    }

    private void saveGameplay() {
        DelvefoldClientRequests.send(new GameplayUpdatePayload(this.snapshot.settingsRevision(), currentGameplay()));
    }

    private void initPortal() {
        int x = this.contentLeft() + 10;
        int y = bodyTop() + (compactHeight() ? 25 : 31);
        int innerWidth = Math.max(1, this.contentWidth() - 20);
        int gap = 6;
        int half = Math.max(1, (innerWidth - gap) / 2);
        this.addBodyButton(
                x,
                y,
                half,
                22,
                portalToggleLabel(
                        "screen.delvefold.portal.travel", this.drafts.portal().enabled()),
                this.drafts.portal().enabled() ? Style.TOGGLE_ON : Style.TOGGLE_OFF,
                button -> {
                    this.drafts = this.drafts.withPortal(this.drafts.portal().toggleEnabled());
                    boolean enabled = this.drafts.portal().enabled();
                    button.setMessage(portalToggleLabel("screen.delvefold.portal.travel", enabled));
                    setButtonStyle(button, enabled ? Style.TOGGLE_ON : Style.TOGGLE_OFF);
                });
        this.addBodyButton(
                x + half + gap,
                y,
                innerWidth - half - gap,
                22,
                portalToggleLabel(
                        "screen.delvefold.portal.overworld_only",
                        this.drafts.portal().overworldOnly()),
                this.drafts.portal().overworldOnly() ? Style.TOGGLE_ON : Style.TOGGLE_OFF,
                button -> {
                    this.drafts = this.drafts.withPortal(this.drafts.portal().toggleOverworldOnly());
                    boolean overworldOnly = this.drafts.portal().overworldOnly();
                    button.setMessage(portalToggleLabel("screen.delvefold.portal.overworld_only", overworldOnly));
                    setButtonStyle(button, overworldOnly ? Style.TOGGLE_ON : Style.TOGGLE_OFF);
                });

        int routingY = y + (compactHeight() ? 27 : 31);
        this.addBodyButton(x, routingY, innerWidth, 22, portalRoutingLabel(), Style.SECONDARY, button -> {
            PortalRoutingMode[] modes = PortalRoutingMode.values();
            PortalRoutingMode next =
                    modes[(GuiEnumOrder.index(this.drafts.portal().routingMode()) + 1) % modes.length];
            this.drafts = this.drafts.withPortal(this.drafts.portal().withRoutingMode(next));
            button.setMessage(portalRoutingLabel());
        });

        int fieldsY = routingY + (compactHeight() ? 27 : 31);
        EditBox cooldown = registerBodyWidget(this.addRenderableWidget(new EditBox(
                this.font, x, fieldsY, half, 20, Component.translatable("screen.delvefold.portal.cooldown"))));
        cooldown.setMaxLength(4);
        cooldown.setValue(this.drafts.portal().cooldown());
        cooldown.setHint(Component.translatable("screen.delvefold.portal.cooldown"));
        cooldown.setTextColor(TEXT);
        cooldown.setTextColorUneditable(DIM_TEXT);
        cooldown.setResponder(value ->
                this.drafts = this.drafts.withPortal(this.drafts.portal().withCooldown(value)));
        EditBox scale = registerBodyWidget(this.addRenderableWidget(new EditBox(
                this.font,
                x + half + gap,
                fieldsY,
                innerWidth - half - gap,
                20,
                Component.translatable("screen.delvefold.portal.scale"))));
        scale.setMaxLength(8);
        scale.setValue(this.drafts.portal().scale());
        scale.setHint(Component.translatable("screen.delvefold.portal.scale"));
        scale.setTextColor(TEXT);
        scale.setTextColorUneditable(DIM_TEXT);
        scale.setResponder(value ->
                this.drafts = this.drafts.withPortal(this.drafts.portal().withScale(value)));

        int hubY = fieldsY + (compactHeight() ? 25 : 29);
        int thirdGap = 4;
        int third = Math.max(1, (innerWidth - thirdGap * 2) / 3);
        EditBox hubX = registerBodyWidget(this.addRenderableWidget(
                new EditBox(this.font, x, hubY, third, 20, Component.translatable("screen.delvefold.portal.hub_x"))));
        hubX.setMaxLength(10);
        hubX.setValue(this.drafts.portal().hubX());
        hubX.setHint(Component.translatable("screen.delvefold.portal.hub_x"));
        hubX.setEditable(this.snapshot.capabilities().canManageWorld());
        if (!this.snapshot.capabilities().canManageWorld()) {
            hubX.setTooltip(Tooltip.create(Component.translatable("screen.delvefold.portal.hub_read_only")));
        }
        hubX.setResponder(value ->
                this.drafts = this.drafts.withPortal(this.drafts.portal().withHubX(value)));
        EditBox hubZ = registerBodyWidget(this.addRenderableWidget(new EditBox(
                this.font,
                x + third + thirdGap,
                hubY,
                third,
                20,
                Component.translatable("screen.delvefold.portal.hub_z"))));
        hubZ.setMaxLength(10);
        hubZ.setValue(this.drafts.portal().hubZ());
        hubZ.setHint(Component.translatable("screen.delvefold.portal.hub_z"));
        hubZ.setEditable(this.snapshot.capabilities().canManageWorld());
        if (!this.snapshot.capabilities().canManageWorld()) {
            hubZ.setTooltip(Tooltip.create(Component.translatable("screen.delvefold.portal.hub_read_only")));
        }
        hubZ.setResponder(value ->
                this.drafts = this.drafts.withPortal(this.drafts.portal().withHubZ(value)));
        EditBox radius = registerBodyWidget(this.addRenderableWidget(new EditBox(
                this.font,
                x + (third + thirdGap) * 2,
                hubY,
                innerWidth - third * 2 - thirdGap * 2,
                20,
                Component.translatable("screen.delvefold.portal.protection_radius"))));
        radius.setMaxLength(3);
        radius.setValue(this.drafts.portal().protectionRadius());
        radius.setHint(Component.translatable("screen.delvefold.portal.protection_radius"));
        radius.setEditable(this.snapshot.capabilities().canManageWorld());
        if (!this.snapshot.capabilities().canManageWorld()) {
            radius.setTooltip(Tooltip.create(Component.translatable("screen.delvefold.portal.hub_read_only")));
        }
        radius.setResponder(value ->
                this.drafts = this.drafts.withPortal(this.drafts.portal().withProtectionRadius(value)));

        int actionY = hubY + (compactHeight() ? 25 : 29);
        this.addBodyButton(
                x,
                actionY,
                half,
                22,
                Component.translatable("screen.delvefold.portal.save"),
                Style.PRIMARY,
                button -> savePortal());
        this.addBodyButton(
                x + half + gap,
                actionY,
                innerWidth - half - gap,
                22,
                Component.translatable("screen.delvefold.refresh"),
                Style.GHOST,
                button -> refresh());

        this.portalStatusVirtualY = actionY + (compactHeight() ? 28 : 32);
        int statusBottom = includeBodyText(this.presentation.portalStatus(), innerWidth, this.portalStatusVirtualY);
        this.portalErrorVirtualY = Math.max(statusBottom + 6, this.contentBottom() - 17);
        if (!this.portalError.getString().isEmpty()) {
            includeBodyText(this.portalError, innerWidth, this.portalErrorVirtualY);
        }
    }

    private void savePortal() {
        try {
            this.portalError = Component.empty();
            DelvefoldClientRequests.send(new PortalUpdatePayload(
                    this.snapshot.settingsRevision(), this.drafts.portal().validatedSettings()));
        } catch (IllegalArgumentException exception) {
            this.portalError = Component.translatable("screen.delvefold.portal.validation");
            includeBodyText(this.portalError, this.contentWidth() - 20, this.portalErrorVirtualY);
            applyBodyScroll();
        }
    }

    private static Component portalToggleLabel(String translationKey, boolean enabled) {
        return Component.translatable(
                enabled ? "screen.delvefold.toggle.on" : "screen.delvefold.toggle.off",
                Component.translatable(translationKey));
    }

    private Component portalRoutingLabel() {
        return Component.translatable(
                "screen.delvefold.portal.routing",
                Component.translatable("option.delvefold.portal_routing."
                        + this.drafts.portal().routingMode().serializedName()));
    }

    private void initDiagnostics() {
        int x = this.contentLeft() + 10;
        int y = bodyTop() + (compactHeight() ? 23 : 25);
        int available = Math.max(1, this.contentWidth() - 20);
        int gap = Math.min(6, Math.max(0, (available - 3) / 2));
        int buttonWidth = Math.max(1, (available - gap * 2) / 3);
        int finalButtonWidth = Math.max(1, available - buttonWidth * 2 - gap * 2);
        this.addButton(
                x,
                y,
                buttonWidth,
                22,
                Component.translatable("screen.delvefold.diagnostics.validate"),
                Style.PRIMARY,
                button -> perform(AdminOperation.VALIDATE_CONFIG, ""));
        this.addButton(
                x + buttonWidth + gap,
                y,
                buttonWidth,
                22,
                Component.translatable("screen.delvefold.diagnostics.reload"),
                Style.SECONDARY,
                button -> perform(AdminOperation.RELOAD_CONFIG, ""));
        this.addButton(
                x + (buttonWidth + gap) * 2,
                y,
                finalButtonWidth,
                22,
                Component.translatable("screen.delvefold.refresh"),
                Style.GHOST,
                button -> refresh());
    }

    private void initIdentity() {
        int x = this.contentLeft() + 10;
        int y = bodyTop() + (compactHeight() ? 23 : 30);
        int width = this.contentWidth() - 20;
        EditBox name = registerBodyWidget(this.addRenderableWidget(
                new EditBox(this.font, x, y, width, 20, Component.translatable("screen.delvefold.identity.name"))));
        name.setMaxLength(64);
        name.setValue(this.drafts.identity().displayName());
        name.setHint(Component.translatable("screen.delvefold.identity.name"));
        name.setResponder(value ->
                this.drafts = this.drafts.withIdentity(this.drafts.identity().withDisplayName(value)));

        int gap = 6;
        int half = (width - gap) / 2;
        Button geology = this.addBodyButton(x, y + 34, width, 22, geologyLockedLabel(), Style.GHOST, button -> {});
        geology.active = false;

        this.addBodyButton(x, y + 68, half, 22, landmarkLabel(), Style.SECONDARY, button -> {
            LandmarkPreset[] values = LandmarkPreset.values();
            LandmarkPreset next =
                    values[(GuiEnumOrder.index(this.drafts.identity().landmarkPreset()) + 1) % values.length];
            this.drafts = this.drafts.withIdentity(this.drafts.identity().withLandmarkPreset(next));
            button.setMessage(landmarkLabel());
        });
        Button renewalToggle = this.addBodyButton(
                x + half + gap,
                y + 68,
                width - half - gap,
                22,
                toggleLabel(
                        Component.translatable("screen.delvefold.identity.renewal"),
                        this.drafts.identity().renewalEnabled()),
                this.drafts.identity().renewalEnabled() ? Style.TOGGLE_ON : Style.TOGGLE_OFF,
                button -> {
                    this.drafts =
                            this.drafts.withIdentity(this.drafts.identity().toggleRenewal());
                    boolean enabled = this.drafts.identity().renewalEnabled();
                    button.setMessage(
                            toggleLabel(Component.translatable("screen.delvefold.identity.renewal"), enabled));
                    setButtonStyle(button, enabled ? Style.TOGGLE_ON : Style.TOGGLE_OFF);
                });
        renewalToggle.active = this.snapshot.capabilities().canManageWorld();

        EditBox days = registerBodyWidget(this.addRenderableWidget(new EditBox(
                this.font, x, y + 102, half, 20, Component.translatable("screen.delvefold.identity.renewal_days"))));
        days.setMaxLength(4);
        days.setValue(this.drafts.identity().renewalDays());
        days.setHint(Component.translatable("screen.delvefold.identity.renewal_days"));
        days.setResponder(value ->
                this.drafts = this.drafts.withIdentity(this.drafts.identity().withRenewalDays(value)));
        days.active = this.snapshot.capabilities().canManageWorld();
        EditBox warning = registerBodyWidget(this.addRenderableWidget(new EditBox(
                this.font,
                x + half + gap,
                y + 102,
                width - half - gap,
                20,
                Component.translatable("screen.delvefold.identity.warning_minutes"))));
        warning.setMaxLength(5);
        warning.setValue(this.drafts.identity().renewalWarning());
        warning.setHint(Component.translatable("screen.delvefold.identity.warning_minutes"));
        warning.setResponder(value ->
                this.drafts = this.drafts.withIdentity(this.drafts.identity().withRenewalWarning(value)));
        warning.active = this.snapshot.capabilities().canManageWorld();

        Button seedMode = this.addBodyButton(x, y + 136, width, 22, renewalSeedModeLabel(), Style.SECONDARY, button -> {
            RenewalSeedMode[] values = RenewalSeedMode.values();
            RenewalSeedMode next =
                    values[(GuiEnumOrder.index(this.drafts.identity().renewalSeedMode()) + 1) % values.length];
            this.drafts = this.drafts.withIdentity(this.drafts.identity().withRenewalSeedMode(next));
            button.setMessage(renewalSeedModeLabel());
        });
        seedMode.active = this.snapshot.capabilities().canManageWorld();

        Button save = this.addBodyButton(
                x,
                y + 164,
                width,
                22,
                Component.translatable("screen.delvefold.identity.save"),
                Style.PRIMARY,
                button -> saveIdentity());
        save.active =
                this.snapshot.backendReady() && this.snapshot.capabilities().canConfigure();

        this.identityMessageVirtualY = compactHeight() ? y + 194 : this.contentBottom() - 15;
        includeBodyBottom(this.identityMessageVirtualY + wrappedTextHeight(identityMessage(), width) + 1);
    }

    private void saveIdentity() {
        try {
            this.identityError = Component.empty();
            DelvefoldClientRequests.send(new IdentityUpdatePayload(
                    this.snapshot.settingsRevision(),
                    this.drafts
                            .identity()
                            .validatedSettings(
                                    this.snapshot.identity(),
                                    this.snapshot.capabilities().canManageWorld(),
                                    System.currentTimeMillis())));
        } catch (NumberFormatException exception) {
            this.identityError = Component.translatable("screen.delvefold.identity.validation");
            includeBodyText(this.identityError, this.contentWidth() - 20, this.identityMessageVirtualY);
            applyBodyScroll();
        }
    }

    private void initWorldManagement() {
        int x = this.contentLeft() + 10;
        int innerWidth = Math.max(1, this.contentWidth() - 20);
        int y = bodyTop() + (compactHeight() ? 22 : 26);
        int choiceGap = 6;
        int choiceWidth = Math.max(1, (innerWidth - choiceGap) / 2);
        this.addBodyButton(x, y, choiceWidth, 22, recreateTerrainLabel(), Style.SECONDARY, button -> {
            TerrainMode[] modes = TerrainMode.values();
            TerrainMode next = modes[(GuiEnumOrder.index(this.drafts.world().terrain()) + 1) % modes.length];
            this.drafts = this.drafts.withWorld(this.drafts.world().withTerrain(next));
            button.setMessage(recreateTerrainLabel());
        });
        this.addBodyButton(
                x + choiceWidth + choiceGap,
                y,
                innerWidth - choiceWidth - choiceGap,
                22,
                recreateVariantLabel(),
                Style.SECONDARY,
                button -> {
                    TerrainVariant[] variants = TerrainVariant.values();
                    TerrainVariant next =
                            variants[(GuiEnumOrder.index(this.drafts.world().variant()) + 1) % variants.length];
                    this.drafts = this.drafts.withWorld(this.drafts.world().withVariant(next));
                    button.setMessage(recreateVariantLabel());
                });
        y += compactHeight() ? 26 : 32;
        this.addBodyButton(x, y, innerWidth, 22, recreateGeologyThemeLabel(), Style.SECONDARY, button -> {
            GeologyTheme[] themes = GeologyTheme.values();
            GeologyTheme next = themes[(GuiEnumOrder.index(this.drafts.world().geologyTheme()) + 1) % themes.length];
            this.drafts = this.drafts.withWorld(this.drafts.world().withGeologyTheme(next));
            button.setMessage(recreateGeologyThemeLabel());
        });
        y += compactHeight() ? 26 : 32;
        int actionGap = Math.min(6, Math.max(0, innerWidth - 2));
        int actionWidth = Math.max(1, (innerWidth - actionGap) / 2);
        int secondaryActionWidth = Math.max(1, innerWidth - actionWidth - actionGap);
        Button deleteWorld = this.addBodyButton(
                x,
                y,
                actionWidth,
                22,
                Component.translatable("screen.delvefold.world.delete"),
                Style.DANGER,
                button -> armOrPerform(AdminOperation.DELETE_WORLD));
        this.destructiveButton = deleteWorld;
        Button recreateWorld = this.addBodyButton(
                x + actionWidth + actionGap,
                y,
                secondaryActionWidth,
                22,
                Component.translatable("screen.delvefold.world.recreate"),
                Style.DANGER,
                button -> armOrPerform(AdminOperation.RECREATE_WORLD));
        this.secondaryDestructiveButton = recreateWorld;
        deleteWorld.active = this.snapshot.capabilities().canManageWorld();
        recreateWorld.active = this.snapshot.capabilities().canManageWorld();
        if (this.snapshot.worldOperationPending()) {
            Button cancelPending = this.addBodyButton(
                    x,
                    y + (compactHeight() ? 24 : 30),
                    innerWidth,
                    22,
                    Component.translatable("screen.delvefold.world.cancel_reset"),
                    Style.GHOST,
                    button -> armOrPerform(AdminOperation.CANCEL_PENDING_RESET));
            this.cancelPendingButton = cancelPending;
            cancelPending.active = this.snapshot.capabilities().canManageWorld();
        }
        y += compactHeight() ? 55 : 65;
        int backupY = y;
        this.addBodyButton(
                x,
                backupY,
                Math.min(220, innerWidth),
                22,
                Component.translatable("screen.delvefold.backup.manage"),
                Style.SECONDARY,
                button -> this.minecraft.setScreen(new DelvefoldBackupScreen(this, this.snapshot)));

        this.worldContentStacked = dashboardLayout().worldContentStacked();
        if (this.worldContentStacked) {
            this.worldStatusVirtualY = backupY + 30;
            int statusBottom = includeBodyText(this.presentation.worldStatus(), innerWidth, this.worldStatusVirtualY);
            int warningTextHeight = wrappedTextHeight(
                    Component.translatable("screen.delvefold.world.warning"), Math.max(1, innerWidth - 22));
            this.worldWarningHeight = Math.max(this.contentWidth() < 380 ? 64 : 52, warningTextHeight + 16);
            this.worldWarningVirtualY = statusBottom + 8;
        } else {
            this.worldStatusVirtualY = bodyTop() + 30;
            includeBodyText(
                    this.presentation.worldStatus(), Math.max(1, this.contentWidth() - 258), this.worldStatusVirtualY);
            this.worldWarningHeight = this.contentWidth() < 380 ? 64 : 52;
            this.worldWarningVirtualY = this.contentBottom() - this.worldWarningHeight - 6;
        }
        includeBodyBottom(this.worldWarningVirtualY + this.worldWarningHeight + 1);
        updateDestructiveLabels();
    }

    private void armOrPerform(AdminOperation operation) {
        DashboardDraftState.ArmDecision decision = this.drafts.world().arm(operation);
        this.drafts = this.drafts.withWorld(decision.draft());
        if (decision.confirmation() == null) {
            updateDestructiveLabels();
            return;
        }
        perform(operation, decision.confirmation());
    }

    private void updateDestructiveLabels() {
        Button deleteWorld = this.destructiveButton;
        if (deleteWorld != null) {
            Component label = this.drafts.world().armedOperation() == AdminOperation.DELETE_WORLD
                    ? Component.translatable("screen.delvefold.world.confirm_delete")
                    : Component.translatable("screen.delvefold.world.delete");
            deleteWorld.setMessage(label);
        }
        Button recreateWorld = this.secondaryDestructiveButton;
        if (recreateWorld != null) {
            recreateWorld.setMessage(
                    this.drafts.world().armedOperation() == AdminOperation.RECREATE_WORLD
                            ? Component.translatable("screen.delvefold.world.confirm_recreate")
                            : Component.translatable("screen.delvefold.world.recreate"));
        }
        Button cancelPending = this.cancelPendingButton;
        if (cancelPending != null) {
            boolean armed = this.drafts.world().armedOperation() == AdminOperation.CANCEL_PENDING_RESET;
            cancelPending.setMessage(
                    armed
                            ? Component.translatable("screen.delvefold.world.confirm_cancel")
                            : Component.translatable("screen.delvefold.world.cancel_reset"));
            setButtonStyle(cancelPending, armed ? Style.DANGER : Style.GHOST);
        }
    }

    private void perform(AdminOperation operation, String confirmation) {
        DelvefoldClientRequests.send(new AdminActionPayload(this.snapshot.settingsRevision(), operation, confirmation));
    }

    private void refresh() {
        perform(AdminOperation.REFRESH, "");
    }

    private GameplaySettings currentGameplay() {
        return this.drafts.gameplay().settings();
    }

    private Component gameplayPresetLabel() {
        return Component.translatable(
                "screen.delvefold.gameplay.preset",
                DelvefoldText.option("gameplay", this.drafts.gameplay().preset().serializedName()));
    }

    private Component recreateTerrainLabel() {
        return Component.translatable(
                "screen.delvefold.world.recreate_terrain",
                DelvefoldText.option("terrain", this.drafts.world().terrain().serializedName()));
    }

    private Component recreateVariantLabel() {
        return Component.translatable(
                "screen.delvefold.world.recreate_scale",
                DelvefoldText.option(
                        "terrain_variant", this.drafts.world().variant().serializedName()));
    }

    private Component recreateGeologyThemeLabel() {
        return Component.translatable(
                "screen.delvefold.world.recreate_geology_theme",
                DelvefoldText.option(
                        "geology_theme", this.drafts.world().geologyTheme().serializedName()));
    }

    private Component geologyLockedLabel() {
        return Component.translatable(
                "screen.delvefold.identity.geology_locked",
                DelvefoldText.option(
                        "geology_theme",
                        this.drafts.identity().activeGeologyTheme().serializedName()));
    }

    private Component landmarkLabel() {
        return Component.translatable(
                "screen.delvefold.identity.landmarks",
                DelvefoldText.option(
                        "landmark", this.drafts.identity().landmarkPreset().serializedName()));
    }

    private Component renewalSeedModeLabel() {
        return Component.translatable(
                "screen.delvefold.identity.seed_mode",
                DelvefoldText.option(
                        "renewal_seed_mode",
                        this.drafts.identity().renewalSeedMode().serializedName()));
    }

    private Component identityMessage() {
        if (!this.identityError.getString().isEmpty()) {
            return this.identityError;
        }
        if (!this.snapshot.capabilities().canManageWorld()) {
            return Component.translatable("screen.delvefold.identity.renewal_read_only");
        }
        return Component.translatable(
                this.snapshot.identity().renewal().enabled()
                        ? "screen.delvefold.identity.renewal_enabled_help"
                        : "screen.delvefold.identity.renewal_disabled_help");
    }

    private static Component toggleLabel(Component label, boolean enabled) {
        return DelvefoldText.toggle(enabled, label);
    }

    private int bodyTop() {
        return dashboardLayout().bodyTop();
    }

    private boolean compactHeight() {
        return dashboardLayout().compactHeight();
    }

    private int footerDoneWidth() {
        return dashboardLayout().footerDoneWidth();
    }

    private int footerPagerButtonWidth() {
        return dashboardLayout().footerPagerButtonWidth();
    }

    private DashboardTabLayout dashboardLayout() {
        DashboardTabLayout layout = this.tabLayout;
        if (layout == null) {
            throw new IllegalStateException("Dashboard layout requested before screen initialization");
        }
        return layout;
    }

    @Override
    public Component getNarrationMessage() {
        return Component.translatable(
                "screen.delvefold.dashboard.narration",
                Component.translatable(this.selectedTab.translationKey),
                this.snapshot.initialized()
                        ? Component.translatable("screen.delvefold.status.ready")
                        : Component.translatable("screen.delvefold.status.not_ready"),
                DelvefoldText.serverMessage(this.snapshot.portalStatus()));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!hasScrollableBody()) {
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
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
        if (hasScrollableBody()
                && mouseX >= this.contentLeft()
                && mouseX < this.contentRight()
                && mouseY >= layout.viewportTop()
                && mouseY < layout.viewportBottom()
                && scrollY != 0.0D
                && layout.maximumScroll() > 0) {
            setBodyScrollOffset(this.bodyScroll.scrollOffset() - (int) Math.signum(scrollY) * BODY_SCROLL_STEP);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (hasScrollableBody()
                && keyCode == GLFW.GLFW_KEY_TAB
                && this.getFocused() instanceof AbstractWidget focused) {
            AbstractWidget next = this.bodyScroll.nextFocusable(focused, !hasShiftDown(), bodyScrollLayout());
            if (next != null) {
                this.setInitialFocus(next);
                return true;
            }
        }
        if (hasScrollableBody()
                && this.getFocused() instanceof AbstractWidget focused
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
        switch (this.selectedTab) {
            case ORES -> {
                this.drawCard(graphics, x, y, width, height);
                this.drawSectionTitle(graphics, Component.translatable("screen.delvefold.section.ores"), x + 10, y + 7);
                if (this.snapshot.oreRules().isEmpty()) {
                    beginBodyScissor(graphics);
                    graphics.drawWordWrap(
                            this.font,
                            Component.translatable("screen.delvefold.ores.empty"),
                            x + 12,
                            bodyScreenY(y + 63),
                            width - 24,
                            MUTED_TEXT);
                    endBodyScissor(graphics);
                }
                DashboardTabLayout.Page page = dashboardLayout()
                        .page(
                                this.snapshot.oreRuleTotal(),
                                this.orePage,
                                com.nightsta69.delvefold.network.ProtocolLimits.GUI_ORE_RULES_PER_PAGE);
                Component pageLabel = Component.translatable(
                        "screen.delvefold.ores.page", this.snapshot.oreRuleTotal(), page.index() + 1, page.count());
                int pagerEnd = this.contentLeft() + footerPagerButtonWidth() * 2 + 16;
                int pageLabelRight = this.contentRight() - footerDoneWidth() - 8;
                if (this.contentWidth() >= 430 && pagerEnd + this.font.width(pageLabel) <= pageLabelRight) {
                    graphics.drawString(this.font, pageLabel, pagerEnd, this.footerButtonY() + 7, DIM_TEXT, false);
                }
            }
            case PROFILES -> {
                this.drawCard(graphics, x, y, width, height);
                this.drawSectionTitle(
                        graphics, Component.translatable("screen.delvefold.section.profiles"), x + 10, y + 7);
                Component active =
                        Component.translatable("screen.delvefold.profiles.active", this.snapshot.activeProfileId());
                graphics.drawString(this.font, active, x + width - this.font.width(active) - 10, y + 8, ACCENT, false);
                if (!this.profileError.getString().isEmpty()) {
                    graphics.drawString(
                            this.font,
                            this.font.plainSubstrByWidth(this.profileError.getString(), width - 20),
                            x + 10,
                            y + height - 15,
                            DANGER,
                            false);
                } else if (this.snapshot.profiles().size() > profilePageSize()) {
                    int pageSize = profilePageSize();
                    DashboardTabLayout.Page page =
                            dashboardLayout().page(this.snapshot.profiles().size(), this.profilePage, pageSize);
                    graphics.drawString(
                            this.font,
                            Component.translatable(
                                    "screen.delvefold.profiles.page",
                                    page.index() + 1,
                                    page.count(),
                                    this.snapshot.profiles().size()),
                            x + 10,
                            y + height - 15,
                            DIM_TEXT,
                            false);
                }
            }
            case GAMEPLAY -> {
                int controlsWidth = Math.min(374, width);
                this.drawCard(graphics, x, y, controlsWidth, height);
                this.drawSectionTitle(
                        graphics, Component.translatable("screen.delvefold.section.gameplay"), x + 10, y + 7);
                if (!compactHeight() && width - controlsWidth >= 150) {
                    int infoX = x + controlsWidth + 8;
                    int infoWidth = width - controlsWidth - 8;
                    this.drawCard(graphics, infoX, y, infoWidth, height);
                    this.drawSectionTitle(
                            graphics, Component.translatable("screen.delvefold.section.about"), infoX + 10, y + 7);
                    beginBodyScissor(graphics);
                    graphics.drawWordWrap(
                            this.font,
                            Component.translatable("screen.delvefold.gameplay.description"),
                            infoX + 10,
                            bodyScreenY(y + 28),
                            infoWidth - 20,
                            MUTED_TEXT);
                    graphics.drawWordWrap(
                            this.font,
                            Component.translatable("screen.delvefold.gameplay.unaffected"),
                            infoX + 10,
                            bodyScreenY(y + 92),
                            infoWidth - 20,
                            DIM_TEXT);
                    endBodyScissor(graphics);
                }
            }
            case PORTAL -> {
                this.drawCard(graphics, x, y, width, height);
                this.drawSectionTitle(
                        graphics, Component.translatable("screen.delvefold.section.portal"), x + 10, y + 7);
                Component readiness = Component.translatable(
                        this.snapshot.initialized()
                                ? "screen.delvefold.status.ready"
                                : "screen.delvefold.status.not_ready");
                this.drawBadge(
                        graphics,
                        readiness,
                        x + width - this.font.width(readiness) - 20,
                        y + 6,
                        this.snapshot.initialized() ? SUCCESS : WARNING);
                beginBodyScissor(graphics);
                graphics.drawWordWrap(
                        this.font,
                        this.presentation.portalStatus(),
                        x + 10,
                        bodyScreenY(this.portalStatusVirtualY),
                        width - 20,
                        MUTED_TEXT);
                if (!this.portalError.getString().isEmpty()) {
                    graphics.drawWordWrap(
                            this.font,
                            this.portalError,
                            x + 10,
                            bodyScreenY(this.portalErrorVirtualY),
                            width - 20,
                            DANGER);
                }
                endBodyScissor(graphics);
            }
            case IDENTITY -> {
                this.drawCard(graphics, x, y, width, height);
                this.drawSectionTitle(
                        graphics, Component.translatable("screen.delvefold.section.identity"), x + 10, y + 7);
                Component variant = Component.translatable(
                        "screen.delvefold.identity.scale_locked",
                        DelvefoldText.option(
                                "terrain_variant",
                                this.snapshot.identity().terrainVariant().serializedName()));
                graphics.drawString(
                        this.font, variant, x + width - this.font.width(variant) - 10, y + 8, ACCENT, false);
                beginBodyScissor(graphics);
                int messageY = bodyScreenY(this.identityMessageVirtualY);
                graphics.drawWordWrap(
                        this.font,
                        identityMessage(),
                        x + 10,
                        messageY,
                        width - 20,
                        this.identityError.getString().isEmpty() ? DIM_TEXT : DANGER);
                endBodyScissor(graphics);
            }
            case DIAGNOSTICS -> {
                this.drawCard(graphics, x, y, width, height);
                this.drawSectionTitle(
                        graphics, Component.translatable("screen.delvefold.section.diagnostics"), x + 10, y + 7);
                renderDiagnostics(graphics, x, y, width, height);
            }
            case WORLD_MANAGEMENT -> {
                this.drawCard(graphics, x, y, width, height);
                this.drawSectionTitle(
                        graphics, Component.translatable("screen.delvefold.section.world"), x + 10, y + 7);
                int statusY = bodyScreenY(this.worldStatusVirtualY);
                int warningY = bodyScreenY(this.worldWarningVirtualY);
                beginBodyScissor(graphics);
                if (!this.worldContentStacked) {
                    drawClampedWrap(
                            graphics,
                            this.presentation.worldStatus(),
                            x + 244,
                            statusY,
                            width - 258,
                            warningY - 5,
                            MUTED_TEXT);
                } else {
                    drawClampedWrap(
                            graphics,
                            this.presentation.worldStatus(),
                            x + 10,
                            statusY,
                            width - 20,
                            warningY - 5,
                            MUTED_TEXT);
                }
                graphics.fill(x + 10, warningY, x + width - 10, warningY + this.worldWarningHeight, 0xCC2A1C20);
                graphics.fill(x + 10, warningY, x + 13, warningY + this.worldWarningHeight, DANGER);
                drawClampedWrap(
                        graphics,
                        Component.translatable("screen.delvefold.world.warning"),
                        x + 21,
                        warningY + 8,
                        Math.max(1, width - 42),
                        warningY + this.worldWarningHeight - 7,
                        DANGER);
                endBodyScissor(graphics);
            }
        }
        if (hasScrollableBody()) {
            drawBodyScrollbar(graphics, x + width - 5, bodyScrollLayout());
        }
    }

    private Button addBodyButton(
            int x, int y, int width, int height, Component label, Style style, Button.OnPress onPress) {
        return registerBodyWidget(this.addButton(x, y, width, height, label, style, onPress));
    }

    private <T extends AbstractWidget> T registerBodyWidget(T widget) {
        this.bodyScroll.register(widget);
        includeBodyBottom(widget.getY() + widget.getHeight() + 6);
        return widget;
    }

    private void includeBodyBottom(int virtualBottom) {
        this.bodyScroll.includeBottom(virtualBottom);
    }

    private int includeBodyText(Component text, int width, int virtualY) {
        int bottom = virtualY + wrappedTextHeight(text, width);
        includeBodyBottom(bottom + 6);
        return bottom;
    }

    private int wrappedTextHeight(Component text, int width) {
        return Math.max(1, this.font.split(text, Math.max(1, width)).size()) * this.font.lineHeight;
    }

    private boolean hasScrollableBody() {
        return switch (this.selectedTab) {
            case ORES, GAMEPLAY, PORTAL, IDENTITY, WORLD_MANAGEMENT -> true;
            case PROFILES, DIAGNOSTICS -> false;
        };
    }

    private int bodyViewportTop() {
        return dashboardLayout().bodyViewportTop();
    }

    private VerticalScrollLayout bodyScrollLayout() {
        int top = bodyViewportTop();
        int bottom = Math.max(top + 1, this.contentBottom() - 1);
        return new VerticalScrollLayout(top, bottom, this.bodyScroll.virtualBottom());
    }

    private int bodyScreenY(int virtualY) {
        return bodyScrollLayout().screenY(virtualY, this.bodyScroll.scrollOffset());
    }

    private void setBodyScrollOffset(int requestedOffset) {
        this.bodyScroll.setScrollOffset(requestedOffset, bodyScrollLayout());
    }

    private void applyBodyScroll() {
        VerticalScrollLayout layout = bodyScrollLayout();
        this.bodyScroll.apply(layout);
        this.bodyScroll.ensureFocusableVisible(layout);
    }

    private void beginBodyScissor(GuiGraphics graphics) {
        VerticalScrollLayout layout = bodyScrollLayout();
        graphics.enableScissor(
                this.contentLeft() + 1, layout.viewportTop(), this.contentRight() - 1, layout.viewportBottom());
    }

    private static void endBodyScissor(GuiGraphics graphics) {
        graphics.disableScissor();
    }

    private void drawBodyScrollbar(GuiGraphics graphics, int x, VerticalScrollLayout layout) {
        int maximum = layout.maximumScroll();
        if (maximum <= 0) {
            return;
        }
        int trackHeight = layout.viewportHeight();
        int virtualHeight = Math.max(trackHeight, this.bodyScroll.virtualBottom() - layout.viewportTop());
        int thumbHeight = Math.min(trackHeight, Math.max(14, trackHeight * trackHeight / virtualHeight));
        int thumbTravel = Math.max(1, trackHeight - thumbHeight);
        int thumbY = layout.viewportTop() + this.bodyScroll.scrollOffset() * thumbTravel / maximum;
        graphics.fill(x, layout.viewportTop(), x + 3, layout.viewportBottom(), 0xAA0B1318);
        graphics.fill(x, thumbY, x + 3, thumbY + thumbHeight, ACCENT);
    }

    private void renderDiagnostics(GuiGraphics graphics, int x, int y, int width, int height) {
        int lineY = y + (compactHeight() ? 57 : 60);
        int availableLines = Math.max(0, (y + height - 8 - lineY) / 12 + 1);
        int wrapWidth = Math.max(1, width - 24);
        List<String> diagnostics = this.snapshot.diagnostics();
        List<Component> presentedDiagnostics = this.presentation.diagnostics();

        for (int index = 0; index < diagnostics.size() && availableLines > 0; index++) {
            Component diagnostic = presentedDiagnostics.get(index);
            List<FormattedCharSequence> lines = this.font.split(
                    Component.translatable(
                            index == 0
                                    ? "screen.delvefold.diagnostics.entry.first"
                                    : "screen.delvefold.diagnostics.entry.next",
                            diagnostic),
                    wrapWidth);
            boolean entriesFollow = index + 1 < diagnostics.size();
            if (lines.size() + (entriesFollow ? 1 : 0) > availableLines) {
                int detailLines = Math.max(0, availableLines - 1);
                for (int line = 0; line < detailLines; line++) {
                    graphics.drawString(
                            this.font, lines.get(line), x + 12, lineY, index == 0 ? TEXT : MUTED_TEXT, false);
                    lineY += 12;
                }
                if (availableLines > 0) {
                    int remainingEntries = diagnostics.size() - index;
                    graphics.drawString(
                            this.font,
                            Component.translatable(
                                    remainingEntries == 1
                                            ? "screen.delvefold.diagnostics.more_one"
                                            : "screen.delvefold.diagnostics.more_many",
                                    remainingEntries),
                            x + 12,
                            lineY,
                            DIM_TEXT,
                            false);
                }
                return;
            }

            for (FormattedCharSequence line : lines) {
                graphics.drawString(this.font, line, x + 12, lineY, index == 0 ? TEXT : MUTED_TEXT, false);
                lineY += 12;
                availableLines--;
            }
        }
    }

    private void drawClampedWrap(GuiGraphics graphics, Component text, int x, int y, int width, int bottom, int color) {
        if (width <= 0 || y + this.font.lineHeight > bottom) {
            return;
        }
        int lineY = y;
        for (FormattedCharSequence line : this.font.split(text, width)) {
            if (lineY + this.font.lineHeight > bottom) {
                return;
            }
            graphics.drawString(this.font, line, x, lineY, color, false);
            lineY += 10;
        }
    }

    private enum Tab {
        ORES("screen.delvefold.tab.ores"),
        PROFILES("screen.delvefold.tab.profiles"),
        GAMEPLAY("screen.delvefold.tab.gameplay"),
        PORTAL("screen.delvefold.tab.portal"),
        IDENTITY("screen.delvefold.tab.identity"),
        DIAGNOSTICS("screen.delvefold.tab.diagnostics"),
        WORLD_MANAGEMENT("screen.delvefold.tab.world");

        private final String translationKey;

        Tab(String translationKey) {
            this.translationKey = translationKey;
        }
    }
}
