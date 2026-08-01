package com.nightsta69.delvefold.admin;

import com.nightsta69.delvefold.network.ProtocolLimits;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Keeps administration payloads protocol-compatible while allowing the client to localize
 * server-authorized result and status messages. Arguments are display-only strings; the server
 * never trusts values decoded by the client.
 */
public final class AdminLocalizedMessage {
    private static final String PREFIX = "delvefold:i18n:v1:";
    private static final Pattern KEY = Pattern.compile("[a-z0-9_.-]+");
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private AdminLocalizedMessage() {
    }

    public static String encode(String translationKey, Object... arguments) {
        if (translationKey == null || translationKey.length() > 256
                || !KEY.matcher(translationKey).matches()) {
            throw new IllegalArgumentException("Invalid translation key");
        }
        List<String> safeArguments = new ArrayList<>();
        if (arguments != null) {
            for (Object argument : arguments) {
                safeArguments.add(String.valueOf(argument));
            }
        }
        String encoded = encodeParts(translationKey, safeArguments);
        while (encoded.length() > ProtocolLimits.MESSAGE_LENGTH) {
            int longest = -1;
            for (int index = 0; index < safeArguments.size(); index++) {
                if (longest < 0 || safeArguments.get(index).length() > safeArguments.get(longest).length()) {
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

    public static Optional<Decoded> decode(String encoded) {
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

    public record Decoded(String translationKey, List<String> arguments) {
        public Decoded {
            arguments = List.copyOf(arguments);
        }
    }
}
