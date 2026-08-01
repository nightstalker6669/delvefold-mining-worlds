package com.nightsta69.delvefold.config.importer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/** Implements the opaque-token security policy shared by ore-import scan and preview sessions. */
final class OreImportTokenSecurity {
    private static final int TOKEN_BYTES = 32;
    private static final int TOKEN_LENGTH = 43;
    private static final int MAX_UNIQUE_TOKEN_ATTEMPTS = 16;

    private OreImportTokenSecurity() {}

    static OreImportSessionService.TokenSource secureTokenSource() {
        SecureRandom random = new SecureRandom();
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return () -> {
            byte[] bytes = new byte[TOKEN_BYTES];
            random.nextBytes(bytes);
            return encoder.encodeToString(bytes);
        };
    }

    static IssuedToken issueUniqueToken(OreImportSessionService.TokenSource source, Predicate<byte[]> tokenInUse) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(tokenInUse, "tokenInUse");
        for (int attempt = 0; attempt < MAX_UNIQUE_TOKEN_ATTEMPTS; attempt++) {
            String value = requireToken(source.nextToken());
            byte[] digest = digest(value);
            if (!tokenInUse.test(digest)) {
                return new IssuedToken(value, digest);
            }
        }
        throw new IllegalStateException("Could not create a unique ore-import session token");
    }

    static boolean tokenMatches(byte[] expectedDigest, @Nullable String supplied) {
        if (supplied == null || !validToken(supplied)) {
            return false;
        }
        return digestsEqual(expectedDigest, digest(supplied));
    }

    static boolean digestsEqual(byte[] expected, byte[] supplied) {
        return MessageDigest.isEqual(expected, supplied);
    }

    static boolean validToken(@Nullable String token) {
        if (token == null || token.length() != TOKEN_LENGTH) {
            return false;
        }
        for (int index = 0; index < token.length(); index++) {
            char character = token.charAt(index);
            if (!(character >= 'a' && character <= 'z')
                    && !(character >= 'A' && character <= 'Z')
                    && !(character >= '0' && character <= '9')
                    && character != '-'
                    && character != '_') {
                return false;
            }
        }
        return true;
    }

    static byte[] digest(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String requireToken(@Nullable String token) {
        if (!validToken(token)) {
            throw new IllegalStateException("Ore-import token source returned an invalid token");
        }
        return Objects.requireNonNull(token, "validated token");
    }

    static final class IssuedToken {
        private final String value;
        private final byte[] digest;

        private IssuedToken(String value, byte[] digest) {
            this.value = value;
            this.digest = digest;
        }

        String value() {
            return value;
        }

        byte[] digest() {
            return digest;
        }
    }
}
