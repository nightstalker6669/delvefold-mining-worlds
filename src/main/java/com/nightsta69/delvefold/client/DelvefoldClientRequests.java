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

/** Client-thread facade for sending bounded, server-authoritative Delvefold requests. */
public final class DelvefoldClientRequests {
    /** Prevents utility-class instantiation. */
    private DelvefoldClientRequests() {}

    /** Requests the setup or administration screen allowed by the server-side player permissions. */
    public static void openGui() {
        send(new OpenGuiRequestPayload());
    }

    /**
     * Sends an initialization request.
     *
     * @param payload initialization request containing explicit lock confirmation and expected revisions
     */
    public static void send(InitializeWorldPayload payload) {
        send((CustomPacketPayload) payload);
    }

    /**
     * Sends an identity-settings update.
     *
     * @param payload identity settings update guarded by its expected settings revision
     */
    public static void send(IdentityUpdatePayload payload) {
        send((CustomPacketPayload) payload);
    }

    /**
     * Sends an ore-rule save request.
     *
     * @param payload ore-rule mutation guarded by its expected ore revision
     */
    public static void send(SaveOreRulePayload payload) {
        send((CustomPacketPayload) payload);
    }

    /**
     * Sends an ore-rule deletion request.
     *
     * @param payload ore-rule deletion guarded by its expected ore revision
     */
    public static void send(DeleteOreRulePayload payload) {
        send((CustomPacketPayload) payload);
    }

    /**
     * Sends a gameplay-settings update.
     *
     * @param payload gameplay settings update guarded by its expected settings revision
     */
    public static void send(GameplayUpdatePayload payload) {
        send((CustomPacketPayload) payload);
    }

    /**
     * Sends a portal-settings update.
     *
     * @param payload portal settings update guarded by its expected settings revision
     */
    public static void send(PortalUpdatePayload payload) {
        send((CustomPacketPayload) payload);
    }

    /**
     * Sends a named-profile lifecycle request.
     *
     * @param payload named-profile lifecycle request validated and authorized by the server
     */
    public static void send(ProfileActionPayload payload) {
        send((CustomPacketPayload) payload);
    }

    /**
     * Requests a clipboard-safe JSON export of a named ore profile.
     *
     * @param profileId bounded server-side profile identifier
     */
    public static void requestProfileExport(String profileId) {
        send(new ProfileExportRequestPayload(profileId));
    }

    /**
     * Sends an administration action.
     *
     * @param payload privileged lifecycle or diagnostic action validated by the server
     */
    public static void send(AdminActionPayload payload) {
        send((CustomPacketPayload) payload);
    }

    /**
     * Sends a backup action.
     *
     * @param payload privileged backup action guarded by its expected settings revision
     */
    public static void send(BackupActionPayload payload) {
        send((CustomPacketPayload) payload);
    }

    /**
     * Requests a bounded page of ore rules from the current server snapshot.
     *
     * @param page zero-based requested page
     * @param knownOreRevision client's last observed non-negative ore revision
     */
    public static void requestOrePage(int page, long knownOreRevision) {
        send(new OrePageRequestPayload(page, knownOreRevision));
    }

    /**
     * Confirms that the authorized guide screen actually opened on the client.
     *
     * @param authorizationId opaque, short-lived server authorization identifier
     */
    public static void confirmGuideOpened(long authorizationId) {
        send(new GuideOpenedPayload(authorizationId));
    }

    /**
     * Requests a bounded, redacted forecast page for a named profile.
     *
     * @param profileId profile to forecast
     * @param page zero-based rule page
     */
    public static void requestForecast(String profileId, int page) {
        send(new ForecastRequestPayload(profileId, page));
    }

    /**
     * Starts server-side discovery of candidate ore blocks.
     *
     * @param expectedOreRevision ore revision used for stale-write protection
     * @param includeVanilla whether vanilla namespace candidates should be included
     */
    public static void requestOreImportScan(long expectedOreRevision, boolean includeVanilla) {
        send(new OreImportScanRequestPayload(expectedOreRevision, includeVanilla));
    }

    /**
     * Requests another bounded page from an existing import scan.
     *
     * @param scanToken opaque server-issued scan token
     * @param page zero-based scan page
     */
    public static void requestOreImportScanPage(String scanToken, int page) {
        send(new OreImportScanPageRequestPayload(scanToken, page));
    }

    /**
     * Requests a non-mutating import diff and workload preview.
     *
     * @param scanToken opaque server-issued scan token
     * @param selectedGroupIds bounded selection copied by the payload contract
     */
    public static void requestOreImportPreview(String scanToken, List<String> selectedGroupIds) {
        send(new OreImportPreviewRequestPayload(scanToken, selectedGroupIds));
    }

    /**
     * Requests another bounded page from an existing import preview.
     *
     * @param commitToken opaque server-issued preview token
     * @param page zero-based preview page
     */
    public static void requestOreImportPreviewPage(String commitToken, int page) {
        send(new OreImportPreviewPageRequestPayload(commitToken, page));
    }

    /**
     * Commits a validated preview into a new, inactive ore profile.
     *
     * @param commitToken opaque token binding the request to the validated preview
     * @param profileId new profile identifier; the server rejects collisions
     */
    public static void createImportedOreProfile(String commitToken, String profileId) {
        send(new OreImportCreatePayload(commitToken, profileId));
    }

    /**
     * Sends one serverbound payload through NeoForge's active client connection.
     *
     * @param payload immutable payload already bounded by its constructor or codec
     */
    private static void send(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }
}
