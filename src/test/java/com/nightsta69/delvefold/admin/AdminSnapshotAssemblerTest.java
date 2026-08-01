package com.nightsta69.delvefold.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nightsta69.delvefold.network.ProtocolLimits;
import org.junit.jupiter.api.Test;

class AdminSnapshotAssemblerTest {
    @Test
    void emptyCatalogAlwaysUsesAnEmptyFirstPage() {
        assertEquals(new AdminSnapshotAssembler.PageBounds(0, 0, 0), AdminSnapshotAssembler.pageBounds(0, 99, 0));
    }

    @Test
    void pageAndSizeAreClampedBeforeSublistBoundsAreDerived() {
        assertEquals(new AdminSnapshotAssembler.PageBounds(0, 0, 4), AdminSnapshotAssembler.pageBounds(10, -5, 4));
        assertEquals(new AdminSnapshotAssembler.PageBounds(2, 8, 10), AdminSnapshotAssembler.pageBounds(10, 99, 4));
    }

    @Test
    void oversizedPageSizeUsesTheWireBound() {
        int total = ProtocolLimits.MAX_ORE_RULES_PER_PAGE + 3;

        assertEquals(
                new AdminSnapshotAssembler.PageBounds(1, ProtocolLimits.MAX_ORE_RULES_PER_PAGE, total),
                AdminSnapshotAssembler.pageBounds(total, 1, Integer.MAX_VALUE));
    }
}
