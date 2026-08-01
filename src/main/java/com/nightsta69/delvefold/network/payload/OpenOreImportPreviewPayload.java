package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.network.codec.OreImportStreamCodecs;
import com.nightsta69.delvefold.network.model.OreImportViews.PreviewView;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenOreImportPreviewPayload(PreviewView view) implements CustomPacketPayload {
    public static final Type<OpenOreImportPreviewPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("delvefold", "open_ore_import_preview"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenOreImportPreviewPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> OreImportStreamCodecs.writePreview(buffer, payload.view()),
            buffer -> new OpenOreImportPreviewPayload(OreImportStreamCodecs.readPreview(buffer)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
