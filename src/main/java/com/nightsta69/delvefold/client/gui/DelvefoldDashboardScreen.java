package com.nightsta69.delvefold.client.gui;

import com.nightsta69.delvefold.client.DelvefoldClientRequests;
import com.nightsta69.delvefold.client.gui.widget.DelvefoldButton.Style;
import com.nightsta69.delvefold.config.model.GameplayPreset;
import com.nightsta69.delvefold.config.model.GameplaySettings;
import com.nightsta69.delvefold.config.model.PortalSettings;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.TerrainVariant;
import com.nightsta69.delvefold.config.model.LandmarkPreset;
import com.nightsta69.delvefold.config.model.RenewalSettings;
import com.nightsta69.delvefold.config.model.WorldIdentitySettings;
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
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class DelvefoldDashboardScreen extends DelvefoldScreen {
    private final Tab selectedTab;
    private final int orePage;
    private final int profilePage;
    private GameplayPreset gameplayPreset;
    private boolean monsters;
    private boolean creatures;
    private boolean ambient;
    private boolean waterCreatures;
    private boolean patrols;
    private boolean phantoms;
    private TerrainMode recreateTerrain;
    private TerrainVariant recreateVariant;
    private boolean portalEnabled;
    private boolean portalOverworldOnly;
    private String portalCooldown;
    private String portalScale;
    private String portalError = "";
    private String profileName = "my_profile";
    private String profileError = "";
    private String identityName;
    private LandmarkPreset landmarkPreset;
    private boolean renewalEnabled;
    private String renewalDays;
    private String renewalWarning;
    private String identityError = "";
    private String deleteArmedProfile = "";
    private boolean profileOverwrite;
    private AdminOperation armedOperation;
    private Button destructiveButton;
    private Button secondaryDestructiveButton;
    private Button cancelPendingButton;

    public DelvefoldDashboardScreen(AdminSnapshot snapshot) {
        this(snapshot, Tab.ORES, snapshot.orePage(), 0);
    }

    public DelvefoldDashboardScreen refreshed(AdminSnapshot updatedSnapshot) {
        return new DelvefoldDashboardScreen(updatedSnapshot, this.selectedTab, updatedSnapshot.orePage(), this.profilePage);
    }

    private DelvefoldDashboardScreen(AdminSnapshot snapshot, Tab selectedTab, int orePage, int profilePage) {
        super(Component.translatable("screen.delvefold.dashboard.title"), snapshot);
        this.selectedTab = selectedTab;
        this.orePage = Math.max(0, orePage);
        this.profilePage = Math.max(0, profilePage);
        setGameplay(snapshot.gameplay());
        setPortal(snapshot.portal());
        setIdentity(snapshot.identity());
        this.recreateTerrain = snapshot.terrainMode();
        this.recreateVariant = snapshot.identity().terrainVariant();
    }

    @Override
    protected void initPanel() {
        int available = this.contentWidth();
        int gap = 4;
        int tabWidth = Math.max(1, (available - gap * (Tab.values().length - 1)) / Tab.values().length);
        int x = this.contentLeft();
        int y = this.contentTop();
        for (Tab tab : Tab.values()) {
            Button button = this.addButton(x, y, tabWidth, 24, Component.translatable(tab.translationKey),
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

        int doneWidth = footerDoneWidth();
        this.addButton(this.contentRight() - doneWidth, footerY(), doneWidth, 22,
                Component.translatable("gui.done"), Style.GHOST, button -> this.onClose());
    }

    private void selectTab(Tab tab) {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new DelvefoldDashboardScreen(
                    this.snapshot, tab, this.snapshot.orePage(), this.profilePage));
        }
    }

    private void initOres() {
        int innerWidth = Math.max(1, this.contentWidth() - 20);
        int actionGap = Math.min(6, Math.max(0, innerWidth - 2));
        int addWidth = Math.min(116, Math.max(1, (innerWidth - actionGap) * 3 / 5));
        int refreshWidth = Math.max(1, innerWidth - addWidth - actionGap);
        int y = bodyTop() + (compactHeight() ? 23 : 25);
        this.addButton(this.contentLeft() + 10, y, addWidth, 22,
                Component.translatable("screen.delvefold.ores.add"), Style.PRIMARY,
                button -> this.minecraft.setScreen(new DelvefoldOrePickerScreen(this, this.snapshot)));
        this.addButton(this.contentLeft() + 10 + addWidth + actionGap, y, refreshWidth, 22,
                Component.translatable("screen.delvefold.refresh"), Style.GHOST, button -> refresh());

        List<AdminSnapshot.OreRuleDraft> rules = this.snapshot.oreRules();
        int pageCount = Math.max(1, (this.snapshot.oreRuleTotal()
                + com.nightsta69.delvefold.network.ProtocolLimits.GUI_ORE_RULES_PER_PAGE - 1)
                / com.nightsta69.delvefold.network.ProtocolLimits.GUI_ORE_RULES_PER_PAGE);
        int safePage = Math.min(this.orePage, pageCount - 1);
        int start = 0;
        int end = rules.size();
        int rowY = y + (compactHeight() ? 28 : 30);
        int rowsHeight = Math.max(1, this.contentBottom() - 5 - rowY);
        int rowStep = rules.isEmpty()
                ? 25
                : Math.min(25, Math.max(10, (rowsHeight + 2) / rules.size()));
        int rowHeight = Math.max(9, Math.min(22, rowStep - 2));
        for (int index = start; index < end; index++) {
            AdminSnapshot.OreRuleDraft rule = rules.get(index);
            String state = rule.enabled() ? "ON" : "OFF";
            String label = state + "  •  " + rule.id() + "   —   " + rule.primaryBlockId();
            this.addButton(this.contentLeft() + 10, rowY, innerWidth, rowHeight, Component.literal(label),
                    rule.enabled() ? Style.TOGGLE_ON : Style.TOGGLE_OFF,
                    button -> this.minecraft.setScreen(new DelvefoldOreRuleWizardScreen(this, this.snapshot, rule)));
            rowY += rowStep;
        }

        int pagerY = footerY();
        int pagerWidth = footerPagerButtonWidth();
        int pagerGap = Math.min(6, Math.max(0, this.contentWidth() - footerDoneWidth() - pagerWidth * 2));
        Button previous = this.addButton(this.contentLeft(), pagerY, pagerWidth, 22,
                Component.translatable("screen.delvefold.previous"), Style.GHOST,
                button -> setOrePage(safePage - 1));
        previous.active = safePage > 0;
        Button next = this.addButton(this.contentLeft() + pagerWidth + pagerGap, pagerY, pagerWidth, 22,
                Component.translatable("screen.delvefold.next"), Style.GHOST,
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
        int pageSize = 5;
        int pageCount = Math.max(1, (this.snapshot.profiles().size() + pageSize - 1) / pageSize);
        int safePage = Math.min(this.profilePage, pageCount - 1);
        int start = safePage * pageSize;
        int end = Math.min(start + pageSize, this.snapshot.profiles().size());
        for (int index = start; index < end; index++) {
            AdminSnapshot.ProfileDraft profile = this.snapshot.profiles().get(index);
            boolean active = profile.id().equals(this.snapshot.activeProfileId());
            String flags = active ? "ACTIVE  •  " : (profile.valid() ? "" : "INVALID  •  ");
            this.addButton(x, y, rowWidth, 20,
                    Component.literal(flags + profile.id() + "  —  " + profile.ruleCount() + " rules"),
                    active ? Style.TOGGLE_ON : Style.GHOST,
                    button -> performProfile(ProfileOperation.SELECT, profile.id(), "", "", false));
            Button delete = this.addButton(x + rowWidth + gap, y, deleteWidth, 20,
                    Component.translatable(profile.id().equals(this.deleteArmedProfile)
                            ? "screen.delvefold.confirm" : "screen.delvefold.delete"),
                    Style.DANGER, button -> deleteProfile(profile));
            delete.active = !active && profile.localOverride();
            y += 23;
        }

        if (pageCount > 1) {
            Button previous = this.addButton(this.contentLeft(), footerY(), 58, 22,
                    Component.translatable("screen.delvefold.previous"), Style.GHOST,
                    button -> setProfilePage(safePage - 1));
            previous.active = safePage > 0;
            Button next = this.addButton(this.contentLeft() + 64, footerY(), 58, 22,
                    Component.translatable("screen.delvefold.next"), Style.GHOST,
                    button -> setProfilePage(safePage + 1));
            next.active = safePage + 1 < pageCount;
        }

        int controlsY = bodyTop() + (compactHeight() ? 145 : 154);
        int nameWidth = Math.max(100, innerWidth / 2);
        EditBox name = this.addRenderableWidget(new EditBox(this.font, x, controlsY, nameWidth, 20,
                Component.translatable("screen.delvefold.profiles.id")));
        name.setMaxLength(com.nightsta69.delvefold.network.ProtocolLimits.ID_LENGTH);
        name.setValue(this.profileName);
        name.setHint(Component.translatable("screen.delvefold.profiles.id_hint"));
        name.setResponder(value -> this.profileName = value);
        name.setTextColor(TEXT);
        this.addButton(x + nameWidth + gap, controlsY, innerWidth - nameWidth - gap, 20,
                portalToggleLabel("Overwrite", this.profileOverwrite),
                this.profileOverwrite ? Style.TOGGLE_ON : Style.TOGGLE_OFF, button -> {
                    this.profileOverwrite = !this.profileOverwrite;
                    button.setMessage(portalToggleLabel("Overwrite", this.profileOverwrite));
                    setButtonStyle(button, this.profileOverwrite ? Style.TOGGLE_ON : Style.TOGGLE_OFF);
                });

        int actionY = controlsY + 25;
        int actionGap = 5;
        int actionWidth = Math.max(1, (innerWidth - actionGap * 2) / 3);
        this.addButton(x, actionY, actionWidth, 20,
                Component.translatable("screen.delvefold.profiles.save_current"), Style.PRIMARY,
                button -> performProfile(ProfileOperation.SAVE_CURRENT, "", this.profileName, "",
                        this.profileOverwrite));
        this.addButton(x + actionWidth + actionGap, actionY, actionWidth, 20,
                Component.translatable("screen.delvefold.profiles.import_clipboard"), Style.SECONDARY,
                button -> importClipboard());
        this.addButton(x + (actionWidth + actionGap) * 2, actionY,
                innerWidth - actionWidth * 2 - actionGap * 2, 20,
                Component.translatable("screen.delvefold.profiles.copy_active"), Style.GHOST,
                button -> DelvefoldClientRequests.requestProfileExport(this.snapshot.activeProfileId()));
    }

    private void setProfilePage(int page) {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new DelvefoldDashboardScreen(
                    this.snapshot, Tab.PROFILES, this.snapshot.orePage(), Math.max(0, page)));
        }
    }

    private void deleteProfile(AdminSnapshot.ProfileDraft profile) {
        if (!profile.id().equals(this.deleteArmedProfile)) {
            this.deleteArmedProfile = profile.id();
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
        if (json == null || json.isBlank()
                || json.length() > com.nightsta69.delvefold.network.ProtocolLimits.MAX_PROFILE_CLIPBOARD_CHARS) {
            this.profileError = "Clipboard JSON is empty or too large; use the server import directory.";
            return;
        }
        performProfile(ProfileOperation.IMPORT_CLIPBOARD, "", this.profileName, json, this.profileOverwrite);
    }

    private void performProfile(
            ProfileOperation operation, String sourceId, String targetId, String json, boolean overwrite) {
        this.profileError = "";
        DelvefoldClientRequests.send(new ProfileActionPayload(this.snapshot.oreRevision(), operation,
                sourceId, targetId, json, overwrite));
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
        this.addButton(x, y, controlsWidth, 22, gameplayPresetLabel(), Style.SECONDARY,
                button -> cycleGameplayPreset());
        int firstRow = compact ? 26 : 30;
        int secondRow = compact ? 50 : 58;
        int thirdRow = compact ? 74 : 86;
        addGameplayToggle(x, y + firstRow, toggleWidth, "Monsters", this.monsters,
                value -> this.monsters = value);
        addGameplayToggle(x + toggleWidth + gap, y + firstRow, toggleWidth, "Creatures", this.creatures,
                value -> this.creatures = value);
        addGameplayToggle(x, y + secondRow, toggleWidth, "Ambient", this.ambient,
                value -> this.ambient = value);
        addGameplayToggle(x + toggleWidth + gap, y + secondRow, toggleWidth, "Water mobs", this.waterCreatures,
                value -> this.waterCreatures = value);
        addGameplayToggle(x, y + thirdRow, toggleWidth, "Patrols", this.patrols,
                value -> this.patrols = value);
        addGameplayToggle(x + toggleWidth + gap, y + thirdRow, toggleWidth, "Phantoms", this.phantoms,
                value -> this.phantoms = value);
        this.addButton(x, y + (compact ? 105 : 124), controlsWidth, 22,
                Component.translatable("screen.delvefold.gameplay.save"), Style.PRIMARY,
                button -> saveGameplay());
    }

    private void addGameplayToggle(
            int x, int y, int width, String label, boolean initialValue, java.util.function.Consumer<Boolean> setter) {
        this.addButton(x, y, width, 22, toggleLabel(label, initialValue),
                initialValue ? Style.TOGGLE_ON : Style.TOGGLE_OFF,
                button -> {
                    boolean next = !button.getMessage().getString().startsWith("ON");
                    setter.accept(next);
                    button.setMessage(toggleLabel(label, next));
                    setButtonStyle(button, next ? Style.TOGGLE_ON : Style.TOGGLE_OFF);
                });
    }

    private void cycleGameplayPreset() {
        GameplayPreset[] values = GameplayPreset.values();
        GameplayPreset next = values[(this.gameplayPreset.ordinal() + 1) % values.length];
        setGameplay(GameplaySettings.fromPreset(next));
        if (this.minecraft != null) {
            this.minecraft.setScreen(new DelvefoldDashboardScreen(
                    new AdminSnapshot(this.snapshot.oreRevision(), this.snapshot.settingsRevision(),
                            this.snapshot.backendReady(), this.snapshot.initialized(),
                            this.snapshot.terrainMode(), this.snapshot.orePreset(), currentGameplay(),
                            this.snapshot.portal(), this.snapshot.identity(),
                            this.snapshot.capabilities(),
                            this.snapshot.activeProfileId(), this.snapshot.profiles(), this.snapshot.backups(),
                            this.snapshot.portalStatus(), this.snapshot.worldStatus(), this.snapshot.resetPending(),
                            this.snapshot.diagnostics(), this.snapshot.oreRuleTotal(), this.snapshot.orePage(),
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
        this.addButton(x, y, half, 22, portalToggleLabel("Portal travel", this.portalEnabled),
                this.portalEnabled ? Style.TOGGLE_ON : Style.TOGGLE_OFF, button -> {
                    this.portalEnabled = !this.portalEnabled;
                    button.setMessage(portalToggleLabel("Portal travel", this.portalEnabled));
                    setButtonStyle(button, this.portalEnabled ? Style.TOGGLE_ON : Style.TOGGLE_OFF);
                });
        this.addButton(x + half + gap, y, innerWidth - half - gap, 22,
                portalToggleLabel("Overworld entry only", this.portalOverworldOnly),
                this.portalOverworldOnly ? Style.TOGGLE_ON : Style.TOGGLE_OFF, button -> {
                    this.portalOverworldOnly = !this.portalOverworldOnly;
                    button.setMessage(portalToggleLabel("Overworld entry only", this.portalOverworldOnly));
                    setButtonStyle(button, this.portalOverworldOnly ? Style.TOGGLE_ON : Style.TOGGLE_OFF);
                });

        int fieldsY = y + (compactHeight() ? 30 : 38);
        EditBox cooldown = this.addRenderableWidget(new EditBox(this.font, x, fieldsY, half, 20,
                Component.translatable("screen.delvefold.portal.cooldown")));
        cooldown.setMaxLength(4);
        cooldown.setValue(this.portalCooldown);
        cooldown.setHint(Component.translatable("screen.delvefold.portal.cooldown"));
        cooldown.setTextColor(TEXT);
        cooldown.setTextColorUneditable(DIM_TEXT);
        cooldown.setResponder(value -> this.portalCooldown = value);
        EditBox scale = this.addRenderableWidget(new EditBox(this.font, x + half + gap, fieldsY,
                innerWidth - half - gap, 20, Component.translatable("screen.delvefold.portal.scale")));
        scale.setMaxLength(8);
        scale.setValue(this.portalScale);
        scale.setHint(Component.translatable("screen.delvefold.portal.scale"));
        scale.setTextColor(TEXT);
        scale.setTextColorUneditable(DIM_TEXT);
        scale.setResponder(value -> this.portalScale = value);

        int actionY = fieldsY + (compactHeight() ? 27 : 34);
        this.addButton(x, actionY, half, 22,
                Component.translatable("screen.delvefold.portal.save"), Style.PRIMARY,
                button -> savePortal());
        this.addButton(x + half + gap, actionY, innerWidth - half - gap, 22,
                Component.translatable("screen.delvefold.refresh"), Style.GHOST, button -> refresh());
    }

    private void setPortal(PortalSettings portal) {
        this.portalEnabled = portal.enabled();
        this.portalOverworldOnly = portal.allowFromOverworldOnly();
        this.portalCooldown = Integer.toString(portal.cooldownSeconds());
        this.portalScale = Double.toString(portal.coordinateScale());
    }

    private void savePortal() {
        try {
            int cooldown = Integer.parseInt(this.portalCooldown.trim());
            double scale = Double.parseDouble(this.portalScale.trim());
            if (cooldown < 1 || cooldown > 3600 || !Double.isFinite(scale) || scale < 0.01D || scale > 100.0D) {
                throw new NumberFormatException();
            }
            this.portalError = "";
            DelvefoldClientRequests.send(new PortalUpdatePayload(this.snapshot.settingsRevision(),
                    new PortalSettings(this.portalEnabled, this.portalOverworldOnly, cooldown, scale)));
        } catch (NumberFormatException exception) {
            this.portalError = "Cooldown must be 1–3600; scale must be 0.01–100.";
        }
    }

    private static Component portalToggleLabel(String label, boolean enabled) {
        return Component.literal((enabled ? "ON  •  " : "OFF  •  ") + label);
    }

    private void initDiagnostics() {
        int x = this.contentLeft() + 10;
        int y = bodyTop() + (compactHeight() ? 23 : 25);
        int available = Math.max(1, this.contentWidth() - 20);
        int gap = Math.min(6, Math.max(0, (available - 3) / 2));
        int buttonWidth = Math.max(1, (available - gap * 2) / 3);
        int finalButtonWidth = Math.max(1, available - buttonWidth * 2 - gap * 2);
        this.addButton(x, y, buttonWidth, 22, Component.translatable("screen.delvefold.diagnostics.validate"),
                Style.PRIMARY, button -> perform(AdminOperation.VALIDATE_CONFIG, ""));
        this.addButton(x + buttonWidth + gap, y, buttonWidth, 22,
                Component.translatable("screen.delvefold.diagnostics.reload"),
                Style.SECONDARY,
                button -> perform(AdminOperation.RELOAD_CONFIG, ""));
        this.addButton(x + (buttonWidth + gap) * 2, y, finalButtonWidth, 22,
                Component.translatable("screen.delvefold.refresh"),
                Style.GHOST, button -> refresh());
    }

    private void initIdentity() {
        int x = this.contentLeft() + 10;
        int y = bodyTop() + 30;
        int width = this.contentWidth() - 20;
        EditBox name = this.addRenderableWidget(new EditBox(this.font, x, y, width, 20,
                Component.translatable("screen.delvefold.identity.name")));
        name.setMaxLength(64);
        name.setValue(this.identityName);
        name.setHint(Component.translatable("screen.delvefold.identity.name"));
        name.setResponder(value -> this.identityName = value);

        int gap = 6;
        int half = (width - gap) / 2;
        this.addButton(x, y + 34, half, 22,
                landmarkLabel(), Style.SECONDARY,
                button -> {
                    LandmarkPreset[] values = LandmarkPreset.values();
                    this.landmarkPreset = values[(this.landmarkPreset.ordinal() + 1) % values.length];
                    button.setMessage(landmarkLabel());
                });
        this.addButton(x + half + gap, y + 34, width - half - gap, 22,
                toggleLabel("Scheduled renewal", this.renewalEnabled),
                this.renewalEnabled ? Style.TOGGLE_ON : Style.TOGGLE_OFF, button -> {
                    this.renewalEnabled = !this.renewalEnabled;
                    button.setMessage(toggleLabel("Scheduled renewal", this.renewalEnabled));
                    setButtonStyle(button, this.renewalEnabled ? Style.TOGGLE_ON : Style.TOGGLE_OFF);
                });

        EditBox days = this.addRenderableWidget(new EditBox(this.font, x, y + 68, half, 20,
                Component.translatable("screen.delvefold.identity.renewal_days")));
        days.setMaxLength(4);
        days.setValue(this.renewalDays);
        days.setHint(Component.translatable("screen.delvefold.identity.renewal_days"));
        days.setResponder(value -> this.renewalDays = value);
        EditBox warning = this.addRenderableWidget(new EditBox(this.font, x + half + gap, y + 68,
                width - half - gap, 20, Component.translatable("screen.delvefold.identity.warning_minutes")));
        warning.setMaxLength(5);
        warning.setValue(this.renewalWarning);
        warning.setHint(Component.translatable("screen.delvefold.identity.warning_minutes"));
        warning.setResponder(value -> this.renewalWarning = value);

        this.addButton(x, y + 102, width, 22,
                Component.translatable("screen.delvefold.identity.save"), Style.PRIMARY,
                button -> saveIdentity());
    }

    private void setIdentity(WorldIdentitySettings identity) {
        this.identityName = identity.displayName();
        this.landmarkPreset = identity.landmarkPreset();
        this.renewalEnabled = identity.renewal().enabled();
        this.renewalDays = Integer.toString(identity.renewal().intervalDays());
        this.renewalWarning = Integer.toString(identity.renewal().warningMinutes());
    }

    private void saveIdentity() {
        try {
            int days = Integer.parseInt(this.renewalDays.trim());
            int warning = Integer.parseInt(this.renewalWarning.trim());
            if (this.identityName.isBlank() || days < 1 || days > 3650 || warning < 1 || warning > 10080) {
                throw new NumberFormatException();
            }
            WorldIdentitySettings current = this.snapshot.identity();
            boolean landmarks = this.landmarkPreset != LandmarkPreset.PURE_MINING;
            long next = this.renewalEnabled
                    ? (current.renewal().enabled() && current.renewal().intervalDays() == days
                            ? current.renewal().nextRenewalAtEpochMillis()
                            : System.currentTimeMillis() + days * 86_400_000L)
                    : 0L;
            RenewalSettings renewal = new RenewalSettings(this.renewalEnabled, days, warning, next);
            WorldIdentitySettings updated = new WorldIdentitySettings(this.identityName,
                    current.terrainVariant(), this.landmarkPreset,
                    landmarks, landmarks, landmarks, renewal);
            this.identityError = "";
            DelvefoldClientRequests.send(new IdentityUpdatePayload(this.snapshot.settingsRevision(), updated));
        } catch (NumberFormatException exception) {
            this.identityError = "Name 1–64 chars; interval 1–3650 days; warning 1–10080 minutes.";
        }
    }

    private void initWorldManagement() {
        int x = this.contentLeft() + 10;
        int innerWidth = Math.max(1, this.contentWidth() - 20);
        int y = bodyTop() + (compactHeight() ? 22 : 26);
        int choiceGap = 6;
        int choiceWidth = Math.max(1, (innerWidth - choiceGap) / 2);
        this.addButton(x, y, choiceWidth, 22, recreateTerrainLabel(), Style.SECONDARY, button -> {
            TerrainMode[] modes = TerrainMode.values();
            this.recreateTerrain = modes[(this.recreateTerrain.ordinal() + 1) % modes.length];
            button.setMessage(recreateTerrainLabel());
        });
        this.addButton(x + choiceWidth + choiceGap, y, innerWidth - choiceWidth - choiceGap, 22,
                recreateVariantLabel(), Style.SECONDARY, button -> {
                    TerrainVariant[] variants = TerrainVariant.values();
                    this.recreateVariant = variants[(this.recreateVariant.ordinal() + 1) % variants.length];
                    button.setMessage(recreateVariantLabel());
                });
        y += compactHeight() ? 26 : 32;
        int actionGap = Math.min(6, Math.max(0, innerWidth - 2));
        int actionWidth = Math.max(1, (innerWidth - actionGap) / 2);
        int secondaryActionWidth = Math.max(1, innerWidth - actionWidth - actionGap);
        this.destructiveButton = this.addButton(x, y, actionWidth, 22,
                Component.translatable("screen.delvefold.world.delete"), Style.DANGER,
                button -> armOrPerform(AdminOperation.DELETE_WORLD));
        this.secondaryDestructiveButton = this.addButton(x + actionWidth + actionGap, y, secondaryActionWidth, 22,
                Component.translatable("screen.delvefold.world.recreate"), Style.DANGER,
                button -> armOrPerform(AdminOperation.RECREATE_WORLD));
        this.destructiveButton.active = this.snapshot.capabilities().canManageWorld();
        this.secondaryDestructiveButton.active = this.snapshot.capabilities().canManageWorld();
        if (this.snapshot.resetPending()) {
            this.cancelPendingButton = this.addButton(x, y + (compactHeight() ? 24 : 30), innerWidth, 22,
                    Component.translatable("screen.delvefold.world.cancel_reset"),
                    Style.GHOST,
                    button -> armOrPerform(AdminOperation.CANCEL_PENDING_RESET));
            this.cancelPendingButton.active = this.snapshot.capabilities().canManageWorld();
        }
        y += compactHeight() ? 55 : 65;
        this.addButton(x, y, Math.min(220, innerWidth), 22,
                Component.translatable("screen.delvefold.backup.manage"),
                Style.SECONDARY, button -> this.minecraft.setScreen(new DelvefoldBackupScreen(this, this.snapshot)));
        updateDestructiveLabels();
    }

    private void armOrPerform(AdminOperation operation) {
        if (this.armedOperation != operation) {
            this.armedOperation = operation;
            updateDestructiveLabels();
            return;
        }
        perform(operation, switch (operation) {
            case DELETE_WORLD -> "DELETE";
            case RECREATE_WORLD -> "RECREATE:"
                    + this.recreateTerrain.serializedName().toUpperCase(java.util.Locale.ROOT) + ":"
                    + this.recreateVariant.serializedName().toUpperCase(java.util.Locale.ROOT);
            case CANCEL_PENDING_RESET -> "CANCEL";
            default -> "";
        });
    }

    private void updateDestructiveLabels() {
        if (this.destructiveButton != null) {
            Component label = this.armedOperation == AdminOperation.DELETE_WORLD
                    ? Component.translatable("screen.delvefold.world.confirm_delete")
                    : Component.translatable("screen.delvefold.world.delete");
            this.destructiveButton.setMessage(label);
        }
        if (this.secondaryDestructiveButton != null) {
            this.secondaryDestructiveButton.setMessage(this.armedOperation == AdminOperation.RECREATE_WORLD
                    ? Component.translatable("screen.delvefold.world.confirm_recreate")
                    : Component.translatable("screen.delvefold.world.recreate"));
        }
        if (this.cancelPendingButton != null) {
            boolean armed = this.armedOperation == AdminOperation.CANCEL_PENDING_RESET;
            this.cancelPendingButton.setMessage(armed
                    ? Component.translatable("screen.delvefold.world.confirm_cancel")
                    : Component.translatable("screen.delvefold.world.cancel_reset"));
            setButtonStyle(this.cancelPendingButton, armed ? Style.DANGER : Style.GHOST);
        }
    }

    private void perform(AdminOperation operation, String confirmation) {
        DelvefoldClientRequests.send(new AdminActionPayload(
                this.snapshot.settingsRevision(), operation, confirmation));
    }

    private void refresh() {
        perform(AdminOperation.REFRESH, "");
    }

    private void setGameplay(GameplaySettings settings) {
        this.gameplayPreset = settings.preset();
        this.monsters = settings.monsters();
        this.creatures = settings.creatures();
        this.ambient = settings.ambient();
        this.waterCreatures = settings.waterCreatures();
        this.patrols = settings.patrols();
        this.phantoms = settings.phantoms();
    }

    private GameplaySettings currentGameplay() {
        return new GameplaySettings(this.gameplayPreset, this.monsters, this.creatures, this.ambient,
                this.waterCreatures, this.patrols, this.phantoms);
    }

    private Component gameplayPresetLabel() {
        return Component.translatable("screen.delvefold.gameplay.preset",
                DelvefoldText.option("gameplay", this.gameplayPreset.serializedName()));
    }

    private Component recreateTerrainLabel() {
        return Component.translatable("screen.delvefold.world.recreate_terrain",
                DelvefoldText.option("terrain", this.recreateTerrain.serializedName()));
    }

    private Component recreateVariantLabel() {
        return Component.translatable("screen.delvefold.world.recreate_scale",
                DelvefoldText.option("terrain_variant", this.recreateVariant.serializedName()));
    }

    private Component landmarkLabel() {
        return Component.translatable("screen.delvefold.identity.landmarks",
                DelvefoldText.option("landmark", this.landmarkPreset.serializedName()));
    }

    private static Component toggleLabel(String name, boolean enabled) {
        return Component.literal((enabled ? "ON  •  " : "OFF  •  ") + name);
    }

    private int bodyTop() {
        return this.contentTop() + 32;
    }

    private int footerY() {
        return this.panelTop + this.panelHeight - 29;
    }

    private boolean compactHeight() {
        return this.panelHeight < 350;
    }

    private int footerDoneWidth() {
        return Math.min(76, Math.max(54, this.contentWidth() / 5));
    }

    private int footerPagerButtonWidth() {
        int spaceBeforeDone = Math.max(2, this.contentWidth() - footerDoneWidth() - 8);
        return Math.min(64, Math.max(1, (spaceBeforeDone - 6) / 2));
    }

    private static String pretty(String name) {
        String text = name.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
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
                    graphics.drawWordWrap(this.font, Component.translatable("screen.delvefold.ores.empty"),
                            x + 12, y + 63, width - 24, MUTED_TEXT);
                }
                int pageCount = Math.max(1, (this.snapshot.oreRuleTotal()
                        + com.nightsta69.delvefold.network.ProtocolLimits.GUI_ORE_RULES_PER_PAGE - 1)
                        / com.nightsta69.delvefold.network.ProtocolLimits.GUI_ORE_RULES_PER_PAGE);
                Component pageLabel = Component.literal(this.snapshot.oreRuleTotal() + " configured  •  page "
                        + (Math.min(this.orePage, pageCount - 1) + 1) + "/" + pageCount);
                int pagerEnd = this.contentLeft() + footerPagerButtonWidth() * 2 + 16;
                int pageLabelRight = this.contentRight() - footerDoneWidth() - 8;
                if (this.contentWidth() >= 430 && pagerEnd + this.font.width(pageLabel) <= pageLabelRight) {
                    graphics.drawString(this.font, pageLabel, pagerEnd, footerY() + 7, DIM_TEXT, false);
                }
            }
            case PROFILES -> {
                this.drawCard(graphics, x, y, width, height);
                this.drawSectionTitle(graphics, Component.translatable("screen.delvefold.section.profiles"), x + 10, y + 7);
                Component active = Component.translatable("screen.delvefold.profiles.active",
                        this.snapshot.activeProfileId());
                graphics.drawString(this.font, active, x + width - this.font.width(active) - 10,
                        y + 8, ACCENT, false);
                if (!this.profileError.isEmpty()) {
                    graphics.drawString(this.font, this.font.plainSubstrByWidth(this.profileError, width - 20),
                            x + 10, y + height - 15, DANGER, false);
                } else if (this.snapshot.profiles().size() > 5) {
                    int pageCount = (this.snapshot.profiles().size() + 4) / 5;
                    graphics.drawString(this.font,
                            Component.translatable("screen.delvefold.profiles.page",
                                    Math.min(this.profilePage, pageCount - 1) + 1,
                                    pageCount, this.snapshot.profiles().size()),
                            x + 10, y + height - 15, DIM_TEXT, false);
                }
            }
            case GAMEPLAY -> {
                int controlsWidth = Math.min(374, width);
                this.drawCard(graphics, x, y, controlsWidth, height);
                this.drawSectionTitle(graphics, Component.translatable("screen.delvefold.section.gameplay"), x + 10, y + 7);
                if (!compactHeight() && width - controlsWidth >= 150) {
                    int infoX = x + controlsWidth + 8;
                    int infoWidth = width - controlsWidth - 8;
                    this.drawCard(graphics, infoX, y, infoWidth, height);
                    this.drawSectionTitle(graphics, Component.translatable("screen.delvefold.section.about"), infoX + 10, y + 7);
                    graphics.drawWordWrap(this.font,
                            Component.translatable("screen.delvefold.gameplay.description"),
                            infoX + 10, y + 28, infoWidth - 20, MUTED_TEXT);
                    graphics.drawWordWrap(this.font,
                            Component.translatable("screen.delvefold.gameplay.unaffected"),
                            infoX + 10, y + 92, infoWidth - 20, DIM_TEXT);
                }
            }
            case PORTAL -> {
                this.drawCard(graphics, x, y, width, height);
                this.drawSectionTitle(graphics, Component.translatable("screen.delvefold.section.portal"), x + 10, y + 7);
                this.drawBadge(graphics, Component.literal(this.snapshot.initialized() ? "READY" : "NOT READY"),
                        x + width - (this.snapshot.initialized() ? 57 : 82), y + 6,
                        this.snapshot.initialized() ? SUCCESS : WARNING);
                int statusY = y + (compactHeight() ? 112 : 142);
                graphics.drawWordWrap(this.font, Component.literal(this.snapshot.portalStatus()),
                        x + 10, statusY, width - 20, MUTED_TEXT);
                if (!this.portalError.isEmpty()) {
                    graphics.drawString(this.font, this.font.plainSubstrByWidth(this.portalError, width - 20),
                            x + 10, y + height - 17, DANGER, false);
                }
            }
            case IDENTITY -> {
                this.drawCard(graphics, x, y, width, height);
                this.drawSectionTitle(graphics, Component.translatable("screen.delvefold.section.identity"), x + 10, y + 7);
                Component variant = Component.translatable("screen.delvefold.identity.scale_locked",
                        DelvefoldText.option("terrain_variant",
                                this.snapshot.identity().terrainVariant().serializedName()));
                graphics.drawString(this.font, variant, x + width - this.font.width(variant) - 10,
                        y + 8, ACCENT, false);
                if (!this.identityError.isEmpty()) {
                    graphics.drawString(this.font, this.font.plainSubstrByWidth(this.identityError, width - 20),
                            x + 10, y + height - 15, DANGER, false);
                } else {
                    String schedule = this.snapshot.identity().renewal().enabled()
                            ? "Renewal creates a backup, evacuates players, and waits for restart."
                            : "Scheduled renewal is opt-in and currently disabled.";
                    graphics.drawString(this.font, this.font.plainSubstrByWidth(schedule, width - 20),
                            x + 10, y + height - 15, DIM_TEXT, false);
                }
            }
            case DIAGNOSTICS -> {
                this.drawCard(graphics, x, y, width, height);
                this.drawSectionTitle(graphics, Component.translatable("screen.delvefold.section.diagnostics"), x + 10, y + 7);
                renderDiagnostics(graphics, x, y, width, height);
            }
            case WORLD_MANAGEMENT -> {
                this.drawCard(graphics, x, y, width, height);
                this.drawSectionTitle(graphics, Component.translatable("screen.delvefold.section.world"), x + 10, y + 7);
                int warningHeight = width < 380 ? 64 : 52;
                int warningY = y + height - warningHeight - 6;
                if (width >= 430) {
                    drawClampedWrap(graphics, Component.literal(this.snapshot.worldStatus()),
                            x + 244, y + 30, width - 258, warningY - 5, MUTED_TEXT);
                } else {
                    int actionY = y + (compactHeight() ? 48 : 58);
                    int statusY = actionY + (this.snapshot.resetPending()
                            ? (compactHeight() ? 50 : 58)
                            : 30);
                    drawClampedWrap(graphics, Component.literal(this.snapshot.worldStatus()),
                            x + 10, statusY, width - 20, warningY - 5, MUTED_TEXT);
                }
                graphics.fill(x + 10, warningY, x + width - 10, warningY + warningHeight, 0xCC2A1C20);
                graphics.fill(x + 10, warningY, x + 13, warningY + warningHeight, DANGER);
                drawClampedWrap(graphics, Component.translatable("screen.delvefold.world.warning"),
                        x + 21, warningY + 8, Math.max(1, width - 42),
                        warningY + warningHeight - 7, DANGER);
            }
        }
    }

    private void renderDiagnostics(GuiGraphics graphics, int x, int y, int width, int height) {
        int lineY = y + (compactHeight() ? 57 : 60);
        int availableLines = Math.max(0, (y + height - 8 - lineY) / 12 + 1);
        int wrapWidth = Math.max(1, width - 24);
        List<String> diagnostics = this.snapshot.diagnostics();

        for (int index = 0; index < diagnostics.size() && availableLines > 0; index++) {
            List<FormattedCharSequence> lines = this.font.split(
                    Component.literal((index == 0 ? "●  " : "·  ") + diagnostics.get(index)), wrapWidth);
            boolean entriesFollow = index + 1 < diagnostics.size();
            if (lines.size() + (entriesFollow ? 1 : 0) > availableLines) {
                int detailLines = Math.max(0, availableLines - 1);
                for (int line = 0; line < detailLines; line++) {
                    graphics.drawString(this.font, lines.get(line), x + 12, lineY,
                            index == 0 ? TEXT : MUTED_TEXT, false);
                    lineY += 12;
                }
                if (availableLines > 0) {
                    int remainingEntries = diagnostics.size() - index;
                    graphics.drawString(this.font,
                            Component.literal("+ " + remainingEntries + " more diagnostic "
                                    + (remainingEntries == 1 ? "entry" : "entries")),
                            x + 12, lineY, DIM_TEXT, false);
                }
                return;
            }

            for (FormattedCharSequence line : lines) {
                graphics.drawString(this.font, line, x + 12, lineY,
                        index == 0 ? TEXT : MUTED_TEXT, false);
                lineY += 12;
                availableLines--;
            }
        }
    }

    private void drawClampedWrap(
            GuiGraphics graphics, Component text, int x, int y, int width, int bottom, int color) {
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
