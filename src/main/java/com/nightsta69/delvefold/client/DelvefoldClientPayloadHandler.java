package com.nightsta69.delvefold.client;

import com.nightsta69.delvefold.client.gui.DelvefoldBackupScreen;
import com.nightsta69.delvefold.client.gui.DelvefoldDashboardScreen;
import com.nightsta69.delvefold.client.gui.DelvefoldGuideScreen;
import com.nightsta69.delvefold.client.gui.DelvefoldOreForecastScreen;
import com.nightsta69.delvefold.client.gui.DelvefoldOreImportScreen;
import com.nightsta69.delvefold.client.gui.DelvefoldOreRuleWizardScreen;
import com.nightsta69.delvefold.client.gui.DelvefoldScreen;
import com.nightsta69.delvefold.client.gui.DelvefoldSetupScreen;
import com.nightsta69.delvefold.client.gui.DelvefoldText;
import com.nightsta69.delvefold.network.model.ActionStatus;
import com.nightsta69.delvefold.network.payload.ActionResultPayload;
import com.nightsta69.delvefold.network.payload.OpenForecastPayload;
import com.nightsta69.delvefold.network.payload.OpenGuiPayload;
import com.nightsta69.delvefold.network.payload.OpenGuidePayload;
import com.nightsta69.delvefold.network.payload.OpenOreImportPreviewPayload;
import com.nightsta69.delvefold.network.payload.OpenOreImportScanPayload;
import com.nightsta69.delvefold.network.payload.ProfileExportPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class DelvefoldClientPayloadHandler {
    private DelvefoldClientPayloadHandler() {}

    public static void open(OpenGuiPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (payload.snapshot().initialized()) {
            if (minecraft.screen instanceof DelvefoldOreRuleWizardScreen wizard && !wizard.closeOnNextSnapshot()) {
                minecraft.setScreen(wizard.refreshed(payload.snapshot()));
            } else if (minecraft.screen instanceof DelvefoldBackupScreen backups) {
                minecraft.setScreen(backups.refreshed(payload.snapshot()));
            } else {
                minecraft.setScreen(
                        minecraft.screen instanceof DelvefoldDashboardScreen dashboard
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
        Component message = DelvefoldText.serverMessage(payload.message());
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
                    Component.translatable("message.delvefold.profile.copied", payload.profileId()), true);
        }
    }

    public static void openGuide(OpenGuidePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new DelvefoldGuideScreen(payload.snapshot()));
        if (minecraft.screen instanceof DelvefoldGuideScreen) {
            DelvefoldClientRequests.confirmGuideOpened(payload.authorizationId());
        }
    }

    public static void openForecast(OpenForecastPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof DelvefoldOreForecastScreen forecastScreen) {
            minecraft.setScreen(forecastScreen.refreshed(payload.forecast()));
            return;
        }
        if (minecraft.screen instanceof DelvefoldScreen delvefoldScreen) {
            minecraft.setScreen(new DelvefoldOreForecastScreen(
                    minecraft.screen, delvefoldScreen.adminSnapshot(), payload.forecast()));
            return;
        }
        minecraft.setScreen(new DelvefoldOreForecastScreen(
                minecraft.screen,
                com.nightsta69.delvefold.network.model.AdminSnapshot.unavailable(),
                payload.forecast()));
    }

    public static void openOreImportScan(OpenOreImportScanPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof DelvefoldOreImportScreen importer) {
            importer.acceptScan(payload.view());
            return;
        }
        if (minecraft.screen instanceof DelvefoldScreen delvefoldScreen) {
            DelvefoldOreImportScreen importer =
                    new DelvefoldOreImportScreen(minecraft.screen, delvefoldScreen.adminSnapshot());
            importer.acceptScan(payload.view());
            minecraft.setScreen(importer);
        }
    }

    public static void openOreImportPreview(OpenOreImportPreviewPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof DelvefoldOreImportScreen importer) {
            importer.acceptPreview(payload.view());
        }
    }
}
