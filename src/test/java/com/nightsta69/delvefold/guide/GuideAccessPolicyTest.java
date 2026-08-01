package com.nightsta69.delvefold.guide;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.config.model.GuideVisibility;
import org.junit.jupiter.api.Test;

class GuideAccessPolicyTest {
    @Test
    void publicModeAllowsOrdinaryPlayersAndOperators() {
        assertTrue(GuideAccessPolicy.allows(GuideVisibility.PUBLIC, false));
        assertTrue(GuideAccessPolicy.allows(GuideVisibility.PUBLIC, true));
    }

    @Test
    void operatorModeRequiresOperatorStatus() {
        assertFalse(GuideAccessPolicy.allows(GuideVisibility.OPERATORS, false));
        assertTrue(GuideAccessPolicy.allows(GuideVisibility.OPERATORS, true));
    }

    @Test
    void disabledModeRejectsEveryPlayer() {
        assertFalse(GuideAccessPolicy.allows(GuideVisibility.DISABLED, false));
        assertFalse(GuideAccessPolicy.allows(GuideVisibility.DISABLED, true));
    }

    @Test
    void missingLegacyValueUsesPublicCompatibilityDefault() {
        assertTrue(GuideAccessPolicy.allows(null, false));
    }
}
