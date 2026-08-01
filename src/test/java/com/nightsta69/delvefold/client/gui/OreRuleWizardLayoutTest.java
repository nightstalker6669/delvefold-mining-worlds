package com.nightsta69.delvefold.client.gui;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Verifies candidate-grid breakpoints, compact rows, pagination, and safe index bounds. */
class OreRuleWizardLayoutTest {
    @ParameterizedTest
    @CsvSource({
        "0,false,2,4,8",
        "389,false,2,4,8",
        "390,false,3,4,12",
        "519,false,3,4,12",
        "520,false,4,4,16",
        "320,true,2,2,4",
        "390,true,3,2,6",
        "520,true,4,2,8"
    })
    void responsiveBreakpointsRetainEstablishedColumnsAndRows(
            int panelWidth, boolean compact, int columns, int rows, int pageSize) {
        OreRuleWizardLayout layout = OreRuleWizardLayout.calculate(panelWidth, compact, 100, 0);

        assertAll(
                () -> assertEquals(columns, layout.columns()),
                () -> assertEquals(rows, layout.rows()),
                () -> assertEquals(pageSize, layout.pageSize()),
                () -> assertEquals(0, layout.start()),
                () -> assertEquals(pageSize, layout.end()));
    }

    @Test
    void requestedPagesAndWindowsAreClampedForEmptyPartialAndNegativeInputs() {
        OreRuleWizardLayout empty = OreRuleWizardLayout.calculate(320, false, 0, Integer.MAX_VALUE);
        OreRuleWizardLayout finalPartial = OreRuleWizardLayout.calculate(320, false, 17, Integer.MAX_VALUE);
        OreRuleWizardLayout first = OreRuleWizardLayout.calculate(520, true, 20, Integer.MIN_VALUE);
        OreRuleWizardLayout negativeCount = OreRuleWizardLayout.calculate(390, false, -12, 4);
        OreRuleWizardLayout integerLimit =
                OreRuleWizardLayout.calculate(320, false, Integer.MAX_VALUE, Integer.MAX_VALUE);

        assertAll(
                () -> assertEquals(new OreRuleWizardLayout(2, 4, 8, 1, 0, 0, 0), empty),
                () -> assertEquals(new OreRuleWizardLayout(2, 4, 8, 3, 2, 16, 17), finalPartial),
                () -> assertEquals(new OreRuleWizardLayout(4, 2, 8, 3, 0, 0, 8), first),
                () -> assertEquals(new OreRuleWizardLayout(3, 4, 12, 1, 0, 0, 0), negativeCount),
                () -> assertEquals(268_435_456, integerLimit.pageCount()),
                () -> assertEquals(2_147_483_640, integerLimit.start()),
                () -> assertEquals(Integer.MAX_VALUE, integerLimit.end()));
    }

    @Test
    void containmentUsesAnInclusiveStartAndExclusiveEnd() {
        OreRuleWizardLayout layout = OreRuleWizardLayout.calculate(390, false, 30, 1);

        assertAll(
                () -> assertFalse(layout.contains(11)),
                () -> assertTrue(layout.contains(12)),
                () -> assertTrue(layout.contains(23)),
                () -> assertFalse(layout.contains(24)),
                () -> assertFalse(layout.contains(Integer.MAX_VALUE)));
    }
}
