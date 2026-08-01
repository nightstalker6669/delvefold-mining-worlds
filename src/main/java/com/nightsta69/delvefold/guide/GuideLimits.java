package com.nightsta69.delvefold.guide;

import java.nio.charset.StandardCharsets;

/** Hard bounds for the public, read-only guide contract. */
public final class GuideLimits {
    /** Leaves headroom below Minecraft's custom-payload ceiling for codec overhead. */
    public static final int MAX_ESTIMATED_NETWORK_BYTES = 24 * 1024;
    public static final int MAX_WORLD_NAME_CHARACTERS = 64;
    public static final int MAX_IDENTIFIER_CHARACTERS = 128;
    public static final int MAX_ORE_ENTRIES = 96;
    public static final int MAX_OUTPUTS_PER_ENTRY = 8;
    public static final int MAX_HEIGHT_BANDS_PER_ENTRY = 8;
    public static final int MAX_APPLICABLE_TERRAINS = 3;
    public static final int MAX_BIOME_SELECTORS_PER_LIST = 16;
    public static final long MAX_RENEWAL_COUNTDOWN_SECONDS = 3650L * 86_400L;

    private GuideLimits() {
    }

    static String boundedText(String value, int maximumCharacters) {
        String source = value == null ? "" : value.trim();
        StringBuilder safe = new StringBuilder(Math.min(source.length(), maximumCharacters));
        var codePoints = source.codePoints().iterator();
        while (codePoints.hasNext()) {
            int codePoint = codePoints.nextInt();
            int requiredUnits = Character.isISOControl(codePoint) ? 1 : Character.charCount(codePoint);
            if (safe.length() + requiredUnits > maximumCharacters) {
                break;
            }
            if (Character.isISOControl(codePoint)) {
                safe.append(' ');
            } else {
                safe.appendCodePoint(codePoint);
            }
        }
        return safe.toString();
    }

    static int networkStringBytes(String value) {
        // Four bytes conservatively cover a length prefix used by a future codec.
        return 4 + value.getBytes(StandardCharsets.UTF_8).length;
    }
}
