package com.nightsta69.delvefold.api.event;

import java.util.Objects;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;

/** Posted after a server player crosses into a Delvefold landmark structure piece. */
public final class DelvefoldLandmarkDiscoveredEvent extends Event {
    private final ServerPlayer player;
    private final ResourceLocation landmarkId;
    private final ResourceKey<Level> dimension;
    private final ChunkPos startChunk;

    public DelvefoldLandmarkDiscoveredEvent(
            ServerPlayer player, ResourceLocation landmarkId, ResourceKey<Level> dimension, ChunkPos startChunk) {
        this.player = Objects.requireNonNull(player, "player");
        this.landmarkId = Objects.requireNonNull(landmarkId, "landmarkId");
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.startChunk = Objects.requireNonNull(startChunk, "startChunk");
    }

    public ServerPlayer player() {
        return player;
    }

    public ResourceLocation landmarkId() {
        return landmarkId;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public ChunkPos startChunk() {
        return startChunk;
    }
}
