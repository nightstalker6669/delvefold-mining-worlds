package com.nightsta69.delvefold.network.codec;

import com.nightsta69.delvefold.config.model.GameplayPreset;
import com.nightsta69.delvefold.config.model.GameplaySettings;
import com.nightsta69.delvefold.config.model.HeightDistribution;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.PortalSettings;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.network.ProtocolLimits;
import com.nightsta69.delvefold.network.model.AdminSnapshot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public final class DelvefoldStreamCodecs {
    private DelvefoldStreamCodecs() {
    }

    public static void writeSnapshot(RegistryFriendlyByteBuf buffer, AdminSnapshot snapshot) {
        buffer.writeLong(snapshot.oreRevision());
        buffer.writeLong(snapshot.settingsRevision());
        buffer.writeBoolean(snapshot.backendReady());
        buffer.writeBoolean(snapshot.initialized());
        writeEnum(buffer, snapshot.terrainMode());
        writeEnum(buffer, snapshot.orePreset());
        writeGameplay(buffer, snapshot.gameplay());
        writePortal(buffer, snapshot.portal());
        writeCapabilities(buffer, snapshot.capabilities());
        writeString(buffer, snapshot.activeProfileId(), ProtocolLimits.ID_LENGTH);
        writeCount(buffer, snapshot.profiles().size(), ProtocolLimits.MAX_PROFILES, "ore profiles");
        for (AdminSnapshot.ProfileDraft profile : snapshot.profiles()) {
            writeString(buffer, profile.id(), ProtocolLimits.ID_LENGTH);
            buffer.writeBoolean(profile.builtIn());
            buffer.writeBoolean(profile.localOverride());
            buffer.writeVarInt(profile.ruleCount());
            buffer.writeLong(profile.revision());
            buffer.writeBoolean(profile.valid());
        }
        writeString(buffer, snapshot.portalStatus(), ProtocolLimits.MESSAGE_LENGTH);
        writeString(buffer, snapshot.worldStatus(), ProtocolLimits.MESSAGE_LENGTH);
        buffer.writeBoolean(snapshot.resetPending());
        writeStringList(buffer, snapshot.diagnostics(), ProtocolLimits.MAX_DIAGNOSTICS,
                ProtocolLimits.MESSAGE_LENGTH);
        buffer.writeVarInt(snapshot.oreRuleTotal());
        buffer.writeVarInt(snapshot.orePage());
        writeCount(buffer, snapshot.oreRules().size(), ProtocolLimits.MAX_ORE_RULES_PER_PAGE, "ore rules");
        for (AdminSnapshot.OreRuleDraft rule : snapshot.oreRules()) {
            writeOreRule(buffer, rule);
        }
    }

    public static AdminSnapshot readSnapshot(RegistryFriendlyByteBuf buffer) {
        long oreRevision = buffer.readLong();
        long settingsRevision = buffer.readLong();
        boolean backendReady = buffer.readBoolean();
        boolean initialized = buffer.readBoolean();
        TerrainMode terrain = readEnum(buffer, TerrainMode.class);
        OrePreset orePreset = readEnum(buffer, OrePreset.class);
        GameplaySettings gameplay = readGameplay(buffer);
        PortalSettings portal = readPortal(buffer);
        AdminSnapshot.AdminCapabilities capabilities = readCapabilities(buffer);
        String activeProfileId = readString(buffer, ProtocolLimits.ID_LENGTH);
        int profileCount = readCount(buffer, ProtocolLimits.MAX_PROFILES, "ore profiles");
        List<AdminSnapshot.ProfileDraft> profiles = new ArrayList<>(profileCount);
        for (int index = 0; index < profileCount; index++) {
            profiles.add(new AdminSnapshot.ProfileDraft(readString(buffer, ProtocolLimits.ID_LENGTH),
                    buffer.readBoolean(), buffer.readBoolean(), buffer.readVarInt(), buffer.readLong(),
                    buffer.readBoolean()));
        }
        String portalStatus = readString(buffer, ProtocolLimits.MESSAGE_LENGTH);
        String worldStatus = readString(buffer, ProtocolLimits.MESSAGE_LENGTH);
        boolean resetPending = buffer.readBoolean();
        List<String> diagnostics = readStringList(buffer, ProtocolLimits.MAX_DIAGNOSTICS,
                ProtocolLimits.MESSAGE_LENGTH);
        int oreRuleTotal = buffer.readVarInt();
        if (oreRuleTotal < 0 || oreRuleTotal > ProtocolLimits.MAX_ORE_RULES) {
            throw new IllegalArgumentException("Invalid total ore rule count: " + oreRuleTotal);
        }
        int orePage = buffer.readVarInt();
        if (orePage < 0 || orePage > ProtocolLimits.MAX_ORE_RULES) {
            throw new IllegalArgumentException("Invalid ore page: " + orePage);
        }
        int oreCount = readCount(buffer, ProtocolLimits.MAX_ORE_RULES_PER_PAGE, "ore rules");
        List<AdminSnapshot.OreRuleDraft> rules = new ArrayList<>(oreCount);
        for (int index = 0; index < oreCount; index++) {
            rules.add(readOreRule(buffer));
        }
        return new AdminSnapshot(oreRevision, settingsRevision, backendReady, initialized, terrain, orePreset, gameplay, portal, capabilities,
                activeProfileId, profiles,
                portalStatus, worldStatus, resetPending, diagnostics, oreRuleTotal, orePage, rules);
    }

    public static void writeCapabilities(RegistryFriendlyByteBuf buffer, AdminSnapshot.AdminCapabilities capabilities) {
        buffer.writeBoolean(capabilities.canView());
        buffer.writeBoolean(capabilities.canConfigure());
        buffer.writeBoolean(capabilities.canManageWorld());
        buffer.writeBoolean(capabilities.canRestoreBackups());
        buffer.writeBoolean(capabilities.canViewDiagnostics());
    }

    public static AdminSnapshot.AdminCapabilities readCapabilities(RegistryFriendlyByteBuf buffer) {
        return new AdminSnapshot.AdminCapabilities(buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
                buffer.readBoolean(), buffer.readBoolean());
    }

    public static void writePortal(RegistryFriendlyByteBuf buffer, PortalSettings portal) {
        buffer.writeBoolean(portal.enabled());
        buffer.writeBoolean(portal.allowFromOverworldOnly());
        buffer.writeBoolean(portal.playerOnly());
        buffer.writeVarInt(portal.cooldownSeconds());
        writeFiniteDouble(buffer, portal.coordinateScale(), "portal coordinate scale");
    }

    public static PortalSettings readPortal(RegistryFriendlyByteBuf buffer) {
        return new PortalSettings(buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
                buffer.readVarInt(), readFiniteDouble(buffer, "portal coordinate scale"));
    }

    public static void writeGameplay(RegistryFriendlyByteBuf buffer, GameplaySettings gameplay) {
        writeEnum(buffer, gameplay.preset());
        buffer.writeBoolean(gameplay.monsters());
        buffer.writeBoolean(gameplay.creatures());
        buffer.writeBoolean(gameplay.ambient());
        buffer.writeBoolean(gameplay.waterCreatures());
        buffer.writeBoolean(gameplay.patrols());
        buffer.writeBoolean(gameplay.phantoms());
    }

    public static GameplaySettings readGameplay(RegistryFriendlyByteBuf buffer) {
        return new GameplaySettings(
                readEnum(buffer, GameplayPreset.class),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean());
    }

    public static void writeOreRule(RegistryFriendlyByteBuf buffer, AdminSnapshot.OreRuleDraft rule) {
        writeString(buffer, rule.id(), ProtocolLimits.ID_LENGTH);
        buffer.writeBoolean(rule.enabled());
        buffer.writeBoolean(rule.required());
        writeResourceId(buffer, rule.primaryBlockId());

        writeCount(buffer, rule.variants().size(), ProtocolLimits.MAX_VARIANTS, "ore variants");
        for (AdminSnapshot.OreVariantDraft variant : rule.variants()) {
            writeResourceId(buffer, variant.blockId());
            writeResourceId(buffer, variant.replaceTag());
        }

        writeCount(buffer, rule.terrainModes().size(), ProtocolLimits.MAX_TERRAIN_MODES, "terrain modes");
        for (TerrainMode terrainMode : rule.terrainModes()) {
            writeEnum(buffer, terrainMode);
        }

        writeCount(buffer, rule.bands().size(), ProtocolLimits.MAX_BANDS, "spawn bands");
        for (AdminSnapshot.OreBandDraft band : rule.bands()) {
            writeOreBand(buffer, band);
        }
    }

    public static AdminSnapshot.OreRuleDraft readOreRule(RegistryFriendlyByteBuf buffer) {
        String id = readString(buffer, ProtocolLimits.ID_LENGTH);
        boolean enabled = buffer.readBoolean();
        boolean required = buffer.readBoolean();
        String primaryBlockId = readResourceId(buffer);

        int variantCount = readCount(buffer, ProtocolLimits.MAX_VARIANTS, "ore variants");
        List<AdminSnapshot.OreVariantDraft> variants = new ArrayList<>(variantCount);
        for (int index = 0; index < variantCount; index++) {
            variants.add(new AdminSnapshot.OreVariantDraft(readResourceId(buffer), readResourceId(buffer)));
        }

        int terrainCount = readCount(buffer, ProtocolLimits.MAX_TERRAIN_MODES, "terrain modes");
        List<TerrainMode> terrainModes = new ArrayList<>(terrainCount);
        for (int index = 0; index < terrainCount; index++) {
            TerrainMode mode = readEnum(buffer, TerrainMode.class);
            if (!terrainModes.contains(mode)) {
                terrainModes.add(mode);
            }
        }

        int bandCount = readCount(buffer, ProtocolLimits.MAX_BANDS, "spawn bands");
        List<AdminSnapshot.OreBandDraft> bands = new ArrayList<>(bandCount);
        for (int index = 0; index < bandCount; index++) {
            bands.add(readOreBand(buffer));
        }
        return new AdminSnapshot.OreRuleDraft(id, enabled, required, primaryBlockId, variants, terrainModes, bands);
    }

    public static void writeOreBand(RegistryFriendlyByteBuf buffer, AdminSnapshot.OreBandDraft band) {
        writeString(buffer, band.id(), ProtocolLimits.ID_LENGTH);
        buffer.writeVarInt(band.veinSize());
        writeFiniteDouble(buffer, band.attemptsPerChunk(), "attempts per chunk");
        writeEnum(buffer, band.distribution());
        buffer.writeInt(band.minY());
        buffer.writeInt(band.maxY());
        buffer.writeInt(band.peakY());
        buffer.writeInt(band.plateauMinY());
        buffer.writeInt(band.plateauMaxY());
        writeFiniteDouble(buffer, band.discardOnAirExposure(), "air exposure discard");
    }

    public static AdminSnapshot.OreBandDraft readOreBand(RegistryFriendlyByteBuf buffer) {
        return new AdminSnapshot.OreBandDraft(
                readString(buffer, ProtocolLimits.ID_LENGTH),
                buffer.readVarInt(),
                readFiniteDouble(buffer, "attempts per chunk"),
                readEnum(buffer, HeightDistribution.class),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                readFiniteDouble(buffer, "air exposure discard"));
    }

    public static void writeString(RegistryFriendlyByteBuf buffer, String value, int maximumLength) {
        String safe = value == null ? "" : value;
        if (safe.length() > maximumLength) {
            throw new IllegalArgumentException("String exceeds protocol maximum of " + maximumLength + " characters");
        }
        buffer.writeUtf(safe, maximumLength);
    }

    public static String readString(RegistryFriendlyByteBuf buffer, int maximumLength) {
        return buffer.readUtf(maximumLength);
    }

    public static void writeResourceId(RegistryFriendlyByteBuf buffer, String value) {
        ResourceLocation id = ResourceLocation.tryParse(value == null ? "" : value);
        if (id == null) {
            throw new IllegalArgumentException("Invalid resource id: " + value);
        }
        writeString(buffer, id.toString(), ProtocolLimits.ID_LENGTH);
    }

    public static String readResourceId(RegistryFriendlyByteBuf buffer) {
        String value = readString(buffer, ProtocolLimits.ID_LENGTH);
        if (ResourceLocation.tryParse(value) == null) {
            throw new IllegalArgumentException("Invalid resource id: " + value);
        }
        return value;
    }

    public static <E extends Enum<E>> void writeEnum(RegistryFriendlyByteBuf buffer, E value) {
        buffer.writeVarInt(value.ordinal());
    }

    public static <E extends Enum<E>> E readEnum(RegistryFriendlyByteBuf buffer, Class<E> enumType) {
        E[] values = enumType.getEnumConstants();
        int ordinal = buffer.readVarInt();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("Invalid " + enumType.getSimpleName() + " ordinal: " + ordinal);
        }
        return values[ordinal];
    }

    private static void writeStringList(RegistryFriendlyByteBuf buffer, List<String> values, int maximumCount,
            int maximumLength) {
        writeCount(buffer, values.size(), maximumCount, "strings");
        for (String value : values) {
            writeString(buffer, value, maximumLength);
        }
    }

    private static List<String> readStringList(RegistryFriendlyByteBuf buffer, int maximumCount, int maximumLength) {
        int count = readCount(buffer, maximumCount, "strings");
        List<String> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            values.add(readString(buffer, maximumLength));
        }
        return values;
    }

    private static void writeCount(RegistryFriendlyByteBuf buffer, int count, int maximum, String label) {
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException("Too many " + label + ": " + count + " (maximum " + maximum + ")");
        }
        buffer.writeVarInt(count);
    }

    private static int readCount(RegistryFriendlyByteBuf buffer, int maximum, String label) {
        int count = buffer.readVarInt();
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException("Invalid " + label + " count: " + count);
        }
        return count;
    }

    private static void writeFiniteDouble(RegistryFriendlyByteBuf buffer, double value, String label) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(label + " must be finite");
        }
        buffer.writeDouble(value);
    }

    private static double readFiniteDouble(RegistryFriendlyByteBuf buffer, String label) {
        double value = buffer.readDouble();
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(label + " must be finite");
        }
        return value;
    }
}
