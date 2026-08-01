package com.nightsta69.delvefold.audit;

import com.mojang.logging.LogUtils;
import com.nightsta69.delvefold.config.ConfigPaths;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

/** Server-lifecycle facade for failure-contained Delvefold mutation auditing. */
public final class DelvefoldAuditService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DelvefoldAuditService INSTANCE = new DelvefoldAuditService(Clock.systemUTC());

    private final Object lock = new Object();
    private final Clock clock;
    private final AuditActorContext actors = new AuditActorContext();
    private MinecraftServer server;
    private AuditWriteQueue writer;

    DelvefoldAuditService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public static DelvefoldAuditService get() {
        return INSTANCE;
    }

    /** Initializes this save's audit directory. A logging failure never aborts server startup. */
    public boolean start(MinecraftServer minecraftServer) {
        Objects.requireNonNull(minecraftServer, "minecraftServer");
        synchronized (lock) {
            closeContained(writer);
            server = null;
            writer = null;
            try {
                Path directory = auditDirectory(ConfigPaths.forServer(minecraftServer));
                RotatingAuditLog replacement = new RotatingAuditLog(directory, clock);
                replacement.prepare();
                AuditWriteQueue replacementWriter = new AuditWriteQueue(
                        replacement, DelvefoldAuditService::reportWriteFailure);
                server = minecraftServer;
                writer = replacementWriter;
                return true;
            } catch (Exception exception) {
                server = null;
                writer = null;
                LOGGER.error("Delvefold audit logging could not be initialized", exception);
                return false;
            }
        }
    }

    public void stop(MinecraftServer minecraftServer) {
        AuditWriteQueue current = null;
        synchronized (lock) {
            if (server == minecraftServer) {
                server = null;
                current = writer;
                writer = null;
            }
        }
        closeContained(current);
    }

    /**
     * Records one accepted mutation. Returns false when auditing is unavailable; logging failures
     * are contained and must never roll back the already-accepted gameplay operation.
     */
    public boolean record(AuditMutation mutation) {
        if (mutation == null) {
            LOGGER.warn("Delvefold dropped a null audit mutation");
            return false;
        }
        AuditMutation captured;
        try {
            captured = actors.capture(mutation);
        } catch (IllegalArgumentException exception) {
            LOGGER.warn("Delvefold dropped an invalid audit mutation", exception);
            return false;
        }
        AuditWriteQueue current;
        synchronized (lock) {
            current = writer;
        }
        if (current == null) {
            LOGGER.warn("Delvefold dropped an audit mutation because the writer is unavailable");
            return false;
        }
        return current.offer(captured);
    }

    public boolean available() {
        synchronized (lock) {
            return writer != null && writer.accepting();
        }
    }

    /**
     * Attributes every nested audit mutation on this caller thread to {@code actor} until closed.
     * Scopes are nest-safe and must be closed in reverse order on their owning thread.
     */
    public ActorScope pushActor(String actor) {
        return actors.push(actor);
    }

    /** Returns the innermost scoped actor, or {@code server} when no caller scope is active. */
    public String currentActorOrServer() {
        return actors.currentActorOrServer();
    }

    private static void closeContained(AuditWriteQueue current) {
        if (current != null) {
            current.close();
        }
    }

    private static void reportWriteFailure(String reason, Throwable failure) {
        if (failure == null) {
            LOGGER.warn("Delvefold dropped an audit mutation: {}", reason);
        } else {
            LOGGER.error("Delvefold audit writer failure: {}", reason, failure);
        }
    }

    /** Closeable token returned by {@link #pushActor(String)}. */
    public interface ActorScope extends AutoCloseable {
        @Override
        void close();
    }

    static Path auditDirectory(ConfigPaths paths) {
        Objects.requireNonNull(paths, "paths");
        Path base = paths.directory().toAbsolutePath().normalize();
        Path result = base.resolve("audit").normalize();
        if (!result.startsWith(base) || result.equals(base)) {
            throw new IllegalStateException("Resolved audit directory escaped Delvefold serverconfig");
        }
        return result;
    }
}
