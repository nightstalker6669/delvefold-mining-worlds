package com.nightsta69.delvefold.guide;

import java.util.Optional;

/** Resolves an optional representative item icon without coupling the guide model to a viewer. */
@FunctionalInterface
public interface GuideIconResolver {
    /** Resolver that deliberately supplies no representative icons. */
    GuideIconResolver NONE = (kind, sourceId) -> Optional.empty();

    /**
     * Resolves a representative exact block for a public output reference.
     *
     * @param kind exact-block or block-tag interpretation of {@code sourceId}
     * @param sourceId bounded registry or tag identifier
     * @return a representative exact block identifier, or an empty optional when no safe icon exists
     */
    Optional<String> representativeBlock(GuideSnapshot.OutputKind kind, String sourceId);
}
