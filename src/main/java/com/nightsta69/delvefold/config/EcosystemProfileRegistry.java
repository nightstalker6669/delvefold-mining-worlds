package com.nightsta69.delvefold.config;

import com.google.gson.JsonElement;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.validation.OreConfigValidator;
import com.nightsta69.delvefold.config.validation.RegistryLookup;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/** Read-only ore profiles supplied by datapacks or optional script integrations. */
public final class EcosystemProfileRegistry {
    public static final int MAX_PROFILE_BYTES = 256 * 1024;
    private static final System.Logger LOGGER = System.getLogger(EcosystemProfileRegistry.class.getName());
    private static final Pattern PROFILE_ID = Pattern.compile(
            "(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+");
    private static final AtomicReference<Map<String, RegisteredProfile>> DATAPACKS =
            new AtomicReference<>(Map.of());
    private static final Map<String, RegisteredProfile> SCRIPTS = new ConcurrentHashMap<>();

    private EcosystemProfileRegistry() {
    }

    public static Map<String, RegisteredProfile> profiles() {
        Map<String, RegisteredProfile> merged = new LinkedHashMap<>(DATAPACKS.get());
        SCRIPTS.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> merged.put(entry.getKey(), entry.getValue()));
        return Map.copyOf(merged);
    }

    public static RegisteredProfile find(String id) {
        RegisteredProfile scripted = SCRIPTS.get(id);
        return scripted == null ? DATAPACKS.get().get(id) : scripted;
    }

    /** Script bridge; registrations live for the game process and are read-only in Delvefold's GUI. */
    public static void registerScriptJson(String owner, String id, String json) {
        String safeOwner = validateOwner(owner);
        String safeId = validateNamespacedProfileId(id);
        OreProfileDocument document = parseProfile(safeId, json);
        SCRIPTS.put(safeId, new RegisteredProfile(document, "script:" + safeOwner));
        LOGGER.log(System.Logger.Level.INFO,
                "Registered Delvefold script profile {0} from {1}", safeId, safeOwner);
    }

    public static boolean unregisterScriptProfile(String owner, String id) {
        String safeOwner = validateOwner(owner);
        String safeId = validateNamespacedProfileId(id);
        RegisteredProfile existing = SCRIPTS.get(safeId);
        return existing != null
                && existing.source().equals("script:" + safeOwner)
                && SCRIPTS.remove(safeId, existing);
    }

    public static String validateProfileId(String id) {
        String normalized = id == null ? "" : id.trim();
        if (normalized.length() > OreProfileCatalog.MAX_PROFILE_ID_LENGTH
                || !PROFILE_ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "Profile ID must be a lowercase path, optionally namespaced (example: packname:rich_ores)");
        }
        return normalized;
    }

    private static String validateNamespacedProfileId(String id) {
        String normalized = validateProfileId(id);
        if (!normalized.contains(":")) {
            throw new IllegalArgumentException("Ecosystem profile IDs must be namespaced (example: packname:rich_ores)");
        }
        return normalized;
    }

    private static String validateOwner(String owner) {
        String normalized = owner == null ? "" : owner.trim();
        if (!normalized.matches("[a-z0-9_.-]{1,64}")) {
            throw new IllegalArgumentException("Script owner must use lowercase letters, digits, _, . or -");
        }
        return normalized;
    }

    private static OreProfileDocument parseProfile(String id, String json) {
        byte[] bytes = (json == null ? "" : json).getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0 || bytes.length > MAX_PROFILE_BYTES) {
            throw new IllegalArgumentException("Profile JSON must be between 1 and " + MAX_PROFILE_BYTES + " bytes");
        }
        try {
            JsonElement parsed = StrictConfigStructure.parseAndValidate(json, OreProfileDocument.class);
            OreProfileDocument decoded = ConfigJson.GSON.fromJson(parsed, OreProfileDocument.class);
            OreProfileDocument normalized = new OreProfileDocument(
                    OreProfileDocument.CURRENT_SCHEMA_VERSION, 0, id, decoded.rules());
            var validation = OreConfigValidator.validate(normalized, RegistryLookup.SKIP);
            if (!validation.valid()) {
                throw new IllegalArgumentException("Profile validation failed: " + validation.issues());
            }
            return normalized;
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException argument) {
                throw argument;
            }
            throw new IllegalArgumentException("Invalid profile JSON: " + exception.getMessage(), exception);
        }
    }

    static OreProfileDocument parseDatapackProfile(String id, String json) {
        return parseProfile(validateProfileId(id), json);
    }

    static void replaceDatapackProfiles(Map<String, RegisteredProfile> profiles) {
        DATAPACKS.set(Map.copyOf(profiles));
    }

    public record RegisteredProfile(OreProfileDocument document, String source) {
        public RegisteredProfile {
            document = java.util.Objects.requireNonNull(document, "document");
            source = source == null || source.isBlank() ? "unknown" : source;
        }
    }

}
