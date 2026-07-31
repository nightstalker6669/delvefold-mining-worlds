package com.nightsta69.delvefold.api.event;

import net.neoforged.bus.api.Event;

/** Posted after a named ore profile becomes the active server-authoritative profile. */
public final class DelvefoldOreProfileActivatedEvent extends Event {
    private final String previousProfileId;
    private final String currentProfileId;

    public DelvefoldOreProfileActivatedEvent(String previousProfileId, String currentProfileId) {
        this.previousProfileId = previousProfileId;
        this.currentProfileId = currentProfileId;
    }

    public String previousProfileId() {
        return previousProfileId;
    }

    public String currentProfileId() {
        return currentProfileId;
    }
}
