package com.nightsta69.delvefold.network.codec;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source-contract checks for code that lives on the NeoForge-only compile classpath. */
class OreRuleDraftCodecTest {
    @Test
    void protocolEightWritesAndReadsVariantWeightAtTheSameWirePosition() throws IOException {
        String codec = Files.readString(Path.of(
                "src/main/java/com/nightsta69/delvefold/network/codec/DelvefoldStreamCodecs.java"));
        String network = Files.readString(Path.of(
                "src/main/java/com/nightsta69/delvefold/network/DelvefoldNetwork.java"));

        String writeSequence = "writeResourceId(buffer, variant.replaceTag());\n"
                + "            buffer.writeVarInt(variant.weight());\n"
                + "            writeCount(buffer, variant.state().size()";
        String readSequence = "String replaceTag = readResourceId(buffer);\n"
                + "            int weight = buffer.readVarInt();\n"
                + "            int stateCount = readCount(buffer";

        assertTrue(codec.contains(writeSequence),
                "Weight must be encoded immediately after the host tag and before block state");
        assertTrue(codec.contains(readSequence),
                "Weight must be decoded from the matching wire position");
        assertTrue(codec.contains("replaceTag, state, weight)"),
                "The decoded weight must reach OreVariantDraft");
        assertTrue(network.contains("PROTOCOL_VERSION = \"8\""),
                "Changing the ore-rule payload shape requires protocol 8");
    }
}
