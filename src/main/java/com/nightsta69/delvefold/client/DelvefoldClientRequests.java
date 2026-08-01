package com.nightsta69.delvefold.client;

import com.nightsta69.delvefold.network.payload.AdminActionPayload;
import com.nightsta69.delvefold.network.payload.BackupActionPayload;
import com.nightsta69.delvefold.network.payload.DeleteOreRulePayload;
import com.nightsta69.delvefold.network.payload.ForecastRequestPayload;
import com.nightsta69.delvefold.network.payload.GameplayUpdatePayload;
import com.nightsta69.delvefold.network.payload.GuideOpenedPayload;
import com.nightsta69.delvefold.network.payload.IdentityUpdatePayload;
import com.nightsta69.delvefold.network.payload.InitializeWorldPayload;
import com.nightsta69.delvefold.network.payload.OpenGuiRequestPayload;
import com.nightsta69.delvefold.network.payload.OreImportCreatePayload;
import com.nightsta69.delvefold.network.payload.OreImportPreviewPageRequestPayload;
import com.nightsta69.delvefold.network.payload.OreImportPreviewRequestPayload;
import com.nightsta69.delvefold.network.payload.OreImportScanPageRequestPayload;
import com.nightsta69.delvefold.network.payload.OreImportScanRequestPayload;
import com.nightsta69.delvefold.network.payload.OrePageRequestPayload;
import com.nightsta69.delvefold.network.payload.PortalUpdatePayload;
import com.nightsta69.delvefold.network.payload.ProfileActionPayload;
import com.nightsta69.delvefold.network.payload.ProfileExportRequestPayload;
import com.nightsta69.delvefold.network.payload.SaveOreRulePayload;
import java.util.List;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;

public final class DelvefoldClientRequests {
    private DelvefoldClientRequests() {}

    public static void openGui() {
        send(new OpenGuiRequestPayload());
    }

    public static void send(InitializeWorldPayload payload) {
        send((CustomPacketPayload) payload);
    }

    public static void send(IdentityUpdatePayload payload) {
        send((CustomPacketPayload) payload);
    }

    public static void send(SaveOreRulePayload payload) {
        send((CustomPacketPayload) payload);
    }

    public static void send(DeleteOreRulePayload payload) {
        send((CustomPacketPayload) payload);
    }

    public static void send(GameplayUpdatePayload payload) {
        send((CustomPacketPayload) payload);
    }

    public static void send(PortalUpdatePayload payload) {
        send((CustomPacketPayload) payload);
    }

    public static void send(ProfileActionPayload payload) {
        send((CustomPacketPayload) payload);
    }

    public static void requestProfileExport(String profileId) {
        send(new ProfileExportRequestPayload(profileId));
    }

    public static void send(AdminActionPayload payload) {
        send((CustomPacketPayload) payload);
    }

    public static void send(BackupActionPayload payload) {
        send((CustomPacketPayload) payload);
    }

    public static void requestOrePage(int page, long knownOreRevision) {
        send(new OrePageRequestPayload(page, knownOreRevision));
    }

    public static void confirmGuideOpened(long authorizationId) {
        send(new GuideOpenedPayload(authorizationId));
    }

    public static void requestForecast(String profileId, int page) {
        send(new ForecastRequestPayload(profileId, page));
    }

    public static void requestOreImportScan(long expectedOreRevision, boolean includeVanilla) {
        send(new OreImportScanRequestPayload(expectedOreRevision, includeVanilla));
    }

    public static void requestOreImportScanPage(String scanToken, int page) {
        send(new OreImportScanPageRequestPayload(scanToken, page));
    }

    public static void requestOreImportPreview(String scanToken, List<String> selectedGroupIds) {
        send(new OreImportPreviewRequestPayload(scanToken, selectedGroupIds));
    }

    public static void requestOreImportPreviewPage(String commitToken, int page) {
        send(new OreImportPreviewPageRequestPayload(commitToken, page));
    }

    public static void createImportedOreProfile(String commitToken, String profileId) {
        send(new OreImportCreatePayload(commitToken, profileId));
    }

    private static void send(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }
}
