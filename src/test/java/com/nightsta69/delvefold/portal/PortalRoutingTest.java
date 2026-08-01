package com.nightsta69.delvefold.portal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.ConfigJson;
import com.nightsta69.delvefold.config.model.PortalHubSettings;
import com.nightsta69.delvefold.config.model.PortalRoutingMode;
import com.nightsta69.delvefold.config.model.PortalSettings;
import org.junit.jupiter.api.Test;

class PortalRoutingTest {
    @Test
    void compatibilityDefaultsPreserveCoordinateLinkedRouting() {
        PortalSettings defaults = PortalSettings.defaults();
        PortalSettings legacyConstructor = new PortalSettings(true, true, 5, 1.0D);
        PortalSettings legacyJson = ConfigJson.GSON.fromJson("""
                {
                  "enabled": true,
                  "allow_from_overworld_only": true,
                  "cooldown_seconds": 5,
                  "coordinate_scale": 1.0
                }
                """, PortalSettings.class);

        assertEquals(PortalRoutingMode.COORDINATE_LINKED, defaults.routingMode());
        assertEquals(PortalHubSettings.defaults(), defaults.hub());
        assertEquals(defaults, legacyConstructor);
        assertEquals(defaults, legacyJson);
    }

    @Test
    void centralHubOnlyOverridesIncomingRoutes() {
        PortalSettings central = new PortalSettings(
                true, true, 5, 1.0D, PortalRoutingMode.CENTRAL_HUB, new PortalHubSettings(120, -240, 32));

        assertTrue(PortalRoutePolicy.usesCentralHub(central, false));
        assertFalse(PortalRoutePolicy.usesCentralHub(central, true));
        assertEquals(
                PortalRoutingMode.COORDINATE_LINKED, PortalRoutePolicy.effectiveMode(PortalSettings.defaults(), false));
    }

    @Test
    void hubSettingsRejectUnsafeCoordinatesAndProtectionRadii() {
        assertEquals(16, PortalHubSettings.defaults().protectionRadius());
        assertThrows(
                IllegalArgumentException.class,
                () -> new PortalHubSettings(PortalHubSettings.MAX_ABSOLUTE_COORDINATE + 1, 0, 16));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PortalHubSettings(0, 0, PortalHubSettings.MIN_PROTECTION_RADIUS - 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PortalHubSettings(0, 0, PortalHubSettings.MAX_PROTECTION_RADIUS + 1));
    }

    @Test
    void protectionUsesAHorizontalInclusiveRadiusWithoutOverflow() {
        PortalHubSettings hub = new PortalHubSettings(100, -100, 16);

        assertTrue(CentralHubProtectionService.isInsideRadius(116, -100, hub));
        assertTrue(CentralHubProtectionService.isInsideRadius(100, -116, hub));
        assertFalse(CentralHubProtectionService.isInsideRadius(116, -99, hub));

        PortalHubSettings edge = new PortalHubSettings(
                PortalHubSettings.MAX_ABSOLUTE_COORDINATE,
                PortalHubSettings.MAX_ABSOLUTE_COORDINATE,
                PortalHubSettings.MAX_PROTECTION_RADIUS);
        assertFalse(CentralHubProtectionService.isInsideRadius(
                -PortalHubSettings.MAX_ABSOLUTE_COORDINATE, -PortalHubSettings.MAX_ABSOLUTE_COORDINATE, edge));
    }

    @Test
    void protectedMutationsRequireWorldManagementPermission() {
        assertFalse(CentralHubProtectionService.modificationAllowed(true, false));
        assertTrue(CentralHubProtectionService.modificationAllowed(true, true));
        assertTrue(CentralHubProtectionService.modificationAllowed(false, false));
    }
}
