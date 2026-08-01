package com.nightsta69.delvefold.audit;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;

/** Thread-safe JSON-lines mutation log with bounded, deterministic rotation. */
public final class RotatingAuditLog implements AutoCloseable {
    /** Production rotation threshold in bytes: 10 MiB. */
    public static final long ROTATE_BYTES = 10L * 1024L * 1024L;

    /** Includes the active log, so production retains the active file and four archives. */
    public static final int RETAINED_FILES = 5;

    /** Stable JSON-lines file name for the currently active audit generation. */
    public static final String ACTIVE_FILENAME = "delvefold-audit.jsonl";

    private static final Gson JSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .disableHtmlEscaping()
            .create();

    private final Path directory;
    private final Path active;
    private final Clock clock;
    private final long rotateBytes;
    private final int retainedFiles;
    private boolean closed;

    /**
     * Creates a production writer using UTC timestamps and production rotation limits.
     *
     * @param directory normalized on construction; created lazily by {@link #prepare()}
     */
    public RotatingAuditLog(Path directory) {
        this(directory, Clock.systemUTC());
    }

    /**
     * Creates a production-limit writer with an injectable timestamp source.
     *
     * @param directory normalized on construction; symbolic links are rejected before I/O
     * @param clock timestamp source used for each accepted entry
     */
    public RotatingAuditLog(Path directory, Clock clock) {
        this(directory, clock, ROTATE_BYTES, RETAINED_FILES);
    }

    RotatingAuditLog(Path directory, Clock clock, long rotateBytes, int retainedFiles) {
        if (directory == null || clock == null) {
            throw new IllegalArgumentException("Audit directory and clock are required");
        }
        if (rotateBytes < 128L || retainedFiles < 1 || retainedFiles > 100) {
            throw new IllegalArgumentException("Audit rotation bounds are invalid");
        }
        this.directory = directory.toAbsolutePath().normalize();
        this.active = this.directory.resolve(ACTIVE_FILENAME);
        this.clock = clock;
        this.rotateBytes = rotateBytes;
        this.retainedFiles = retainedFiles;
    }

    /**
     * Appends and durably flushes one accepted mutation, rotating before the size limit is crossed.
     *
     * @param mutation already-redacted whitelisted mutation
     * @return immutable entry containing the UTC timestamp and serialized logical fields that were written
     * @throws IOException if safety validation, rotation, writing, or file forcing fails
     */
    public synchronized AuditEntry append(AuditMutation mutation) throws IOException {
        checkOpen();
        if (mutation == null) {
            throw new IllegalArgumentException("mutation is required");
        }
        ensureDirectory();
        AuditEntry entry = new AuditEntry(
                AuditEntry.CURRENT_FORMAT_VERSION,
                Instant.now(clock).toString(),
                mutation.actor(),
                mutation.operation().serializedName(),
                mutation.affectedObject(),
                mutation.oldRevision(),
                mutation.newRevision());
        byte[] line = (JSON.toJson(entry) + "\n").getBytes(StandardCharsets.UTF_8);
        if (line.length > rotateBytes) {
            throw new IOException("One audit entry exceeds the configured log size limit");
        }
        validateRegularOrMissing(active);
        long currentBytes = Files.exists(active) ? Files.size(active) : 0L;
        if (currentBytes > 0L && currentBytes + line.length > rotateBytes) {
            rotate();
        }
        appendDurably(line);
        return entry;
    }

    /**
     * Creates and validates the log directory without writing an audit event.
     *
     * @throws IOException if the directory, active file, or retained archives fail safety checks
     */
    public synchronized void prepare() throws IOException {
        checkOpen();
        ensureDirectory();
        validateRegularOrMissing(active);
        for (int generation = 1; generation < retainedFiles; generation++) {
            validateRegularOrMissing(archivePath(generation));
        }
    }

    /**
     * Returns the immutable normalized path of the active generation.
     *
     * @return active JSON-lines path; the file may not exist before the first append
     */
    public Path activePath() {
        return active;
    }

    /**
     * Resolves one retained archive generation under the configured directory.
     *
     * @param generation one-based archive generation below {@link #RETAINED_FILES}
     * @return normalized archive path; generation one is the newest archive
     * @throws IllegalArgumentException if the generation falls outside the retention window
     */
    public Path archivePath(int generation) {
        if (generation < 1 || generation >= retainedFiles) {
            throw new IllegalArgumentException("Archive generation is outside the retention window");
        }
        return directory.resolve("delvefold-audit." + generation + ".jsonl");
    }

    /**
     * Forces every accepted byte in the active file to stable storage.
     *
     * @throws IOException if the path is unsafe, the writer is closed, or forcing the file fails
     */
    public synchronized void flush() throws IOException {
        checkOpen();
        forceActive();
    }

    /**
     * Flushes before making this writer permanently unavailable; repeated closes are no-ops.
     *
     * @throws IOException if the final file force fails
     */
    @Override
    public synchronized void close() throws IOException {
        if (closed) {
            return;
        }
        try {
            forceActive();
        } finally {
            closed = true;
        }
    }

    private void ensureDirectory() throws IOException {
        if (Files.notExists(directory)) {
            Files.createDirectories(directory);
        }
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory)) {
            throw new IOException("Audit directory failed safety checks");
        }
    }

    private void rotate() throws IOException {
        if (retainedFiles == 1) {
            Files.deleteIfExists(active);
            return;
        }
        Path oldest = archivePath(retainedFiles - 1);
        validateRegularOrMissing(oldest);
        Files.deleteIfExists(oldest);
        for (int generation = retainedFiles - 2; generation >= 1; generation--) {
            Path source = archivePath(generation);
            Path destination = archivePath(generation + 1);
            validateRegularOrMissing(source);
            validateRegularOrMissing(destination);
            if (Files.exists(source)) {
                move(source, destination);
            }
        }
        if (Files.exists(active)) {
            move(active, archivePath(1));
        }
    }

    private void appendDurably(byte[] line) throws IOException {
        try (FileChannel channel = FileChannel.open(
                active, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            ByteBuffer buffer = ByteBuffer.wrap(line);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    private void forceActive() throws IOException {
        validateRegularOrMissing(active);
        if (Files.notExists(active)) {
            return;
        }
        try (FileChannel channel = FileChannel.open(active, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private void checkOpen() throws IOException {
        if (closed) {
            throw new IOException("Audit log is closed");
        }
    }

    private static void validateRegularOrMissing(Path path) throws IOException {
        if (Files.isSymbolicLink(path) || (Files.exists(path) && !Files.isRegularFile(path))) {
            throw new IOException("Audit log path failed safety checks: " + path.getFileName());
        }
    }

    private static void move(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
