package com.nightsta69.delvefold.audit;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Whitelisted input for one accepted Delvefold configuration or lifecycle mutation.
 *
 * <p>There is deliberately no arbitrary metadata field: confirmation tokens, configuration
 * documents, addresses, filesystem paths, and unrelated player information have no place in the
 * audit API and therefore cannot be serialized by the logger.</p>
 */
public record AuditMutation(
        String actor,
        Operation operation,
        ObjectType affectedObjectType,
        String affectedObjectId,
        long oldRevision,
        long newRevision
) {
    private static final Pattern ACTOR = Pattern.compile("[A-Za-z0-9_@ \\-]{1,64}");
    private static final Pattern OBJECT_ID = Pattern.compile("[A-Za-z0-9_#./:\\-]{1,160}");
    private static final Pattern IPV4_SOCKET = Pattern.compile(
            "(?:^|[^0-9])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}:[0-9]{1,5}(?:$|[^0-9])");
    private static final Pattern HOST_SOCKET = Pattern.compile(
            "(?i)(?:^|[^a-z0-9_.-])(?:[a-z0-9-]+\\.)+[a-z]{2,63}:[0-9]{1,5}(?:$|[^0-9])");
    private static final Pattern IPV6_SOCKET = Pattern.compile("(?i)(?:[0-9a-f]{0,4}:){2,}[0-9a-f]{0,4}:?[0-9]{1,5}");

    public AuditMutation {
        actor = validatedActor(actor);
        if (operation == null || affectedObjectType == null) {
            throw new IllegalArgumentException("Audit operation and object type are required");
        }
        if (affectedObjectId == null || !OBJECT_ID.matcher(affectedObjectId).matches()
                || affectedObjectId.startsWith("/") || affectedObjectId.contains("../")
                || IPV4_SOCKET.matcher(affectedObjectId).find()
                || HOST_SOCKET.matcher(affectedObjectId).find()
                || IPV6_SOCKET.matcher(affectedObjectId).matches()) {
            throw new IllegalArgumentException("Audit object ID is not a safe logical identifier");
        }
        if (oldRevision < -1L || newRevision < -1L) {
            throw new IllegalArgumentException("Audit revisions must be non-negative or -1 when unavailable");
        }
    }

    public String affectedObject() {
        return affectedObjectType.serializedName() + ":" + affectedObjectId;
    }

    static String validatedActor(String actor) {
        if (actor == null || !ACTOR.matcher(actor).matches()) {
            throw new IllegalArgumentException("Audit actor must be a player identifier, console, or server");
        }
        return actor;
    }

    public enum Operation {
        CONFIGURATION_ACCEPTED,
        PROFILE_CREATED,
        PROFILE_UPDATED,
        PROFILE_DELETED,
        PROFILE_ACTIVATED,
        WORLD_OPERATION_ACCEPTED,
        WORLD_OPERATION_CANCELLED,
        BACKUP_MANIFEST_CREATED,
        BACKUP_RESTORE_ACCEPTED,
        BACKUP_RESTORE_CANCELLED,
        BACKUP_PINNED,
        BACKUP_UNPINNED,
        BACKUP_DELETED,
        RETENTION_PRUNE_PREVIEWED,
        RETENTION_PRUNE_ACCEPTED,
        PORTAL_ROUTING_CHANGED,
        HUB_PROTECTION_CHANGED,
        LANDMARK_CATALOG_RELOADED;

        public String serializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum ObjectType {
        SETTINGS,
        PROFILE,
        WORLD,
        BACKUP,
        RETENTION,
        PORTAL,
        HUB,
        LANDMARK_CATALOG;

        public String serializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
