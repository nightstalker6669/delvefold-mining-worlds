package com.nightsta69.delvefold.portal;

import com.nightsta69.delvefold.config.model.PortalRoutingMode;
import com.nightsta69.delvefold.config.model.PortalSettings;

/** Pure direction-aware routing policy shared by transition code and tests. */
final class PortalRoutePolicy {
    private PortalRoutePolicy() {
    }

    static PortalRoutingMode effectiveMode(PortalSettings settings, boolean returningToOverworld) {
        if (returningToOverworld) {
            return PortalRoutingMode.COORDINATE_LINKED;
        }
        return settings == null ? PortalRoutingMode.COORDINATE_LINKED : settings.routingMode();
    }

    static boolean usesCentralHub(PortalSettings settings, boolean returningToOverworld) {
        return effectiveMode(settings, returningToOverworld) == PortalRoutingMode.CENTRAL_HUB;
    }
}
