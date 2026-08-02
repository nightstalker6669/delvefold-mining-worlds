package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.network.codec.OreLibraryStreamCodec;
import com.nightsta69.delvefold.network.model.OreLibraryView;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Clientbound server-authoritative Unified Ores library page.
 *
 * @param view immutable bounded catalog page
 */
public record OpenOreLibraryPayload(OreLibraryView view) implements CustomPacketPayload {
    /** NeoForge payload type for clientbound library pages. */
    public static final Type<OpenOreLibraryPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("delvefold", "open_ore_library"));

    /** Wire codec delegating to the strict bounded library view codec. */
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenOreLibraryPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> OreLibraryStreamCodec.write(buffer, payload.view()),
            buffer -> new OpenOreLibraryPayload(OreLibraryStreamCodec.read(buffer)));

    /** Requires an immutable non-null library page. */
    public OpenOreLibraryPayload {
        Objects.requireNonNull(view, "view");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
