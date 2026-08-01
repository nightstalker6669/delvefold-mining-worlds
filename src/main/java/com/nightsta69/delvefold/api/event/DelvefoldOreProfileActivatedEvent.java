package com.nightsta69.delvefold.api.event;

import net.neoforged.bus.api.Event;

/** Posted after a named ore profile becomes the active server-authoritative profile. */
public final class DelvefoldOreProfileActivatedEvent extends Event {
    private final String previousProfileId;
    private final String currentProfileId;

    /**
     * Creates an immutable post-activation event.
     *
     * @param previousProfileId profile active before the committed mutation
     * @param currentProfileId profile active after the committed mutation
     */
    public DelvefoldOreProfileActivatedEvent(String previousProfileId, String currentProfileId) {
        this.previousProfileId = previousProfileId;
        this.currentProfileId = currentProfileId;
    }

    /**
     * Returns the previously active profile.
     *
     * @return the profile identifier active before the change
     */
    public String previousProfileId() {
        return previousProfileId;
    }

    /**
     * Returns the newly active profile.
     *
     * @return the newly active profile identifier
     */
    public String currentProfileId() {
        return currentProfileId;
    }
}
