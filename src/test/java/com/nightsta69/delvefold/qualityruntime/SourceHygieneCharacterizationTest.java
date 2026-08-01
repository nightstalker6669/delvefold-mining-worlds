package com.nightsta69.delvefold.qualityruntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class SourceHygieneCharacterizationTest {
    private static final Pattern SUPPRESSION_START = Pattern.compile("@SuppressWarnings\\b");
    private static final Pattern SINGLE_CATEGORY = Pattern.compile("(?:value\\s*=\\s*)?\"([^\"\\r\\n]+)\"");
    private static final Pattern TYPE_DECLARATION =
            Pattern.compile("(?:^|\\s)(?:@interface|class|enum|interface|record)\\s+([A-Za-z_$][A-Za-z0-9_$]*)");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
    private static final Set<OwnerKind> NARROW_OWNERS =
            EnumSet.of(OwnerKind.CALLABLE, OwnerKind.FIELD, OwnerKind.PARAMETER);
    private static final Comparator<SuppressionSite> SITE_ORDER = Comparator.comparing(SuppressionSite::path)
            .thenComparing(SuppressionSite::category)
            .thenComparing(site -> site.owner().kind())
            .thenComparing(site -> site.owner().signature());
    private static final List<SuppressionSite> RELEASED_SUPPRESSIONS = sorted(List.of(
            site(
                    "admin/DefaultDelvefoldAdminService.java",
                    "ReferenceEquality",
                    OwnerKind.CALLABLE,
                    "deletionRejection(failure)"),
            site(
                    "api/event/DelvefoldWorldLifecycleEvent.java",
                    "NullableOptional",
                    OwnerKind.CALLABLE,
                    "DelvefoldWorldLifecycleEvent(action,previous,current,operationId)"),
            site("audit/AuditActorContext.java", "ThreadLocalUsage", OwnerKind.FIELD, "stacks"),
            site("audit/AuditActorContext.java", "ReferenceEquality", OwnerKind.CALLABLE, "close()"),
            site("audit/DelvefoldAuditService.java", "ReferenceEquality", OwnerKind.CALLABLE, "stop(minecraftServer)"),
            site("client/gui/GuiEnumOrder.java", "EnumOrdinal", OwnerKind.CALLABLE, "index(value)"),
            site("command/DelvefoldCommands.java", "try", OwnerKind.CALLABLE, "asAuditActor(context,action)"),
            site("command/DelvefoldCommands.java", "try", OwnerKind.CALLABLE, "asAuditActorIo(context,action)"),
            site(
                    "command/DelvefoldCommands.java",
                    "ReferenceEquality",
                    OwnerKind.CALLABLE,
                    "deletionRejection(failure)"),
            site("compat/jei/DelvefoldJeiPortalCategory.java", "removal", OwnerKind.CALLABLE, "getBackground()"),
            site(
                    "config/DelvefoldConfigService.java",
                    "ReferenceEquality",
                    OwnerKind.CALLABLE,
                    "stop(minecraftServer)"),
            site(
                    "config/DelvefoldPermissions.java",
                    "unchecked",
                    OwnerKind.CALLABLE,
                    "node(name,defaultResolver,title,description)"),
            site(
                    "gameplay/SpawnPolicy.java",
                    "ReferenceEquality",
                    OwnerKind.CALLABLE,
                    "allowed(settings,type,spawnType)"),
            site("guide/GuideGameTests.java", "EnumOrdinal", OwnerKind.CALLABLE, "writeOversizedWireSnapshot(buffer)"),
            site(
                    "guide/GuideSnapshot.java",
                    "UnusedVariable",
                    OwnerKind.PARAMETER,
                    "estimatedNetworkBytes#formatVersion"),
            site(
                    "guide/MinecraftGuideIconResolver.java",
                    "ReferenceEquality",
                    OwnerKind.CALLABLE,
                    "representativeBlock(kind,sourceId)"),
            site("network/DelvefoldNetwork.java", "try", OwnerKind.CALLABLE, "invoke(player,call)"),
            site(
                    "network/codec/DelvefoldStreamCodecs.java",
                    "EnumOrdinal",
                    OwnerKind.CALLABLE,
                    "writeEnumOrdinal(buffer,value)"),
            site("network/codec/GuideStreamCodecs.java", "EnumOrdinal", OwnerKind.CALLABLE, "writeEnum(buffer,value)"),
            site(
                    "network/codec/OreForecastStreamCodecs.java",
                    "EnumOrdinal",
                    OwnerKind.CALLABLE,
                    "writeEnum(buffer,value)"),
            site(
                    "network/codec/OreImportStreamCodecs.java",
                    "EnumOrdinal",
                    OwnerKind.CALLABLE,
                    "writeEnum(buffer,value)"),
            site(
                    "portal/PortalGameTests.java",
                    "removal",
                    OwnerKind.CALLABLE,
                    "centralHubEventsDenyOrdinaryPlayersAndAllowWorldManagers(helper)"),
            site(
                    "world/feature/DelvefoldGameTests.java",
                    "ReferenceEquality",
                    OwnerKind.CALLABLE,
                    "defaultTargetWeightsPreserveTheLegacyRandomSequence(helper)"),
            site(
                    "world/feature/DelvefoldGameTests.java",
                    "ReferenceEquality",
                    OwnerKind.CALLABLE,
                    "configuredTargetWeightsAreDeterministicAndEffective(helper)"),
            site(
                    "world/feature/DelvefoldGameTests.java",
                    "ReferenceEquality",
                    OwnerKind.CALLABLE,
                    "tagWeightIsSharedAcrossSortedMembersAndOverlapsAreDeduplicated(helper)"),
            site(
                    "world/feature/GenerationSeedGameTests.java",
                    "ReferenceEquality",
                    OwnerKind.CALLABLE,
                    "zeroSaltLandmarksPreserveLegacyDrawOrderAndPosition(helper)"),
            site(
                    "world/feature/LivingGeologyGameTests.java",
                    "ReferenceEquality",
                    OwnerKind.CALLABLE,
                    "adjacentChunksUseTheSameDeterministicProvinceOutput(helper)"),
            site(
                    "world/landmark/GenerationSaltedRandomSpreadPlacement.java",
                    "deprecation",
                    OwnerKind.CALLABLE,
                    "GenerationSaltedRandomSpreadPlacement(locateOffset,frequencyReductionMethod,frequency,salt,exclusionZone,spacing,separation,spreadType)"),
            site(
                    "world/landmark/LandmarkGameTests.java",
                    "ReferenceEquality",
                    OwnerKind.CALLABLE,
                    "invalidCatalogReloadRetainsTheExactLastKnownGoodSnapshot(helper)"),
            site(
                    "world/landmark/LandmarkGameTests.java",
                    "ReferenceEquality",
                    OwnerKind.CALLABLE,
                    "landmarkPresetAcceptanceAndSelectionAreDeterministic(helper)"),
            site(
                    "world/landmark/LandmarkGameTests.java",
                    "ReferenceEquality",
                    OwnerKind.CALLABLE,
                    "capturedCatalogRevisionAndPlacementProbeCacheRemainCoherent(helper)"),
            site(
                    "world/landmark/catalog/LandmarkCatalogGameTests.java",
                    "ReferenceEquality",
                    OwnerKind.CALLABLE,
                    "malformedListenerInputRetainsTheExactLastKnownGoodCatalog(helper)")));

    @Test
    void productionHasNoConsoleDebuggingStackTracePrintingOrWorkMarkers() throws IOException {
        for (Path path : ProductionSources.allJavaFiles()) {
            String source = Files.readString(path);
            assertFalse(source.contains("System.out."), () -> "Console debugging remains in " + path);
            assertFalse(source.contains("System.err."), () -> "Console error printing remains in " + path);
            assertFalse(source.contains(".printStackTrace("), () -> "Direct stack-trace printing remains in " + path);
            assertFalse(
                    source.matches("(?s).*\\b(?:TODO|FIXME)\\b.*"), () -> "Unresolved work marker remains in " + path);
        }
    }

    @Test
    void suppressionsRemainNarrowAndNoNewSuppressionsAreAddedSilently() throws IOException {
        Path root = Path.of("src/main/java/com/nightsta69/delvefold");
        List<SuppressionSite> observedSuppressions = new ArrayList<>();
        for (Path path : ProductionSources.allJavaFiles()) {
            String source = Files.readString(path);
            assertFalse(source.contains("@NullUnmarked"), () -> "NullAway is disabled for an entire scope in " + path);

            String relative = root.relativize(path).toString().replace('\\', '/');
            for (SuppressionSite site : scanSuppressions(source, relative)) {
                assertTrue(
                        NARROW_OWNERS.contains(site.owner().kind()),
                        () -> "Broad " + site.owner().kind() + " warning suppression is forbidden at " + site);
                assertFalse(
                        site.category().equals("NullAway") || site.category().equals("all"),
                        () -> "Broad/nullness warning suppression is forbidden at " + site);
                observedSuppressions.add(site);
            }
        }

        observedSuppressions.sort(SITE_ORDER);
        assertEquals(
                RELEASED_SUPPRESSIONS,
                observedSuppressions,
                "Every suppression occurrence and its owning declaration must match production exactly");
    }

    @Test
    void suppressionScannerPreservesDuplicateSitesAndRecognizesNamedValueSyntax() {
        String source = """
                final class Fixture {
                    @SuppressWarnings(value = "unchecked")
                    void duplicate() {}

                    @SuppressWarnings("unchecked")
                    void duplicate() {}
                }
                """;

        List<SuppressionSite> observed = scanSuppressions(source, "Fixture.java");

        SuppressionSite duplicate = site("Fixture.java", "unchecked", OwnerKind.CALLABLE, "duplicate()");
        assertEquals(List.of(duplicate, duplicate), observed, "Duplicate suppression sites must not collapse");
    }

    @Test
    void suppressionScannerRejectsMultiCategorySyntaxAndIdentifiesBroadOwners() {
        assertThrows(
                IllegalArgumentException.class,
                () -> scanSuppressions(
                        "@SuppressWarnings(value = {\"unchecked\", \"rawtypes\"}) class Fixture {}", "Fixture.java"));

        List<SuppressionSite> broad =
                scanSuppressions("@SuppressWarnings(value = \"unchecked\") class Fixture {}", "Fixture.java");
        assertEquals(OwnerKind.TYPE, broad.getFirst().owner().kind());
    }

    private static List<SuppressionSite> scanSuppressions(String source, String path) {
        List<SuppressionSite> result = new ArrayList<>();
        Matcher matcher = SUPPRESSION_START.matcher(maskNonCode(source));
        while (matcher.find()) {
            int openingParenthesis = skipWhitespace(source, matcher.end());
            if (openingParenthesis >= source.length() || source.charAt(openingParenthesis) != '(') {
                throw malformedSuppression(path, matcher.start(), "missing argument list");
            }
            int closingParenthesis = matchingParenthesis(source, openingParenthesis);
            String arguments =
                    source.substring(openingParenthesis + 1, closingParenthesis).trim();
            Matcher categoryMatcher = SINGLE_CATEGORY.matcher(arguments);
            if (!categoryMatcher.matches()) {
                throw malformedSuppression(path, matcher.start(), "exactly one literal warning category is required");
            }
            DeclarationOwner owner = declarationOwner(source, matcher.start(), closingParenthesis + 1, path);
            result.add(new SuppressionSite(path, categoryMatcher.group(1), owner));
        }
        return List.copyOf(result);
    }

    private static DeclarationOwner declarationOwner(
            String source, int annotationStart, int annotationEnd, String path) {
        int declarationStart = skipDeclarationAnnotations(source, annotationEnd);
        DeclarationHead declaration = declarationHead(source, declarationStart, path);
        String head = declaration.text().trim();
        if (head.startsWith("package ") || head.startsWith("module ") || head.startsWith("open module ")) {
            return new DeclarationOwner(OwnerKind.PACKAGE_OR_MODULE, head);
        }
        Matcher type = TYPE_DECLARATION.matcher(head);
        if (type.find()) {
            return new DeclarationOwner(OwnerKind.TYPE, type.group(1));
        }
        if (declaration.delimiter() == ',') {
            String parameter = lastIdentifier(head, path);
            return new DeclarationOwner(
                    OwnerKind.PARAMETER, enclosingCallable(source, annotationStart, path) + "#" + parameter);
        }
        if (declaration.delimiter() == '=' || declaration.delimiter() == ';') {
            return new DeclarationOwner(OwnerKind.FIELD, lastIdentifier(head, path));
        }
        if (declaration.delimiter() == '{') {
            int parametersStart = firstTopLevelParenthesis(head);
            if (parametersStart >= 0) {
                String name = lastIdentifier(head.substring(0, parametersStart), path);
                int parametersEnd = matchingParenthesis(head, parametersStart);
                return new DeclarationOwner(
                        OwnerKind.CALLABLE,
                        name + "("
                                + String.join(
                                        ",", parameterNames(head.substring(parametersStart + 1, parametersEnd), path))
                                + ")");
            }
        }
        return new DeclarationOwner(OwnerKind.OTHER, head);
    }

    private static int skipDeclarationAnnotations(String source, int offset) {
        int cursor = skipTrivia(source, offset);
        while (cursor < source.length() && source.charAt(cursor) == '@') {
            int nameEnd = cursor + 1;
            while (nameEnd < source.length()
                    && (Character.isJavaIdentifierPart(source.charAt(nameEnd)) || source.charAt(nameEnd) == '.')) {
                nameEnd++;
            }
            if (source.startsWith("@interface", cursor)) {
                break;
            }
            cursor = skipTrivia(source, nameEnd);
            if (cursor < source.length() && source.charAt(cursor) == '(') {
                cursor = matchingParenthesis(source, cursor) + 1;
            }
            cursor = skipTrivia(source, cursor);
        }
        return cursor;
    }

    private static DeclarationHead declarationHead(String source, int start, String path) {
        int parenthesisDepth = 0;
        int angleDepth = 0;
        int bracketDepth = 0;
        for (int cursor = start; cursor < source.length(); cursor++) {
            char current = source.charAt(cursor);
            if (current == '"' || current == '\'') {
                cursor = quotedEnd(source, cursor, current);
                continue;
            }
            if (current == '/' && cursor + 1 < source.length()) {
                char next = source.charAt(cursor + 1);
                if (next == '/' || next == '*') {
                    cursor = commentEnd(source, cursor, next);
                    continue;
                }
            }
            if (current == '(') {
                parenthesisDepth++;
            } else if (current == ')') {
                parenthesisDepth--;
            } else if (current == '<' && parenthesisDepth == 0) {
                angleDepth++;
            } else if (current == '>' && parenthesisDepth == 0 && angleDepth > 0) {
                angleDepth--;
            } else if (current == '[') {
                bracketDepth++;
            } else if (current == ']') {
                bracketDepth--;
            } else if (parenthesisDepth == 0
                    && angleDepth == 0
                    && bracketDepth == 0
                    && (current == '{' || current == ';' || current == '=' || current == ',')) {
                return new DeclarationHead(source.substring(start, cursor), current);
            }
        }
        throw new IllegalArgumentException("Cannot find declaration after suppression in " + path);
    }

    private static int firstTopLevelParenthesis(String declaration) {
        int angleDepth = 0;
        for (int cursor = 0; cursor < declaration.length(); cursor++) {
            char current = declaration.charAt(cursor);
            if (current == '<') {
                angleDepth++;
            } else if (current == '>' && angleDepth > 0) {
                angleDepth--;
            } else if (current == '(' && angleDepth == 0) {
                return cursor;
            }
        }
        return -1;
    }

    private static List<String> parameterNames(String parameters, String path) {
        if (parameters.isBlank()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        int start = 0;
        int angleDepth = 0;
        int parenthesisDepth = 0;
        int bracketDepth = 0;
        for (int cursor = 0; cursor <= parameters.length(); cursor++) {
            char current = cursor == parameters.length() ? ',' : parameters.charAt(cursor);
            if (current == '<') {
                angleDepth++;
            } else if (current == '>' && angleDepth > 0) {
                angleDepth--;
            } else if (current == '(') {
                parenthesisDepth++;
            } else if (current == ')') {
                parenthesisDepth--;
            } else if (current == '[') {
                bracketDepth++;
            } else if (current == ']') {
                bracketDepth--;
            } else if (current == ',' && angleDepth == 0 && parenthesisDepth == 0 && bracketDepth == 0) {
                names.add(lastIdentifier(parameters.substring(start, cursor), path));
                start = cursor + 1;
            }
        }
        return List.copyOf(names);
    }

    private static String enclosingCallable(String source, int offset, String path) {
        int closingDepth = 0;
        for (int cursor = offset - 1; cursor >= 0; cursor--) {
            char current = source.charAt(cursor);
            if (current == ')') {
                closingDepth++;
            } else if (current == '(') {
                if (closingDepth == 0) {
                    return lastIdentifier(source.substring(0, cursor), path);
                }
                closingDepth--;
            }
        }
        throw new IllegalArgumentException("Cannot find enclosing callable for parameter suppression in " + path);
    }

    private static String lastIdentifier(String source, String path) {
        Matcher matcher = IDENTIFIER.matcher(source);
        String last = "";
        while (matcher.find()) {
            last = matcher.group();
        }
        if (last.isEmpty()) {
            throw new IllegalArgumentException("Cannot identify suppressed declaration in " + path);
        }
        return last;
    }

    private static int matchingParenthesis(String source, int openingParenthesis) {
        int depth = 0;
        for (int cursor = openingParenthesis; cursor < source.length(); cursor++) {
            char current = source.charAt(cursor);
            if (current == '"' || current == '\'') {
                cursor = quotedEnd(source, cursor, current);
            } else if (current == '(') {
                depth++;
            } else if (current == ')' && --depth == 0) {
                return cursor;
            }
        }
        throw new IllegalArgumentException("Unterminated parenthesized source declaration");
    }

    private static int skipTrivia(String source, int offset) {
        int cursor = offset;
        while (cursor < source.length()) {
            int whitespaceEnd = skipWhitespace(source, cursor);
            cursor = whitespaceEnd;
            if (cursor + 1 >= source.length() || source.charAt(cursor) != '/') {
                return cursor;
            }
            char next = source.charAt(cursor + 1);
            if (next != '/' && next != '*') {
                return cursor;
            }
            cursor = commentEnd(source, cursor, next) + 1;
        }
        return cursor;
    }

    private static int skipWhitespace(String source, int offset) {
        int cursor = offset;
        while (cursor < source.length() && Character.isWhitespace(source.charAt(cursor))) {
            cursor++;
        }
        return cursor;
    }

    private static int commentEnd(String source, int slash, char commentType) {
        if (commentType == '/') {
            int newline = source.indexOf('\n', slash + 2);
            return newline < 0 ? source.length() - 1 : newline;
        }
        int closing = source.indexOf("*/", slash + 2);
        if (closing < 0) {
            throw new IllegalArgumentException("Unterminated block comment");
        }
        return closing + 1;
    }

    private static int quotedEnd(String source, int quote, char delimiter) {
        for (int cursor = quote + 1; cursor < source.length(); cursor++) {
            char current = source.charAt(cursor);
            if (current == '\\') {
                cursor++;
            } else if (current == delimiter) {
                return cursor;
            }
        }
        throw new IllegalArgumentException("Unterminated quoted source value");
    }

    private static String maskNonCode(String source) {
        char[] mask = source.toCharArray();
        for (int cursor = 0; cursor < source.length(); cursor++) {
            char current = source.charAt(cursor);
            if (current == '"' || current == '\'') {
                int end = quotedEnd(source, cursor, current);
                blank(mask, cursor, end);
                cursor = end;
            } else if (current == '/' && cursor + 1 < source.length()) {
                char next = source.charAt(cursor + 1);
                if (next == '/' || next == '*') {
                    int end = commentEnd(source, cursor, next);
                    blank(mask, cursor, end);
                    cursor = end;
                }
            }
        }
        return new String(mask);
    }

    private static void blank(char[] target, int start, int end) {
        for (int cursor = start; cursor <= end; cursor++) {
            if (target[cursor] != '\n' && target[cursor] != '\r') {
                target[cursor] = ' ';
            }
        }
    }

    private static IllegalArgumentException malformedSuppression(String path, int offset, String reason) {
        return new IllegalArgumentException(
                "Malformed @SuppressWarnings in " + path + " at offset " + offset + ": " + reason);
    }

    private static SuppressionSite site(String path, String category, OwnerKind kind, String signature) {
        return new SuppressionSite(path, category, new DeclarationOwner(kind, signature));
    }

    private static List<SuppressionSite> sorted(List<SuppressionSite> sites) {
        return sites.stream().sorted(SITE_ORDER).toList();
    }

    private enum OwnerKind {
        CALLABLE,
        FIELD,
        PARAMETER,
        TYPE,
        PACKAGE_OR_MODULE,
        OTHER
    }

    private record DeclarationHead(String text, char delimiter) {}

    private record DeclarationOwner(OwnerKind kind, String signature) {}

    private record SuppressionSite(String path, String category, DeclarationOwner owner) {}
}
