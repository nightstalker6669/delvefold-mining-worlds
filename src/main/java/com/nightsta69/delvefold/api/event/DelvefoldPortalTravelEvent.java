package com.nightsta69.delvefold.api.event;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** Cancellable game-bus event fired immediately before Delvefold resolves a portal transition. */
public final class DelvefoldPortalTravelEvent extends Event implements ICancellableEvent {
    private final ServerPlayer player;
    private final ResourceKey<Level> source;
    private final ResourceKey<Level> destination;

    /**
     * Creates a cancellable pre-travel event on the server game thread.
     *
     * @param player player whose transition is being resolved
     * @param source dimension the player is leaving
     * @param destination dimension the player would enter unless the event is cancelled
     */
    public DelvefoldPortalTravelEvent(ServerPlayer player, ResourceKey<Level> source, ResourceKey<Level> destination) {
        this.player = java.util.Objects.requireNonNull(player, "player");
        this.source = java.util.Objects.requireNonNull(source, "source");
        this.destination = java.util.Objects.requireNonNull(destination, "destination");
    }

    /**
     * Returns the traveling player.
     *
     * @return the player attempting portal travel
     */
    public ServerPlayer player() {
        return player;
    }

    /**
     * Returns the source dimension.
     *
     * @return the source dimension
     */
    public ResourceKey<Level> source() {
        return source;
    }

    /**
     * Returns the proposed destination.
     *
     * @return the proposed destination dimension
     */
    public ResourceKey<Level> destination() {
        return destination;
    }
}
