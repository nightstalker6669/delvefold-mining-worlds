package com.nightsta69.delvefold.client;

import com.nightsta69.delvefold.network.payload.AdminActionPayload;
import com.nightsta69.delvefold.network.payload.DeleteOreRulePayload;
import com.nightsta69.delvefold.network.payload.GameplayUpdatePayload;
import com.nightsta69.delvefold.network.payload.InitializeWorldPayload;
import com.nightsta69.delvefold.network.payload.OpenGuiRequestPayload;
import com.nightsta69.delvefold.network.payload.OrePageRequestPayload;
import com.nightsta69.delvefold.network.payload.PortalUpdatePayload;
import com.nightsta69.delvefold.network.payload.ProfileActionPayload;
import com.nightsta69.delvefold.network.payload.ProfileExportRequestPayload;
import com.nightsta69.delvefold.network.payload.SaveOreRulePayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;

public final class DelvefoldClientRequests {
    private DelvefoldClientRequests() {
    }

    public static void openGui() {
        send(new OpenGuiRequestPayload());
    }

    public static void send(InitializeWorldPayload payload) {
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

    public static void requestOrePage(int page, long knownOreRevision) {
        send(new OrePageRequestPayload(page, knownOreRevision));
    }

    private static void send(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }
}
