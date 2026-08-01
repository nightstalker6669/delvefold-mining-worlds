package com.nightsta69.delvefold.network.codec;

import com.nightsta69.delvefold.guide.GuideLimits;
import com.nightsta69.delvefold.guide.GuideSnapshot;
import com.nightsta69.delvefold.guide.GuideSnapshot.Applicability;
import com.nightsta69.delvefold.guide.GuideSnapshot.HeightBand;
import com.nightsta69.delvefold.guide.GuideSnapshot.OreEntry;
import com.nightsta69.delvefold.guide.GuideSnapshot.Output;
import com.nightsta69.delvefold.guide.GuideSnapshot.OutputKind;
import com.nightsta69.delvefold.guide.GuideSnapshot.PortalStatus;
import com.nightsta69.delvefold.guide.GuideSnapshot.RelativeFrequency;
import com.nightsta69.delvefold.guide.GuideSnapshot.Renewal;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** Strict bounded codec for the public Seam Ledger snapshot. */
public final class GuideStreamCodecs {
    private GuideStreamCodecs() {}

    /**
     * Writes a current-format public guide in protocol 12 field order.
     *
     * @param buffer destination registry-aware network buffer
     * @param snapshot immutable redacted guide snapshot
     * @throws IllegalArgumentException if the format or estimated payload size violates guide bounds
     */
    public static void write(RegistryFriendlyByteBuf buffer, GuideSnapshot snapshot) {
        if (snapshot.formatVersion() != GuideSnapshot.CURRENT_FORMAT_VERSION) {
            throw new IllegalArgumentException(
                    "Only the current guide format can be sent over protocol 12: " + snapshot.formatVersion());
        }
        if (snapshot.estimatedNetworkBytes() > GuideLimits.MAX_ESTIMATED_NETWORK_BYTES) {
            throw new IllegalArgumentException("Guide snapshot exceeds its network budget");
        }
        buffer.writeVarInt(snapshot.formatVersion());
        writeString(buffer, snapshot.worldName(), GuideLimits.MAX_WORLD_NAME_CHARACTERS);
        writeString(buffer, snapshot.terrain(), GuideLimits.MAX_IDENTIFIER_CHARACTERS);
        writeString(buffer, snapshot.terrainVariant(), GuideLimits.MAX_IDENTIFIER_CHARACTERS);
        writeString(buffer, snapshot.geologyTheme(), GuideLimits.MAX_IDENTIFIER_CHARACTERS);
        writeString(buffer, snapshot.activeProfile(), GuideLimits.MAX_IDENTIFIER_CHARACTERS);
        writeEnum(buffer, snapshot.portalStatus());
        writeRenewal(buffer, snapshot.renewal());
        writeCount(buffer, snapshot.ores().size(), GuideLimits.MAX_ORE_ENTRIES, "guide ores");
        for (OreEntry ore : snapshot.ores()) {
            writeOre(buffer, ore);
        }
        buffer.writeBoolean(snapshot.truncated());
    }

