package com.nightsta69.delvefold.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightsta69.delvefold.network.ProtocolLimits;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdminLocalizedMessageTest {
    @Test
    void roundTripsTranslationKeyAndUnicodeArguments() {
        String encoded =
                AdminLocalizedMessage.encode("message.delvefold.admin.backup.deleted", "backup.2026-08-01", "Fold ‘A’");

        AdminLocalizedMessage.Decoded decoded =
                AdminLocalizedMessage.decode(encoded).orElseThrow();
        assertEquals("message.delvefold.admin.backup.deleted", decoded.translationKey());
        assertEquals(List.of("backup.2026-08-01", "Fold ‘A’"), decoded.arguments());
    }

    @Test
    void rejectsLegacyTextMalformedPayloadsAndInvalidKeys() {
        assertTrue(AdminLocalizedMessage.decode("ordinary server text").isEmpty());
        assertTrue(AdminLocalizedMessage.decode("delvefold:i18n:v1:not_base64!").isEmpty());
        assertTrue(AdminLocalizedMessage.decode("delvefold:i18n:v1:"
                        + java.util.Base64.getUrlEncoder()
                                .withoutPadding()
                                .encodeToString("Invalid Key".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .isEmpty());
    }

    @Test
    void truncatesArgumentsWithoutBreakingTheEncodedEnvelope() {
        String encoded = AdminLocalizedMessage.encode("message.delvefold.admin.backup.failed", "x".repeat(4_096));

        assertTrue(encoded.length() <= ProtocolLimits.MESSAGE_LENGTH);
        AdminLocalizedMessage.Decoded decoded =
                AdminLocalizedMessage.decode(encoded).orElseThrow();
        assertEquals("message.delvefold.admin.backup.failed", decoded.translationKey());
        assertTrue(decoded.arguments().getFirst().length() < 4_096);
    }

    @Test
    void nestedLocalizedArgumentsRemainStructuredComponents() {
        String detail = AdminLocalizedMessage.encode("message.delvefold.admin.refreshed");
        String outer = AdminLocalizedMessage.encode("message.delvefold.admin.profile.result", detail);

        AdminLocalizedMessage.Decoded decoded =
                AdminLocalizedMessage.decode(outer).orElseThrow();
        assertEquals(detail, decoded.arguments().getFirst());
    }
}
