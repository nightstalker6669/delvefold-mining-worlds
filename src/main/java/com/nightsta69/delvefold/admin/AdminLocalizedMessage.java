package com.nightsta69.delvefold.admin;

import com.nightsta69.delvefold.network.ProtocolLimits;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Keeps administration payloads protocol-compatible while allowing the client to localize server-authorized result and
 * status messages. Arguments are display-only strings; the server never trusts values decoded by the client.
 */
public final class AdminLocalizedMessage {
    private static final String PREFIX = "delvefold:i18n:v1:";
    private static final Pattern KEY = Pattern.compile("[a-z0-9_.-]+");
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private AdminLocalizedMessage() {}

    /**
     * Encodes a translation key and display-only arguments into a bounded protocol-safe envelope.
     *
     * <p>Arguments are converted with {@link String#valueOf(Object)} and the longest arguments are truncated as needed
     * so the returned value never exceeds {@link ProtocolLimits#MESSAGE_LENGTH} UTF-16 code units.
     *
     * @param translationKey lowercase resource-style translation key
     * @param arguments nullable array whose nullable elements are display-only values
     * @return a versioned URL-safe Base64 envelope
     * @throws IllegalArgumentException if the key is invalid or fixed envelope data cannot fit the protocol limit
     */
    public static String encode(String translationKey, @Nullable Object @Nullable ... arguments) {
        if (translationKey == null
                || translationKey.length() > 256
                || !KEY.matcher(translationKey).matches()) {
            throw new IllegalArgumentException("Invalid translation key");
        }
        List<String> safeArguments = new ArrayList<>();
        if (arguments != null) {
            for (@Nullable Object argument : arguments) {
                safeArguments.add(String.valueOf(argument));
            }
        }
        String encoded = encodeParts(translationKey, safeArguments);
        while (encoded.length() > ProtocolLimits.MESSAGE_LENGTH) {
            int longest = -1;
            for (int index = 0; index < safeArguments.size(); index++) {
                if (longest < 0
                        || safeArguments.get(index).length()
                                > safeArguments.get(longest).length()) {
                    longest = index;
                }
            }
            if (longest < 0 || safeArguments.get(longest).isEmpty()) {
                throw new IllegalArgumentException("Localized message exceeds the protocol limit");
            }
            String value = safeArguments.get(longest);
            int excess = encoded.length() - ProtocolLimits.MESSAGE_LENGTH;
            int remove = Math.min(value.length(), Math.max(1, excess));
            safeArguments.set(longest, value.substring(0, value.length() - remove));
            encoded = encodeParts(translationKey, safeArguments);
        }
        return encoded;
    }

    /**
     * Decodes a well-formed v1 envelope without trusting it as authorization or configuration input.
     *
     * @param encoded candidate envelope, or {@code null}
     * @return immutable decoded display data, or empty for foreign, malformed, or unsupported input
     */
    public static Optional<Decoded> decode(@Nullable String encoded) {
        if (encoded == null || !encoded.startsWith(PREFIX)) {
            return Optional.empty();
        }
        try {
            String[] parts = encoded.substring(PREFIX.length()).split("\\.", -1);
            if (parts.length == 0) {
                return Optional.empty();
            }
            String key = text(parts[0]);
            if (!KEY.matcher(key).matches()) {
                return Optional.empty();
            }
            List<String> arguments = new ArrayList<>(Math.max(0, parts.length - 1));
            for (int index = 1; index < parts.length; index++) {
                arguments.add(text(parts[index]));
            }
            return Optional.of(new Decoded(key, arguments));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static String part(String value) {
        return ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String encodeParts(String key, List<String> arguments) {
        StringBuilder encoded = new StringBuilder(PREFIX).append(part(key));
        for (String argument : arguments) {
            encoded.append('.').append(part(argument));
        }
        return encoded.toString();
    }

    private static String text(String value) {
        return new String(DECODER.decode(value), StandardCharsets.UTF_8);
    }

    /**
     * Immutable display-only contents of a localized-message envelope.
     *
     * @param translationKey validated translation key
     * @param arguments immutable ordered argument strings
     */
    public record Decoded(String translationKey, List<String> arguments) {
        /**
         * Defensively snapshots decoded arguments.
         *
         * @param translationKey validated translation key
         * @param arguments ordered argument strings to snapshot
         */
        public Decoded {
            arguments = List.copyOf(arguments);
        }
    }
}
