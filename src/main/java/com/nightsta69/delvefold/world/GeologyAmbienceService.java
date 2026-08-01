package com.nightsta69.delvefold.world;

import com.nightsta69.delvefold.config.DelvefoldConfigService;
import com.nightsta69.delvefold.config.model.GeologyTheme;
import com.nightsta69.delvefold.config.model.TerrainMode;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.Nullable;

/** Sends small, bounded vanilla ambience bursts for the active recreation-locked geology theme. */
public final class GeologyAmbienceService {
    private static final int PARTICLE_INTERVAL = 160;
    private static final int SOUND_INTERVAL = 640;
    private static boolean registered;

    private GeologyAmbienceService() {}

    /**
     * Registers the idempotent server-side player-tick listener on NeoForge's gameplay bus.
     *
     * <p>Non-classic themes emit at most one five-particle burst per player every 160 ticks and one ambient sound every
     * 640 ticks. UUID-derived phases spread that work across ticks.
     */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, GeologyAmbienceService::onPlayerTick);
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !DelvefoldWorldgen.isMiningLevel(player.level().dimension())) {
            return;
        }
        GeologyTheme theme;
        try {
            var settings = DelvefoldConfigService.get().snapshot().settings();
            @Nullable TerrainMode activeTerrain = settings.terrainMode();
            if (!settings.initialized()
                    || activeTerrain == null
                    || !player.level()
                            .dimension()
                            .equals(DelvefoldWorldgen.levelFor(
                                    activeTerrain, settings.identity().terrainVariant()))) {
                return;
            }
            theme = settings.identity().geologyTheme();
        } catch (IllegalStateException ignored) {
            return;
        }
        if (theme == GeologyTheme.CLASSIC) {
            return;
        }
        int phase = Math.floorMod(player.getUUID().hashCode(), PARTICLE_INTERVAL);
        if (player.tickCount % PARTICLE_INTERVAL != phase) {
            return;
        }

        AmbientPalette palette = palette(theme);
        double x = player.getX() + (player.getRandom().nextDouble() - 0.5D) * 8.0D;
        double y = player.getY() + 1.0D + player.getRandom().nextDouble() * 3.0D;
        double z = player.getZ() + (player.getRandom().nextDouble() - 0.5D) * 8.0D;
        player.serverLevel().sendParticles(player, palette.particle(), false, x, y, z, 5, 1.5D, 1.0D, 1.5D, 0.01D);

        int soundPhase = Math.floorMod(player.getUUID().hashCode(), SOUND_INTERVAL);
        if (player.tickCount % SOUND_INTERVAL == soundPhase) {
            player.playNotifySound(
                    palette.sound(),
                    SoundSource.AMBIENT,
                    0.18F,
                    0.9F + player.getRandom().nextFloat() * 0.2F);
        }
    }

    private static AmbientPalette palette(GeologyTheme theme) {
        return switch (theme) {
            case VOLCANIC -> new AmbientPalette(ParticleTypes.ASH, SoundEvents.LAVA_POP);
            case DRIPSTONE ->
                new AmbientPalette(ParticleTypes.DRIPPING_DRIPSTONE_WATER, SoundEvents.POINTED_DRIPSTONE_DRIP_WATER);
            case LUSH -> new AmbientPalette(ParticleTypes.SPORE_BLOSSOM_AIR, SoundEvents.AZALEA_LEAVES_STEP);
            case CRYSTAL -> new AmbientPalette(ParticleTypes.END_ROD, SoundEvents.AMETHYST_BLOCK_CHIME);
            case CLASSIC -> throw new IllegalArgumentException("Classic geology has no ambience palette");
        };
    }

    private record AmbientPalette(ParticleOptions particle, SoundEvent sound) {}
}
