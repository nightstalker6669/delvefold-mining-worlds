package com.nightsta69.delvefold.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OreImportScanRequestPayload(long expectedOreRevision, boolean includeVanilla)
        implements CustomPacketPayload {
    public static final Type<OreImportScanRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("delvefold", "ore_import_scan"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OreImportScanRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarLong(payload.expectedOreRevision());
                buffer.writeBoolean(payload.includeVanilla());
            },
            buffer -> new OreImportScanRequestPayload(buffer.readVarLong(), buffer.readBoolean()));

    public OreImportScanRequestPayload {
        if (expectedOreRevision < 0L) throw new IllegalArgumentException("Negative ore revision");
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
