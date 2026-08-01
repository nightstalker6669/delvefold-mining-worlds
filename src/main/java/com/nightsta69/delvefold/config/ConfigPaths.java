package com.nightsta69.delvefold.config;

import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Normalized canonical paths for one save's Delvefold configuration tree.
 *
 * @param directory normalized {@code <save>/serverconfig/delvefold} directory
 * @param ores canonical {@code ores.json} path inside {@code directory}
 * @param settings canonical {@code settings.json} path inside {@code directory}
 */
public record ConfigPaths(Path directory, Path ores, Path settings) {
    /**
     * Resolves the local named-profile directory without creating it.
     *
     * @return {@code <configuration>/profiles}
     */
    public Path profiles() {
        return directory.resolve("profiles");
    }

    /**
     * Resolves the bounded guided-import transfer directory without creating it.
     *
     * @return {@code <configuration>/imports}
     */
    public Path imports() {
        return directory.resolve("imports");
    }

    /**
     * Resolves the administrator-export directory without creating it.
     *
     * @return {@code <configuration>/exports}
     */
    public Path exports() {
        return directory.resolve("exports");
    }

    /**
     * Derives normalized save-local paths for a running server.
     *
     * <p>The result is checked to remain under the normalized save root before it is returned. This method does not
     * create directories or follow symlinks; individual repositories perform stronger regular-file checks at access.
     *
     * @param server running logical server whose save root owns the configuration
     * @return canonical Delvefold directory and JSON paths
     * @throws IllegalStateException if normalized resolution would escape the save root
     */
    public static ConfigPaths forServer(MinecraftServer server) {
        Path saveRoot = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path directory = saveRoot.resolve("serverconfig").resolve("delvefold").normalize();
        if (!directory.startsWith(saveRoot)) {
            throw new IllegalStateException("Resolved Delvefold configuration directory escaped the save root");
        }
        return new ConfigPaths(directory, directory.resolve("ores.json"), directory.resolve("settings.json"));
    }
}
