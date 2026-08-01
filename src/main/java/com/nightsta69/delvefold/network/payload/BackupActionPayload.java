package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.network.ProtocolLimits;
import com.nightsta69.delvefold.network.codec.DelvefoldStreamCodecs;
import com.nightsta69.delvefold.network.model.BackupOperation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BackupActionPayload(long expectedSettingsRevision, BackupOperation operation, String backupId)
        implements CustomPacketPayload {
    public static final Type<BackupActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("delvefold", "backup_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BackupActionPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeLong(payload.expectedSettingsRevision());
                DelvefoldStreamCodecs.writeEnum(buffer, payload.operation());
                DelvefoldStreamCodecs.writeString(buffer, payload.backupId(), ProtocolLimits.SHORT_TEXT_LENGTH);
            },
            buffer -> new BackupActionPayload(
                    buffer.readLong(),
                    DelvefoldStreamCodecs.readEnum(buffer, BackupOperation.class),
                    DelvefoldStreamCodecs.readString(buffer, ProtocolLimits.SHORT_TEXT_LENGTH)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
