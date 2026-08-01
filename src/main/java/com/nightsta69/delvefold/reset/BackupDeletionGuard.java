package com.nightsta69.delvefold.reset;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Coordinates backup deletion with operations that retain a logical backup reference.
 *
 * <p>The server thread captures both JVM-local drafts and persisted journals before a deletion is queued. A reservation
 * then remains active until the asynchronous worker has finished deleting and refreshing the catalog. Restore draft
 * creation uses the same lock, so a restore cannot acquire a reference in the scheduling window between the
 * server-thread check and the worker-thread filesystem walk.
 */
public final class BackupDeletionGuard {
    private static final BackupDeletionGuard INSTANCE = new BackupDeletionGuard();

    private final Object lock = new Object();
    private final Map<Key, Reservation> reservations = new HashMap<>();

    private BackupDeletionGuard() {}

    /**
     * Returns the process-wide coordinator shared by command, GUI, restore, and catalog services.
     *
     * @return singleton deletion guard
     */
    public static BackupDeletionGuard get() {
        return INSTANCE;
    }

    Reservation reserve(MinecraftServer server, String backupId) throws IOException {
        Objects.requireNonNull(server, "server");
        Path saveRoot = normalize(server.getWorldPath(LevelResource.ROOT));
        synchronized (lock) {
            Set<String> referenced = new LinkedHashSet<>(BackupRetentionService.protectedBackupIds(saveRoot));
            referenced.addAll(WorldRestoreService.get().referencedBackupIds(server));
            referenced.addAll(WorldOperationService.get().referencedBackupIds(server));
            return reserveLocked(saveRoot, backupId, referenced);
        }
    }

    /** Test seam that also exercises persisted-journal collection without a live server. */
    Reservation reserveForTest(Path saveRoot, String backupId, Set<String> inMemoryReferences) throws IOException {
        Path root = normalize(saveRoot);
        synchronized (lock) {
            Set<String> referenced = new LinkedHashSet<>(BackupRetentionService.protectedBackupIds(root));
            if (inMemoryReferences != null) {
                referenced.addAll(inMemoryReferences);
            }
            return reserveLocked(root, backupId, referenced);
        }
    }

