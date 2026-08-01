package com.nightsta69.delvefold.world.landmark.catalog;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.nightsta69.delvefold.audit.AuditMutation;
import com.nightsta69.delvefold.audit.DelvefoldAuditService;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.storage.loot.LootTable;
import org.slf4j.Logger;

/** Reloads {@code data/<namespace>/delvefold/landmarks/*.json} as one atomic catalog. */
public final class LandmarkCatalogReloadListener extends
        SimplePreparableReloadListener<LandmarkCatalogReloadListener.LoadResult> {
    public static final String DIRECTORY = "delvefold/landmarks";
    public static final int MAX_DEFINITIONS = 256;
    public static final int MAX_DEFINITION_BYTES = 65_536;
    public static final long MAX_TEMPLATE_NBT_BYTES = 8L * 1024L * 1024L;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicReference<AuditMutation> PENDING_STARTUP_AUDIT = new AtomicReference<>();

    @Override
    protected LoadResult prepare(ResourceManager resources, ProfilerFiller profiler) {
        Map<ResourceLocation, LandmarkDefinition> definitions = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        List<Map.Entry<ResourceLocation, Resource>> entries = resources
                .listResources(DIRECTORY, id -> id.getPath().endsWith(".json"))
                .entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.naturalOrder())).toList();
        if (entries.size() > MAX_DEFINITIONS) {
            errors.add("catalog contains " + entries.size() + " definitions; maximum is " + MAX_DEFINITIONS);
        }
        entries.stream().limit(MAX_DEFINITIONS)
                .forEach(entry -> loadOne(resources, entry.getKey(), entry.getValue(), definitions, errors));
        errors.addAll(LandmarkCatalogWorkBudget.validate(definitions.values()));
        if (definitions.isEmpty() && errors.isEmpty()) {
            errors.add("catalog contains no landmark definitions");
        }
        return new LoadResult(Map.copyOf(definitions), List.copyOf(errors), Instant.now());
    }

    private void loadOne(
            ResourceManager resources,
            ResourceLocation fileId,
            Resource resource,
            Map<ResourceLocation, LandmarkDefinition> output,
            List<String> errors) {
        ResourceLocation definitionId = definitionId(fileId);
        try (InputStream input = resource.open()) {
            byte[] bytes = input.readNBytes(MAX_DEFINITION_BYTES + 1);
            LandmarkDefinition definition = decodeDefinition(fileId, bytes);
            validateDependencies(resources, definition);
            if (output.putIfAbsent(definitionId, definition) != null) {
                throw new IllegalArgumentException("duplicate definition ID " + definitionId);
            }
        } catch (IOException | RuntimeException exception) {
            errors.add(fileId + ": " + safeMessage(exception));
        }
    }

    /** Package-private deterministic seam for malformed-resource and LKG tests. */
    static LandmarkDefinition decodeDefinition(ResourceLocation fileId, byte[] bytes) {
        if (bytes == null || bytes.length > MAX_DEFINITION_BYTES) {
            throw new IllegalArgumentException("exceeds " + MAX_DEFINITION_BYTES + " bytes");
        }
        ResourceLocation definitionId = definitionId(fileId);
        JsonElement json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
        LandmarkDefinition.Body body = LandmarkDefinition.BODY_CODEC.parse(JsonOps.INSTANCE, json)
                .getOrThrow(message -> new IllegalArgumentException(message));
        return LandmarkDefinition.from(definitionId, body);
    }

    private void validateDependencies(ResourceManager resources, LandmarkDefinition definition) throws IOException {
        ResourceLocation templateFile = ResourceLocation.fromNamespaceAndPath(
                definition.template().getNamespace(), "structure/" + definition.template().getPath() + ".nbt");
        Resource templateResource = resources.getResource(templateFile).orElse(null);
        if (templateResource == null) {
            throw new IllegalArgumentException("missing structure template " + definition.template());
        }
        try (InputStream input = templateResource.open()) {
            validateTemplateSize(definition.template(),
                    NbtIo.readCompressed(input, NbtAccounter.create(MAX_TEMPLATE_NBT_BYTES)));
        }
        HolderLookup.Provider lookup = getRegistryLookup();
        HolderLookup.RegistryLookup<StructureProcessorList> processors = lookup.lookupOrThrow(Registries.PROCESSOR_LIST);
        for (ResourceKey<StructureProcessorList> key : definition.processors()) {
            if (processors.get(key).isEmpty()) {
                throw new IllegalArgumentException("missing processor list " + key.location());
            }
        }
        HolderLookup.RegistryLookup<LootTable> lootTables = lookup.lookupOrThrow(Registries.LOOT_TABLE);
        if (lootTables.get(definition.lootTable()).isEmpty()) {
            throw new IllegalArgumentException("missing loot table " + definition.lootTable().location());
        }
    }

    /** Package-private deterministic seam for dependency-validation GameTests. */
    static void validateTemplateSize(ResourceLocation templateId, CompoundTag root) {
        ListTag size = root.getList("size", Tag.TAG_INT);
        if (size.size() != 3) {
            throw new IllegalArgumentException("structure template " + templateId
                    + " must declare three size values");
        }
        int width = size.getInt(0);
        int height = size.getInt(1);
        int depth = size.getInt(2);
        if (width <= 0 || height <= 0 || depth <= 0) {
            throw new IllegalArgumentException("structure template " + templateId
                    + " must have positive dimensions");
        }
        if (width > LandmarkDefinition.MAX_TEMPLATE_HORIZONTAL_SPAN
                || depth > LandmarkDefinition.MAX_TEMPLATE_HORIZONTAL_SPAN
                || height > LandmarkDefinition.MAX_TEMPLATE_VERTICAL_SPAN) {
            throw new IllegalArgumentException("structure template " + templateId + " size "
                    + width + 'x' + height + 'x' + depth + " exceeds Delvefold's "
                    + LandmarkDefinition.MAX_TEMPLATE_HORIZONTAL_SPAN + 'x'
                    + LandmarkDefinition.MAX_TEMPLATE_VERTICAL_SPAN + 'x'
                    + LandmarkDefinition.MAX_TEMPLATE_HORIZONTAL_SPAN + " placement bound");
        }
    }

    static ResourceLocation definitionId(ResourceLocation fileId) {
        String prefix = DIRECTORY + '/';
        String path = fileId.getPath();
        if (!path.startsWith(prefix) || !path.endsWith(".json")) {
            throw new IllegalArgumentException("invalid landmark resource path " + fileId);
        }
        return ResourceLocation.fromNamespaceAndPath(fileId.getNamespace(),
                path.substring(prefix.length(), path.length() - ".json".length()));
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    @Override
    protected void apply(LoadResult result, ResourceManager resources, ProfilerFiller profiler) {
        LandmarkCatalogService.ReloadOutcome outcome = LandmarkCatalogService.get().publish(
                result.definitions(), result.errors(), result.attemptedAt());
        if (outcome.applied()) {
            long revision = outcome.activeSnapshot().revision();
            auditAcceptedReload(Math.max(0L, revision - 1L), revision);
            LOGGER.info("Loaded {} Delvefold landmark definition(s) as catalog revision {}",
                    outcome.activeSnapshot().definitions().size(), outcome.activeSnapshot().revision());
        } else {
            LOGGER.error("Rejected Delvefold landmark catalog reload; retaining revision {} with {} definition(s)",
                    outcome.activeSnapshot().revision(), outcome.activeSnapshot().definitions().size());
            outcome.diagnostics().errors().forEach(error -> LOGGER.error("Landmark catalog: {}", error));
        }
    }

    /**
     * Flushes the initial datapack reload after the per-save audit writer is ready. Resource
     * reload callbacks do not expose the command source that initiated {@code /reload}, so both
     * startup and runtime catalog publications are explicitly attributed to the server.
     */
    public static void flushPendingAudit() {
        AuditMutation pending = PENDING_STARTUP_AUDIT.getAndSet(null);
        if (pending != null) {
            DelvefoldAuditService.get().record(pending);
        }
    }

    private static void auditAcceptedReload(long oldRevision, long newRevision) {
        AuditMutation mutation = new AuditMutation(
                "server",
                AuditMutation.Operation.LANDMARK_CATALOG_RELOADED,
                AuditMutation.ObjectType.LANDMARK_CATALOG,
                "catalog",
                oldRevision,
                newRevision);
        if (DelvefoldAuditService.get().available()) {
            DelvefoldAuditService.get().record(mutation);
            return;
        }
        PENDING_STARTUP_AUDIT.updateAndGet(previous -> previous == null
                ? mutation
                : new AuditMutation(
                        "server",
                        AuditMutation.Operation.LANDMARK_CATALOG_RELOADED,
                        AuditMutation.ObjectType.LANDMARK_CATALOG,
                        "catalog",
                        previous.oldRevision(),
                        newRevision));
    }

    record LoadResult(
            Map<ResourceLocation, LandmarkDefinition> definitions,
            List<String> errors,
            Instant attemptedAt) {
    }
}
