package com.nightsta69.delvefold.reset;

import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;

/**
 * Establishes one lock order for delete/recreate and restore draft transitions.
 *
 * <p>Both services keep their own implementation locks, but every request,
 * confirmation, and cancellation enters this coordinator first. Cross-service
 * state queries therefore cannot form the opposing service-lock cycle that a
 * pair of ad-hoc synchronized checks would create.</p>
 */
final class LifecycleOperationCoordinator {
    private static final Object LOCK = new Object();

    private LifecycleOperationCoordinator() {
    }

    static <T> T coordinate(Supplier<T> action) {
        Objects.requireNonNull(action, "action");
        synchronized (LOCK) {
            return action.get();
        }
    }

    static void coordinate(Runnable action) {
        Objects.requireNonNull(action, "action");
        synchronized (LOCK) {
            action.run();
        }
    }

    static boolean conflictingOperationExists(MinecraftServer server, Kind requestedKind) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(requestedKind, "requestedKind");
        synchronized (LOCK) {
            boolean worldOperation = requestedKind == Kind.RESTORE
                    && WorldOperationService.get().hasActiveLifecycleOperation(server);
            boolean restore = requestedKind == Kind.WORLD_OPERATION
                    && WorldRestoreService.get().hasActiveLifecycleOperation(server);
            return conflicts(requestedKind, worldOperation, restore);
        }
    }

    /** Pure policy seam used by tests and by the coordinated service query above. */
    static boolean conflicts(Kind requestedKind, boolean worldOperation, boolean restore) {
        Objects.requireNonNull(requestedKind, "requestedKind");
        return switch (requestedKind) {
            case WORLD_OPERATION -> restore;
            case RESTORE -> worldOperation;
        };
    }

    enum Kind {
        WORLD_OPERATION,
        RESTORE
    }
}
