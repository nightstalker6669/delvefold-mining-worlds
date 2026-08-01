package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.network.codec.OreImportStreamCodecs;
import com.nightsta69.delvefold.network.model.OreImportViews.PreviewView;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Clientbound, paged preview of a proposed ore import.
 *
 * <p>The server sends this bounded view only after configure authorization and validation of the player's import
 * session. Its commit token is still subject to server-side ownership, expiry, revision, and single-use checks.
 *
 * @param view server-computed preview page, validation details, and commit-session metadata
 */
public record OpenOreImportPreviewPayload(PreviewView view) implements CustomPacketPayload {
    /** NeoForge payload type for the clientbound ore-import preview view. */
    public static final Type<OpenOreImportPreviewPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("delvefold", "open_ore_import_preview"));

    /** Wire codec for the bounded preview view. */
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenOreImportPreviewPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> OreImportStreamCodecs.writePreview(buffer, payload.view()),
            buffer -> new OpenOreImportPreviewPayload(OreImportStreamCodecs.readPreview(buffer)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
