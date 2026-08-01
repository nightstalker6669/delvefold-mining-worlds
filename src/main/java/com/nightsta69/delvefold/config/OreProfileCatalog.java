package com.nightsta69.delvefold.config;

import com.google.gson.JsonElement;
import com.nightsta69.delvefold.admin.AdminLocalizedMessage;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.validation.ConfigIssue;
import com.nightsta69.delvefold.config.validation.OreConfigValidator;
import com.nightsta69.delvefold.config.validation.RegistryLookup;
import com.nightsta69.delvefold.config.validation.ValidationReport;
import com.nightsta69.delvefold.internal.io.AtomicFiles;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Safe per-save catalog for bundled, ecosystem, and administrator-created ore profiles.
 *
 * <p>Local and transfer names are constrained to simple IDs/files, normalized paths must remain inside their configured
 * directories, and existing symlinks or non-regular files are never followed. Local writes are forced through bounded
 * temporary files and atomic replacement where supported. The catalog is not internally synchronized; the configuration
 * service serializes ordinary mutations, while create-new persistence independently rejects a concurrent winner.
 */
public final class OreProfileCatalog {
    /** Maximum characters in a local or ecosystem profile ID. */
    public static final int MAX_PROFILE_ID_LENGTH = 128;
    /** Maximum UTF-8 transfer or local-profile size: 256 KiB. */
    public static final int MAX_TRANSFER_BYTES = 256 * 1024;

    private static final Map<String, OrePreset> BUILT_INS = Map.of(
            "vanilla_balanced", OrePreset.VANILLA_BALANCED,
            "rich", OrePreset.RICH,
            "empty", OrePreset.EMPTY);

    private final ConfigPaths paths;
    private final RegistryLookup registryLookup;

    /**
     * Creates a catalog bound to one save and current registry-validation view.
     *
     * @param paths normalized save-local profile, import, and export paths
     * @param registryLookup read-only registry lookup used before a profile is loaded or written
     */
    public OreProfileCatalog(ConfigPaths paths, RegistryLookup registryLookup) {
        this.paths = paths;
        this.registryLookup = registryLookup;
    }

