package com.nightsta69.delvefold.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Characterizes the public command root, domain vocabulary, and stable registration facade. */
class CommandSurfaceCompatibilityTest {
    private static final Path COMMANDS =
            Path.of("src/main/java/com/nightsta69/delvefold/command/DelvefoldCommands.java");
    private static final Path COMMAND_PACKAGE = COMMANDS.getParent();
    private static final Path COMMAND_NAMES =
            Path.of("src/main/java/com/nightsta69/delvefold/command/DelvefoldCommandNames.java");
    private static final Pattern LITERAL = Pattern.compile("Commands\\.literal\\(\\s*\"([^\"]+)\"\\s*\\)");
    private static final Set<String> DOMAIN_LITERALS = Set.of(
            "add", "add-tag", "backup", "band", "cancel", "config", "configure", "confirm",
            "create", "delete", "disable", "doctor", "duplicate", "enable", "export",
            "geology-theme", "gui", "guide", "hub", "identity", "import", "initialize",
            "landmarks", "list", "name", "ore", "overwrite", "pin", "placement", "portal",
            "profile", "province", "recreate", "reload", "remove", "remove-tag", "renewal",
            "request", "restore", "retention", "routing", "save-current", "scan", "seed-mode",
            "select", "set", "set-tag-weight", "set-weight", "show", "status", "target", "unpin",
            "validate", "variant", "verify", "visibility", "world");

    @Test
    void delvefoldRemainsTheOnlyRegisteredRoot() throws Exception {
        String names = compact(Files.readString(COMMAND_NAMES));
        assertTrue(names.contains("REGISTERED_ROOTS=List.of(\"delvefold\")"),
                "The public root must remain /delvefold with no legacy or accidental aliases");
    }

    @Test
    void registrationMethodsRemainPublicStaticFacades() throws Exception {
        String commands = compact(Files.readString(COMMANDS));
        assertTrue(commands.contains("publicstaticvoidonRegisterCommands(RegisterCommandsEventevent){"
                + "register(event.getDispatcher());}"));
        assertTrue(commands.contains("publicstaticvoidregister(CommandDispatcher<CommandSourceStack>dispatcher){"));
        assertTrue(commands.contains("for(Stringname:DelvefoldCommandNames.REGISTERED_ROOTS){"
                + "dispatcher.register(root(name));}"));
    }

    @Test
    void commandDomainVocabularyMatchesTheVersion130Surface() throws Exception {
        Set<String> actual = new TreeSet<>();
        Set<Path> commandSources;
        try (var files = Files.list(COMMAND_PACKAGE)) {
            commandSources = files
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .collect(Collectors.toSet());
        }
        for (Path commandSource : commandSources) {
            var matcher = LITERAL.matcher(Files.readString(commandSource));
            while (matcher.find()) {
                actual.add(matcher.group(1));
            }
        }
        assertEquals(new TreeSet<>(DOMAIN_LITERALS), actual,
                "A command literal was removed, renamed, or added without an intentional compatibility decision");
    }

    private static String compact(String source) {
        return source.replaceAll("\\s+", "");
    }
}