    /**
     * Serializes restore draft creation with deletion reservation for the selected ID. Both suppliers execute on the
     * caller (server) thread.
     *
     * @param <T> result type produced by either branch
     * @param server server whose normalized save root owns the backup
     * @param backupId normalized backup identifier being referenced
     * @param allowed action executed while holding the guard when no deletion is reserved
     * @param rejected action executed while holding the guard when deletion is already reserved
     * @return result of exactly one supplied action
     */
    public <T> T coordinateRestoreRequest(
            MinecraftServer server, String backupId, Supplier<T> allowed, Supplier<T> rejected) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(allowed, "allowed");
        Objects.requireNonNull(rejected, "rejected");
        Key key = new Key(normalize(server.getWorldPath(LevelResource.ROOT)), normalizedId(backupId));
        synchronized (lock) {
            return reservations.containsKey(key) ? rejected.get() : allowed.get();
        }
    }

    /** Test seam for proving the same reservation/reference atomicity without Minecraft state. */
    boolean coordinateReferenceForTest(Path saveRoot, String backupId, Runnable action) {
        Objects.requireNonNull(action, "action");
        Key key = new Key(normalize(saveRoot), normalizedId(backupId));
        synchronized (lock) {
            if (reservations.containsKey(key)) {
                return false;
            }
            action.run();
            return true;
        }
    }

    /**
     * Revokes outstanding reservations for one server session before worker/cache shutdown.
     *
     * <p>Queued workers subsequently fail their immediate pre-delete check and cannot begin a filesystem walk.
     *
     * @param server server session whose normalized save-root reservations are revoked
     */
    public void clear(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        Path saveRoot = normalize(server.getWorldPath(LevelResource.ROOT));
        synchronized (lock) {
            reservations.entrySet().removeIf(entry -> {
                if (!entry.getKey().saveRoot().equals(saveRoot)) {
                    return false;
                }
                entry.getValue().revokeLocked();
                return true;
            });
        }
    }

    private Reservation reserveLocked(Path saveRoot, String backupId, Set<String> referenced)
            throws DeletionRejectedException {
        String id = normalizedId(backupId);
        Key key = new Key(saveRoot, id);
        if (reservations.containsKey(key)) {
            throw new DeletionRejectedException(id, Reason.IN_PROGRESS);
        }
        if (referenced.contains(id)) {
            throw new DeletionRejectedException(id, Reason.REFERENCED);
        }
        Reservation reservation = new Reservation(this, key);
        reservations.put(key, reservation);
        return reservation;
    }

    private boolean permitLocked(Reservation reservation) {
        synchronized (lock) {
            return !reservation.closed && !reservation.revoked && reservations.get(reservation.key) == reservation;
        }
    }

    private void release(Reservation reservation) {
        synchronized (lock) {
            if (reservation.closed) {
                return;
            }
            reservation.closed = true;
            reservations.remove(reservation.key, reservation);
        }
    }

    private static Path normalize(Path saveRoot) {
        return Objects.requireNonNull(saveRoot, "saveRoot").toAbsolutePath().normalize();
    }

    private static String normalizedId(String backupId) {
        return backupId == null ? "" : backupId;
    }

    private record Key(Path saveRoot, String backupId) {}

    /** Stable rejection reason exposed to asynchronous deletion callers. */
    public enum Reason {
        /** An in-memory draft or persisted lifecycle journal references the backup. */
        REFERENCED,

        /** Another deletion already holds a reservation for the same save and backup identifier. */
        IN_PROGRESS,

        /** Server-session shutdown revoked the reservation before the worker began deletion. */
        SESSION_CLOSED
    }

    /** Checked failure raised when a backup cannot safely acquire or retain a deletion reservation. */
    public static final class DeletionRejectedException extends IOException {
        private static final long serialVersionUID = 1L;

        /** Normalized logical backup identifier rejected by the guard. */
        private final String backupId;
        /** Stable reason the deletion reservation was rejected. */
        private final Reason reason;

        /**
         * Creates a rejection carrying a bounded backup identifier and stable machine-readable reason.
         *
         * @param backupId normalized requested backup identifier
         * @param reason condition that prevented deletion
         */
        public DeletionRejectedException(String backupId, Reason reason) {
            super("Backup deletion rejected (" + reason + "): " + backupId);
            this.backupId = backupId;
            this.reason = Objects.requireNonNull(reason, "reason");
        }

        /**
         * Returns the backup identifier associated with the rejected request.
         *
         * @return normalized identifier, or the original empty fallback used by invalid callers
         */
        public String backupId() {
            return backupId;
        }

        /**
         * Returns the stable rejection category.
         *
         * @return non-null rejection reason
         */
        public Reason reason() {
            return reason;
        }
    }

    /**
     * Exclusive, save-scoped deletion capability retained until asynchronous deletion and refresh complete.
     *
     * <p>Reservations are idempotently closeable. Holding one does not itself authorize filesystem deletion; the worker
     * must call {@link #permitImmediatelyBeforeDelete()} immediately before walking the backup.
     */
    public static final class Reservation implements AutoCloseable {
        private final BackupDeletionGuard owner;
        private final Key key;
        private boolean closed;
        private boolean revoked;

        private Reservation(BackupDeletionGuard owner, Key key) {
            this.owner = owner;
            this.key = key;
        }

        /**
         * Performs the worker-thread check immediately before entering the filesystem deletion walk.
         *
         * @return {@code true} only while this exact reservation remains current, open, and unrevoked
         */
        public boolean permitImmediatelyBeforeDelete() {
            return owner.permitLocked(this);
        }

        boolean matches(Path saveRoot, String backupId) {
            return key.equals(new Key(normalize(saveRoot), normalizedId(backupId)));
        }

        private void revokeLocked() {
            revoked = true;
        }

        /** Releases this capability; repeated calls are harmless. */
        @Override
        public void close() {
            owner.release(this);
        }
    }
}
