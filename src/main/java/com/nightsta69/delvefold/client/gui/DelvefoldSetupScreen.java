package com.nightsta69.delvefold.client.gui;

import com.nightsta69.delvefold.client.DelvefoldClientRequests;
import com.nightsta69.delvefold.client.gui.widget.DelvefoldButton.Style;
import com.nightsta69.delvefold.config.model.GameplayPreset;
import com.nightsta69.delvefold.config.model.GameplaySettings;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.TerrainVariant;
import com.nightsta69.delvefold.config.model.LandmarkPreset;
import com.nightsta69.delvefold.config.model.WorldIdentitySettings;
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
    private TerrainVariant terrainVariant;
    private LandmarkPreset landmarkPreset;
    private Step step = Step.TERRAIN;
    private boolean lockConfirmed;
    private Button confirmationButton;
    private Button initializeButton;
    private String localStatus = "";
    private int localStatusColor = ACCENT;

    public DelvefoldSetupScreen(AdminSnapshot snapshot) {
        super(Component.translatable("screen.delvefold.setup.title"), snapshot);
        this.terrainMode = snapshot.terrainMode();
        this.orePreset = snapshot.orePreset();
        this.gameplayPreset = snapshot.gameplay().preset();
        this.terrainVariant = snapshot.identity().terrainVariant();
        this.landmarkPreset = snapshot.identity().landmarkPreset();
    }

    @Override
    protected void initPanel() {
        int x = this.contentLeft();
        int width = this.contentWidth();
        int gap = 6;
        int tabWidth = (width - gap * 2) / 3;
        for (Step value : Step.values()) {
            int tabX = x + value.ordinal() * (tabWidth + gap);
            this.addButton(tabX, this.contentTop(), tabWidth, 22,
                    Component.literal((value.ordinal() + 1) + "  •  " + value.label),
                    this.step == value ? Style.TAB_SELECTED : Style.GHOST,
                    button -> moveTo(value));
        }

        switch (this.step) {
            case TERRAIN -> initTerrain();
            case RESOURCES -> initResources();
            case REVIEW -> initReview();
        }

        int footerY = this.panelTop + this.panelHeight - 29;
        if (this.step == Step.TERRAIN) {
            this.addButton(x, footerY, 76, 22, Component.translatable("gui.cancel"), Style.GHOST,
                    button -> this.onClose());
        } else {
            this.addButton(x, footerY, 76, 22, Component.literal("‹  Back"), Style.GHOST,
                    button -> moveTo(Step.values()[this.step.ordinal() - 1]));
        }

        if (this.step != Step.REVIEW) {
            this.addButton(this.contentRight() - 104, footerY, 104, 22,
                    Component.literal("Continue  ›"), Style.PRIMARY,
                    button -> moveTo(Step.values()[this.step.ordinal() + 1]));
        } else {
            this.initializeButton = this.addButton(this.contentRight() - 172, footerY, 172, 22,
                    Component.translatable("screen.delvefold.setup.initialize"), Style.PRIMARY,
                    button -> initialize());
            this.initializeButton.active = this.snapshot.backendReady() && this.lockConfirmed;
        }
    }

    private void initTerrain() {
        int x = this.contentLeft() + 12;
        int y = bodyTop() + 38;
        int width = this.contentWidth() - 24;
        int gap = 8;
        int optionWidth = (width - gap * 2) / 3;
        for (TerrainMode mode : TerrainMode.values()) {
            int optionX = x + mode.ordinal() * (optionWidth + gap);
            this.addButton(optionX, y, optionWidth, 42,
                    Component.literal(pretty(mode.name())),
                    this.terrainMode == mode ? Style.TOGGLE_ON : Style.SECONDARY,
                    button -> {
                        this.terrainMode = mode;
                        resetConfirmation();
                        rebuildWidgets();
                    });
        }
        int variantY = y + 54;
        int variantWidth = (width - gap) / 2;
        for (TerrainVariant variant : TerrainVariant.values()) {
            int optionX = x + variant.ordinal() * (variantWidth + gap);
            this.addButton(optionX, variantY, variantWidth, 24,
                    Component.literal(pretty(variant.name())),
                    this.terrainVariant == variant ? Style.TOGGLE_ON : Style.SECONDARY,
                    button -> {
                        this.terrainVariant = variant;
                        resetConfirmation();
                        rebuildWidgets();
                    });
        }
    }

    private void initResources() {
        int x = this.contentLeft() + 12;
        int y = bodyTop() + 39;
        int width = this.contentWidth() - 24;
        int gap = 8;
        int optionWidth = (width - gap * 2) / 3;
        for (OrePreset preset : OrePreset.values()) {
            int optionX = x + preset.ordinal() * (optionWidth + gap);
            this.addButton(optionX, y, optionWidth, 32,
                    Component.literal(oreName(preset)), this.orePreset == preset ? Style.TOGGLE_ON : Style.SECONDARY,
                    button -> {
                        this.orePreset = preset;
                        resetConfirmation();
                        rebuildWidgets();
                    });
        }
        int gameplayY = y + 76;
        for (GameplayPreset preset : GameplayPreset.values()) {
            int optionX = x + preset.ordinal() * (optionWidth + gap);
            this.addButton(optionX, gameplayY, optionWidth, 32,
                    Component.literal(pretty(preset.name())),
                    this.gameplayPreset == preset ? Style.TOGGLE_ON : Style.SECONDARY,
                    button -> {
                        this.gameplayPreset = preset;
                        resetConfirmation();
                        rebuildWidgets();
                    });
        }
        int landmarkY = y + 124;
        for (LandmarkPreset preset : LandmarkPreset.values()) {
            int optionX = x + preset.ordinal() * (optionWidth + gap);
            this.addButton(optionX, landmarkY, optionWidth, 28,
                    Component.literal(pretty(preset.name())),
                    this.landmarkPreset == preset ? Style.TOGGLE_ON : Style.SECONDARY,
                    button -> {
                        this.landmarkPreset = preset;
                        resetConfirmation();
                        rebuildWidgets();
                    });
        }
    }

    private void initReview() {
        int x = this.contentLeft() + 10;
        int y = bodyTop() + 118;
        int width = this.contentWidth() - 20;
        this.confirmationButton = this.addButton(x, y, width, 24,
                confirmationLabel(), this.lockConfirmed ? Style.TOGGLE_ON : Style.TOGGLE_OFF,
                button -> toggleConfirmation());
    }

    private void moveTo(Step requested) {
        this.step = requested;
        rebuildWidgets();
    }

    private void toggleConfirmation() {
        this.lockConfirmed = !this.lockConfirmed;
        this.confirmationButton.setMessage(confirmationLabel());
        setButtonStyle(this.confirmationButton, this.lockConfirmed ? Style.TOGGLE_ON : Style.TOGGLE_OFF);
        this.initializeButton.active = this.snapshot.backendReady() && this.lockConfirmed;
    }

    private void resetConfirmation() {
        this.lockConfirmed = false;
    }

    private void initialize() {
        if (!this.lockConfirmed || !this.snapshot.backendReady()) {
            return;
        }
        this.initializeButton.active = false;
        this.localStatus = "Sending initialization request to the server…";
        this.localStatusColor = ACCENT;
        DelvefoldClientRequests.send(new InitializeWorldPayload(
                this.snapshot.oreRevision(), this.snapshot.settingsRevision(), this.terrainMode, this.orePreset,
                GameplaySettings.fromPreset(this.gameplayPreset), selectedIdentity(), true));
    }

    private WorldIdentitySettings selectedIdentity() {
        WorldIdentitySettings source = this.snapshot.identity();
        boolean enabled = this.landmarkPreset != LandmarkPreset.PURE_MINING;
        return new WorldIdentitySettings(source.displayName(), this.terrainVariant, this.landmarkPreset,
                enabled && source.surveyStations(), enabled && source.motherlodes(),
                enabled && source.faultLines(), source.renewal());
    }

    private Component confirmationLabel() {
        return Component.literal((this.lockConfirmed ? "CONFIRMED  •  " : "CONFIRM  •  ")
                + "I understand terrain changes require recreation");
    }

    private int bodyTop() {
        return this.contentTop() + 31;
    }

    private static String pretty(String enumName) {
        String normalized = enumName.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private static String oreName(OrePreset preset) {
        return preset == OrePreset.VANILLA_BALANCED ? "Vanilla-balanced" : pretty(preset.name());
    }

    @Override
    protected void renderPanelContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = this.contentLeft();
        int y = bodyTop();
        int width = this.contentWidth();
        int height = this.contentBottom() - y;
        this.drawCard(graphics, x, y, width, height);
        switch (this.step) {
            case TERRAIN -> renderTerrain(graphics, x, y, width);
            case RESOURCES -> renderResources(graphics, x, y, width);
            case REVIEW -> renderReview(graphics, x, y, width);
        }
    }

    private void renderTerrain(GuiGraphics graphics, int x, int y, int width) {
        this.drawSectionTitle(graphics, Component.literal("CHOOSE THE WORLD SHAPE"), x + 10, y + 8);
        graphics.drawWordWrap(this.font, Component.literal(
                "Terrain is the permanent foundation of this mining world. You can change it later only through a confirmed recreation."),
                x + 12, y + 23, width - 24, MUTED_TEXT);
        String detail = switch (this.terrainMode) {
            case FLAT -> "Flat — layered, predictable geology for efficient branch mining and automation.";
            case CAVERN -> "Cavern — an enclosed cave network with a solid roof and underground atmosphere.";
            case WILD -> "Wild — hills, valleys, caves, and an Overworld-like exploration experience.";
        };
        graphics.drawString(this.font, Component.literal("TERRAIN SCALE"), x + 12, y + 83, MUTED_TEXT, false);
        drawNotice(graphics, x + 12, y + 121, width - 24,
                detail + (this.terrainVariant == TerrainVariant.EXPANSIVE
                        ? " Expansive uses deeper or amplified generation." : " Classic uses the original scale."), ACCENT);
    }

    private void renderResources(GuiGraphics graphics, int x, int y, int width) {
        this.drawSectionTitle(graphics, Component.literal("STARTING ORE PROFILE"), x + 10, y + 8);
        graphics.drawString(this.font, Component.literal(oreDescription()), x + 12, y + 76, MUTED_TEXT, false);
        this.drawSectionTitle(graphics, Component.literal("GAMEPLAY & MOB SPAWNING"), x + 10, y + 99);
        this.drawSectionTitle(graphics, Component.literal("LANDMARK DENSITY"), x + 10, y + 150);
        graphics.drawString(this.font, Component.literal(gameplayDescription()), x + 12, y + 185, DIM_TEXT, false);
    }

    private void renderReview(GuiGraphics graphics, int x, int y, int width) {
        this.drawSectionTitle(graphics, Component.literal("REVIEW BEFORE INITIALIZATION"), x + 10, y + 8);
        graphics.drawString(this.font, Component.literal("Terrain"), x + 14, y + 29, MUTED_TEXT, false);
        graphics.drawString(this.font, Component.literal(pretty(this.terrainMode.name())), x + width / 2, y + 29, TEXT, false);
        graphics.drawString(this.font, Component.literal("Ore profile"), x + 14, y + 45, MUTED_TEXT, false);
        graphics.drawString(this.font, Component.literal(oreName(this.orePreset)), x + width / 2, y + 45, TEXT, false);
        graphics.drawString(this.font, Component.literal("Gameplay"), x + 14, y + 61, MUTED_TEXT, false);
        graphics.drawString(this.font, Component.literal(pretty(this.gameplayPreset.name())), x + width / 2, y + 61, TEXT, false);
        graphics.drawString(this.font, Component.literal("Terrain scale"), x + 14, y + 77, MUTED_TEXT, false);
        graphics.drawString(this.font, Component.literal(pretty(this.terrainVariant.name())), x + width / 2, y + 77, TEXT, false);
        graphics.drawString(this.font, Component.literal("Landmarks"), x + 14, y + 93, MUTED_TEXT, false);
        graphics.drawString(this.font, Component.literal(pretty(this.landmarkPreset.name())), x + width / 2, y + 93, TEXT, false);

        String status = !this.snapshot.backendReady() ? this.snapshot.worldStatus() : this.localStatus;
        int color = !this.snapshot.backendReady() ? DANGER : this.localStatusColor;
        if (!status.isEmpty()) {
            drawNotice(graphics, x + 10, y + 154, width - 20, status, color);
        }
    }

    private void drawNotice(GuiGraphics graphics, int x, int y, int width, String message, int color) {
        graphics.fill(x, y, x + width, y + 34, 0xCC111A20);
        graphics.fill(x, y, x + 3, y + 34, color);
        graphics.drawWordWrap(this.font, Component.literal(message), x + 9, y + 7, width - 16, color);
    }

    private String oreDescription() {
        return switch (this.orePreset) {
            case VANILLA_BALANCED -> "Familiar vanilla-style heights and rarity; edit or replace the profile later.";
            case RICH -> "The balanced layout with twice the placement attempts for resource-heavy play.";
            case EMPTY -> "No ore rules at initialization; build the complete profile yourself.";
        };
    }

    private String gameplayDescription() {
        return switch (this.gameplayPreset) {
            case SAFE -> "No natural mob spawning; commands, eggs, breeding, and spawners still work.";
            case HOSTILE -> "Hostile and ambient natural spawning only.";
            case NORMAL -> "All supported natural spawn categories are enabled.";
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

    private enum Step {
        TERRAIN("TERRAIN"),
        RESOURCES("RESOURCES"),
        REVIEW("REVIEW");

        private final String label;

        Step(String label) {
            this.label = label;
        }
    }
}
