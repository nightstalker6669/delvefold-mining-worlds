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

    public DelvefoldPortalTravelEvent(ServerPlayer player, ResourceKey<Level> source,
            ResourceKey<Level> destination) {
        this.player = java.util.Objects.requireNonNull(player, "player");
        this.source = java.util.Objects.requireNonNull(source, "source");
        this.destination = java.util.Objects.requireNonNull(destination, "destination");
    }

    public ServerPlayer player() {
        return player;
    }

    public ResourceKey<Level> source() {
        return source;
    }

    public ResourceKey<Level> destination() {
        return destination;
    }
}
