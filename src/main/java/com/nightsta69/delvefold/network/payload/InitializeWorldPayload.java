package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.config.model.GameplaySettings;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.WorldIdentitySettings;
import com.nightsta69.delvefold.network.codec.DelvefoldStreamCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record InitializeWorldPayload(
        long expectedOreRevision,
        long expectedSettingsRevision,
        TerrainMode terrainMode,
        OrePreset orePreset,
        GameplaySettings gameplay,
        WorldIdentitySettings identity,
        boolean lockConfirmed)
        implements CustomPacketPayload {

    public static final Type<InitializeWorldPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("delvefold", "initialize_world"));
    public static final StreamCodec<RegistryFriendlyByteBuf, InitializeWorldPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeLong(payload.expectedOreRevision());
                buffer.writeLong(payload.expectedSettingsRevision());
                DelvefoldStreamCodecs.writeEnum(buffer, payload.terrainMode());
                DelvefoldStreamCodecs.writeEnum(buffer, payload.orePreset());
                DelvefoldStreamCodecs.writeGameplay(buffer, payload.gameplay());
                DelvefoldStreamCodecs.writeIdentity(buffer, payload.identity());
                buffer.writeBoolean(payload.lockConfirmed());
            },
            buffer -> new InitializeWorldPayload(
                    buffer.readLong(),
                    buffer.readLong(),
                    DelvefoldStreamCodecs.readEnum(buffer, TerrainMode.class),
                    DelvefoldStreamCodecs.readEnum(buffer, OrePreset.class),
                    DelvefoldStreamCodecs.readGameplay(buffer),
                    DelvefoldStreamCodecs.readIdentity(buffer),
                    buffer.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
