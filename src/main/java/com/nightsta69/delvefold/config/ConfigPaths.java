package com.nightsta69.delvefold.config;

import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

public record ConfigPaths(Path directory, Path ores, Path settings) {
    public Path profiles() {
        return directory.resolve("profiles");
    }

    public Path imports() {
        return directory.resolve("imports");
    }

    public Path exports() {
        return directory.resolve("exports");
    }

    public static ConfigPaths forServer(MinecraftServer server) {
        Path saveRoot = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path directory = saveRoot.resolve("serverconfig").resolve("delvefold").normalize();
        if (!directory.startsWith(saveRoot)) {
            throw new IllegalStateException("Resolved Delvefold configuration directory escaped the save root");
        }
        return new ConfigPaths(directory, directory.resolve("ores.json"), directory.resolve("settings.json"));
    }
}
