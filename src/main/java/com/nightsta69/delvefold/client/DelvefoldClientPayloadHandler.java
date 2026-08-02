package com.nightsta69.delvefold.client;

import com.nightsta69.delvefold.client.gui.DelvefoldBackupScreen;
import com.nightsta69.delvefold.client.gui.DelvefoldDashboardScreen;
import com.nightsta69.delvefold.client.gui.DelvefoldGuideScreen;
import com.nightsta69.delvefold.client.gui.DelvefoldOreForecastScreen;
import com.nightsta69.delvefold.client.gui.DelvefoldOreImportScreen;
import com.nightsta69.delvefold.client.gui.DelvefoldOrePickerScreen;
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
import com.nightsta69.delvefold.network.payload.OpenOreLibraryPayload;
import com.nightsta69.delvefold.network.payload.ProfileExportPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Client-thread terminal handlers for Delvefold's clientbound payloads.
 *
 * <p>NeoForge invokes these handlers after network-thread handoff; they mutate only Minecraft client UI and clipboard
 * state. Server payloads remain authoritative.
 */
public final class DelvefoldClientPayloadHandler {
    /** Prevents utility-class instantiation. */
    private DelvefoldClientPayloadHandler() {}

    /**
     * Opens or refreshes the correct administration screen for a server snapshot.
     *
     * @param payload immutable bounded administration snapshot
     */
    public static void open(OpenGuiPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (payload.snapshot().initialized()) {
            if (minecraft.screen instanceof DelvefoldOreRuleWizardScreen wizard) {
                if (!wizard.closeOnNextSnapshot()) {
                    minecraft.setScreen(wizard.refreshed(payload.snapshot()));
                } else {
                    Screen acceptedReturn = wizard.acceptedReturnScreen(payload.snapshot());
                    minecraft.setScreen(
                            acceptedReturn == null ? new DelvefoldDashboardScreen(payload.snapshot()) : acceptedReturn);
                }
            } else if (minecraft.screen instanceof DelvefoldBackupScreen backups) {
                minecraft.setScreen(backups.refreshed(payload.snapshot()));
            } else if (minecraft.screen instanceof DelvefoldOrePickerScreen picker) {
                minecraft.setScreen(picker.refreshed(payload.snapshot()));
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

    /**
     * Routes an action result to the active Delvefold screen and displays its localized message.
     *
     * @param payload server-authoritative status, revision, and encoded translation message
     */
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

    /**
     * Copies a size-bounded profile export into the local clipboard.
     *
     * @param payload profile identifier and server-validated JSON export
     */
    public static void copyProfileExport(ProfileExportPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.keyboardHandler.setClipboard(payload.json());
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(
                    Component.translatable("message.delvefold.profile.copied", payload.profileId()), true);
        }
    }

    /**
     * Opens the read-only Seam Ledger and acknowledges success only after the screen is installed.
     *
     * @param payload redacted guide snapshot plus opaque authorization identifier
     */
    public static void openGuide(OpenGuidePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new DelvefoldGuideScreen(payload.snapshot()));
        if (minecraft.screen instanceof DelvefoldGuideScreen) {
            DelvefoldClientRequests.confirmGuideOpened(payload.authorizationId());
        }
    }

    /**
     * Opens or refreshes the read-only administrative forecast screen.
     *
     * @param payload bounded forecast page containing no seeds or exact coordinates
     */
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

    /**
     * Opens or updates the import wizard with a server-owned discovery page.
     *
     * @param payload bounded scan view tied to an opaque server token
     */
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

    /**
     * Updates an already-open import wizard with a non-mutating preview page.
     *
     * @param payload bounded validation, diff, and workload preview
     */
    public static void openOreImportPreview(OpenOreImportPreviewPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof DelvefoldOreImportScreen importer) {
            importer.acceptPreview(payload.view());
        }
    }

    /**
     * Accepts a server-authoritative Unified Ores library page.
     *
     * <p>The picker installs the concrete page-routing behavior; retaining this bounded terminal hook keeps common
     * network registration dedicated-server safe while the client screen owns presentation state.
     *
     * @param payload immutable bounded library page
     */
    public static void openOreLibrary(OpenOreLibraryPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof DelvefoldOrePickerScreen picker) {
            picker.acceptLibrary(payload.view());
        }
    }
}
