package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.network.codec.DelvefoldStreamCodecs;
import com.nightsta69.delvefold.network.model.AdminSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Clientbound authoritative snapshot used to open or refresh the administration screen.
 *
 * <p>The server sends the snapshot only after checking configure access. Its revisions are the values clients must echo
 * in subsequent optimistic-concurrency requests; possession of a snapshot does not replace server authorization.
 *
 * @param snapshot bounded server-owned administration state and current revisions
 */
public record OpenGuiPayload(AdminSnapshot snapshot) implements CustomPacketPayload {
    /** NeoForge payload type for the clientbound administration snapshot. */
    public static final Type<OpenGuiPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("delvefold", "open_gui"));

    /** Wire codec for the bounded administration snapshot. */
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenGuiPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> DelvefoldStreamCodecs.writeSnapshot(buffer, payload.snapshot()),
            buffer -> new OpenGuiPayload(DelvefoldStreamCodecs.readSnapshot(buffer)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
