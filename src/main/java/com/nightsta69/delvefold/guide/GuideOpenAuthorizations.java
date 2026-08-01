package com.nightsta69.delvefold.guide;

import java.security.SecureRandom;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Short-lived, single-use acknowledgements for successful client guide openings. */
final class GuideOpenAuthorizations {
    static final int MAX_PENDING = 1024;
    static final long TTL_TICKS = 20L * 10L;

    private final SecureRandom random = new SecureRandom();
    private final LinkedHashMap<UUID, Pending> pending = new LinkedHashMap<>();

    synchronized long issue(UUID playerId, long currentTick) {
        cleanup(currentTick);
        while (pending.size() >= MAX_PENDING) {
            Iterator<UUID> oldest = pending.keySet().iterator();
            if (!oldest.hasNext()) {
                break;
            }
            oldest.next();
            oldest.remove();
        }
        long authorizationId;
        do {
            authorizationId = random.nextLong();
        } while (authorizationId == 0L);
        pending.put(playerId, new Pending(authorizationId, currentTick, currentTick + TTL_TICKS));
        return authorizationId;
    }

    synchronized boolean confirm(UUID playerId, long authorizationId, long currentTick) {
        Pending issued = pending.get(playerId);
        if (issued == null) {
            return false;
        }
        if (currentTick < issued.issuedAtTick || currentTick > issued.expiresAtTick) {
            pending.remove(playerId);
            return false;
        }
        if (issued.authorizationId != authorizationId) {
            return false;
        }
        return pending.remove(playerId, issued);
    }

    synchronized void discard(UUID playerId, long authorizationId) {
        Pending issued = pending.get(playerId);
        if (issued != null && issued.authorizationId == authorizationId) {
            pending.remove(playerId);
        }
    }

    synchronized int size() {
        return pending.size();
    }

    private void cleanup(long currentTick) {
        pending.entrySet().removeIf(entry -> currentTick > entry.getValue().expiresAtTick
                || currentTick < entry.getValue().issuedAtTick);
    }

    private record Pending(long authorizationId, long issuedAtTick, long expiresAtTick) {
    }
}
