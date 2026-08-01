package com.nightsta69.delvefold.client.gui;

import org.jspecify.annotations.Nullable;

/**
 * Platform-independent parser for Minecraft's textual resource-identifier character rules.
 *
 * <p>The parser intentionally mirrors {@code ResourceLocation.tryParse} for the wizard's local draft and validation
 * models, including default-namespace identifiers and a leading namespace separator. Keeping this tiny boundary free of
 * Minecraft classes makes all draft and validation branches executable in ordinary unit tests.
 */
final class ResourceIdentifierText {
    private ResourceIdentifierText() {}

    /** Returns whether the value follows Minecraft's namespace/path character rules. */
    static boolean isValid(String value) {
        return path(value) != null;
    }

    // Returns the parsed path, or null when either textual component contains a forbidden character.
    static @Nullable String path(String value) {
        int separator = value.indexOf(':');
        String path = separator >= 0 ? value.substring(separator + 1) : value;
        if (!hasOnlyPathCharacters(path)) {
            return null;
        }
        if (separator > 0 && !hasOnlyNamespaceCharacters(value.substring(0, separator))) {
            return null;
        }
        return path;
    }

    private static boolean hasOnlyPathCharacters(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character != '_'
                    && character != '-'
                    && character != '/'
                    && character != '.'
                    && (character < 'a' || character > 'z')
                    && (character < '0' || character > '9')) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasOnlyNamespaceCharacters(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character != '_'
                    && character != '-'
                    && character != '.'
                    && (character < 'a' || character > 'z')
                    && (character < '0' || character > '9')) {
                return false;
            }
        }
        return true;
    }
}
