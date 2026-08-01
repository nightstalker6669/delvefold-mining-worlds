package com.nightsta69.delvefold.api.event;

import com.nightsta69.delvefold.api.MiningWorldView;
import java.util.Optional;
import net.neoforged.bus.api.Event;

/** Posted on the NeoForge game bus after a mining-world lifecycle transaction commits. */
public final class DelvefoldWorldLifecycleEvent extends Event {
    private final Action action;
    private final Optional<MiningWorldView> previous;
    private final Optional<MiningWorldView> current;
    private final String operationId;

    public DelvefoldWorldLifecycleEvent(
            Action action, Optional<MiningWorldView> previous, Optional<MiningWorldView> current, String operationId) {
        this.action = java.util.Objects.requireNonNull(action, "action");
        this.previous = previous == null ? Optional.empty() : previous;
        this.current = current == null ? Optional.empty() : current;
        this.operationId = operationId == null ? "" : operationId;
    }

    public Action action() {
        return action;
    }

    public Optional<MiningWorldView> previous() {
        return previous;
    }

    public Optional<MiningWorldView> current() {
        return current;
    }

    public String operationId() {
        return operationId;
    }

    public enum Action {
        INITIALIZED,
        RECREATED,
        DELETED
    }
}
