package com.nightsta69.delvefold.client.gui;

import com.nightsta69.delvefold.client.DelvefoldClientRequests;
import com.nightsta69.delvefold.client.gui.widget.DelvefoldButton.Style;
import com.nightsta69.delvefold.config.model.GameplayPreset;
import com.nightsta69.delvefold.config.model.GameplaySettings;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.network.model.ActionStatus;
import com.nightsta69.delvefold.network.model.AdminSnapshot;
import com.nightsta69.delvefold.network.payload.ActionResultPayload;
import com.nightsta69.delvefold.network.payload.InitializeWorldPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class DelvefoldSetupScreen extends DelvefoldScreen {
    private TerrainMode terrainMode;
    private OrePreset orePreset;
    private GameplayPreset gameplayPreset;
    private boolean lockConfirmed;
    private Button terrainButton;
    private Button orePresetButton;
    private Button gameplayButton;
    private Button confirmationButton;
    private Button initializeButton;
    private String localStatus = "";
    private int localStatusColor = ACCENT;

    public DelvefoldSetupScreen(AdminSnapshot snapshot) {
        super(Component.translatable("screen.delvefold.setup.title"), snapshot);
        this.terrainMode = snapshot.terrainMode();
        this.orePreset = snapshot.orePreset();
        this.gameplayPreset = snapshot.gameplay().preset();
    }

    @Override
    protected void initPanel() {
        int x = this.contentLeft();
        int width = this.contentWidth();
        boolean compact = this.panelHeight < 350;
        int cardY = this.contentTop() + (compact ? 24 : 30);
        int optionOffset = Math.min(Math.min(150, Math.max(90, width / 3)), Math.max(60, width - 92));
        int optionX = x + optionOffset;
        int optionWidth = x + width - 12 - optionX;
        int firstOptionY = cardY + (compact ? 20 : 22);
        int optionStep = compact ? 27 : 32;

        this.terrainButton = this.addButton(optionX, firstOptionY, optionWidth, compact ? 20 : 24,
                terrainLabel(), Style.SECONDARY, button -> cycleTerrain());
        this.orePresetButton = this.addButton(optionX, firstOptionY + optionStep, optionWidth, compact ? 20 : 24,
                orePresetLabel(), Style.SECONDARY, button -> cycleOrePreset());
        this.gameplayButton = this.addButton(optionX, firstOptionY + optionStep * 2, optionWidth, compact ? 20 : 24,
                gameplayLabel(), Style.SECONDARY, button -> cycleGameplay());

        int confirmationY = cardY + (compact ? 112 : 132);
        this.confirmationButton = this.addButton(x + 10, confirmationY, width - 20, compact ? 20 : 24,
                confirmationLabel(), Style.TOGGLE_OFF, button -> toggleConfirmation());

        int footerY = this.panelTop + this.panelHeight - 29;
        int initializeWidth = Math.min(172, Math.max(86, width - 84));
        this.initializeButton = this.addButton(this.contentRight() - initializeWidth, footerY, initializeWidth, 22,
                Component.translatable("screen.delvefold.setup.initialize"), Style.PRIMARY, button -> initialize());
        this.initializeButton.active = this.snapshot.backendReady() && this.lockConfirmed;

        this.addButton(x, footerY, 76, 22,
                Component.translatable("gui.cancel"), Style.GHOST, button -> this.onClose());
    }

    private void cycleTerrain() {
        TerrainMode[] values = TerrainMode.values();
        this.terrainMode = values[(this.terrainMode.ordinal() + 1) % values.length];
        this.terrainButton.setMessage(terrainLabel());
        resetConfirmation();
    }

    private void cycleOrePreset() {
        OrePreset[] values = OrePreset.values();
        this.orePreset = values[(this.orePreset.ordinal() + 1) % values.length];
        this.orePresetButton.setMessage(orePresetLabel());
        resetConfirmation();
    }

    private void cycleGameplay() {
        GameplayPreset[] values = GameplayPreset.values();
        this.gameplayPreset = values[(this.gameplayPreset.ordinal() + 1) % values.length];
        this.gameplayButton.setMessage(gameplayLabel());
        resetConfirmation();
    }

    private void toggleConfirmation() {
        this.lockConfirmed = !this.lockConfirmed;
        this.confirmationButton.setMessage(confirmationLabel());
        setButtonStyle(this.confirmationButton, this.lockConfirmed ? Style.TOGGLE_ON : Style.TOGGLE_OFF);
        this.initializeButton.active = this.snapshot.backendReady() && this.lockConfirmed;
    }

    private void resetConfirmation() {
        this.lockConfirmed = false;
        if (this.confirmationButton != null) {
            this.confirmationButton.setMessage(confirmationLabel());
            setButtonStyle(this.confirmationButton, Style.TOGGLE_OFF);
        }
        if (this.initializeButton != null) {
            this.initializeButton.active = false;
        }
    }

    private void initialize() {
        if (!this.lockConfirmed || !this.snapshot.backendReady()) {
            return;
        }
        this.initializeButton.active = false;
        this.localStatus = "Sending initialization request to the server…";
        this.localStatusColor = ACCENT;
        DelvefoldClientRequests.send(new InitializeWorldPayload(
                this.snapshot.oreRevision(),
                this.snapshot.settingsRevision(),
                this.terrainMode,
                this.orePreset,
                GameplaySettings.fromPreset(this.gameplayPreset),
                true));
    }

    private Component terrainLabel() {
        return Component.literal(display(this.terrainMode.name()) + "  ›");
    }

    private Component orePresetLabel() {
        String name = this.orePreset == OrePreset.VANILLA_BALANCED ? "Vanilla-balanced" : display(this.orePreset.name());
        return Component.literal(name + "  ›");
    }

    private Component gameplayLabel() {
        return Component.literal(display(this.gameplayPreset.name()) + "  ›");
    }

    private Component confirmationLabel() {
        return Component.literal((this.lockConfirmed ? "CONFIRMED  •  " : "CONFIRM  •  ")
                + "Lock these generation choices");
    }

    private static String display(String enumName) {
        String normalized = enumName.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    @Override
    protected void renderPanelContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = this.contentLeft();
        int width = this.contentWidth();
        boolean compact = this.panelHeight < 350;
        int cardY = this.contentTop() + (compact ? 24 : 30);
        graphics.drawWordWrap(this.font,
                Component.translatable("screen.delvefold.setup.description"),
                x, this.contentTop(), width, MUTED_TEXT);

        this.drawCard(graphics, x, cardY, width, compact ? 104 : 116);
        this.drawSectionTitle(graphics, Component.literal("WORLD PROFILE"), x + 12, cardY + 6);
        int firstLabelY = cardY + (compact ? 23 : 25);
        int labelStep = compact ? 27 : 32;
        drawOptionLabel(graphics, "Terrain", terrainDescription(), x + 14, firstLabelY, compact);
        drawOptionLabel(graphics, "Ore profile", oreDescription(), x + 14, firstLabelY + labelStep, compact);
        drawOptionLabel(graphics, "Gameplay", gameplayDescription(), x + 14, firstLabelY + labelStep * 2, compact);

        int statusY = compact ? this.contentBottom() - 27 : cardY + 164;
        int statusColor = WARNING;
        Component status = Component.translatable("screen.delvefold.setup.warning");
        if (!this.snapshot.backendReady()) {
            status = Component.literal(this.snapshot.worldStatus());
            statusColor = DANGER;
        } else if (!this.localStatus.isEmpty()) {
            status = Component.literal(this.localStatus);
            statusColor = this.localStatusColor;
        }
        graphics.fill(x, statusY - 5, x + width, statusY + 27, 0xCC171F24);
        graphics.fill(x, statusY - 5, x + 3, statusY + 27, statusColor);
        graphics.drawWordWrap(this.font, status, x + 10, statusY + 1, width - 18, statusColor);
    }

    private void drawOptionLabel(
            GuiGraphics graphics, String label, String description, int x, int y, boolean compact) {
        graphics.drawString(this.font, Component.literal(label), x, y, TEXT, false);
        if (!compact && this.contentWidth() >= 420) {
            graphics.drawString(this.font, Component.literal(description), x, y + 10, DIM_TEXT, false);
        }
    }

    private String terrainDescription() {
        return switch (this.terrainMode) {
            case FLAT -> "Layered and predictable";
            case CAVERN -> "Enclosed cave network";
            case WILD -> "Overworld-shaped terrain";
        };
    }

    private String oreDescription() {
        return switch (this.orePreset) {
            case VANILLA_BALANCED -> "Familiar vanilla balance";
            case RICH -> "Twice the ore attempts";
            case EMPTY -> "Start with no ore rules";
        };
    }

    private String gameplayDescription() {
        return switch (this.gameplayPreset) {
            case SAFE -> "No natural mob spawning";
            case HOSTILE -> "Hostile and ambient mobs";
            case NORMAL -> "All natural categories";
        };
    }

    @Override
    public void handleActionResult(ActionResultPayload payload) {
        this.localStatus = payload.message();
        this.localStatusColor = payload.status() == ActionStatus.ACCEPTED ? SUCCESS : DANGER;
        if (payload.status() != ActionStatus.ACCEPTED && this.initializeButton != null) {
            this.initializeButton.active = this.snapshot.backendReady() && this.lockConfirmed;
        }
    }
}
