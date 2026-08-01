package com.nightsta69.delvefold.guide;

import com.nightsta69.delvefold.config.DelvefoldConfigService;
import com.nightsta69.delvefold.reset.WorldOperationService;
import java.util.Optional;

/** Minimal read-only bridge from the live server state to the public guide contract. */
public final class GuideSnapshotService {
    private GuideSnapshotService() {
    }

    public static Optional<GuideSnapshot> current() {
        return current(System.currentTimeMillis());
    }

    static Optional<GuideSnapshot> current(long nowEpochMillis) {
        try {
            return Optional.of(GuideSnapshotBuilder.build(
                    DelvefoldConfigService.get().snapshot(),
                    nowEpochMillis,
                    WorldOperationService.get().isEntryBlocked()));
        } catch (IllegalStateException ignored) {
            return Optional.empty();
        }
    }
}
