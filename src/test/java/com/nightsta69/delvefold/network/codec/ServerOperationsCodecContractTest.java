package com.nightsta69.delvefold.network.codec;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Wire-shape regression checks for the NeoForge-only codec implementation. */
class ServerOperationsCodecContractTest {
    @Test
    void protocolTwelveCarriesPortalRoutingAndHubInSymmetricOrder() throws IOException {
        String codec = source("network/codec/DelvefoldStreamCodecs.java");
        String network = source("network/DelvefoldNetwork.java");

        assertTrue(network.contains("PROTOCOL_VERSION = \"12\""));
        assertTrue(codec.contains("writeEnum(buffer, portal.routingMode());\n"
                + "        buffer.writeInt(portal.hub().x());\n"
                + "        buffer.writeInt(portal.hub().z());\n"
                + "        buffer.writeVarInt(portal.hub().protectionRadius());"));
        assertTrue(codec.contains("readEnum(buffer, PortalRoutingMode.class),\n"
                + "                new PortalHubSettings(buffer.readInt(), buffer.readInt(), buffer.readVarInt())"),
                "Portal routing and all hub fields must be decoded in their write order");
    }

    @Test
    void protocolTwelveCarriesAllBackupIntegrityFlagsInSymmetricOrder() throws IOException {
        String codec = source("network/codec/DelvefoldStreamCodecs.java");

        assertTrue(codec.contains("buffer.writeBoolean(backup.valid());\n"
                + "            buffer.writeBoolean(backup.manifestPresent());\n"
                + "            buffer.writeBoolean(backup.verified());\n"
                + "            buffer.writeBoolean(backup.legacy());"));
        assertTrue(codec.contains("buffer.readLong(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),\n"
                + "                    buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean())"),
                "Backup valid, manifest, verified, and legacy state must all be decoded");
    }

    @Test
    void protocolTwelveCarriesTheTypedPendingOperationSymmetrically() throws IOException {
        String codec = source("network/codec/DelvefoldStreamCodecs.java");
        String network = source("network/DelvefoldNetwork.java");

        assertTrue(network.contains("PROTOCOL_VERSION = \"12\""));
        assertTrue(codec.contains("writeEnum(buffer, snapshot.pendingOperation());"));
        assertTrue(codec.contains("readEnum(buffer, AdminSnapshot.PendingOperation.class)"));
        assertTrue(codec.contains("portalStatus, worldStatus, pendingOperation, diagnostics"),
                "The decoded operation kind must occupy the same snapshot position as its encoder");
    }

    private static String source(String relative) throws IOException {
        return Files.readString(Path.of("src/main/java/com/nightsta69/delvefold").resolve(relative));
    }
}
