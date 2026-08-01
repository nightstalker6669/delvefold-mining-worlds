package com.nightsta69.delvefold.config;

import com.mojang.logging.LogUtils;
import com.nightsta69.delvefold.config.importer.MinecraftOreImportRegistry;
import com.nightsta69.delvefold.config.importer.OreImportSessionService;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.world.DelvefoldWorldgen;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.slf4j.Logger;

/**
 * Server-resource-reload adapter for bounded datapack ore profiles.
 *
 * <p>Preparation reads matching JSON resources in registry-ID order, rejects any individual resource larger than
 * {@link EcosystemProfileRegistry#MAX_PROFILE_BYTES}, and records failures without aborting the entire resource reload.
 * Apply publishes an immutable datapack-profile snapshot and invalidates generation/import caches and session-bound
 * import tokens so no preview can outlive the registry state on which it was based.
 */
public final class EcosystemProfileReloadListener
        extends SimplePreparableReloadListener<EcosystemProfileReloadListener.LoadResult> {
    /** Creates the reload listener registered for the current server resource manager. */
    public EcosystemProfileReloadListener() {}

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DIRECTORY = "delvefold/ore_profiles";

    /**
     * Registers one profile listener with the current server reload pipeline.
     *
     * @param event NeoForge reload-listener registration event
     */
    public static void register(AddReloadListenerEvent event) {
        event.addListener(new EcosystemProfileReloadListener());
    }

    /**
     * Reads and parses bounded profile resources on the reload preparation executor.
     *
     * @param resources reload resource snapshot
     * @param profiler reload profiler
     * @return immutable successful-profile map and immutable human-readable rejection list
     */
    @Override
    protected LoadResult prepare(ResourceManager resources, ProfilerFiller profiler) {
        Map<String, EcosystemProfileRegistry.RegisteredProfile> loaded = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        resources.listResources(DIRECTORY, id -> id.getPath().endsWith(".json")).entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .forEach(entry -> load(entry.getKey(), entry.getValue(), loaded, errors));
        return new LoadResult(Map.copyOf(loaded), List.copyOf(errors));
    }

    private static void load(
            ResourceLocation resourceId,
            Resource resource,
            Map<String, EcosystemProfileRegistry.RegisteredProfile> loaded,
            List<String> errors) {
        String path = resourceId.getPath().substring((DIRECTORY + '/').length());
        path = path.substring(0, path.length() - ".json".length());
        String profileId = resourceId.getNamespace() + ':' + path;
        try (InputStream input = resource.open()) {
            byte[] bytes = input.readNBytes(EcosystemProfileRegistry.MAX_PROFILE_BYTES + 1);
            if (bytes.length > EcosystemProfileRegistry.MAX_PROFILE_BYTES) {
                throw new IOException("exceeds " + EcosystemProfileRegistry.MAX_PROFILE_BYTES + " bytes");
            }
            OreProfileDocument document =
                    EcosystemProfileRegistry.parseDatapackProfile(profileId, new String(bytes, StandardCharsets.UTF_8));
            loaded.put(
                    profileId,
                    new EcosystemProfileRegistry.RegisteredProfile(document, "datapack:" + resource.sourcePackId()));
        } catch (IOException | RuntimeException exception) {
            errors.add(resourceId + ": " + exception.getMessage());
        }
    }

    /**
     * Publishes prepared datapack profiles and invalidates registry-dependent caches on the reload apply executor.
     *
     * @param result prepared immutable reload result
     * @param resources reload resource snapshot
     * @param profiler reload profiler
     */
    @Override
    protected void apply(LoadResult result, ResourceManager resources, ProfilerFiller profiler) {
        EcosystemProfileRegistry.replaceDatapackProfiles(result.profiles());
        DelvefoldWorldgen.MINING_ORE_FEATURE.get().invalidateRuntimeProfile();
        MinecraftOreImportRegistry.invalidateCache();
        OreImportSessionService.get().invalidateAll();
        LOGGER.info(
                "Loaded {} Delvefold datapack ore profile(s)", result.profiles().size());
        result.errors().forEach(error -> LOGGER.error("Rejected Delvefold datapack profile {}", error));
    }

    record LoadResult(Map<String, EcosystemProfileRegistry.RegisteredProfile> profiles, List<String> errors) {}
}
