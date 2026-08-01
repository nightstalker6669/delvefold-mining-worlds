package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.network.codec.OreImportStreamCodecs;
import com.nightsta69.delvefold.network.model.OreImportViews.ScanView;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Clientbound, paged view of ore groups discovered by an import scan.
 *
 * <p>The server emits this bounded view only after configure authorization. The embedded scan token remains
 * player-bound and server-validated for later page and preview requests.
 *
 * @param view server-computed scan page and its session metadata
 */
public record OpenOreImportScanPayload(ScanView view) implements CustomPacketPayload {
    /** NeoForge payload type for the clientbound ore-import scan view. */
    public static final Type<OpenOreImportScanPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("delvefold", "open_ore_import_scan"));

    /** Wire codec for the bounded scan view. */
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenOreImportScanPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> OreImportStreamCodecs.writeScan(buffer, payload.view()),
            buffer -> new OpenOreImportScanPayload(OreImportStreamCodecs.readScan(buffer)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
