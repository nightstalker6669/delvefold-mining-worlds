package com.nightsta69.delvefold.reset;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nightsta69.delvefold.config.model.RenewalSettings;
import java.util.List;
import org.junit.jupiter.api.Test;

class RenewalSchedulerTest {
    @Test
    void warningScheduleIncludesConfiguredTenAndOneMinuteThresholds() {
        assertEquals(List.of(30, 10, 1), List.copyOf(RenewalSettings.warningThresholds(30)));
        assertEquals(List.of(5, 1), List.copyOf(RenewalSettings.warningThresholds(5)));
        assertEquals(List.of(1), List.copyOf(RenewalSettings.warningThresholds(1)));
    }
}
