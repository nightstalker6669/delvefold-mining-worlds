package com.nightsta69.delvefold.audit;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Whitelisted input for one accepted Delvefold configuration or lifecycle mutation.
 *
 * <p>There is deliberately no arbitrary metadata field: confirmation tokens, configuration documents, addresses,
 * filesystem paths, and unrelated player information have no place in the audit API and therefore cannot be serialized
 * by the logger.
 *
 * @param actor validated player identifier, {@code console}, or {@code server}; never an address
 * @param operation accepted mutation category
 * @param affectedObjectType logical object category
 * @param affectedObjectId bounded logical identifier, never an absolute/traversing path or socket address
 * @param oldRevision preceding domain revision, or {@code -1} when unavailable
 * @param newRevision resulting domain revision, or {@code -1} when unavailable
 */
public record AuditMutation(
        String actor,
        Operation operation,
        ObjectType affectedObjectType,
        String affectedObjectId,
        long oldRevision,
        long newRevision) {
    private static final Pattern ACTOR = Pattern.compile("[A-Za-z0-9_@ \\-]{1,64}");
    private static final Pattern OBJECT_ID = Pattern.compile("[A-Za-z0-9_#./:\\-]{1,160}");
    private static final Pattern IPV4_SOCKET =
            Pattern.compile("(?:^|[^0-9])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}:[0-9]{1,5}(?:$|[^0-9])");
    private static final Pattern HOST_SOCKET =
            Pattern.compile("(?i)(?:^|[^a-z0-9_.-])(?:[a-z0-9-]+\\.)+[a-z]{2,63}:[0-9]{1,5}(?:$|[^0-9])");
    private static final Pattern IPV6_SOCKET = Pattern.compile("(?i)(?:[0-9a-f]{0,4}:){2,}[0-9a-f]{0,4}:?[0-9]{1,5}");

    /**
     * Validates the deliberately narrow and redacted audit boundary.
     *
     * @param actor player identifier, {@code console}, or {@code server}
     * @param operation accepted mutation category
     * @param affectedObjectType logical object category
     * @param affectedObjectId bounded logical identifier
     * @param oldRevision preceding domain revision, or {@code -1}
     * @param newRevision resulting domain revision, or {@code -1}
     * @throws IllegalArgumentException if any value could expose unsafe data or violates revision bounds
     */
    public AuditMutation {
        actor = validatedActor(actor);
        if (operation == null || affectedObjectType == null) {
            throw new IllegalArgumentException("Audit operation and object type are required");
        }
        if (affectedObjectId == null
                || !OBJECT_ID.matcher(affectedObjectId).matches()
                || affectedObjectId.startsWith("/")
                || affectedObjectId.contains("../")
                || IPV4_SOCKET.matcher(affectedObjectId).find()
                || HOST_SOCKET.matcher(affectedObjectId).find()
                || IPV6_SOCKET.matcher(affectedObjectId).matches()) {
            throw new IllegalArgumentException("Audit object ID is not a safe logical identifier");
        }
        if (oldRevision < -1L || newRevision < -1L) {
            throw new IllegalArgumentException("Audit revisions must be non-negative or -1 when unavailable");
        }
    }

    /**
     * Builds the normalized type-qualified identifier written to the log.
     *
     * @return {@code <serialized-type>:<logical-id>}
     */
    public String affectedObject() {
        return affectedObjectType.serializedName() + ":" + affectedObjectId;
    }

    static String validatedActor(String actor) {
        if (actor == null || !ACTOR.matcher(actor).matches()) {
            throw new IllegalArgumentException("Audit actor must be a player identifier, console, or server");
        }
        return actor;
    }

    /** Whitelisted accepted mutation categories; declaration names serialize in lowercase. */
    public enum Operation {
        /** Server-authoritative configuration revision was accepted. */
        CONFIGURATION_ACCEPTED,

        /** A new named ore profile was persisted without activation. */
        PROFILE_CREATED,

        /** An existing named ore profile revision was updated. */
        PROFILE_UPDATED,

        /** An inactive named ore profile was deleted. */
        PROFILE_DELETED,

        /** A named ore profile became the active generation profile. */
        PROFILE_ACTIVATED,

        /** A destructive world lifecycle operation passed confirmation and admission. */
        WORLD_OPERATION_ACCEPTED,

        /** A pending world lifecycle operation was cancelled. */
        WORLD_OPERATION_CANCELLED,

        /** A backup manifest was created after successful legacy validation. */
        BACKUP_MANIFEST_CREATED,

        /** A verified backup restore passed confirmation and admission. */
        BACKUP_RESTORE_ACCEPTED,

        /** A pending backup restore was cancelled. */
        BACKUP_RESTORE_CANCELLED,

        /** An administrator pinned a backup against retention. */
        BACKUP_PINNED,

        /** An administrator removed a backup pin. */
        BACKUP_UNPINNED,

        /** A backup passed deletion guards and was deleted. */
        BACKUP_DELETED,

        /** Automatic retention produced a non-mutating prune preview. */
        RETENTION_PRUNE_PREVIEWED,

        /** A reviewed retention prune plan was accepted. */
        RETENTION_PRUNE_ACCEPTED,

        /** Portal routing mode or central-hub destination settings changed. */
        PORTAL_ROUTING_CHANGED,

        /** Central-hub protection settings changed. */
        HUB_PROTECTION_CHANGED,

        /** A valid landmark catalog revision was published. */
        LANDMARK_CATALOG_RELOADED;

        /**
         * Returns the stable lowercase JSON-lines operation value.
         *
         * @return locale-independent lowercase enum name
         */
        public String serializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Logical affected-object categories allowed in redacted audit entries. */
    public enum ObjectType {
        /** World settings document. */
        SETTINGS,

        /** Named ore profile. */
        PROFILE,

        /** Mining-world lifecycle state. */
        WORLD,

        /** One normalized backup identifier. */
        BACKUP,

        /** Backup-retention policy or run. */
        RETENTION,

        /** Portal routing policy. */
        PORTAL,

        /** Central-hub placement or protection. */
        HUB,

        /** Reloadable landmark catalog revision. */
        LANDMARK_CATALOG;

        /**
         * Returns the stable lowercase JSON-lines object-type value.
         *
         * @return locale-independent lowercase enum name
         */
        public String serializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