    /**
     * Reads a current-format guide while checking the aggregate decode budget after each variable section.
     *
     * @param buffer source registry-aware network buffer positioned at the guide's first byte
     * @return immutable validated and redacted guide snapshot
     * @throws IllegalArgumentException if format, lengths, counts, ordinals, or aggregate bytes are invalid
     */
    public static GuideSnapshot read(RegistryFriendlyByteBuf buffer) {
        int startIndex = buffer.readerIndex();
        int format = buffer.readVarInt();
        if (format != GuideSnapshot.CURRENT_FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported guide format version: " + format);
        }
        ensureDecodeBudget(buffer, startIndex);
        String world = readString(buffer, GuideLimits.MAX_WORLD_NAME_CHARACTERS);
        ensureDecodeBudget(buffer, startIndex);
        String terrain = readString(buffer, GuideLimits.MAX_IDENTIFIER_CHARACTERS);
        ensureDecodeBudget(buffer, startIndex);
        String variant = readString(buffer, GuideLimits.MAX_IDENTIFIER_CHARACTERS);
        ensureDecodeBudget(buffer, startIndex);
        String geology = readString(buffer, GuideLimits.MAX_IDENTIFIER_CHARACTERS);
        ensureDecodeBudget(buffer, startIndex);
        String profile = readString(buffer, GuideLimits.MAX_IDENTIFIER_CHARACTERS);
        ensureDecodeBudget(buffer, startIndex);
        PortalStatus portal = readEnum(buffer, PortalStatus.class);
        Renewal renewal = readRenewal(buffer, startIndex);
        int count = readCount(buffer, GuideLimits.MAX_ORE_ENTRIES, "guide ores");
        ensureDecodeBudget(buffer, startIndex);
        List<OreEntry> ores = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            ores.add(readOre(buffer, startIndex));
        }
        boolean truncated = buffer.readBoolean();
        ensureDecodeBudget(buffer, startIndex);
        return new GuideSnapshot(format, world, terrain, variant, geology, profile, portal, renewal, ores, truncated);
    }

    private static void writeRenewal(RegistryFriendlyByteBuf buffer, Renewal renewal) {
        buffer.writeBoolean(renewal.enabled());
        buffer.writeBoolean(renewal.scheduled());
        buffer.writeBoolean(renewal.due());
        buffer.writeVarLong(renewal.remainingSeconds());
    }

    private static Renewal readRenewal(RegistryFriendlyByteBuf buffer, int startIndex) {
        boolean enabled = buffer.readBoolean();
        boolean scheduled = buffer.readBoolean();
        boolean due = buffer.readBoolean();
        long seconds = buffer.readVarLong();
        ensureDecodeBudget(buffer, startIndex);
        if (seconds < 0L || seconds > GuideLimits.MAX_RENEWAL_COUNTDOWN_SECONDS) {
            throw new IllegalArgumentException("Invalid guide renewal countdown: " + seconds);
        }
        return new Renewal(enabled, scheduled, due, seconds);
    }

    private static void writeOre(RegistryFriendlyByteBuf buffer, OreEntry ore) {
        writeString(buffer, ore.ruleId(), GuideLimits.MAX_IDENTIFIER_CHARACTERS);
        writeCount(buffer, ore.outputs().size(), GuideLimits.MAX_OUTPUTS_PER_ENTRY, "guide outputs");
        for (Output output : ore.outputs()) {
            writeEnum(buffer, output.kind());
            writeString(buffer, output.sourceId(), GuideLimits.MAX_IDENTIFIER_CHARACTERS);
            writeString(buffer, output.iconBlockId(), GuideLimits.MAX_IDENTIFIER_CHARACTERS);
        }
        writeCount(
                buffer, ore.applicability().terrains().size(), GuideLimits.MAX_APPLICABLE_TERRAINS, "guide terrains");
        for (String terrain : ore.applicability().terrains()) {
            writeString(buffer, terrain, GuideLimits.MAX_IDENTIFIER_CHARACTERS);
        }
        buffer.writeBoolean(ore.applicability().appliesToActiveTerrain());
        buffer.writeBoolean(ore.applicability().biomeFiltered());
        writeCount(
                buffer,
                ore.applicability().biomeIncludes().size(),
                GuideLimits.MAX_BIOME_SELECTORS_PER_LIST,
                "guide biome includes");
        for (String selector : ore.applicability().biomeIncludes()) {
            writeString(buffer, selector, GuideLimits.MAX_IDENTIFIER_CHARACTERS);
        }
        writeCount(
                buffer,
                ore.applicability().biomeExcludes().size(),
                GuideLimits.MAX_BIOME_SELECTORS_PER_LIST,
                "guide biome excludes");
        for (String selector : ore.applicability().biomeExcludes()) {
            writeString(buffer, selector, GuideLimits.MAX_IDENTIFIER_CHARACTERS);
        }
        writeCount(buffer, ore.heightBands().size(), GuideLimits.MAX_HEIGHT_BANDS_PER_ENTRY, "guide height bands");
        for (HeightBand band : ore.heightBands()) {
            writeString(buffer, band.bandId(), GuideLimits.MAX_IDENTIFIER_CHARACTERS);
            writeString(buffer, band.distribution(), GuideLimits.MAX_IDENTIFIER_CHARACTERS);
            buffer.writeInt(band.minY());
            buffer.writeInt(band.maxY());
            buffer.writeInt(band.bestMinY());
            buffer.writeInt(band.bestMaxY());
            buffer.writeVarInt(band.veinSize());
        }
        writeEnum(buffer, ore.relativeFrequency());
        buffer.writeBoolean(ore.truncated());
    }

    private static OreEntry readOre(RegistryFriendlyByteBuf buffer, int startIndex) {
        String id = readString(buffer, GuideLimits.MAX_IDENTIFIER_CHARACTERS);
        ensureDecodeBudget(buffer, startIndex);
        int outputCount = readCount(buffer, GuideLimits.MAX_OUTPUTS_PER_ENTRY, "guide outputs");
        ensureDecodeBudget(buffer, startIndex);
        List<Output> outputs = new ArrayList<>(outputCount);
        for (int index = 0; index < outputCount; index++) {
            outputs.add(new Output(
                    readEnum(buffer, OutputKind.class),
                    readString(buffer, GuideLimits.MAX_IDENTIFIER_CHARACTERS),
                    readString(buffer, GuideLimits.MAX_IDENTIFIER_CHARACTERS)));
            ensureDecodeBudget(buffer, startIndex);
        }
        int terrainCount = readCount(buffer, GuideLimits.MAX_APPLICABLE_TERRAINS, "guide terrains");
        ensureDecodeBudget(buffer, startIndex);
        List<String> terrains = new ArrayList<>(terrainCount);
        for (int index = 0; index < terrainCount; index++) {
            terrains.add(readString(buffer, GuideLimits.MAX_IDENTIFIER_CHARACTERS));
            ensureDecodeBudget(buffer, startIndex);
        }
        boolean activeTerrain = buffer.readBoolean();
        boolean biomeFiltered = buffer.readBoolean();
        ensureDecodeBudget(buffer, startIndex);
        int includeCount = readCount(buffer, GuideLimits.MAX_BIOME_SELECTORS_PER_LIST, "guide biome includes");
        List<String> biomeIncludes = new ArrayList<>(includeCount);
        for (int index = 0; index < includeCount; index++) {
            biomeIncludes.add(readString(buffer, GuideLimits.MAX_IDENTIFIER_CHARACTERS));
            ensureDecodeBudget(buffer, startIndex);
        }
        int excludeCount = readCount(buffer, GuideLimits.MAX_BIOME_SELECTORS_PER_LIST, "guide biome excludes");
        List<String> biomeExcludes = new ArrayList<>(excludeCount);
        for (int index = 0; index < excludeCount; index++) {
            biomeExcludes.add(readString(buffer, GuideLimits.MAX_IDENTIFIER_CHARACTERS));
            ensureDecodeBudget(buffer, startIndex);
        }
        Applicability applicability =
                new Applicability(terrains, activeTerrain, biomeFiltered, biomeIncludes, biomeExcludes);
        int bandCount = readCount(buffer, GuideLimits.MAX_HEIGHT_BANDS_PER_ENTRY, "guide height bands");
        ensureDecodeBudget(buffer, startIndex);
        List<HeightBand> bands = new ArrayList<>(bandCount);
        for (int index = 0; index < bandCount; index++) {
            bands.add(new HeightBand(
                    readString(buffer, GuideLimits.MAX_IDENTIFIER_CHARACTERS),
                    readString(buffer, GuideLimits.MAX_IDENTIFIER_CHARACTERS),
                    buffer.readInt(),
                    buffer.readInt(),
                    buffer.readInt(),
                    buffer.readInt(),
                    buffer.readVarInt()));
            ensureDecodeBudget(buffer, startIndex);
        }
        RelativeFrequency frequency = readEnum(buffer, RelativeFrequency.class);
        boolean truncated = buffer.readBoolean();
        ensureDecodeBudget(buffer, startIndex);
        return new OreEntry(id, outputs, applicability, bands, frequency, truncated);
    }

    private static void writeString(RegistryFriendlyByteBuf buffer, String value, int maximum) {
        String safe = value == null ? "" : value;
        if (safe.codePointCount(0, safe.length()) > maximum) {
            throw new IllegalArgumentException("Guide string exceeds " + maximum + " characters");
        }
        buffer.writeUtf(safe, maximum);
    }

    private static String readString(RegistryFriendlyByteBuf buffer, int maximum) {
        return buffer.readUtf(maximum);
    }

    private static void writeCount(RegistryFriendlyByteBuf buffer, int count, int maximum, String label) {
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException("Too many " + label + ": " + count);
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

    // Protocol 12 encodes enum declaration order; changing this value would break wire compatibility.
    @SuppressWarnings("EnumOrdinal")
    private static <E extends Enum<E>> void writeEnum(RegistryFriendlyByteBuf buffer, E value) {
        buffer.writeVarInt(value.ordinal());
    }

    private static <E extends Enum<E>> E readEnum(RegistryFriendlyByteBuf buffer, Class<E> type) {
        E[] values = type.getEnumConstants();
        int ordinal = buffer.readVarInt();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("Invalid " + type.getSimpleName() + " ordinal: " + ordinal);
        }
        return values[ordinal];
    }

    private static void ensureDecodeBudget(RegistryFriendlyByteBuf buffer, int startIndex) {
        int consumed = buffer.readerIndex() - startIndex;
        if (consumed < 0 || consumed > GuideLimits.MAX_ESTIMATED_NETWORK_BYTES) {
            throw new IllegalArgumentException(
                    "Guide payload exceeds its " + GuideLimits.MAX_ESTIMATED_NETWORK_BYTES + " byte decode budget");
        }
    }
}
