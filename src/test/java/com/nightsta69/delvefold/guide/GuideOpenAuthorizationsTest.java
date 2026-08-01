package com.nightsta69.delvefold.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class GuideOpenAuthorizationsTest {
    @Test
    void acknowledgementIsSingleUseAndPlayerBound() {
        GuideOpenAuthorizations authorizations = new GuideOpenAuthorizations();
        UUID player = UUID.randomUUID();
        long id = authorizations.issue(player, 100L);

        assertFalse(authorizations.confirm(UUID.randomUUID(), id, 101L));
        assertTrue(authorizations.confirm(player, id, 101L));
        assertFalse(authorizations.confirm(player, id, 101L));
    }

    @Test
    void expiredOrIncorrectAcknowledgementsCannotAward() {
        GuideOpenAuthorizations authorizations = new GuideOpenAuthorizations();
        UUID player = UUID.randomUUID();
        long id = authorizations.issue(player, 50L);

        assertFalse(authorizations.confirm(player, id + 1L, 51L));
        assertTrue(authorizations.confirm(player, id, 51L));
        long expired = authorizations.issue(player, 50L);
        assertFalse(authorizations.confirm(player, expired, 50L + GuideOpenAuthorizations.TTL_TICKS + 1L));
    }

    @Test
    void staleAcknowledgementCannotConsumeTheNewestAuthorization() {
        GuideOpenAuthorizations authorizations = new GuideOpenAuthorizations();
        UUID player = UUID.randomUUID();
        long first = authorizations.issue(player, 100L);
        long second = authorizations.issue(player, 101L);

        assertFalse(authorizations.confirm(player, first, 102L));
        assertTrue(authorizations.confirm(player, second, 102L));
    }

    @Test
    void pendingAuthorizationsStayBounded() {
        GuideOpenAuthorizations authorizations = new GuideOpenAuthorizations();
        for (int index = 0; index < GuideOpenAuthorizations.MAX_PENDING + 20; index++) {
            authorizations.issue(new UUID(0L, index + 1L), 10L);
        }
        assertEquals(GuideOpenAuthorizations.MAX_PENDING, authorizations.size());
    }
}
