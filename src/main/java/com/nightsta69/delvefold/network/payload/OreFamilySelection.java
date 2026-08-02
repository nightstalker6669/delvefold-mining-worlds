package com.nightsta69.delvefold.network.payload;

import com.nightsta69.delvefold.network.ProtocolLimits;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Pure validation boundary for the family identifiers carried by an atomic Unified Ores request. */
final class OreFamilySelection {
    private static final Pattern RESOURCE_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private OreFamilySelection() {}

    /**
     * Returns a normalized immutable selection in request order.
     *
     * <p>Requiring Minecraft's canonical ASCII resource-ID grammar also gives every accepted identifier a one-byte
     * UTF-8 representation per Java character. Together with the count and character bounds, this makes the request's
     * aggregate wire size statically bounded.
     *
     * @param familyIds untrusted family IDs received from or about to be sent by a client
     * @return immutable, trimmed, distinct canonical resource IDs
     * @throws IllegalArgumentException when the selection is empty, oversized, duplicated, or contains an invalid ID
     */
    static List<String> normalize(@Nullable List<@Nullable String> familyIds) {
        if (familyIds == null || familyIds.isEmpty() || familyIds.size() > ProtocolLimits.MAX_ORE_LIBRARY_SELECTIONS) {
            throw new IllegalArgumentException("Invalid Unified Ores family selection");
        }

        List<String> normalized = new ArrayList<>(familyIds.size());
        Set<String> distinct = new HashSet<>(familyIds.size());
        for (@Nullable String familyId : familyIds) {
            String id = familyId == null ? "" : familyId.trim();
            if (id.length() > ProtocolLimits.ID_LENGTH
                    || !RESOURCE_ID.matcher(id).matches()
                    || !distinct.add(id)) {
                throw new IllegalArgumentException("Invalid Unified Ores family selection");
            }
            normalized.add(id);
        }
        return List.copyOf(normalized);
    }
}
