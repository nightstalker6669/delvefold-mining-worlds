package com.nightsta69.delvefold.network;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.network.model.AdminOperation;
import org.junit.jupiter.api.Test;

class AdminOperationTest {
    @Test
    void recreationConfirmationIncludesShapeScaleAndGeologyTheme() {
        assertTrue(AdminOperation.RECREATE_WORLD.confirmationMatches("RECREATE:WILD:EXPANSIVE:VOLCANIC"));
        assertTrue(AdminOperation.RECREATE_WORLD.confirmationMatches("recreate:flat:classic:classic"));
        assertTrue(AdminOperation.RECREATE_WORLD.confirmationMatches("RECREATE:CAVERN:EXPANSIVE:CRYSTAL"));
        assertFalse(AdminOperation.RECREATE_WORLD.confirmationMatches("RECREATE:WILD"));
        assertFalse(AdminOperation.RECREATE_WORLD.confirmationMatches("RECREATE:WILD:EXPANSIVE"));
        assertFalse(AdminOperation.RECREATE_WORLD.confirmationMatches("RECREATE:WILD:UNKNOWN"));
        assertFalse(AdminOperation.RECREATE_WORLD.confirmationMatches("RECREATE:WILD:EXPANSIVE:UNKNOWN"));
    }
}
