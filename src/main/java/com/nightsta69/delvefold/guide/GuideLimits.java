package com.nightsta69.delvefold.guide;

import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;

/** Hard bounds for the public, read-only guide contract. */
public final class GuideLimits {
    /** Leaves headroom below Minecraft's custom-payload ceiling for codec overhead. */
    public static final int MAX_ESTIMATED_NETWORK_BYTES = 24 * 1024;

    /** Maximum player-facing world-name length in UTF-16 code units. */
    public static final int MAX_WORLD_NAME_CHARACTERS = 64;

    /** Maximum registry, tag, profile, rule, or band identifier length in UTF-16 code units. */
    public static final int MAX_IDENTIFIER_CHARACTERS = 128;

    /** Maximum ore-rule entries in one public guide snapshot. */
    public static final int MAX_ORE_ENTRIES = 96;

    /** Maximum output references retained for one ore entry. */
    public static final int MAX_OUTPUTS_PER_ENTRY = 8;

    /** Maximum height-band summaries retained for one ore entry. */
    public static final int MAX_HEIGHT_BANDS_PER_ENTRY = 8;

    /** Maximum serialized terrain modes retained for one applicability summary. */
    public static final int MAX_APPLICABLE_TERRAINS = 3;

    /** Maximum include or exclude biome selectors retained per list. */
    public static final int MAX_BIOME_SELECTORS_PER_LIST = 16;

    /** Maximum public renewal countdown in seconds, equal to ten 365-day years. */
    public static final long MAX_RENEWAL_COUNTDOWN_SECONDS = 3650L * 86_400L;

    private GuideLimits() {}

    static String boundedText(@Nullable String value, int maximumCharacters) {
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
