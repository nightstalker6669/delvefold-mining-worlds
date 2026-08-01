package com.nightsta69.delvefold.api.event;

import com.nightsta69.delvefold.api.MiningWorldView;
import java.util.Optional;
import net.neoforged.bus.api.Event;
import org.jspecify.annotations.Nullable;

/** Posted on the NeoForge game bus after a mining-world lifecycle transaction commits. */
public final class DelvefoldWorldLifecycleEvent extends Event {
    private final Action action;
    private final Optional<MiningWorldView> previous;
    private final Optional<MiningWorldView> current;
    private final String operationId;

    /**
     * Creates an immutable post-commit lifecycle notification.
     *
     * @param action committed lifecycle action
     * @param previous world view before the transaction; {@code null} and an empty optional both mean no prior world
     * @param current world view after the transaction; {@code null} and an empty optional both mean no current world
     * @param operationId audit-safe lifecycle operation identifier; {@code null} becomes an empty string
     */
    // The released API deliberately normalizes null Optional containers at this public boundary;
    // replacing them with a non-null-only contract would misdescribe the compatibility behavior.
    @SuppressWarnings("NullableOptional")
    public DelvefoldWorldLifecycleEvent(
            Action action,
            @Nullable Optional<MiningWorldView> previous,
            @Nullable Optional<MiningWorldView> current,
            @Nullable String operationId) {
        this.action = java.util.Objects.requireNonNull(action, "action");
        this.previous = previous == null ? Optional.empty() : previous;
        this.current = current == null ? Optional.empty() : current;
        this.operationId = operationId == null ? "" : operationId;
    }

    /**
     * Returns the committed action.
     *
     * @return the committed lifecycle action
     */
    public Action action() {
        return action;
    }

    /**
     * Returns the pre-transaction view.
     *
     * @return the immutable pre-transaction world view, if a world existed
     */
    public Optional<MiningWorldView> previous() {
        return previous;
    }

    /**
     * Returns the post-transaction view.
     *
     * @return the immutable post-transaction world view, if a world exists
     */
    public Optional<MiningWorldView> current() {
        return current;
    }

    /**
     * Returns the lifecycle operation identifier.
     *
     * @return the audit-safe operation identifier, possibly empty
     */
    public String operationId() {
        return operationId;
    }

    /** Identifies the committed mining-world lifecycle transition. */
    public enum Action {
        /** The mining world was initialized for the first time. */
        INITIALIZED,
        /** Existing world data was replaced by a newly generated world. */
        RECREATED,
        /** Existing mining-world data was deleted. */
        DELETED
    }
}
