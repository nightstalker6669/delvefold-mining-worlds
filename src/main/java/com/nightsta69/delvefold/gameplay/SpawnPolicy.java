package com.nightsta69.delvefold.gameplay;

import com.nightsta69.delvefold.config.DelvefoldConfigService;
import com.nightsta69.delvefold.config.model.GameplaySettings;
import com.nightsta69.delvefold.world.DelvefoldWorldgen;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/** Applies live gameplay presets only to natural generation in Delvefold dimensions. */
public final class SpawnPolicy {
    private SpawnPolicy() {
    }

    public static void onPositionCheck(MobSpawnEvent.PositionCheck event) {
        ResourceKey<Level> dimension = event.getLevel().getLevel().dimension();
        if (!isDelvefold(dimension) || !isNatural(event.getSpawnType())) {
            return;
        }
        GameplaySettings settings;
        try {
            settings = DelvefoldConfigService.get().snapshot().settings().gameplay();
        } catch (IllegalStateException ignored) {
            return;
        }
        if (!allowed(settings, event.getEntity().getType(), event.getSpawnType())) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }

    private static boolean allowed(GameplaySettings settings, EntityType<?> type, MobSpawnType spawnType) {
        if (spawnType == MobSpawnType.PATROL && !settings.patrols()) {
            return false;
        }
        if (type == EntityType.PHANTOM && !settings.phantoms()) {
            return false;
        }
        MobCategory category = type.getCategory();
        if (category == MobCategory.MONSTER) {
            return settings.monsters();
        }
        if (category == MobCategory.CREATURE) {
            return settings.creatures();
        }
        if (category == MobCategory.AMBIENT) {
            return settings.ambient();
        }
        if (category == MobCategory.AXOLOTLS
                || category == MobCategory.UNDERGROUND_WATER_CREATURE
                || category == MobCategory.WATER_CREATURE
                || category == MobCategory.WATER_AMBIENT) {
            return settings.waterCreatures();
        }
        return true;
    }

    private static boolean isNatural(MobSpawnType type) {
        return type == MobSpawnType.NATURAL
                || type == MobSpawnType.CHUNK_GENERATION
                || type == MobSpawnType.PATROL;
    }

    private static boolean isDelvefold(ResourceKey<Level> dimension) {
        return dimension.equals(DelvefoldWorldgen.FLAT_LEVEL)
                || dimension.equals(DelvefoldWorldgen.CAVERN_LEVEL)
                || dimension.equals(DelvefoldWorldgen.WILD_LEVEL);
    }
}
