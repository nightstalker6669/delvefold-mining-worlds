package com.nightsta69.delvefold.guide;

import com.nightsta69.delvefold.Delvefold;
import com.nightsta69.delvefold.guide.GuideSnapshot.Applicability;
import com.nightsta69.delvefold.guide.GuideSnapshot.HeightBand;
import com.nightsta69.delvefold.guide.GuideSnapshot.OreEntry;
import com.nightsta69.delvefold.guide.GuideSnapshot.Output;
import com.nightsta69.delvefold.guide.GuideSnapshot.OutputKind;
import com.nightsta69.delvefold.guide.GuideSnapshot.PortalStatus;
import com.nightsta69.delvefold.guide.GuideSnapshot.RelativeFrequency;
import com.nightsta69.delvefold.guide.GuideSnapshot.Renewal;
import com.nightsta69.delvefold.network.codec.GuideStreamCodecs;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;

@GameTestHolder(Delvefold.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GuideGameTests {
    private static final String EMPTY_TEMPLATE = "bastion/mobs/empty";

    private GuideGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void guideCodecRoundTripsEveryPublicField(GameTestHelper helper) {
        GuideSnapshot expected = sample();
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), helper.getLevel().registryAccess(), ConnectionType.NEOFORGE);
        try {
            GuideStreamCodecs.write(buffer, expected);
            helper.assertTrue(buffer.readableBytes() <= GuideLimits.MAX_ESTIMATED_NETWORK_BYTES,
                    "Encoded guide exceeded its payload budget");
            GuideSnapshot decoded = GuideStreamCodecs.read(buffer);
            helper.assertTrue(expected.equals(decoded), "Guide codec changed public fields");
            helper.assertTrue(buffer.readableBytes() == 0, "Guide codec left unread bytes");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void guideCodecRejectsUnknownFormatBeforeAllocatingEntries(GameTestHelper helper) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), helper.getLevel().registryAccess(), ConnectionType.NEOFORGE);
        try {
            buffer.writeVarInt(GuideSnapshot.CURRENT_FORMAT_VERSION + 1);
            boolean rejected = false;
            try {
                GuideStreamCodecs.read(buffer);
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            helper.assertTrue(rejected, "Unknown guide format was accepted");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = EMPTY_TEMPLATE)
    public static void guideCodecRejectsAggregatePayloadBeyondBudget(GameTestHelper helper) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), helper.getLevel().registryAccess(), ConnectionType.NEOFORGE);
        try {
            writeOversizedWireSnapshot(buffer);
            boolean rejected = false;
            try {
                GuideStreamCodecs.read(buffer);
            } catch (IllegalArgumentException expected) {
                rejected = expected.getMessage().contains("decode budget");
            }
            helper.assertTrue(rejected, "Aggregate guide decode budget was not enforced");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    private static void writeOversizedWireSnapshot(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(GuideSnapshot.CURRENT_FORMAT_VERSION);
        buffer.writeUtf("mine", GuideLimits.MAX_WORLD_NAME_CHARACTERS);
        buffer.writeUtf("flat", GuideLimits.MAX_IDENTIFIER_CHARACTERS);
        buffer.writeUtf("classic", GuideLimits.MAX_IDENTIFIER_CHARACTERS);
        buffer.writeUtf("profile", GuideLimits.MAX_IDENTIFIER_CHARACTERS);
        buffer.writeVarInt(PortalStatus.AVAILABLE.ordinal());
        buffer.writeBoolean(false);
        buffer.writeBoolean(false);
        buffer.writeBoolean(false);
        buffer.writeVarLong(0L);
        buffer.writeVarInt(GuideLimits.MAX_ORE_ENTRIES);
        String largeId = "x".repeat(GuideLimits.MAX_IDENTIFIER_CHARACTERS);
        for (int ore = 0; ore < GuideLimits.MAX_ORE_ENTRIES; ore++) {
            buffer.writeUtf(largeId, GuideLimits.MAX_IDENTIFIER_CHARACTERS);
            buffer.writeVarInt(GuideLimits.MAX_OUTPUTS_PER_ENTRY);
            for (int output = 0; output < GuideLimits.MAX_OUTPUTS_PER_ENTRY; output++) {
                buffer.writeVarInt(OutputKind.BLOCK.ordinal());
                buffer.writeUtf(largeId, GuideLimits.MAX_IDENTIFIER_CHARACTERS);
                buffer.writeUtf(largeId, GuideLimits.MAX_IDENTIFIER_CHARACTERS);
            }
            buffer.writeVarInt(0);
            buffer.writeBoolean(true);
            buffer.writeBoolean(false);
            buffer.writeVarInt(0);
            buffer.writeVarInt(0);
            buffer.writeVarInt(0);
            buffer.writeVarInt(RelativeFrequency.COMMON.ordinal());
            buffer.writeBoolean(false);
        }
        buffer.writeBoolean(false);
    }

    private static GuideSnapshot sample() {
        OreEntry ore = new OreEntry(
                "tin",
                List.of(new Output(OutputKind.BLOCK_TAG, "c:ores/tin", "minecraft:iron_ore")),
                new Applicability(List.of("flat", "cavern"), true, true,
                        List.of("#delvefold:mining_biomes"), List.of("minecraft:deep_dark")),
                List.of(new HeightBand("main", "triangle", -32, 80, 12, 12, 6)),
                RelativeFrequency.UNCOMMON,
                true);
        return new GuideSnapshot(
                GuideSnapshot.CURRENT_FORMAT_VERSION,
                "Public Mine",
                "cavern",
                "classic",
                "pack:metals",
                PortalStatus.AVAILABLE,
                new Renewal(true, true, false, 12_345L),
                List.of(ore),
                true);
    }
}
