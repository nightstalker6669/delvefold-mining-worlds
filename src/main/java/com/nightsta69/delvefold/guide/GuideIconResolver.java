package com.nightsta69.delvefold.guide;

import java.util.Optional;

/** Resolves an optional representative item icon without coupling the guide model to a viewer. */
@FunctionalInterface
public interface GuideIconResolver {
    GuideIconResolver NONE = (kind, sourceId) -> Optional.empty();

    Optional<String> representativeBlock(GuideSnapshot.OutputKind kind, String sourceId);
}
