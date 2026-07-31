package com.nightsta69.delvefold.client.gui;

import com.nightsta69.delvefold.client.DelvefoldClientRequests;
import com.nightsta69.delvefold.client.gui.widget.DelvefoldButton.Style;
import com.nightsta69.delvefold.config.model.GameplayPreset;
import com.nightsta69.delvefold.config.model.GameplaySettings;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.network.model.AdminOperation;
import com.nightsta69.delvefold.network.model.AdminSnapshot;
import com.nightsta69.delvefold.network.payload.AdminActionPayload;
import com.nightsta69.delvefold.network.payload.GameplayUpdatePayload;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class DelvefoldDashboardScreen extends DelvefoldScreen {
    private final Tab selectedTab;
    private final int orePage;
    private GameplayPreset gameplayPreset;
    private boolean monsters;
    private boolean creatures;
    private boolean ambient;
    private boolean waterCreatures;
    private boolean patrols;
    private boolean phantoms;
    private TerrainMode recreateTerrain;
    private AdminOperation armedOperation;
    private Button destructiveButton;
    private Button secondaryDestructiveButton;
    private Button cancelPendingButton;

    public DelvefoldDashboardScreen(AdminSnapshot snapshot) {
        this(snapshot, Tab.ORES, snapshot.orePage());
    }

    public DelvefoldDashboardScreen refreshed(AdminSnapshot updatedSnapshot) {
        return new DelvefoldDashboardScreen(updatedSnapshot, this.selectedTab, updatedSnapshot.orePage());
    }

    private DelvefoldDashboardScreen(AdminSnapshot snapshot, Tab selectedTab, int orePage) {
        super(Component.translatable("screen.delvefold.dashboard.title"), snapshot);
        this.selectedTab = selectedTab;
        this.orePage = Math.max(0, orePage);
        setGameplay(snapshot.gameplay());
        this.recreateTerrain = snapshot.terrainMode();
    }

    @Override
    protected void initPanel() {
        int available = this.contentWidth();
        int gap = 4;
        int tabWidth = Math.max(1, (available - gap * (Tab.values().length - 1)) / Tab.values().length);
        int x = this.contentLeft();
        int y = this.contentTop();
        for (Tab tab : Tab.values()) {
            Button button = this.addButton(x, y, tabWidth, 24, Component.literal(tab.label),
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
            case GAMEPLAY -> initGameplay();
            case PORTAL -> initPortal();
            case DIAGNOSTICS -> initDiagnostics();
            case WORLD_MANAGEMENT -> initWorldManagement();
        }

        int doneWidth = footerDoneWidth();
        this.addButton(this.contentRight() - doneWidth, footerY(), doneWidth, 22,
                Component.translatable("gui.done"), Style.GHOST, button -> this.onClose());
    }

    private void selectTab(Tab tab) {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new DelvefoldDashboardScreen(this.snapshot, tab, this.snapshot.orePage()));
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
                Component.literal("‹  Prev"), Style.GHOST,
                button -> setOrePage(safePage - 1));
        previous.active = safePage > 0;
        Button next = this.addButton(this.contentLeft() + pagerWidth + pagerGap, pagerY, pagerWidth, 22,
                Component.literal("Next  ›"), Style.GHOST,
                button -> setOrePage(safePage + 1));
        next.active = safePage + 1 < pageCount;
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
                            this.snapshot.portalStatus(), this.snapshot.worldStatus(), this.snapshot.resetPending(),
                            this.snapshot.diagnostics(), this.snapshot.oreRuleTotal(), this.snapshot.orePage(),
                            this.snapshot.oreRules()),
                    Tab.GAMEPLAY,
                    0));
        }
    }

    private void saveGameplay() {
        DelvefoldClientRequests.send(new GameplayUpdatePayload(this.snapshot.settingsRevision(), currentGameplay()));
    }

    private void initPortal() {
        int y = Math.min(bodyTop() + 67, this.contentBottom() - 27);
        this.addButton(this.contentLeft() + 10, Math.max(bodyTop() + 25, y),
                Math.min(100, Math.max(1, this.contentWidth() - 20)), 22,
                Component.translatable("screen.delvefold.refresh"), Style.GHOST, button -> refresh());
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

    private void initWorldManagement() {
        int x = this.contentLeft() + 10;
        int innerWidth = Math.max(1, this.contentWidth() - 20);
        int y = bodyTop() + (compactHeight() ? 22 : 26);
        this.addButton(x, y, Math.min(220, innerWidth), 22, recreateTerrainLabel(), Style.SECONDARY, button -> {
            TerrainMode[] modes = TerrainMode.values();
            this.recreateTerrain = modes[(this.recreateTerrain.ordinal() + 1) % modes.length];
            button.setMessage(recreateTerrainLabel());
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
        if (this.snapshot.resetPending()) {
            this.cancelPendingButton = this.addButton(x, y + (compactHeight() ? 24 : 30), innerWidth, 22,
                    Component.translatable("screen.delvefold.world.cancel_reset"),
                    Style.GHOST,
                    button -> armOrPerform(AdminOperation.CANCEL_PENDING_RESET));
        }
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
            case RECREATE_WORLD -> "RECREATE:" + this.recreateTerrain.serializedName().toUpperCase(java.util.Locale.ROOT);
            case CANCEL_PENDING_RESET -> "CANCEL";
            default -> "";
        });
    }

    private void updateDestructiveLabels() {
        if (this.destructiveButton != null) {
            Component label = this.armedOperation == AdminOperation.DELETE_WORLD
                    ? Component.literal("Confirm: delete mining world")
                    : Component.translatable("screen.delvefold.world.delete");
            this.destructiveButton.setMessage(label);
        }
        if (this.secondaryDestructiveButton != null) {
            this.secondaryDestructiveButton.setMessage(this.armedOperation == AdminOperation.RECREATE_WORLD
                    ? Component.literal("Confirm: recreate mining world")
                    : Component.translatable("screen.delvefold.world.recreate"));
        }
        if (this.cancelPendingButton != null) {
            boolean armed = this.armedOperation == AdminOperation.CANCEL_PENDING_RESET;
            this.cancelPendingButton.setMessage(armed
                    ? Component.literal("Confirm: cancel pending reset")
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
        return Component.literal("Preset: " + pretty(this.gameplayPreset.name()));
    }

    private Component recreateTerrainLabel() {
        return Component.literal("Recreate terrain: " + pretty(this.recreateTerrain.name()));
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
                this.drawSectionTitle(graphics, Component.literal("ORE RULES"), x + 10, y + 7);
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
            case GAMEPLAY -> {
                int controlsWidth = Math.min(374, width);
                this.drawCard(graphics, x, y, controlsWidth, height);
                this.drawSectionTitle(graphics, Component.literal("NATURAL SPAWNING"), x + 10, y + 7);
                if (!compactHeight() && width - controlsWidth >= 150) {
                    int infoX = x + controlsWidth + 8;
                    int infoWidth = width - controlsWidth - 8;
                    this.drawCard(graphics, infoX, y, infoWidth, height);
                    this.drawSectionTitle(graphics, Component.literal("ABOUT"), infoX + 10, y + 7);
                    graphics.drawWordWrap(this.font,
                            Component.translatable("screen.delvefold.gameplay.description"),
                            infoX + 10, y + 28, infoWidth - 20, MUTED_TEXT);
                    graphics.drawWordWrap(this.font,
                            Component.literal("Spawn eggs, commands, breeding and spawners remain available."),
                            infoX + 10, y + 92, infoWidth - 20, DIM_TEXT);
                }
            }
            case PORTAL -> {
                this.drawCard(graphics, x, y, width, height);
                this.drawSectionTitle(graphics, Component.literal("PORTAL STATUS"), x + 10, y + 7);
                this.drawBadge(graphics, Component.literal(this.snapshot.initialized() ? "READY" : "NOT READY"),
                        x + 10, y + 27, this.snapshot.initialized() ? SUCCESS : WARNING);
                graphics.drawWordWrap(this.font, Component.literal(this.snapshot.portalStatus()),
                        x + 10, y + 49, width - 20, MUTED_TEXT);
            }
            case DIAGNOSTICS -> {
                this.drawCard(graphics, x, y, width, height);
                this.drawSectionTitle(graphics, Component.literal("CONFIGURATION HEALTH"), x + 10, y + 7);
                renderDiagnostics(graphics, x, y, width, height);
            }
            case WORLD_MANAGEMENT -> {
                this.drawCard(graphics, x, y, width, height);
                this.drawSectionTitle(graphics, Component.literal("WORLD LIFECYCLE"), x + 10, y + 7);
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
        ORES("Ores"),
        GAMEPLAY("Gameplay"),
        PORTAL("Portal"),
        DIAGNOSTICS("Diagnostics"),
        WORLD_MANAGEMENT("World");

        private final String label;

        Tab(String label) {
            this.label = label;
        }
    }
}
