package com.nightsta69.delvefold.config.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Base64;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class OreImportTokenSecurityTest {
    private static final String FIRST_TOKEN = "A".repeat(43);
    private static final String SECOND_TOKEN = "B".repeat(43);

    @Test
    void validatesTheExactUrlSafeUnpaddedTokenFormat() {
        assertTrue(OreImportTokenSecurity.validToken(FIRST_TOKEN));
        assertTrue(OreImportTokenSecurity.validToken("-_aZ09" + "A".repeat(37)));

        assertFalse(OreImportTokenSecurity.validToken(null));
        assertFalse(OreImportTokenSecurity.validToken("A".repeat(42)));
        assertFalse(OreImportTokenSecurity.validToken("A".repeat(44)));
        assertFalse(OreImportTokenSecurity.validToken("A".repeat(42) + "+"));
        assertFalse(OreImportTokenSecurity.validToken("A".repeat(42) + "/"));
        assertFalse(OreImportTokenSecurity.validToken("A".repeat(42) + "="));
    }

    @Test
    void hashesTokensWithSha256AndMatchesOnlyWellFormedInputs() {
        byte[] firstDigest = OreImportTokenSecurity.digest(FIRST_TOKEN);

        assertEquals(32, firstDigest.length);
        assertTrue(OreImportTokenSecurity.digestsEqual(firstDigest, OreImportTokenSecurity.digest(FIRST_TOKEN)));
        assertFalse(OreImportTokenSecurity.digestsEqual(firstDigest, OreImportTokenSecurity.digest(SECOND_TOKEN)));
        assertTrue(OreImportTokenSecurity.tokenMatches(firstDigest, FIRST_TOKEN));
        assertFalse(OreImportTokenSecurity.tokenMatches(firstDigest, SECOND_TOKEN));
        assertFalse(OreImportTokenSecurity.tokenMatches(firstDigest, null));
        assertFalse(OreImportTokenSecurity.tokenMatches(firstDigest, "malformed"));
    }

    @Test
    void secureSourceEmitsThirtyTwoRandomBytesAsUrlSafeBase64() {
        String token = OreImportTokenSecurity.secureTokenSource().nextToken();

        assertTrue(OreImportTokenSecurity.validToken(token));
        assertEquals(32, Base64.getUrlDecoder().decode(token).length);
    }

    @Test
    void uniqueIssuanceRetriesDigestCollisionsAndReturnsTheFirstUnusedToken() {
        Queue<String> values = new ArrayDeque<>(List.of(FIRST_TOKEN, SECOND_TOKEN));
        byte[] occupied = OreImportTokenSecurity.digest(FIRST_TOKEN);

        OreImportTokenSecurity.IssuedToken issued = OreImportTokenSecurity.issueUniqueToken(
                values::remove, candidate -> OreImportTokenSecurity.digestsEqual(occupied, candidate));

        assertEquals(SECOND_TOKEN, issued.value());
        assertTrue(OreImportTokenSecurity.digestsEqual(OreImportTokenSecurity.digest(SECOND_TOKEN), issued.digest()));
    }

    @Test
    void uniqueIssuanceStopsAfterSixteenCollisions() {
        AtomicInteger calls = new AtomicInteger();
        var exception = assertThrows(
                IllegalStateException.class,
                () -> OreImportTokenSecurity.issueUniqueToken(
                        () -> {
                            calls.incrementAndGet();
                            return FIRST_TOKEN;
                        },
                        ignored -> true));

        assertEquals(16, calls.get());
        assertEquals("Could not create a unique ore-import session token", exception.getMessage());
    }

    @Test
    void malformedInjectedTokenFailsBeforeCollisionLookup() {
        AtomicInteger lookups = new AtomicInteger();
        var exception = assertThrows(
                IllegalStateException.class,
                () -> OreImportTokenSecurity.issueUniqueToken(() -> "malformed", ignored -> {
                    lookups.incrementAndGet();
                    return false;
                }));

        assertEquals(0, lookups.get());
        assertEquals("Ore-import token source returned an invalid token", exception.getMessage());
    }
}
