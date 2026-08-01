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

    /**
     * Creates an immutable discovery notification on the server game thread.
     *
     * @param player server player entering the landmark
     * @param landmarkId reloadable landmark definition identifier
     * @param dimension dimension in which discovery occurred
     * @param startChunk structure start chunk, not the player's exact position
     */
    public DelvefoldLandmarkDiscoveredEvent(
            ServerPlayer player, ResourceLocation landmarkId, ResourceKey<Level> dimension, ChunkPos startChunk) {
        this.player = Objects.requireNonNull(player, "player");
        this.landmarkId = Objects.requireNonNull(landmarkId, "landmarkId");
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.startChunk = Objects.requireNonNull(startChunk, "startChunk");
    }

    /**
     * Returns the player credited with discovery.
     *
     * @return the server player credited with discovery
     */
    public ServerPlayer player() {
        return player;
    }

    /**
     * Returns the discovered definition identifier.
     *
     * @return the discovered landmark definition identifier
     */
    public ResourceLocation landmarkId() {
        return landmarkId;
    }

    /**
     * Returns the discovery dimension.
     *
     * @return the dimension containing the discovered structure
     */
    public ResourceKey<Level> dimension() {
        return dimension;
    }

    /**
     * Returns the structure start chunk.
     *
     * @return the structure start chunk without an exact block position
     */
    public ChunkPos startChunk() {
        return startChunk;
    }
}
