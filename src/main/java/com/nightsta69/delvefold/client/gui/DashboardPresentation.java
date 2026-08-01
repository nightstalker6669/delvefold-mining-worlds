package com.nightsta69.delvefold.client.gui;

import java.util.List;
import java.util.Objects;

/**
 * Immutable cache of server-authored dashboard messages after client localization envelopes are resolved.
 *
 * @param portalStatus resolved portal status retained for initialization and rendering
 * @param worldStatus resolved world status retained for initialization and rendering
 * @param diagnostics immutable resolved diagnostic entries
 */
record DashboardPresentation<T>(T portalStatus, T worldStatus, List<T> diagnostics) {
    DashboardPresentation {
        Objects.requireNonNull(portalStatus, "portalStatus");
        Objects.requireNonNull(worldStatus, "worldStatus");
        diagnostics = List.copyOf(diagnostics);
    }
}
