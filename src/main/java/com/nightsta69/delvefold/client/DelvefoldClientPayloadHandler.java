package com.nightsta69.delvefold.client;

import com.nightsta69.delvefold.client.gui.DelvefoldDashboardScreen;
import com.nightsta69.delvefold.client.gui.DelvefoldBackupScreen;
import com.nightsta69.delvefold.client.gui.DelvefoldOreRuleWizardScreen;
import com.nightsta69.delvefold.client.gui.DelvefoldScreen;
import com.nightsta69.delvefold.client.gui.DelvefoldSetupScreen;
import com.nightsta69.delvefold.network.model.ActionStatus;
import com.nightsta69.delvefold.network.payload.ActionResultPayload;
import com.nightsta69.delvefold.network.payload.OpenGuiPayload;
import com.nightsta69.delvefold.network.payload.ProfileExportPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class DelvefoldClientPayloadHandler {
    private DelvefoldClientPayloadHandler() {
    }

    public static void open(OpenGuiPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (payload.snapshot().initialized()) {
            if (minecraft.screen instanceof DelvefoldOreRuleWizardScreen wizard
                    && !wizard.closeOnNextSnapshot()) {
                minecraft.setScreen(wizard.refreshed(payload.snapshot()));
            } else if (minecraft.screen instanceof DelvefoldBackupScreen backups) {
                minecraft.setScreen(backups.refreshed(payload.snapshot()));
            } else {
                minecraft.setScreen(minecraft.screen instanceof DelvefoldDashboardScreen dashboard
                        ? dashboard.refreshed(payload.snapshot())
                        : new DelvefoldDashboardScreen(payload.snapshot()));
            }
        } else {
            minecraft.setScreen(new DelvefoldSetupScreen(payload.snapshot()));
        }
    }

    public static void showResult(ActionResultPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        Component message = Component.literal(payload.message());
        if (minecraft.screen instanceof DelvefoldScreen delvefoldScreen) {
            delvefoldScreen.handleActionResult(payload);
        }
        minecraft.player.displayClientMessage(message, payload.status() == ActionStatus.ACCEPTED);
    }

    public static void copyProfileExport(ProfileExportPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.keyboardHandler.setClipboard(payload.json());
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(
                    Component.literal("Copied profile '" + payload.profileId() + "' JSON to the clipboard."), true);
        }
    }
}
