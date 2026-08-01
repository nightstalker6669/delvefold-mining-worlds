package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.network.codec.OreImportStreamCodecs;
import com.nightsta69.delvefold.network.model.OreImportViews.ScanView;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenOreImportScanPayload(ScanView view) implements CustomPacketPayload {
    public static final Type<OpenOreImportScanPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("delvefold", "open_ore_import_scan"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenOreImportScanPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> OreImportStreamCodecs.writeScan(buffer, payload.view()),
            buffer -> new OpenOreImportScanPayload(OreImportStreamCodecs.readScan(buffer)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
