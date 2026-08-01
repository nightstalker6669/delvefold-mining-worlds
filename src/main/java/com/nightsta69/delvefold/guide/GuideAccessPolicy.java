package com.nightsta69.delvefold.guide;

import com.nightsta69.delvefold.config.model.GuideVisibility;
import java.util.Objects;

/** Pure authorization policy shared by commands, item use, and tests. */
public final class GuideAccessPolicy {
    private GuideAccessPolicy() {}

    public static boolean allows(GuideVisibility visibility, boolean operator) {
        return switch (Objects.requireNonNullElse(visibility, GuideVisibility.PUBLIC)) {
            case PUBLIC -> true;
            case OPERATORS -> operator;
            case DISABLED -> false;
        };
    }
}
