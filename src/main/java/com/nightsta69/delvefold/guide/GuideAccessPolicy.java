package com.nightsta69.delvefold.guide;

import com.nightsta69.delvefold.config.model.GuideVisibility;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Pure authorization policy shared by commands, item use, and tests. */
public final class GuideAccessPolicy {
    private GuideAccessPolicy() {}

    /**
     * Evaluates guide visibility without mutating or consulting player state.
     *
     * @param visibility configured visibility; absent legacy values are treated as public
     * @param operator whether the requesting subject has configuration permission
     * @return whether the subject may receive guide contents
     */
    public static boolean allows(@Nullable GuideVisibility visibility, boolean operator) {
        return switch (Objects.requireNonNullElse(visibility, GuideVisibility.PUBLIC)) {
            case PUBLIC -> true;
            case OPERATORS -> operator;
            case DISABLED -> false;
        };
    }
}
