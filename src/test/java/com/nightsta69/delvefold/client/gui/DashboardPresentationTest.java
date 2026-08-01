package com.nightsta69.delvefold.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardPresentationTest {
    @Test
    void presentationRetainsResolvedComponentsAndDefensivelyFreezesDiagnostics() {
        String portal = "test.portal";
        String world = "test.world";
        String diagnostic = "test.diagnostic";
        List<String> source = new ArrayList<>(List.of(diagnostic));

        DashboardPresentation<String> presentation = new DashboardPresentation<>(portal, world, source);
        source.clear();

        assertEquals(portal, presentation.portalStatus());
        assertEquals(world, presentation.worldStatus());
        assertEquals(diagnostic, presentation.diagnostics().getFirst());
        assertThrows(
                UnsupportedOperationException.class,
                () -> presentation.diagnostics().add("other"));
    }
}
