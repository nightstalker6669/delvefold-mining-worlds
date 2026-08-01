package com.nightsta69.delvefold.client.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ClientAccessibilitySourceContractTest {
    private static final Path CLIENT_ROOT = Path.of("src/main/java/com/nightsta69/delvefold/client");
    private static final Pattern STATIC_ENGLISH_LITERAL = Pattern.compile(
            "Component\\.literal\\(\\s*\\\"[A-Za-z]");

    @Test
    void scopedScreensDoNotEmbedStaticEnglishComponents() throws IOException {
        try (var sources = Files.walk(CLIENT_ROOT)) {
            List<Path> files = sources
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
            for (Path source : files) {
                String text = Files.readString(source);
                assertFalse(STATIC_ENGLISH_LITERAL.matcher(text).find(),
                        () -> "Static English Component.literal remains in " + source);
            }
        }
    }

    @Test
    void selectionAndFocusStatesHaveNonColorCues() throws IOException {
        String base = Files.readString(CLIENT_ROOT.resolve("gui/DelvefoldScreen.java"));
        String setup = Files.readString(CLIENT_ROOT.resolve("gui/DelvefoldSetupScreen.java"));
        String wizard = Files.readString(CLIENT_ROOT.resolve("gui/DelvefoldOreRuleWizardScreen.java"));
        String dashboard = Files.readString(CLIENT_ROOT.resolve("gui/DelvefoldDashboardScreen.java"));
        String backups = Files.readString(CLIENT_ROOT.resolve("gui/DelvefoldBackupScreen.java"));

        assertTrue(base.contains("this.setInitialFocus(widget)"),
                "Every administration screen needs a keyboard-focus fallback");
        assertTrue(setup.contains("DelvefoldText.choice("),
                "Selected setup choices must be labeled as well as colored");
        assertTrue(wizard.contains("screen.delvefold.status.missing"));
        assertTrue(wizard.contains("screen.delvefold.status.on"));
        assertTrue(wizard.contains("screen.delvefold.status.off"));
        assertTrue(dashboard.contains("screen.delvefold.dashboard.narration"),
                "The dashboard must narrate its selected section and readiness state");
        assertTrue(dashboard.contains("DelvefoldText.serverMessage(this.snapshot.portalStatus())"),
                "Server-authorized dashboard status must retain client localization");
        assertTrue(backups.contains("screen.delvefold.backup.narration"),
                "The backup screen must narrate page and confirmation state");
        assertTrue(backups.contains("screen.delvefold.backup.restore.tooltip.unavailable"),
                "Disabled backup actions need a non-color explanation");
        assertTrue(wizard.contains("FormattedCharSequence clipped(Component text"),
                "Wizard text clipping must preserve translated component styling");
        assertFalse(wizard.contains("plainSubstrByWidth(this.validationMessage.getString()"),
                "Wizard validation text must not round-trip translated components through plain strings");
        assertFalse(wizard.contains("Component.literal(exception.getMessage())"),
                "Wizard validation failures must retain their translatable component");
        assertFalse(wizard.contains("Component.translatable(\n                        \"screen.delvefold.ore_wizard.validation.state_format\").getString()"),
                "Wizard validation exceptions must not flatten translated components into English");
    }

    @Test
    void encodedImportDiffMessagesAreResolvedBeforeRendering() throws IOException {
        String importScreen = Files.readString(CLIENT_ROOT.resolve("gui/DelvefoldOreImportScreen.java"));

        assertTrue(importScreen.contains("DelvefoldText.serverMessage(entry.message())"));
        assertFalse(importScreen.contains("Component.literal(entry.message())"));
    }
}
