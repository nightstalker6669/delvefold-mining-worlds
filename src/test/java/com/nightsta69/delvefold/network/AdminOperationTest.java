package com.nightsta69.delvefold.network;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.network.model.AdminOperation;
import org.junit.jupiter.api.Test;

class AdminOperationTest {
    @Test
    void recreationConfirmationIncludesShapeAndScale() {
        assertTrue(AdminOperation.RECREATE_WORLD.confirmationMatches("RECREATE:WILD:EXPANSIVE"));
        assertTrue(AdminOperation.RECREATE_WORLD.confirmationMatches("recreate:flat:classic"));
        assertFalse(AdminOperation.RECREATE_WORLD.confirmationMatches("RECREATE:WILD"));
        assertFalse(AdminOperation.RECREATE_WORLD.confirmationMatches("RECREATE:WILD:UNKNOWN"));
    }
}