    /**
     * Lists bundled, current ecosystem, and local profiles in lexical ID order.
     *
     * <p>The method creates the profile/import/export directories when missing, ignores symlinks and non-regular local
     * entries, and contains malformed local profiles as summaries with diagnostics. A local profile overrides the same
     * bundled ID in the merged summary while preserving both provenance flags. The returned list is unmodifiable.
     *
     * @return immutable point-in-time summaries sorted by profile ID
     * @throws IOException if catalog directories or directory enumeration cannot be accessed safely
     */
    public List<ProfileSummary> list() throws IOException {
        createDirectories();
        Map<String, ProfileSummary> summaries = new LinkedHashMap<>();
        for (Map.Entry<String, OrePreset> builtIn : BUILT_INS.entrySet()) {
            OreProfileDocument document = OrePresets.create(builtIn.getValue());
            summaries.put(builtIn.getKey(), summary(document, true, false));
        }
        for (Map.Entry<String, EcosystemProfileRegistry.RegisteredProfile> entry :
                EcosystemProfileRegistry.profiles().entrySet()) {
            OreProfileDocument document = entry.getValue().document();
            ValidationReport report = OreConfigValidator.validate(document, registryLookup);
            summaries.put(
                    entry.getKey(),
                    new ProfileSummary(
                            entry.getKey(),
                            true,
                            false,
                            document.rules().size(),
                            document.revision(),
                            report.issues()));
        }
        try (var files = Files.list(paths.profiles())) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .toList()) {
                if (Files.isSymbolicLink(file) || !Files.isRegularFile(file)) {
                    continue;
                }
                String id = file.getFileName().toString();
                id = id.substring(0, id.length() - ".json".length());
                try {
                    validateId(id);
                    OreProfileDocument document = read(file);
                    ValidationReport report = OreConfigValidator.validate(document, registryLookup);
                    summaries.put(
                            id,
                            new ProfileSummary(
                                    id,
                                    BUILT_INS.containsKey(id),
                                    true,
                                    document.rules().size(),
                                    document.revision(),
                                    report.issues()));
                } catch (IOException | RuntimeException exception) {
                    summaries.put(
                            id,
                            new ProfileSummary(
                                    id,
                                    BUILT_INS.containsKey(id),
                                    true,
                                    0,
                                    0,
                                    List.of(ConfigIssue.error("profile.invalid", "$", exception.getMessage()))));
                }
            }
        }
        return summaries.values().stream()
                .sorted(Comparator.comparing(ProfileSummary::id))
                .toList();
    }

    /**
     * Loads and registry-validates one immutable profile snapshot.
     *
     * <p>For an unnamespaced ID, a local file takes precedence over an ecosystem or bundled entry. Ecosystem profiles
     * take precedence over bundled profiles when IDs overlap. Local files must be regular, non-symlink entries no
     * larger than {@value #MAX_TRANSFER_BYTES} bytes and must pass strict structure and schema-2 validation.
     *
     * @param id local or namespaced profile ID
     * @return validated immutable profile document
     * @throws IllegalArgumentException if the ID syntax is invalid
     * @throws IOException if the profile is unknown, unsafe, oversized, malformed, or invalid for the current registry
     */
    public OreProfileDocument load(String id) throws IOException {
        String safeId = validateId(id);
        Path local = isLocalId(safeId) ? localPath(safeId) : null;
        if (local != null && Files.exists(local)) {
            ensureSafeFile(local);
            OreProfileDocument document = read(local);
            ValidationReport report = OreConfigValidator.validate(document, registryLookup);
            if (!report.valid()) {
                throw new IOException("Profile '" + safeId + "' failed validation: " + report.issues());
            }
            return document;
        }
        EcosystemProfileRegistry.RegisteredProfile ecosystem = EcosystemProfileRegistry.find(safeId);
        if (ecosystem != null) {
            ValidationReport report = OreConfigValidator.validate(ecosystem.document(), registryLookup);
            if (!report.valid()) {
                throw new IOException("Profile '" + safeId + "' failed validation: " + report.issues());
            }
            return ecosystem.document();
        }
        OrePreset preset = BUILT_INS.get(safeId);
        if (preset != null) {
            return OrePresets.create(preset);
        }
        throw new IOException("Unknown ore profile: " + safeId);
    }

    /**
     * Saves source rules under a local profile ID, optionally replacing an existing local entry.
     *
     * <p>The catalog rewrites schema, profile ID, and revision: a new file starts at revision zero, while replacement
     * increments the existing nonnegative revision. A local entry may intentionally override a bundled ID. Registry,
     * profile, and aggregate work validation run before a bounded atomic write; rejection returns a result without
     * changing disk.
     *
     * @param id simple lowercase local profile ID
     * @param source immutable source document whose complete rule list will be copied
     * @param overwrite whether an existing local file may be replaced
     * @return committed profile and prior revision, or a localized rejection result
     * @throws IllegalArgumentException if the local ID is invalid
     * @throws IOException if an existing entry is unsafe/malformed or persistence fails
     */
    public ProfileWriteResult saveAs(String id, OreProfileDocument source, boolean overwrite) throws IOException {
        String safeId = validateLocalId(id);
        Path target = localPath(safeId);
        boolean exists = Files.exists(target, LinkOption.NOFOLLOW_LINKS);
        if (!overwrite && exists) {
            return ProfileWriteResult.rejected(localized("message.delvefold.profile.local_exists", safeId));
        }
        long previousRevision = exists ? Math.max(0, read(target).revision()) : -1L;
        long revision = previousRevision < 0L ? 0L : previousRevision + 1L;
        OreProfileDocument replacement =
                new OreProfileDocument(OreProfileDocument.CURRENT_SCHEMA_VERSION, revision, safeId, source.rules());
        ValidationReport report = OreConfigValidator.validate(replacement, registryLookup);
        if (!report.valid()) {
            return new ProfileWriteResult(
                    false,
                    null,
                    report.issues(),
                    localized("message.delvefold.profile.validation_failed"),
                    previousRevision);
        }
        write(target, replacement);
        return new ProfileWriteResult(
                true,
                replacement,
                report.issues(),
                localized("message.delvefold.profile.saved", safeId),
                previousRevision);
    }

    /**
     * Creates a genuinely new local profile.
     *
     * <p>Unlike {@link #saveAs(String, OreProfileDocument, boolean)}, this path cannot create a local override of a
     * bundled/ecosystem profile and never replaces an existing filesystem entry. A concurrent creator wins atomically;
     * this call then returns a localized rejection. Successful profiles start at revision zero and remain inactive
     * until explicitly selected through the configuration service.
     *
     * @param id simple lowercase local profile ID
     * @param source immutable source document whose complete rule list will be copied
     * @return created inactive profile, or a localized collision/validation rejection
     * @throws NullPointerException if {@code source} is {@code null}
     * @throws IllegalArgumentException if the local ID is invalid
     * @throws IOException if catalog preparation or bounded persistence fails
     */
    public ProfileWriteResult createNew(String id, OreProfileDocument source) throws IOException {
        String safeId = validateLocalId(id);
        java.util.Objects.requireNonNull(source, "source");
        Path target = localPath(safeId);
        if (BUILT_INS.containsKey(safeId)
                || EcosystemProfileRegistry.find(safeId) != null
                || Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return ProfileWriteResult.rejected(localized("message.delvefold.profile.exists", safeId));
        }
        OreProfileDocument replacement =
                new OreProfileDocument(OreProfileDocument.CURRENT_SCHEMA_VERSION, 0, safeId, source.rules());
        ValidationReport report = OreConfigValidator.validate(replacement, registryLookup);
        if (!report.valid()) {
            return new ProfileWriteResult(
                    false, null, report.issues(), localized("message.delvefold.profile.validation_failed"), -1L);
        }
        try {
            writeNew(target, replacement);
        } catch (java.nio.file.FileAlreadyExistsException exception) {
            return ProfileWriteResult.rejected(localized("message.delvefold.profile.exists", safeId));
        }
        return new ProfileWriteResult(
                true, replacement, report.issues(), localized("message.delvefold.profile.created", safeId), -1L);
    }

    /**
     * Strictly parses and saves a bounded JSON profile under a local ID.
     *
     * <p>The UTF-8 payload must contain 1 through {@value #MAX_TRANSFER_BYTES} bytes. Unknown structure, JSON null,
     * malformed content, and validation failures are returned as localized rejections rather than parser exceptions. No
     * imported profile becomes active automatically.
     *
     * @param id destination local profile ID
     * @param json complete schema-2 ore-profile JSON
     * @param overwrite whether an existing local profile may be replaced
     * @return committed inactive local profile or a localized rejection
     * @throws IOException if destination inspection or persistence fails
     */
    public ProfileWriteResult importJson(String id, String json, boolean overwrite) throws IOException {
        byte[] bytes = (json == null ? "" : json).getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0 || bytes.length > MAX_TRANSFER_BYTES) {
            return ProfileWriteResult.rejected(localized("message.delvefold.profile.json_size", MAX_TRANSFER_BYTES));
        }
        try {
            JsonElement parsed = StrictConfigStructure.parseAndValidate(json, OreProfileDocument.class);
            OreProfileDocument source = ConfigJson.GSON.fromJson(parsed, OreProfileDocument.class);
            return saveAs(id, source, overwrite);
        } catch (RuntimeException exception) {
            return ProfileWriteResult.rejected(
                    localized("message.delvefold.profile.invalid_json", exception.getMessage()));
        }
    }

    /**
     * Imports a simple named JSON file from the save-local transfer directory.
     *
     * <p>The source must be a regular non-symlink file, its name must match the bounded simple-filename policy, and its
     * size must not exceed {@value #MAX_TRANSFER_BYTES} bytes. The source file is not deleted after import.
     *
     * @param fileName simple transfer filename of 1–160 safe characters plus the {@code .json} suffix
     * @param id destination local profile ID
     * @param overwrite whether an existing local profile may be replaced
     * @return committed inactive local profile or a localized size/parse/validation rejection
     * @throws IOException if the transfer path escapes, is missing/unsafe, cannot be read, or persistence fails
     */
    public ProfileWriteResult importFile(String fileName, String id, boolean overwrite) throws IOException {
        Path source = transferPath(paths.imports(), fileName);
        ensureSafeFile(source);
        if (Files.size(source) > MAX_TRANSFER_BYTES) {
            return ProfileWriteResult.rejected(localized("message.delvefold.profile.import_size", MAX_TRANSFER_BYTES));
        }
        return importJson(id, Files.readString(source, StandardCharsets.UTF_8), overwrite);
    }

    /**
     * Serializes one currently valid profile with the canonical JSON codec.
     *
     * @param id local, ecosystem, or bundled profile ID
     * @return pretty-printed schema-2 JSON without a trailing line separator
     * @throws IllegalArgumentException if the ID syntax is invalid
     * @throws IOException if the profile cannot be loaded and validated
     */
    public String exportJson(String id) throws IOException {
        return ConfigJson.GSON.toJson(load(id));
    }

    /**
     * Atomically writes one currently valid profile to the save-local export directory.
     *
     * @param id local, ecosystem, or bundled profile ID
     * @param fileName simple destination filename of 1–160 safe characters plus the {@code .json} suffix
     * @return normalized export path inside the configured export directory
     * @throws IllegalArgumentException if the profile ID syntax is invalid
     * @throws IOException if loading, path containment, size enforcement, or persistence fails
     */
    public Path exportFile(String id, String fileName) throws IOException {
        Path target = transferPath(paths.exports(), fileName);
        writeBytesAtomically(target, (exportJson(id) + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
        return target;
    }

    /**
     * Deletes one local profile file without affecting a same-ID bundled fallback.
     *
     * @param id simple lowercase local profile ID
     * @return {@code true} when a regular local file was deleted; {@code false} when none existed
     * @throws IllegalArgumentException if the local ID is invalid
     * @throws IOException if the existing entry is unsafe, malformed, or cannot be deleted
     */
    public boolean deleteLocal(String id) throws IOException {
        return deleteLocalWithRevision(id).deleted();
    }

    /**
     * Deletes one local profile and reports the revision that actually existed on disk.
     *
     * @param id simple lowercase local profile ID
     * @return deletion outcome with prior revision, or revision {@code -1} when no local file existed
     * @throws IllegalArgumentException if the local ID is invalid
     * @throws IOException if the existing entry is unsafe, malformed, or cannot be deleted
     */
    public ProfileDeleteResult deleteLocalWithRevision(String id) throws IOException {
        Path target = localPath(validateLocalId(id));
        if (Files.notExists(target, LinkOption.NOFOLLOW_LINKS)) {
            return new ProfileDeleteResult(false, -1L);
        }
        ensureSafeFile(target);
        long previousRevision = Math.max(0L, read(target).revision());
        return new ProfileDeleteResult(Files.deleteIfExists(target), previousRevision);
    }

    private void createDirectories() throws IOException {
        Files.createDirectories(paths.profiles());
        Files.createDirectories(paths.imports());
        Files.createDirectories(paths.exports());
    }

    private Path localPath(String id) throws IOException {
        createDirectories();
        return contained(paths.profiles(), id + ".json");
    }

    private Path transferPath(Path directory, String fileName) throws IOException {
        createDirectories();
        if (fileName == null || !fileName.matches("[a-zA-Z0-9_.-]{1,160}\\.json")) {
            throw new IOException("Transfer filename must be a simple .json filename");
        }
        return contained(directory, fileName);
    }

    private static Path contained(Path directory, String child) throws IOException {
        Path root = directory.toAbsolutePath().normalize();
        Path result = root.resolve(child).normalize();
        if (!result.startsWith(root) || result.equals(root)) {
            throw new IOException("Profile path escaped its configured directory");
        }
        return result;
    }

    private static String validateId(String id) {
        return EcosystemProfileRegistry.validateProfileId(id);
    }

    private static String validateLocalId(String id) {
        String normalized = id == null ? "" : id.trim();
        if (!isLocalId(normalized) || normalized.length() > MAX_PROFILE_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "Local profile ID may contain lowercase letters, digits, _, . and - only");
        }
        return normalized;
    }

    private static boolean isLocalId(String id) {
        return id != null && id.matches("[a-z0-9_.-]+");
    }

    private OreProfileDocument read(Path path) throws IOException {
        ensureSafeFile(path);
        if (Files.size(path) > MAX_TRANSFER_BYTES) {
            throw new IOException(path.getFileName() + " exceeds the profile size limit");
        }
        String json = Files.readString(path, StandardCharsets.UTF_8);
        JsonElement parsed = StrictConfigStructure.parseAndValidate(json, OreProfileDocument.class);
        OreProfileDocument value = ConfigJson.GSON.fromJson(parsed, OreProfileDocument.class);
        if (value == null) {
            throw new IOException(path.getFileName() + " contains JSON null");
        }
        return value;
    }

    private static void ensureSafeFile(Path path) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path)) {
            throw new IOException("Refusing non-regular profile file: " + path.getFileName());
        }
    }

    private static void write(Path target, OreProfileDocument document) throws IOException {
        writeBytesAtomically(
                target, (ConfigJson.GSON.toJson(document) + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
    }

    private static void writeNew(Path target, OreProfileDocument document) throws IOException {
        byte[] bytes = (ConfigJson.GSON.toJson(document) + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_TRANSFER_BYTES) {
            throw new IOException(target.getFileName() + " exceeds the profile size limit");
        }
        Files.createDirectories(target.getParent());
        Path temporary =
                Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        boolean moved = false;
        try {
            try (FileChannel channel =
                    FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            // No REPLACE_EXISTING option: a concurrent creator wins and this call is rejected.
            Files.move(temporary, target);
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static void writeBytesAtomically(Path target, byte[] bytes) throws IOException {
        if (bytes.length > MAX_TRANSFER_BYTES) {
            throw new IOException(target.getFileName() + " exceeds the profile size limit");
        }
        AtomicFiles.writeReplacing(target, bytes);
    }

    private static ProfileSummary summary(OreProfileDocument document, boolean builtIn, boolean local) {
        return new ProfileSummary(
                document.profile(), builtIn, local, document.rules().size(), document.revision(), List.of());
    }

    /**
     * Immutable bounded catalog row suitable for administration snapshots.
     *
     * @param id local or namespaced profile ID
     * @param builtIn whether the ID has a read-only bundled or ecosystem base source
     * @param localOverride whether a save-local file currently supplies or overrides this ID
     * @param ruleCount decoded rule count, or zero when the local file could not be decoded
     * @param revision nonnegative local/document revision, or zero for an invalid summary
     * @param issues immutable validation or contained parse diagnostics
     */
    public record ProfileSummary(
            String id, boolean builtIn, boolean localOverride, int ruleCount, long revision, List<ConfigIssue> issues) {
        /**
         * Creates a summary and takes an immutable snapshot of its diagnostics.
         *
         * @param id profile ID
         * @param builtIn whether a read-only base source exists
         * @param localOverride whether a local file supplies the ID
         * @param ruleCount decoded rule count
         * @param revision document revision
         * @param issues validation or parse diagnostics
         * @throws NullPointerException if {@code issues} is {@code null}
         */
        public ProfileSummary {
            issues = List.copyOf(issues);
        }
    }

    /**
     * Immutable local-profile persistence outcome with audit revision metadata.
     *
     * @param saved whether the local file was committed
     * @param profile committed immutable profile, or {@code null} on rejection
     * @param issues immutable validation warnings/errors
     * @param message encoded localized user-facing outcome
     * @param previousRevision prior local revision, or {@code -1} when no prior local profile is known
     */
    public record ProfileWriteResult(
            boolean saved,
            @Nullable OreProfileDocument profile,
            List<ConfigIssue> issues,
            String message,
            long previousRevision) {
        /**
         * Creates a write result, snapshots issues, normalizes a blank message, and validates audit metadata.
         *
         * @param saved whether persistence succeeded
         * @param profile committed profile, or {@code null} on rejection
         * @param issues ordered validation diagnostics
         * @param message encoded localized user-facing outcome
         * @param previousRevision prior local revision, or {@code -1}
         * @throws IllegalArgumentException if {@code previousRevision} is less than {@code -1}
         */
        public ProfileWriteResult {
            issues = issues == null ? List.of() : List.copyOf(issues);
            message = message == null || message.isBlank()
                    ? localized("message.delvefold.profile.operation_failed")
                    : message;
            if (previousRevision < -1L) {
                throw new IllegalArgumentException("Previous profile revision must be non-negative or -1");
            }
        }

        /**
         * Creates a source-compatible result for callers predating prior-revision audit metadata.
         *
         * @param saved whether persistence succeeded
         * @param profile committed profile, or {@code null} on rejection
         * @param issues ordered validation diagnostics
         * @param message encoded localized user-facing outcome
         */
        public ProfileWriteResult(
                boolean saved, @Nullable OreProfileDocument profile, List<ConfigIssue> issues, String message) {
            this(saved, profile, issues, message, -1L);
        }

        /**
         * Creates a simple rejection with no profile, issues, or known prior revision.
         *
         * @param message encoded localized rejection message
         * @return unsaved rejection result
         */
        public static ProfileWriteResult rejected(String message) {
            return new ProfileWriteResult(false, null, List.of(), message, -1L);
        }
    }

    /**
     * Immutable local-profile deletion outcome used for audit revision metadata.
     *
     * @param deleted whether a local file was deleted
     * @param previousRevision deleted document revision, or {@code -1} when no file existed
     */
    public record ProfileDeleteResult(boolean deleted, long previousRevision) {
        /**
         * Validates a deletion outcome.
         *
         * @param deleted whether a local file was deleted
         * @param previousRevision deleted document revision, or {@code -1}
         * @throws IllegalArgumentException if {@code previousRevision} is less than {@code -1}
         */
        public ProfileDeleteResult {
            if (previousRevision < -1L) {
                throw new IllegalArgumentException("Previous profile revision must be non-negative or -1");
            }
        }
    }

    private static String localized(String translationKey, @Nullable Object... arguments) {
        return AdminLocalizedMessage.encode(translationKey, arguments);
    }
}
