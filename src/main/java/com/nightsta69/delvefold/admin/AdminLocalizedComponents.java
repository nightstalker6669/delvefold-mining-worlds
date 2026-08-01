package com.nightsta69.delvefold.admin;

import java.util.Optional;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Resolves bounded administration-message envelopes on either client or command output paths. */
public final class AdminLocalizedComponents {
    private AdminLocalizedComponents() {}

    /**
     * Resolves a bounded localized-message envelope, falling back to literal text for foreign input.
     *
     * @param encoded encoded envelope, literal server text, or {@code null}
     * @return a client-renderable component; nested envelopes are resolved to a maximum depth of four
     */
    public static Component resolve(@Nullable String encoded) {
        return resolve(encoded, 0);
    }

    private static Component resolve(@Nullable String encoded, int depth) {
        Optional<AdminLocalizedMessage.Decoded> decoded = AdminLocalizedMessage.decode(encoded);
        if (decoded.isEmpty() || depth >= 4) {
            return Component.literal(encoded == null ? "" : encoded);
        }
        AdminLocalizedMessage.Decoded message = decoded.orElseThrow();
        Object[] arguments = message.arguments().stream()
                .map(argument ->
                        AdminLocalizedMessage.decode(argument).isPresent() ? resolve(argument, depth + 1) : argument)
                .toArray();
        return Component.translatable(message.translationKey(), arguments);
    }
}
