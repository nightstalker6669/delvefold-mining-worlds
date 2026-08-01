package com.nightsta69.delvefold.network.codec;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source-contract checks for the NeoForge identity codec and server-only salt boundary. */
class RenewalSeedModeCodecContractTest {
    @Test
    void identityCodecCarriesModeButNeverGenerationSalt() throws Exception {
        String codec = Files.readString(
                Path.of("src/main/java/com/nightsta69/delvefold/network/codec/DelvefoldStreamCodecs.java"));
        String setup = Files.readString(
                Path.of("src/main/java/com/nightsta69/delvefold/client/gui/DelvefoldSetupScreen.java"));
        String dashboard = Files.readString(
                Path.of("src/main/java/com/nightsta69/delvefold/client/gui/DelvefoldDashboardScreen.java"));
        String admin = Files.readString(
                Path.of("src/main/java/com/nightsta69/delvefold/admin/DefaultDelvefoldAdminService.java"));
        String compactCodec = compact(codec);
        String compactDashboard = compact(dashboard);
        String compactAdmin = compact(admin);

        assertTrue(compactCodec.contains("writeEnum(buffer,renewal.seedMode())"));
        assertTrue(compactCodec.contains("readEnum(buffer,RenewalSeedMode.class)"));
        assertFalse(codec.contains("generationSalt"), "The derived generation salt is server-only");
        assertFalse(setup.contains("generationSalt"), "Setup must select only the public mode");
        assertFalse(dashboard.contains("generationSalt"), "Dashboard must not render or transmit the salt");
        assertTrue(
                compactAdmin.contains("gameplay.preset(),identity"),
                "GUI initialization must persist identity and seed mode in the initialization transaction");
        assertFalse(
                compactAdmin.contains("withGameplay(gameplay).withIdentity(identity)"),
                "Identity must not be patched in a second post-initialization settings write");
        assertTrue(
                compactAdmin.contains("identity().renewal().equals(identity.renewal())")
                        && compactAdmin.contains("!AdminAccess.canManageWorld(player)"),
                "The server must reject configure-only attempts to mutate renewal settings");
        assertTrue(
                compactDashboard.contains("seedMode.active=this.snapshot.capabilities().canManageWorld()")
                        && compactDashboard.contains(":current.renewal()"),
                "The dashboard must make renewal controls read-only and preserve renewal for configure-only saves");
    }

    private static String compact(String source) {
        return source.replaceAll("\\s+", "");
    }
}
