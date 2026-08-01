package com.nightsta69.delvefold.config;

import com.google.gson.JsonElement;
import com.nightsta69.delvefold.admin.AdminLocalizedMessage;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.validation.ConfigIssue;
import com.nightsta69.delvefold.config.validation.OreConfigValidator;
import com.nightsta69.delvefold.config.validation.RegistryLookup;
import com.nightsta69.delvefold.config.validation.ValidationReport;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Safe per-save catalog for built-in and administrator-created ore profiles. */
public final class OreProfileCatalog {
    public static final int MAX_PROFILE_ID_LENGTH = 128;
    public static final int MAX_TRANSFER_BYTES = 256 * 1024;
    private static final Map<String, OrePreset> BUILT_INS = Map.of(
            "vanilla_balanced", OrePreset.VANILLA_BALANCED,
            "rich", OrePreset.RICH,
            "empty", OrePreset.EMPTY);

    private final ConfigPaths paths;
    private final RegistryLookup registryLookup;

    public OreProfileCatalog(ConfigPaths paths, RegistryLookup registryLookup) {
        this.paths = paths;
        this.registryLookup = registryLookup;
    }

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
     * Creates a genuinely new local profile. Unlike {@link #saveAs}, this path cannot create a local override of a
     * built-in/ecosystem profile and never replaces an existing filesystem entry.
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

    public ProfileWriteResult importFile(String fileName, String id, boolean overwrite) throws IOException {
        Path source = transferPath(paths.imports(), fileName);
        ensureSafeFile(source);
        if (Files.size(source) > MAX_TRANSFER_BYTES) {
            return ProfileWriteResult.rejected(localized("message.delvefold.profile.import_size", MAX_TRANSFER_BYTES));
        }
        return importJson(id, Files.readString(source, StandardCharsets.UTF_8), overwrite);
    }

    public String exportJson(String id) throws IOException {
        return ConfigJson.GSON.toJson(load(id));
    }

    public Path exportFile(String id, String fileName) throws IOException {
        Path target = transferPath(paths.exports(), fileName);
        writeBytesAtomically(target, (exportJson(id) + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
        return target;
    }

    public boolean deleteLocal(String id) throws IOException {
        return deleteLocalWithRevision(id).deleted();
    }

    /** Deletes one local profile and reports the revision that actually existed on disk. */
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
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static ProfileSummary summary(OreProfileDocument document, boolean builtIn, boolean local) {
        return new ProfileSummary(
                document.profile(), builtIn, local, document.rules().size(), document.revision(), List.of());
    }

    public record ProfileSummary(
            String id, boolean builtIn, boolean localOverride, int ruleCount, long revision, List<ConfigIssue> issues) {
        public ProfileSummary {
            issues = List.copyOf(issues);
        }
    }

    public record ProfileWriteResult(
            boolean saved,
            OreProfileDocument profile,
            List<ConfigIssue> issues,
            String message,
            long previousRevision) {
        public ProfileWriteResult {
            issues = issues == null ? List.of() : List.copyOf(issues);
            message = message == null || message.isBlank()
                    ? localized("message.delvefold.profile.operation_failed")
                    : message;
            if (previousRevision < -1L) {
                throw new IllegalArgumentException("Previous profile revision must be non-negative or -1");
            }
        }

        /** Source-compatible constructor for callers predating mutation-kind audit metadata. */
        public ProfileWriteResult(boolean saved, OreProfileDocument profile, List<ConfigIssue> issues, String message) {
            this(saved, profile, issues, message, -1L);
        }

        public static ProfileWriteResult rejected(String message) {
            return new ProfileWriteResult(false, null, List.of(), message, -1L);
        }
    }

    public record ProfileDeleteResult(boolean deleted, long previousRevision) {
        public ProfileDeleteResult {
            if (previousRevision < -1L) {
                throw new IllegalArgumentException("Previous profile revision must be non-negative or -1");
            }
        }
    }

    private static String localized(String translationKey, Object... arguments) {
        return AdminLocalizedMessage.encode(translationKey, arguments);
    }
}
